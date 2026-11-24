package worker

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/controlplane"
	"github.com/iflytek/skillhub/github-sync-go/internal/github"
	"github.com/iflytek/skillhub/github-sync-go/internal/repository"
)

type RepositoryLimits struct {
	TempRoot          string
	AllowedS3Hosts    []string
	MaxTreeRequests   int
	MaxTreeEntries    int
	MaxWorkspaceBytes int64
	MinFreeBytes      int64
}

type repositoryGitHub interface {
	repository.TreeReader
	SetQuotaObserver(func(context.Context, github.Quota))
	GetRepository(context.Context, string, string) (github.Repository, github.Quota, error)
	GetHead(context.Context, github.Repository) (string, github.Quota, error)
	GetCommitTreeSHA(context.Context, string, string, string) (string, github.Quota, error)
}

// ProcessRepository keeps all business progress in Java. A returned error means
// the caller should classify and report task failure while its lease is current.
func ProcessRepository(ctx context.Context, control *controlplane.Client,
	task *controlplane.ClaimedTask, limits RepositoryLimits) error {
	if task == nil {
		return errors.New("invalid repository task or worker limits")
	}
	return processRepository(ctx, control, task, limits, github.NewClient(credentialToken(task)))
}

func processRepository(ctx context.Context, control *controlplane.Client,
	task *controlplane.ClaimedTask, limits RepositoryLimits, upstream repositoryGitHub) error {
	if task == nil || task.Kind != "REPOSITORY_SYNC" || task.Repository == nil ||
		control == nil || upstream == nil ||
		limits.MaxTreeRequests < 1 || limits.MaxTreeEntries < 1 ||
		limits.MaxWorkspaceBytes < 1 || limits.MinFreeBytes < 0 || limits.TempRoot == "" {
		return errors.New("invalid repository task or worker limits")
	}
	snapshot := task.Repository
	completion := controlplane.RepositoryCompletion{
		CommitSHA: snapshot.FixedCommitSHA, PathCheckpoint: snapshot.PathCheckpoint,
		IncompleteSubtrees: append([]string(nil), snapshot.IncompleteSubtrees...),
	}
	if state, ok := ctx.Value(platformContextKey{}).(*platformExecution); ok {
		state.completion = &completion
	}
	if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
		return err
	}
	var quotaGuard *officialQuotaGuard
	if repositoryNeedsOfficialControl(task) {
		guard, err := installOfficialQuotaGuard(control, task, upstream)
		if err != nil {
			return err
		}
		quotaGuard = guard
		ctx = repository.WithGitRequestPolicy(ctx, guard.beforeGit, guard.afterGit)
	} else {
		upstream.SetQuotaObserver(observeCredentialQuota(control, task))
	}
	metadata, _, err := upstream.GetRepository(ctx, snapshot.Owner, snapshot.Name)
	if err != nil {
		return err
	}
	if metadata.ID != snapshot.RepositoryID {
		return errors.New("GitHub repository ID changed for task source")
	}
	if err := control.UpdateRepositoryMetadata(ctx, task.TaskID, task.Lease,
		controlplane.RepositoryMetadata{RepositoryID: metadata.ID, OwnerID: metadata.Owner.ID,
			Owner: metadata.Owner.Login, Name: metadata.Name,
			DefaultBranch: metadata.DefaultBranch}); err != nil {
		return err
	}
	if task.Mode == "HEAD_ONLY" {
		if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
			return err
		}
		head, _, err := upstream.GetHead(ctx, metadata)
		if err != nil {
			return err
		}
		if completion.CommitSHA == "" {
			completion.CommitSHA = head
		}
		if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
			return err
		}
		return control.CompleteHeadOnly(ctx, task.TaskID, task.Lease, head)
	}
	if task.Mode != "FULL" || task.MaxFilesPerPackage < 1 || task.MaxPackageBytes < 1 ||
		task.MaxSingleFileBytes < 1 || task.MaxUploadBytes < 1 {
		return errors.New("repository task has no valid package policy")
	}
	commitSHA := snapshot.FixedCommitSHA
	if commitSHA == "" {
		if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
			return err
		}
		commitSHA, _, err = upstream.GetHead(ctx, metadata)
		if err != nil {
			return err
		}
	}
	completion.CommitSHA = commitSHA
	if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
		return err
	}
	treeSHA, _, err := upstream.GetCommitTreeSHA(ctx, metadata.Owner.Login, metadata.Name, commitSHA)
	if err != nil {
		return err
	}
	var treeReader repository.TreeReader = upstream
	treeContext := ctx
	var guardedTree *officialRepositoryTreeReader
	if repositoryNeedsOfficialControl(task) {
		var cancelTree context.CancelFunc
		treeContext, cancelTree = context.WithCancel(ctx)
		defer cancelTree()
		// An interrupted walk is replayed on resume; it is never complete coverage.
		completion.IncompleteSubtrees = []string{"."}
		guardedTree = &officialRepositoryTreeReader{reader: upstream, stop: cancelTree,
			before: func(ctx context.Context) (bool, error) {
				return pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion)
			}}
		treeReader = guardedTree
	}
	tree, treeErr := repository.EnumerateTree(treeContext, treeReader, metadata.Owner.Login,
		metadata.Name, treeSHA, repository.TreeLimits{
			MaxTreeRequests: limits.MaxTreeRequests, MaxEntries: limits.MaxTreeEntries,
		})
	if guardedTree != nil && guardedTree.stopped {
		return guardedTree.err
	}
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if treeErr != nil && len(tree.Entries) == 0 && len(tree.IncompleteSubtrees) == 0 {
		return treeErr
	}
	roots := repository.RootsFromSnapshot(tree, snapshot.IgnorePatterns)
	preflight, err := repository.PreflightPackages(tree, roots.Paths, repository.PackageLimits{
		MaxFiles: task.MaxFilesPerPackage, MaxSingleFileBytes: task.MaxSingleFileBytes,
		MaxPackageBytes: task.MaxPackageBytes,
	})
	if err != nil {
		return err
	}
	plans := make(map[string]repository.PackagePlan, len(preflight.Plans))
	failures := make(map[string]repository.PackageFailure, len(preflight.Failures))
	paths := make([]string, 0, len(preflight.Plans)+len(preflight.Failures))
	for _, plan := range preflight.Plans {
		plans[plan.Root] = plan
		paths = append(paths, plan.Root)
	}
	for _, failure := range preflight.Failures {
		failures[failure.Root] = failure
		paths = append(paths, failure.Root)
	}
	retryPaths := make(map[string]struct{}, len(snapshot.RetryPaths))
	acceptedPaths := make(map[string]struct{}, len(snapshot.AcceptedPaths))
	for _, path := range snapshot.AcceptedPaths {
		acceptedPaths[path] = struct{}{}
	}
	for _, path := range snapshot.RetryPaths {
		retryPaths[path] = struct{}{}
		if _, planned := plans[path]; !planned {
			if _, failed := failures[path]; !failed && len(roots.IncompleteSubtrees) == 0 {
				failures[path] = repository.PackageFailure{Root: path, Code: "SKILL_ROOT_NOT_FOUND"}
				paths = append(paths, path)
			}
		}
	}
	sort.Strings(paths)
	completion.IncompleteSubtrees = append([]string(nil), roots.IncompleteSubtrees...)
	// Resolved at most once, only when a package is actually delivered.
	var sourceTag *controlplane.SourceTag
	for _, path := range paths {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if skipPersistedPath(path, snapshot.PathCheckpoint, acceptedPaths, retryPaths) {
			continue
		}
		if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
			return err
		}
		if quotaGuard != nil {
			if err := quotaGuard.before(ctx); err != nil {
				return completeDeferred(ctx, control, task, &completion, "RATE_LIMIT", time.Now().Add(time.Minute))
			}
		}
		if failure, failed := failures[path]; failed {
			outcome := "TERMINAL_FAILURE"
			if failure.Retryable {
				outcome = "RETRYABLE_FAILURE"
			}
			completion.PathResults = append(completion.PathResults, controlplane.RepositoryPathResult{
				SkillPath: path, Outcome: outcome, Reason: failure.Code,
			})
			advanceCheckpoint(&completion, path)
			continue
		}
		admission, err := control.AdmitPackage(ctx, task.TaskID, task.Lease, commitSHA, path)
		if err != nil {
			var apiError *controlplane.APIError
			if errors.As(err, &apiError) && apiError.Code == "BACKPRESSURE_ACTIVE" {
				return completeDeferred(ctx, control, task, &completion, "BACKPRESSURE", time.Now().Add(15*time.Minute))
			}
			if errors.As(err, &apiError) && apiError.Code == "PATH_IGNORED" {
				completion.PathResults = append(completion.PathResults, controlplane.RepositoryPathResult{
					SkillPath: path, Outcome: "IGNORED", Reason: "SKILL_PATH_IGNORED",
				})
				advanceCheckpoint(&completion, path)
				continue
			}
			return err
		}
		if sourceTag == nil {
			sourceTag = resolveTaskSourceTag(ctx, quotaGuard, metadata, commitSHA, limits)
		}
		artifact, err := acquirePackage(ctx, metadata.Owner.Login, metadata.Name,
			commitSHA, limits.TempRoot, plans[path], repository.AcquisitionLimits{
				MaxWorkspaceBytes: limits.MaxWorkspaceBytes, MaxArchiveBytes: task.MaxUploadBytes,
				MinFreeBytes: limits.MinFreeBytes,
			})
		if err != nil {
			if ctx.Err() != nil {
				return ctx.Err()
			}
			if errors.Is(err, repository.ErrCapacity) {
				return completeDeferred(ctx, control, task, &completion, "CAPACITY", time.Now().Add(24*time.Hour))
			}
			if errors.Is(err, repository.ErrLocalDiskBudget) {
				return completeDeferred(ctx, control, task, &completion, "LOCAL_DISK_BUDGET", time.Now().Add(15*time.Minute))
			}
			outcome := "RETRYABLE_FAILURE"
			reason := "PACKAGE_ACQUISITION_FAILED"
			if errors.Is(err, repository.ErrArchiveTooLarge) {
				outcome, reason = "TERMINAL_FAILURE", "ARCHIVE_TOO_LARGE"
			}
			completion.PathResults = append(completion.PathResults, controlplane.RepositoryPathResult{
				SkillPath: path, Outcome: outcome, Reason: reason,
			})
			advanceCheckpoint(&completion, path)
			continue
		}
		if err := beginPlatformDelivery(ctx); err != nil {
			if closeErr := artifact.Close(); closeErr != nil {
				return fmt.Errorf("remove temporary package: %w", closeErr)
			}
			return err
		}
		err = deliverPackage(ctx, control, task, admission, commitSHA, path,
			artifact, limits.AllowedS3Hosts, sourceTag)
		closeErr := artifact.Close()
		endPlatformDelivery(ctx)
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if closeErr != nil {
			return fmt.Errorf("remove temporary package: %w", closeErr)
		}
		if err != nil {
			completion.PathResults = append(completion.PathResults, controlplane.RepositoryPathResult{
				SkillPath: path, Outcome: "RETRYABLE_FAILURE", Reason: "PACKAGE_DELIVERY_FAILED",
			})
		}
		advanceCheckpoint(&completion, path)
	}
	if treeErr != nil {
		var apiError *github.APIError
		if errors.As(treeErr, &apiError) && apiError.Code == "GITHUB_RATE_LIMITED" {
			if reportErr := reportCredentialProblem(ctx, control, task, treeErr); reportErr != nil {
				return reportErr
			}
			return completeDeferred(ctx, control, task, &completion, "RATE_LIMIT", futureRetry(apiError.RetryAt))
		}
		if len(completion.IncompleteSubtrees) == 0 {
			completion.IncompleteSubtrees = []string{"."}
		}
	}
	if len(completion.IncompleteSubtrees) > 0 || hasRetryable(completion.PathResults) {
		return completeDeferred(ctx, control, task, &completion, "BUDGET", time.Now().Add(30*time.Minute))
	}
	if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, &completion); err != nil || paused {
		return err
	}
	_, err = control.CompleteRepository(ctx, task.TaskID, task.Lease, completion)
	return err
}

func skipPersistedPath(path, checkpoint string, acceptedPaths, retryPaths map[string]struct{}) bool {
	if _, accepted := acceptedPaths[path]; accepted {
		return true
	}
	_, retry := retryPaths[path]
	return checkpoint != "" && path <= checkpoint && !retry
}

func deliverPackage(ctx context.Context, control *controlplane.Client,
	task *controlplane.ClaimedTask, admission controlplane.PackageAdmission,
	commitSHA, path string, artifact *repository.Artifact, allowedS3Hosts []string,
	sourceTag *controlplane.SourceTag) error {
	authorization, err := control.AuthorizeUpload(ctx, task.TaskID, task.Lease,
		admission.AdmissionID, commitSHA, path, artifact.Size, artifact.SHA256)
	if err != nil {
		return err
	}
	if err := control.UploadPackage(ctx, authorization, artifact.Path, allowedS3Hosts); err != nil {
		return err
	}
	_, err = control.ReportPackage(ctx, task.TaskID, task.Lease, admission.AdmissionID,
		commitSHA, path, authorization, artifact.Size, artifact.SHA256, sourceTag)
	return err
}

// Test seams; production acquires packages and resolves tags through public Git.
var (
	acquirePackage   = repository.AcquirePackage
	resolveSourceTag = repository.ResolveSourceTag
)

// resolveTaskSourceTag never fails the task: every problem becomes UNKNOWN.
// Official tasks honour an active quota cooldown before any extra request, but a
// source tag failure is not reported as a quota observation.
func resolveTaskSourceTag(ctx context.Context, quotaGuard *officialQuotaGuard,
	metadata github.Repository, commitSHA string, limits RepositoryLimits) *controlplane.SourceTag {
	var resolved repository.SourceTag
	if quotaGuard != nil && !quotaGuard.allowsOptionalGit(ctx) {
		resolved = repository.SourceTag{Status: repository.SourceTagUnknown, Reason: "QUOTA_COOLDOWN"}
	} else {
		resolved = resolveSourceTag(ctx, metadata.Owner.Login, metadata.Name, commitSHA,
			limits.TempRoot, limits.MaxWorkspaceBytes, limits.MinFreeBytes)
	}
	report := &controlplane.SourceTag{Status: resolved.Status, Tag: resolved.Tag,
		TagCommitSHA: resolved.TagCommitSHA, Reason: resolved.Reason}
	if resolved.Status == repository.SourceTagExact || resolved.Status == repository.SourceTagAfterTag {
		distance := resolved.CommitsAfterTag
		report.CommitsAfterTag = &distance
	}
	return report
}

func advanceCheckpoint(completion *controlplane.RepositoryCompletion, path string) {
	if completion.PathCheckpoint == "" || path > completion.PathCheckpoint {
		completion.PathCheckpoint = path
	}
}

func hasRetryable(results []controlplane.RepositoryPathResult) bool {
	for _, result := range results {
		if result.Outcome == "RETRYABLE_FAILURE" {
			return true
		}
	}
	return false
}

func completeDeferred(ctx context.Context, control *controlplane.Client,
	task *controlplane.ClaimedTask, completion *controlplane.RepositoryCompletion,
	reason string, retryAt time.Time) error {
	if paused, err := pauseOfficialRepositoryIfNeeded(ctx, control, task, completion); err != nil || paused {
		return err
	}
	completion.DeferralReason = reason
	completion.RetryAt = futureRetry(retryAt)
	_, err := control.CompleteRepository(ctx, task.TaskID, task.Lease, *completion)
	return err
}

func futureRetry(retryAt time.Time) time.Time {
	if retryAt.After(time.Now().Add(time.Minute)) {
		return retryAt
	}
	return time.Now().Add(15 * time.Minute)
}

func credentialToken(task *controlplane.ClaimedTask) string {
	if task.Credential == nil {
		return ""
	}
	return task.Credential.Token
}
