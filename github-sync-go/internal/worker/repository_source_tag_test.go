package worker

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/controlplane"
	"github.com/iflytek/skillhub/github-sync-go/internal/github"
	"github.com/iflytek/skillhub/github-sync-go/internal/repository"
)

type sourceTagServer struct {
	mu          sync.Mutex
	reports     []map[string]any
	completion  map[string]any
	failReports bool
}

func (fixture *sourceTagServer) handler(t *testing.T) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		fixture.mu.Lock()
		defer fixture.mu.Unlock()
		writer.Header().Set("Content-Type", "application/json")
		switch {
		case strings.HasSuffix(request.URL.Path, "/repository-metadata"):
			writer.WriteHeader(http.StatusOK)
		case strings.HasSuffix(request.URL.Path, "/package-admissions"):
			_, _ = writer.Write([]byte(`{"admissionId":"admission"}`))
		case strings.HasSuffix(request.URL.Path, "/upload-authorizations"):
			var body map[string]any
			_ = json.NewDecoder(request.Body).Decode(&body)
			id := "upload-" + strings.ReplaceAll(body["skillPath"].(string), "/", "-")
			_ = json.NewEncoder(writer).Encode(map[string]any{
				"uploadId": id, "method": http.MethodPut, "mode": "LOCAL_STREAM",
				"uploadUrl":    "/internal/github-sync/v1/uploads/" + id + "/content",
				"uploadTicket": "ticket", "expiresAt": time.Now().Add(time.Hour), "maxBytes": 4096,
			})
		case strings.HasSuffix(request.URL.Path, "/content"):
			writer.WriteHeader(http.StatusNoContent)
		case strings.HasSuffix(request.URL.Path, "/packages/report"):
			var body map[string]any
			if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
				t.Error(err)
			}
			fixture.reports = append(fixture.reports, body)
			if fixture.failReports {
				writer.WriteHeader(http.StatusInternalServerError)
				return
			}
			_, _ = writer.Write([]byte(`{"ingestionId":"ingestion","status":"RECEIVED"}`))
		case strings.HasSuffix(request.URL.Path, "/complete"):
			if err := json.NewDecoder(request.Body).Decode(&fixture.completion); err != nil {
				t.Error(err)
			}
			if fixture.completion["coverage"] == "INCOMPLETE" {
				_, _ = writer.Write([]byte(`{"status":"RETRY_WAIT"}`))
			} else {
				_, _ = writer.Write([]byte(`{"status":"ACQUISITION_COMPLETE"}`))
			}
		default:
			t.Errorf("unexpected request %s", request.URL.Path)
			writer.WriteHeader(http.StatusNotFound)
		}
	})
}

func stubPackageAcquisition(t *testing.T) {
	t.Helper()
	previous := acquirePackage
	acquirePackage = func(_ context.Context, _, _, _, tempRoot string, plan repository.PackagePlan,
		_ repository.AcquisitionLimits) (*repository.Artifact, error) {
		path := filepath.Join(tempRoot, strings.ReplaceAll(plan.Root, "/", "-")+".zip")
		if err := os.WriteFile(path, []byte("zip"), 0600); err != nil {
			return nil, err
		}
		return &repository.Artifact{Path: path, Size: 3, SHA256: strings.Repeat("a", 64)}, nil
	}
	t.Cleanup(func() { acquirePackage = previous })
}

func stubSourceTag(t *testing.T, result repository.SourceTag) *int {
	t.Helper()
	calls := 0
	previous := resolveSourceTag
	resolveSourceTag = func(context.Context, string, string, string, string, int64, int64) repository.SourceTag {
		calls++
		return result
	}
	t.Cleanup(func() { resolveSourceTag = previous })
	return &calls
}

func sourceTagTask(paths ...string) (*controlplane.ClaimedTask, []github.TreeEntry) {
	task := &controlplane.ClaimedTask{
		TaskID: "source-tag-task", Kind: "REPOSITORY_SYNC", Mode: "FULL",
		Lease: controlplane.LeaseProof{Generation: 1, Token: strings.Repeat("a", 64)},
		Repository: &controlplane.RepositorySnapshot{RepositoryID: 42, Owner: "fixture", Name: "repo",
			FixedCommitSHA: strings.Repeat("c", 40)},
		MaxFilesPerPackage: 10, MaxPackageBytes: 1024, MaxSingleFileBytes: 1024, MaxUploadBytes: 2048,
	}
	entries := make([]github.TreeEntry, 0, len(paths))
	for _, path := range paths {
		entries = append(entries, github.TreeEntry{Path: path + "/SKILL.md", Type: "blob", Mode: "100644",
			SHA: strings.Repeat("d", 40), Size: 10})
	}
	return task, entries
}

func runSourceTagTask(t *testing.T, fixture *sourceTagServer, task *controlplane.ClaimedTask,
	entries []github.TreeEntry) {
	t.Helper()
	server := httptest.NewServer(fixture.handler(t))
	defer server.Close()
	control, err := controlplane.NewClient(server.URL, strings.Repeat("t", 32))
	if err != nil {
		t.Fatal(err)
	}
	limits := RepositoryLimits{TempRoot: t.TempDir(), MaxTreeRequests: 5, MaxTreeEntries: 20,
		MaxWorkspaceBytes: 32 << 20}
	if err := processRepository(context.Background(), control, task, limits,
		&repositoryTreeFixture{entries: entries}); err != nil {
		t.Fatal(err)
	}
}

func TestRepositoryReportsOneSourceTagForEveryPackage(t *testing.T) {
	stubPackageAcquisition(t)
	calls := stubSourceTag(t, repository.SourceTag{Status: repository.SourceTagAfterTag, Tag: "v1.3.2",
		TagCommitSHA: strings.Repeat("d", 40), CommitsAfterTag: 3})
	fixture := &sourceTagServer{}
	task, entries := sourceTagTask("skills/a", "skills/b")
	runSourceTagTask(t, fixture, task, entries)
	if *calls != 1 || len(fixture.reports) != 2 {
		t.Fatalf("source tag must be resolved once for the task: calls=%d reports=%d", *calls, len(fixture.reports))
	}
	for _, report := range fixture.reports {
		tag, ok := report["sourceTag"].(map[string]any)
		if !ok || tag["status"] != "AFTER_TAG" || tag["tag"] != "v1.3.2" ||
			tag["tagCommitSha"] != strings.Repeat("d", 40) || tag["commitsAfterTag"] != float64(3) {
			t.Fatalf("package report lacks the shared source tag: %v", report["sourceTag"])
		}
	}
	if fixture.completion["coverage"] != "COMPLETE" {
		t.Fatalf("source tag must not change completion: %v", fixture.completion)
	}
}

func TestRepositoryUnknownSourceTagKeepsPackageOutcome(t *testing.T) {
	stubPackageAcquisition(t)
	stubSourceTag(t, repository.SourceTag{Status: repository.SourceTagUnknown, Reason: "TAG_LIST_FAILED"})
	fixture := &sourceTagServer{}
	task, entries := sourceTagTask("skills/a")
	runSourceTagTask(t, fixture, task, entries)
	if len(fixture.reports) != 1 || fixture.completion["coverage"] != "COMPLETE" {
		t.Fatalf("unknown source tag changed acquisition: reports=%d completion=%v",
			len(fixture.reports), fixture.completion)
	}
	tag := fixture.reports[0]["sourceTag"].(map[string]any)
	if tag["status"] != "UNKNOWN" || tag["reason"] != "TAG_LIST_FAILED" || tag["commitsAfterTag"] != nil {
		t.Fatalf("unexpected unknown source tag payload: %v", tag)
	}
}

func TestRepositoryWithoutDeliveryDoesNotResolveSourceTag(t *testing.T) {
	stubPackageAcquisition(t)
	calls := stubSourceTag(t, repository.SourceTag{Status: repository.SourceTagNoTag})
	fixture := &sourceTagServer{}
	task, entries := sourceTagTask("skills/a")
	task.Repository.AcceptedPaths = []string{"skills/a"}
	task.Repository.PathCheckpoint = "skills/a"
	runSourceTagTask(t, fixture, task, entries)
	if *calls != 0 || len(fixture.reports) != 0 {
		t.Fatalf("no package was delivered, yet source tag was resolved: calls=%d", *calls)
	}
}

func TestRepositoryDeliveryFailureIsIndependentOfSourceTag(t *testing.T) {
	stubPackageAcquisition(t)
	stubSourceTag(t, repository.SourceTag{Status: repository.SourceTagExact, Tag: "v7.0.1",
		TagCommitSHA: strings.Repeat("c", 40)})
	fixture := &sourceTagServer{failReports: true}
	task, entries := sourceTagTask("skills/a")
	runSourceTagTask(t, fixture, task, entries)
	if fixture.completion["coverage"] != "INCOMPLETE" {
		t.Fatalf("report failure must stay a retryable delivery failure: %v", fixture.completion)
	}
	tag := fixture.reports[0]["sourceTag"].(map[string]any)
	if tag["status"] != "EXACT" || tag["commitsAfterTag"] != float64(0) {
		t.Fatalf("exact tag must report distance zero: %v", tag)
	}
}

func TestOfficialCooldownSkipsSourceTagRequests(t *testing.T) {
	for _, testCase := range []struct {
		name    string
		control string
		stopped bool
		calls   int
	}{
		{"guard already stopped", `{"action":"CONTINUE","configurationVersion":1}`, true, 0},
		{"deployment-wide cooldown", `{"action":"PAUSE","reason":"QUOTA_COOLDOWN","configurationVersion":1,"retryAt":"2999-01-01T00:00:00Z"}`, false, 0},
		{"no cooldown", `{"action":"CONTINUE","configurationVersion":1}`, false, 1},
	} {
		t.Run(testCase.name, func(t *testing.T) {
			resetOfficialQuota(t)
			server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
				writer.Header().Set("Content-Type", "application/json")
				_, _ = writer.Write([]byte(testCase.control))
			}))
			defer server.Close()
			control, err := controlplane.NewClient(server.URL, strings.Repeat("t", 32))
			if err != nil {
				t.Fatal(err)
			}
			task, _ := sourceTagTask("skills/a")
			guard := &officialQuotaGuard{control: control, task: task, resource: "CORE"}
			if testCase.stopped {
				guard.stopped = errors.New("cooldown")
			}
			calls := stubSourceTag(t, repository.SourceTag{Status: repository.SourceTagNoTag})
			report := resolveTaskSourceTag(context.Background(), guard, github.Repository{}, strings.Repeat("c", 40),
				RepositoryLimits{TempRoot: t.TempDir(), MaxWorkspaceBytes: 32 << 20})
			if *calls != testCase.calls {
				t.Fatalf("unexpected source tag requests: calls=%d report=%+v", *calls, report)
			}
			if testCase.calls == 0 && (report.Status != "UNKNOWN" || report.Reason != "QUOTA_COOLDOWN") {
				t.Fatalf("cooldown must be reported as UNKNOWN/QUOTA_COOLDOWN: %+v", report)
			}
			if !testCase.stopped && guard.stopped != nil {
				t.Fatalf("an optional source tag check must not stop the package guard: %v", guard.stopped)
			}
		})
	}
}
