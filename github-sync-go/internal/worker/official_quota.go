package worker

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"sync"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/controlplane"
	"github.com/iflytek/skillhub/github-sync-go/internal/github"
)

// A single bounded process gate prevents token rotation from bypassing secondary
// cooldown or a failed persistence attempt. Primary limits are scoped to the credential revision and resource.
var officialProcessQuota = struct {
	sync.Mutex
	globalUntil   time.Time
	resourceUntil map[string]time.Time
	failures      int
	lastFailure   time.Time
}{resourceUntil: make(map[string]time.Time)}

type officialRequestPolicy interface {
	SetRequestPolicy(func(context.Context) error, func(context.Context, github.Quota, error) error)
}

type officialQuotaGuard struct {
	control  *controlplane.Client
	task     *controlplane.ClaimedTask
	resource string
	stopped  error
}

func installOfficialQuotaGuard(control *controlplane.Client, task *controlplane.ClaimedTask, upstream any) (*officialQuotaGuard, error) {
	client, ok := upstream.(officialRequestPolicy)
	if !ok {
		return nil, errors.New("official GitHub client lacks quota request policy")
	}
	resource := "CORE"
	if task.OfficialDiscovery != nil && task.OfficialDiscovery.Channel != "OWNER_EXPANSION" {
		resource = "SEARCH"
	}
	guard := &officialQuotaGuard{control: control, task: task, resource: resource}
	client.SetRequestPolicy(guard.before, guard.after)
	return guard, nil
}

func (g *officialQuotaGuard) before(ctx context.Context) error {
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if g.stopped != nil {
		return g.stopped
	}
	officialProcessQuota.Lock()
	blocked := time.Now().Before(officialProcessQuota.globalUntil) || time.Now().Before(officialProcessQuota.resourceUntil[g.quotaKey(g.resource)]) || time.Now().Before(officialProcessQuota.resourceUntil[g.quotaKey("*")])
	officialProcessQuota.Unlock()
	if blocked {
		g.stopped = errors.New("official GitHub requests are in process quota cooldown")
	}
	return g.stopped
}

func (g *officialQuotaGuard) after(ctx context.Context, quota github.Quota, requestErr error) error {
	return g.observe(ctx, quota, requestErr, g.resource)
}

func (g *officialQuotaGuard) afterGit(ctx context.Context, requestErr error) error {
	// Git HTTPS exposes no REST quota headers. UNKNOWN is not a fabricated CORE
	// observation; a failed transport requests a conservative shared cooldown.
	return g.observe(ctx, github.Quota{Remaining: -1}, requestErr, "OTHER")
}

func (g *officialQuotaGuard) beforeGit(ctx context.Context) error {
	if err := g.before(ctx); err != nil {
		return err
	}
	// Another worker may have persisted a deployment-wide cooldown while this
	// package was admitted. Finish local delivery, but do not fetch more blobs.
	control, err := g.control.GetOfficialTaskControl(ctx, g.task.TaskID, g.task.Lease)
	if err != nil {
		g.stopped = err
	} else if control.Action == "PAUSE" && control.Reason == "QUOTA_COOLDOWN" {
		g.stopped = errors.New("official Git acquisition is in quota cooldown")
	}
	return g.stopped
}

// allowsOptionalGit is the read-only form of beforeGit for optional requests such
// as source tag resolution: it honours process and deployment-wide cooldowns but
// never stops the guard, so the package path decides exactly as before.
func (g *officialQuotaGuard) allowsOptionalGit(ctx context.Context) bool {
	if g.stopped != nil || ctx.Err() != nil {
		return false
	}
	officialProcessQuota.Lock()
	blocked := time.Now().Before(officialProcessQuota.globalUntil) || time.Now().Before(officialProcessQuota.resourceUntil[g.quotaKey(g.resource)]) || time.Now().Before(officialProcessQuota.resourceUntil[g.quotaKey("*")])
	officialProcessQuota.Unlock()
	if blocked {
		return false
	}
	control, err := g.control.GetOfficialTaskControl(ctx, g.task.TaskID, g.task.Lease)
	return err == nil && !(control.Action == "PAUSE" && control.Reason == "QUOTA_COOLDOWN")
}

func (g *officialQuotaGuard) observe(ctx context.Context, observed github.Quota, requestErr error, resource string) error {
	now := time.Now().UTC()
	observation := controlplane.OfficialQuotaObservation{Resource: resource, ObservedAt: now, State: "UNKNOWN"}
	quota := credentialQuota(observed)
	if quota != nil && quota.ResetAt.Before(now.Add(24*time.Hour)) {
		observation.Resource, observation.State = quota.Resource, "OBSERVED"
		observation.Remaining, observation.ResetAt = &quota.Remaining, &quota.ResetAt
	}
	var upstreamError *github.APIError
	var until time.Time
	global := false
	if errors.As(requestErr, &upstreamError) && upstreamError.Code == "AUTH_INVALID" {
		observation.State, until = "AUTH_INVALID", now.Add(time.Minute)
		global = g.task == nil || g.task.Credential == nil || g.task.Credential.Token == ""
	} else if errors.As(requestErr, &upstreamError) && upstreamError.Code == "GITHUB_RATE_LIMITED" {
		if quota != nil && quota.Remaining == 0 && quota.ResetAt.After(now) && quota.ResetAt.Before(now.Add(24*time.Hour)) && !upstreamError.SecondaryRateLimit {
			observation.State, until = "PRIMARY_EXHAUSTED", quota.ResetAt
		} else {
			observation.State, until, global = "SECONDARY_COOLDOWN", upstreamError.RetryAt, true
			if !until.After(now) {
				until = now.Add(time.Minute)
			}
			if until.After(now.Add(24 * time.Hour)) {
				until = now.Add(24 * time.Hour)
			}
		}
	} else if quota != nil && observation.State == "OBSERVED" && quota.Remaining == 0 && quota.ResetAt.After(now) {
		observation.State, until = "PRIMARY_EXHAUSTED", quota.ResetAt
	} else if requestErr != nil && observation.State == "UNKNOWN" {
		until, global = now.Add(time.Minute), true
	}
	if !until.IsZero() {
		seconds := int(until.Sub(now).Seconds())
		if seconds < 1 {
			seconds = 1
		}
		observation.RetryAfterSeconds = &seconds
		key := g.quotaKey(observation.Resource)
		if observation.State == "AUTH_INVALID" {
			key = g.quotaKey("*")
		}
		rememberOfficialCooldown(key, until, global)
	}
	// Missing headers always retain nil balances and reset time, including Git.
	reportCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := g.control.ReportOfficialQuota(reportCtx, g.task.TaskID, g.task.Lease, observation); err != nil {
		rememberOfficialPersistenceFailure(now)
		g.stopped = errors.New("official quota persistence failed; GitHub requests stopped")
		return g.stopped
	}
	if !until.IsZero() {
		g.stopped = errors.New("official GitHub quota requires cooldown")
		if requestErr != nil {
			return requestErr
		}
		return g.stopped
	}
	return nil
}

// Token digests are process-local keys, never persisted or logged. A rotated token
// must not inherit the previous token's primary exhaustion. Anonymous requests
// share one key; different tokens are not assumed to have independent balances.
func (g *officialQuotaGuard) quotaKey(resource string) string {
	if g.task == nil || g.task.Credential == nil || g.task.Credential.Token == "" {
		return "anonymous:" + resource
	}
	digest := sha256.Sum256([]byte(g.task.Credential.Token))
	return hex.EncodeToString(digest[:]) + ":" + resource
}

func rememberOfficialCooldown(resource string, until time.Time, global bool) {
	officialProcessQuota.Lock()
	defer officialProcessQuota.Unlock()
	for key, expiry := range officialProcessQuota.resourceUntil {
		if !expiry.After(time.Now()) {
			delete(officialProcessQuota.resourceUntil, key)
		}
	}
	if global {
		if until.After(officialProcessQuota.globalUntil) {
			officialProcessQuota.globalUntil = until
		}
	} else if until.After(officialProcessQuota.resourceUntil[resource]) {
		officialProcessQuota.resourceUntil[resource] = until
	}
}

func rememberOfficialPersistenceFailure(now time.Time) {
	officialProcessQuota.Lock()
	defer officialProcessQuota.Unlock()
	if now.Sub(officialProcessQuota.lastFailure) > 15*time.Minute {
		officialProcessQuota.failures = 0
	}
	officialProcessQuota.failures = min(officialProcessQuota.failures+1, 5)
	officialProcessQuota.lastFailure = now
	until := now.Add(min(time.Minute*time.Duration(1<<uint(officialProcessQuota.failures-1)), 15*time.Minute))
	if until.After(officialProcessQuota.globalUntil) {
		officialProcessQuota.globalUntil = until
	}
}
