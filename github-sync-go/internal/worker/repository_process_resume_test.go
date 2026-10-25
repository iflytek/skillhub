package worker

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"strings"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/controlplane"
	"github.com/iflytek/skillhub/github-sync-go/internal/github"
)

type repositoryTreeFixture struct {
	entries []github.TreeEntry
	id      int64
	owner   string
	name    string
}

type headOnlyRepositoryFixture struct {
	*repositoryTreeFixture
	treeCalls int
}

type selectedLiveRepositoryTree struct {
	*github.Client
	treeCalls       int
	rawEntries      int
	selectedEntries int
	selectAll       bool
}

func (reader *selectedLiveRepositoryTree) GetTree(ctx context.Context, owner, name, sha string,
	recursive bool) (github.Tree, github.Quota, error) {
	reader.treeCalls++
	tree, quota, err := reader.Client.GetTree(ctx, owner, name, sha, recursive)
	if err != nil || !recursive || tree.Truncated {
		return tree, quota, err
	}
	reader.rawEntries = len(tree.Entries)
	if reader.selectAll {
		reader.selectedEntries = len(tree.Entries)
		return tree, quota, nil
	}
	selected := tree.Entries[:0]
	for _, entry := range tree.Entries {
		if strings.HasPrefix(entry.Path, "skills/.system/skill-creator/") ||
			strings.HasPrefix(entry.Path, "skills/.curated/define-goal/") {
			selected = append(selected, entry)
		}
	}
	tree.Entries = selected
	reader.selectedEntries = len(selected)
	return tree, quota, nil
}

func (fixture *headOnlyRepositoryFixture) GetCommitTreeSHA(context.Context, string, string, string) (string, github.Quota, error) {
	fixture.treeCalls++
	return "", github.Quota{}, errors.New("HEAD_ONLY must not inspect a commit tree")
}

func (fixture *headOnlyRepositoryFixture) GetTree(context.Context, string, string, string, bool) (github.Tree, github.Quota, error) {
	fixture.treeCalls++
	return github.Tree{}, github.Quota{}, errors.New("HEAD_ONLY must not enumerate a tree")
}

func (*repositoryTreeFixture) SetQuotaObserver(func(context.Context, github.Quota)) {}

func (fixture *repositoryTreeFixture) GetRepository(context.Context, string, string) (github.Repository, github.Quota, error) {
	id, owner, name := fixture.id, fixture.owner, fixture.name
	if id == 0 {
		id, owner, name = 42, "fixture", "repo"
	}
	repo := github.Repository{ID: id, Name: name, DefaultBranch: "main"}
	repo.Owner.Login = owner
	return repo, github.Quota{}, nil
}

func (*repositoryTreeFixture) GetHead(context.Context, github.Repository) (string, github.Quota, error) {
	return strings.Repeat("c", 40), github.Quota{}, nil
}

func (*repositoryTreeFixture) GetCommitTreeSHA(context.Context, string, string, string) (string, github.Quota, error) {
	return strings.Repeat("b", 40), github.Quota{}, nil
}

func (fixture *repositoryTreeFixture) GetTree(context.Context, string, string, string, bool) (github.Tree, github.Quota, error) {
	return github.Tree{Entries: fixture.entries}, github.Quota{}, nil
}

func TestProcessRepositorySkipsReceivedPackageAndRevisitsFailedPathBeforeCheckpoint(t *testing.T) {
	var reports []map[string]any
	var admissions int
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if strings.HasSuffix(request.URL.Path, "/repository-metadata") {
			writer.WriteHeader(http.StatusOK)
			return
		}
		if strings.HasSuffix(request.URL.Path, "/package-admissions") {
			admissions++
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}
		if !strings.HasSuffix(request.URL.Path, "/complete") {
			writer.WriteHeader(http.StatusNotFound)
			return
		}
		var report map[string]any
		if err := json.NewDecoder(request.Body).Decode(&report); err != nil {
			writer.WriteHeader(http.StatusBadRequest)
			return
		}
		reports = append(reports, report)
		writer.Header().Set("Content-Type", "application/json")
		if report["coverage"] == "INCOMPLETE" {
			_, _ = writer.Write([]byte(`{"status":"RETRY_WAIT"}`))
		} else {
			_, _ = writer.Write([]byte(`{"status":"PARTIAL"}`))
		}
	}))
	defer server.Close()
	control, err := controlplane.NewClient(server.URL, strings.Repeat("t", 32))
	if err != nil {
		t.Fatal(err)
	}
	commit := strings.Repeat("c", 40)
	task := &controlplane.ClaimedTask{
		TaskID: "fixture-task", Kind: "REPOSITORY_SYNC", Mode: "FULL",
		Lease: controlplane.LeaseProof{Generation: 2, Token: strings.Repeat("a", 64)},
		Repository: &controlplane.RepositorySnapshot{
			RepositoryID: 42, Owner: "fixture", Name: "repo", FixedCommitSHA: commit,
			PathCheckpoint: "skills/retry", AcceptedPaths: []string{"skills/accepted"},
			RetryPaths: []string{"skills/retry"},
		},
		MaxFilesPerPackage: 10, MaxPackageBytes: 1024,
		MaxSingleFileBytes: 1024, MaxUploadBytes: 2048,
	}
	limits := RepositoryLimits{TempRoot: t.TempDir(), MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 32 << 20}
	accepted := github.TreeEntry{Path: "skills/accepted/SKILL.md", Type: "blob",
		Mode: "100644", SHA: strings.Repeat("d", 40), Size: 10}
	retry := github.TreeEntry{Path: "skills/retry/SKILL.md", Type: "blob",
		Mode: "100644", SHA: strings.Repeat("e", 40), Size: -1}
	if err := processRepository(context.Background(), control, task, limits,
		&repositoryTreeFixture{entries: []github.TreeEntry{accepted, retry}}); err != nil {
		t.Fatal(err)
	}
	if admissions != 0 || len(reports) != 1 || reports[0]["coverage"] != "INCOMPLETE" {
		t.Fatalf("received package was reacquired or retry was not deferred: admissions=%d reports=%v",
			admissions, reports)
	}
	retryAt, err := time.Parse(time.RFC3339Nano, reports[0]["retryAt"].(string))
	if err != nil || !retryAt.After(time.Now()) {
		t.Fatalf("retryAt was not a future instant: %v, %v", reports[0]["retryAt"], err)
	}
	assertOnlyPathResult(t, reports[0], "skills/retry", "RETRYABLE_FAILURE")

	// On the next lease the failing path has disappeared from the fixed tree.
	// It must still be revisited despite being at the persisted checkpoint.
	if err := processRepository(context.Background(), control, task, limits,
		&repositoryTreeFixture{entries: []github.TreeEntry{accepted}}); err != nil {
		t.Fatal(err)
	}
	if admissions != 0 || len(reports) != 2 || reports[1]["coverage"] != "COMPLETE" {
		t.Fatalf("received package was reacquired or failed path was not resolved: admissions=%d reports=%v",
			admissions, reports)
	}
	assertOnlyPathResult(t, reports[1], "skills/retry", "TERMINAL_FAILURE")
}

func TestProcessRepositoryDefersAtFixedCheckpointWhenOnePackageCannotFit(t *testing.T) {
	var completion map[string]any
	admissions := 0
	uploads := 0
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch {
		case strings.HasSuffix(request.URL.Path, "/repository-metadata"):
			writer.WriteHeader(http.StatusOK)
		case strings.HasSuffix(request.URL.Path, "/package-admissions"):
			admissions++
			writer.Header().Set("Content-Type", "application/json")
			_, _ = writer.Write([]byte(`{"admissionId":"capacity-fixture"}`))
		case strings.HasSuffix(request.URL.Path, "/complete"):
			if err := json.NewDecoder(request.Body).Decode(&completion); err != nil {
				writer.WriteHeader(http.StatusBadRequest)
				return
			}
			writer.Header().Set("Content-Type", "application/json")
			_, _ = writer.Write([]byte(`{"status":"RETRY_WAIT"}`))
		default:
			uploads++
			writer.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()
	control, err := controlplane.NewClient(server.URL, strings.Repeat("t", 32))
	if err != nil {
		t.Fatal(err)
	}
	commit := strings.Repeat("c", 40)
	task := &controlplane.ClaimedTask{
		TaskID: "capacity-task", Kind: "REPOSITORY_SYNC", Mode: "FULL",
		Lease: controlplane.LeaseProof{Generation: 2, Token: strings.Repeat("a", 64)},
		Repository: &controlplane.RepositorySnapshot{
			RepositoryID: 42, Owner: "fixture", Name: "repo", FixedCommitSHA: commit,
			PathCheckpoint: "skills/a-previous", AcceptedPaths: []string{"skills/a-previous"},
		},
		MaxFilesPerPackage: 10, MaxPackageBytes: 1024,
		MaxSingleFileBytes: 1024, MaxUploadBytes: 2048,
	}
	tempRoot := t.TempDir()
	limits := RepositoryLimits{TempRoot: tempRoot, MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 1 << 20}
	entries := []github.TreeEntry{
		{Path: "skills/a-previous/SKILL.md", Type: "blob", Mode: "100644",
			SHA: strings.Repeat("d", 40), Size: 10},
		{Path: "skills/z-next/SKILL.md", Type: "blob", Mode: "100644",
			SHA: strings.Repeat("e", 40), Size: 10},
	}
	if err := processRepository(context.Background(), control, task, limits,
		&repositoryTreeFixture{entries: entries}); err != nil {
		t.Fatal(err)
	}
	if admissions != 1 || uploads != 0 || completion == nil ||
		completion["coverage"] != "INCOMPLETE" || completion["deferralReason"] != "CAPACITY" ||
		completion["pathCheckpoint"] != "skills/a-previous" {
		t.Fatalf("capacity deferral lost the fixed checkpoint: admissions=%d uploads=%d completion=%v",
			admissions, uploads, completion)
	}
	retryAt, err := time.Parse(time.RFC3339Nano, completion["retryAt"].(string))
	if err != nil || retryAt.Before(time.Now().Add(23*time.Hour)) {
		t.Fatalf("insufficient package capacity retried too soon: %v, %v", completion["retryAt"], err)
	}
	remaining, err := os.ReadDir(tempRoot)
	if err != nil || len(remaining) != 0 {
		t.Fatalf("capacity deferral left temporary files: %v, %v", remaining, err)
	}
}

func assertOnlyPathResult(t *testing.T, report map[string]any, path, outcome string) {
	t.Helper()
	results, ok := report["pathResults"].([]any)
	if !ok || len(results) != 1 {
		t.Fatalf("unexpected path results: %v", report["pathResults"])
	}
	result, ok := results[0].(map[string]any)
	if !ok || result["skillPath"] != path || result["outcome"] != outcome {
		t.Fatalf("unexpected path result: %v", results[0])
	}
}

// Opt-in cross-service check. Java owns the task and retry clock; this test runs
// the actual repository worker with a controlled GitHub tree in a separate Go process.
func TestInteropProcessRepositoryRecovery(t *testing.T) {
	phase := os.Getenv("SKILLHUB_INTEROP_WORKER_PHASE")
	if phase == "" {
		t.Skip("requires an HTTP interoperability fixture")
	}
	if phase != "retry" && phase != "resolve" {
		t.Fatal("invalid HTTP interoperability worker phase")
	}
	control, err := controlplane.NewClient(os.Getenv("SKILLHUB_INTEROP_JAVA_URL"),
		os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN"))
	if err != nil {
		t.Fatal(err)
	}
	task, err := control.ClaimWithCapabilities(context.Background(), "interop-recovery-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		t.Fatal(err)
	}
	if task == nil || task.PlatformControlVersion == nil || task.TaskID != os.Getenv("SKILLHUB_INTEROP_TASK_ID") ||
		task.Repository == nil || task.Repository.RepositoryID != 42 ||
		task.Repository.FixedCommitSHA != strings.Repeat("c", 40) ||
		len(task.Repository.AcceptedPaths) != 1 ||
		task.Repository.AcceptedPaths[0] != "skills/.system/skill-creator" ||
		len(task.Repository.RetryPaths) != 1 || task.Repository.RetryPaths[0] != "skills/retry" {
		t.Fatal("Java did not return the expected persisted recovery paths")
	}
	entries := []github.TreeEntry{{Path: "skills/.system/skill-creator/SKILL.md",
		Type: "blob", Mode: "100644", SHA: strings.Repeat("d", 40), Size: 10}}
	if phase == "retry" {
		entries = append(entries, github.TreeEntry{Path: "skills/retry/SKILL.md",
			Type: "blob", Mode: "100644", SHA: strings.Repeat("e", 40), Size: -1})
	}
	upstream := &repositoryTreeFixture{entries: entries, id: 42, owner: "openai", name: "skills"}
	limits := RepositoryLimits{TempRoot: t.TempDir(), MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 32 << 20}
	if err := processRepository(context.Background(), control, task, limits, upstream); err != nil {
		t.Fatal(err)
	}
}

// The real Java API grants admission; Go must return a delayed capacity result
// before Git can fetch any blob, and leave no package directory behind.
func TestInteropProcessRepositoryCapacity(t *testing.T) {
	if os.Getenv("SKILLHUB_INTEROP_WORKER_CAPACITY") != "1" {
		t.Skip("requires a Java HTTP capacity fixture")
	}
	control, err := controlplane.NewClient(os.Getenv("SKILLHUB_INTEROP_JAVA_URL"),
		os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN"))
	if err != nil {
		t.Fatal(err)
	}
	task, err := control.ClaimWithCapabilities(context.Background(), "interop-capacity-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		t.Fatal(err)
	}
	if task == nil || task.PlatformControlVersion == nil || task.TaskID != os.Getenv("SKILLHUB_INTEROP_TASK_ID") ||
		task.Repository == nil || task.Repository.RepositoryID != 987654330 ||
		task.Repository.FixedCommitSHA != strings.Repeat("c", 40) || task.Mode != "FULL" {
		t.Fatal("Java did not lease the expected fixed-commit repository task")
	}
	tempRoot := t.TempDir()
	entries := []github.TreeEntry{{Path: "skills/fixture/SKILL.md", Type: "blob",
		Mode: "100644", SHA: strings.Repeat("d", 40), Size: 10}}
	upstream := &repositoryTreeFixture{entries: entries, id: 987654330,
		owner: "openai", name: "skills"}
	limits := RepositoryLimits{TempRoot: tempRoot, MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 1 << 20}
	if err := executeLeasedTask(context.Background(), control, task, func(ctx context.Context) error {
		return processRepository(ctx, control, task, limits, upstream)
	}); err != nil {
		t.Fatal(err)
	}
	remaining, err := os.ReadDir(tempRoot)
	if err != nil || len(remaining) != 0 {
		t.Fatalf("capacity deferral left temporary files: %v, %v", remaining, err)
	}
}

// Java supplies the lease and package admission; the Tree fixture isolates the
// REST quota while Git fetches two real Skill directories from one fixed commit.
func TestInteropProcessRepositoryPublicMultiSkill(t *testing.T) {
	if os.Getenv("SKILLHUB_INTEROP_WORKER_PUBLIC_MULTI") != "1" {
		t.Skip("requires a Java HTTP lease and public Git access")
	}
	const commitSHA = "49f948faa9258a0c61caceaf225e179651397431"
	realTree := os.Getenv("SKILLHUB_INTEROP_WORKER_REAL_TREE") == "1"
	allTree := os.Getenv("SKILLHUB_INTEROP_WORKER_ALL_TREE") == "1"
	interruptAfterReceipt := os.Getenv("SKILLHUB_INTEROP_WORKER_INTERRUPT_AFTER_RECEIPT") == "1"
	if allTree && !realTree {
		t.Fatal("full Tree interop requires the real GitHub Tree API")
	}
	repositoryID := int64(987654350)
	if realTree {
		repositoryID = 1103643573
	}
	javaURL := os.Getenv("SKILLHUB_INTEROP_JAVA_URL")
	workContext := context.Background()
	var interrupted atomic.Bool
	if interruptAfterReceipt {
		var stop context.CancelFunc
		workContext, stop = signal.NotifyContext(workContext, syscall.SIGTERM)
		defer stop()
		proxy := interruptAfterPackageReceipt(t, javaURL, &interrupted)
		defer proxy.Close()
		javaURL = proxy.URL + "/internal/github-sync/v1"
	}
	control, err := controlplane.NewClient(javaURL,
		os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN"))
	if err != nil {
		t.Fatal(err)
	}
	task, err := control.ClaimWithCapabilities(context.Background(), "interop-public-multi-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		t.Fatal(err)
	}
	// Java creates a TEAM source here; platform pause epochs apply only to @github.
	if task == nil || task.PlatformControlVersion != nil || task.TaskID != os.Getenv("SKILLHUB_INTEROP_TASK_ID") ||
		task.Repository == nil || task.Repository.RepositoryID != repositoryID ||
		task.Repository.FixedCommitSHA != commitSHA || task.Mode != "FULL" {
		t.Fatal("Java did not lease the expected TEAM repository task without platform control")
	}
	if allTree && (task.MaxFilesPerPackage != 100 || task.MaxPackageBytes != 100<<20 ||
		task.MaxUploadBytes != 100<<20) {
		t.Fatal("full Tree lease did not use the current production package policy")
	}
	files := []struct {
		path string
		sha  string
		size int64
	}{
		{"skills/.system/skill-creator/LICENSE.txt", "d645695673349e3947e8e5ae42332d0ac3164cd7", 11358},
		{"skills/.system/skill-creator/SKILL.md", "72bc0b97e7a6476254a9d5c424c9971748402ec3", 18664},
		{"skills/.system/skill-creator/agents/openai.yaml", "f5ce09ed118b72908b7308fcfc1619f59e89f6ea", 192},
		{"skills/.system/skill-creator/references/openai_yaml.md", "da5629f8de500ec6f81291d239a2d9c68d1d9044", 2130},
		{"skills/.system/skill-creator/scripts/generate_openai_yaml.py", "1a9d784f8704999daf55a2b9036094949d9af3f8", 6614},
		{"skills/.system/skill-creator/scripts/init_skill.py", "f90703eca8159a21a9684ed1df974c702d92f814", 14483},
		{"skills/.system/skill-creator/scripts/quick_validate.py", "0547b4041a5f58fa19892079a114a1df98286406", 3293},
		{"skills/.curated/define-goal/LICENSE.txt", "13e25df86ce06eb6488e6a6bc5c5847f5dedc352", 10776},
		{"skills/.curated/define-goal/SKILL.md", "87f111bd700e0d993465f7ac741847b5daee57d6", 5647},
		{"skills/.curated/define-goal/agents/openai.yaml", "28c7e7a644d7ae3f5616c7ddd408c573139999f0", 208},
	}
	entries := make([]github.TreeEntry, 0, len(files))
	for _, file := range files {
		entries = append(entries, github.TreeEntry{Path: file.path, Type: "blob",
			Mode: "100644", SHA: file.sha, Size: file.size})
	}
	tempRoot := os.Getenv("SKILLHUB_INTEROP_WORKER_TEMP_ROOT")
	if tempRoot == "" {
		tempRoot = t.TempDir()
	}
	limits := RepositoryLimits{TempRoot: tempRoot, MaxTreeRequests: 5,
		MaxTreeEntries: 100, MaxWorkspaceBytes: 64 << 20, MinFreeBytes: 16 << 20}
	if allTree {
		limits.MaxTreeEntries = 5000
	}
	if host := os.Getenv("SKILLHUB_INTEROP_ALLOWED_S3_HOST"); host != "" {
		limits.AllowedS3Hosts = []string{host}
	}
	var upstream repositoryGitHub = &repositoryTreeFixture{entries: entries, id: repositoryID,
		owner: "openai", name: "skills"}
	var liveReader *selectedLiveRepositoryTree
	if realTree {
		token := os.Getenv("SKILLHUB_GITHUB_SYNC_LIVE_TOKEN")
		if token == "" {
			lookupCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			output, lookupErr := exec.CommandContext(lookupCtx, "gh", "auth", "token").Output()
			cancel()
			if lookupErr != nil {
				t.Fatal("real Tree mode requires a configured GitHub test token or local gh authentication")
			}
			token = strings.TrimSpace(string(output))
		}
		if token == "" {
			t.Fatal("real Tree mode has no GitHub test token")
		}
		liveReader = &selectedLiveRepositoryTree{Client: github.NewClient(token), selectAll: allTree}
		upstream = liveReader
	}
	err = executeLeasedTask(workContext, control, task, func(ctx context.Context) error {
		return processRepository(ctx, control, task, limits, upstream)
	})
	if interruptAfterReceipt {
		if !interrupted.Load() || !errors.Is(err, context.Canceled) {
			t.Fatalf("worker did not stop after Java accepted the first package: interrupted=%v error=%v",
				interrupted.Load(), err)
		}
		t.Log("interrupted after Java package receipt")
	} else if err != nil {
		t.Fatal(err)
	}
	if realTree {
		if liveReader.treeCalls != 1 || liveReader.rawEntries < 1000 ||
			liveReader.selectedEntries < len(entries) {
			t.Fatalf("real Tree API did not provide the expected complete metadata: calls=%d raw=%d selected=%d",
				liveReader.treeCalls, liveReader.rawEntries, liveReader.selectedEntries)
		}
		t.Logf("real Tree API: tree_calls=%d raw_entries=%d selected_entries=%d",
			liveReader.treeCalls, liveReader.rawEntries, liveReader.selectedEntries)
	}
	remaining, err := os.ReadDir(tempRoot)
	if err != nil || len(remaining) != 0 {
		t.Fatalf("multi-Skill acquisition left temporary files: %v, %v", remaining, err)
	}
}

func interruptAfterPackageReceipt(t *testing.T, javaURL string, interrupted *atomic.Bool) *httptest.Server {
	t.Helper()
	target, err := url.Parse(javaURL)
	if err != nil || target.Scheme != "http" || target.Host == "" {
		t.Fatal("interruption fixture requires a local Java HTTP endpoint")
	}
	client := &http.Client{Timeout: 30 * time.Second, Transport: &http.Transport{Proxy: nil}}
	return httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		forward := request.Clone(request.Context())
		forward.URL.Scheme = target.Scheme
		forward.URL.Host = target.Host
		forward.Host = target.Host
		forward.RequestURI = ""
		response, err := client.Do(forward)
		if err != nil {
			writer.WriteHeader(http.StatusBadGateway)
			return
		}
		defer response.Body.Close()
		for key, values := range response.Header {
			writer.Header()[key] = values
		}
		writer.WriteHeader(response.StatusCode)
		if strings.HasSuffix(request.URL.Path, "/packages/report") &&
			response.StatusCode >= 200 && response.StatusCode < 300 &&
			interrupted.CompareAndSwap(false, true) {
			_ = syscall.Kill(os.Getpid(), syscall.SIGTERM)
		}
		_, _ = io.Copy(writer, response.Body)
	}))
}

func TestInteropProcessRepositoryHeadOnly(t *testing.T) {
	if os.Getenv("SKILLHUB_INTEROP_WORKER_HEAD_ONLY") != "1" {
		t.Skip("requires a Java HTTP HEAD_ONLY fixture")
	}
	control, err := controlplane.NewClient(os.Getenv("SKILLHUB_INTEROP_JAVA_URL"),
		os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN"))
	if err != nil {
		t.Fatal(err)
	}
	task, err := control.ClaimWithCapabilities(context.Background(), "interop-head-only-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		t.Fatal(err)
	}
	if task == nil || task.PlatformControlVersion == nil || task.TaskID != os.Getenv("SKILLHUB_INTEROP_TASK_ID") ||
		task.Repository == nil || task.Repository.RepositoryID != 987654332 ||
		task.Repository.Owner != "old-owner" || task.Repository.Name != "old-name" ||
		task.Mode != "HEAD_ONLY" {
		t.Fatal("Java did not downgrade the registered source to HEAD_ONLY")
	}
	fixture := &headOnlyRepositoryFixture{repositoryTreeFixture: &repositoryTreeFixture{
		id: 987654332, owner: "new-owner", name: "new-name"}}
	tempRoot := t.TempDir()
	limits := RepositoryLimits{TempRoot: tempRoot, MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 1 << 20}
	if err := executeLeasedTask(context.Background(), control, task, func(ctx context.Context) error {
		return processRepository(ctx, control, task, limits, fixture)
	}); err != nil {
		t.Fatal(err)
	}
	remaining, err := os.ReadDir(tempRoot)
	if err != nil || len(remaining) != 0 || fixture.treeCalls != 0 {
		t.Fatalf("HEAD_ONLY inspected files or left temporary state: files=%v treeCalls=%d error=%v",
			remaining, fixture.treeCalls, err)
	}
}

func TestInteropProcessRepositoryRenamedCandidateWithoutSkill(t *testing.T) {
	if os.Getenv("SKILLHUB_INTEROP_WORKER_EMPTY_CANDIDATE") != "1" {
		t.Skip("requires a Java HTTP candidate validation lease")
	}
	control, err := controlplane.NewClient(os.Getenv("SKILLHUB_INTEROP_JAVA_URL"),
		os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN"))
	if err != nil {
		t.Fatal(err)
	}
	task, err := control.ClaimWithCapabilities(context.Background(), "interop-empty-candidate-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		t.Fatal(err)
	}
	if task == nil || task.PlatformControlVersion != nil || task.TaskID != os.Getenv("SKILLHUB_INTEROP_TASK_ID") ||
		task.Repository == nil || task.Repository.RepositoryID != 987654333 ||
		task.Repository.Owner != "old-owner" || task.Repository.Name != "old-name" ||
		task.Mode != "FULL" {
		t.Fatal("Java did not lease the expected candidate repository")
	}
	fixture := &repositoryTreeFixture{
		id: 987654333, owner: "new-owner", name: "new-name",
		entries: []github.TreeEntry{{Path: "README.md", Type: "blob", Mode: "100644",
			SHA: strings.Repeat("a", 40), Size: 10}},
	}
	limits := RepositoryLimits{TempRoot: t.TempDir(), MaxTreeRequests: 5,
		MaxTreeEntries: 20, MaxWorkspaceBytes: 1 << 20}
	if err := executeLeasedTask(context.Background(), control, task, func(ctx context.Context) error {
		return processRepository(ctx, control, task, limits, fixture)
	}); err != nil {
		t.Fatal(err)
	}
}
