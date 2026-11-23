package repository

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

type tagFixture struct {
	t   *testing.T
	dir string
}

func newTagFixture(t *testing.T) *tagFixture {
	t.Helper()
	fixture := &tagFixture{t: t, dir: t.TempDir()}
	fixture.git("init", "--quiet", "--initial-branch=main")
	fixture.git("config", "user.email", "fixture@example.invalid")
	fixture.git("config", "user.name", "Fixture")
	fixture.git("config", "uploadpack.allowFilter", "true")
	fixture.git("config", "uploadpack.allowAnySHA1InWant", "true")
	return fixture
}

func (fixture *tagFixture) git(args ...string) string {
	fixture.t.Helper()
	command := exec.Command("git", args...)
	command.Dir = fixture.dir
	command.Env = append(os.Environ(), "GIT_CONFIG_GLOBAL=/dev/null", "GIT_CONFIG_NOSYSTEM=1")
	output, err := command.CombinedOutput()
	if err != nil {
		fixture.t.Fatalf("git %v: %v\n%s", args, err, output)
	}
	return strings.TrimSpace(string(output))
}

func (fixture *tagFixture) commit(message string) string {
	fixture.t.Helper()
	// One file per commit keeps branch fixtures merge-conflict free.
	name := strings.ReplaceAll(message, " ", "-") + ".md"
	if err := os.WriteFile(filepath.Join(fixture.dir, name), []byte(message+"\n"), 0600); err != nil {
		fixture.t.Fatal(err)
	}
	fixture.git("add", name)
	fixture.git("commit", "--quiet", "-m", message)
	return fixture.git("rev-parse", "HEAD")
}

func (fixture *tagFixture) resolve(commit string) SourceTag {
	fixture.t.Helper()
	previousRemote, previousProtocol := sourceTagRemote, sourceTagAllowedProtocol
	sourceTagRemote = func(string, string) (string, error) { return "file://" + fixture.dir, nil }
	sourceTagAllowedProtocol = "file"
	defer func() { sourceTagRemote, sourceTagAllowedProtocol = previousRemote, previousProtocol }()
	return ResolveSourceTag(context.Background(), "fixture", "repo", commit, fixture.t.TempDir(),
		64<<20, 0)
}

func TestSourceTagExactAnnotatedTag(t *testing.T) {
	fixture := newTagFixture(t)
	fixture.commit("one")
	commit := fixture.commit("two")
	fixture.git("tag", "-a", "v7.0.1", "-m", "release")
	got := fixture.resolve(commit)
	if got.Status != SourceTagExact || got.Tag != "v7.0.1" || got.TagCommitSHA != commit || got.CommitsAfterTag != 0 {
		t.Fatalf("annotated tag on the commit must be exact: %+v", got)
	}
}

func TestSourceTagAfterLightweightTag(t *testing.T) {
	fixture := newTagFixture(t)
	tagged := fixture.commit("release")
	fixture.git("tag", "v1.3.2")
	fixture.commit("a")
	fixture.commit("b")
	commit := fixture.commit("c")
	got := fixture.resolve(commit)
	if got.Status != SourceTagAfterTag || got.Tag != "v1.3.2" || got.TagCommitSHA != tagged || got.CommitsAfterTag != 3 {
		t.Fatalf("commit three after the tag must report distance 3: %+v", got)
	}
}

func TestSourceTagNoTagCases(t *testing.T) {
	fixture := newTagFixture(t)
	commit := fixture.commit("only")
	if got := fixture.resolve(commit); got.Status != SourceTagNoTag || got.Tag != "" {
		t.Fatalf("repository without tags: %+v", got)
	}
	fixture.git("tag", "latest")
	fixture.git("tag", "skill-a/v1.0.0")
	fixture.git("tag", "release-2024")
	if got := fixture.resolve(commit); got.Status != SourceTagNoTag {
		t.Fatalf("non-semver and path-prefixed tags must be ignored: %+v", got)
	}
}

func TestSourceTagIgnoresTagOnUnrelatedBranch(t *testing.T) {
	fixture := newTagFixture(t)
	fixture.commit("base")
	fixture.git("tag", "v1.0.0")
	fixture.git("checkout", "--quiet", "-b", "side")
	fixture.commit("side work")
	fixture.git("tag", "v9.0.0")
	fixture.git("checkout", "--quiet", "main")
	commit := fixture.commit("main work")
	got := fixture.resolve(commit)
	if got.Status != SourceTagAfterTag || got.Tag != "v1.0.0" || got.CommitsAfterTag != 1 {
		t.Fatalf("unreachable higher tag must not be selected: %+v", got)
	}
}

func TestSourceTagMultipleTagsOnOneCommitPreferHighestSemver(t *testing.T) {
	fixture := newTagFixture(t)
	commit := fixture.commit("release")
	fixture.git("tag", "v2.0.0-rc.1")
	fixture.git("tag", "-a", "v2.0.0", "-m", "final")
	fixture.git("tag", "v1.9.9")
	got := fixture.resolve(commit)
	if got.Status != SourceTagExact || got.Tag != "v2.0.0" {
		t.Fatalf("highest semver tag on the commit must win: %+v", got)
	}
}

func TestSourceTagNearestReachableTagAcrossMerge(t *testing.T) {
	fixture := newTagFixture(t)
	fixture.commit("base")
	fixture.git("tag", "v1.0.0")
	fixture.git("checkout", "--quiet", "-b", "feature")
	fixture.commit("feature")
	fixture.git("tag", "v1.1.0")
	fixture.git("checkout", "--quiet", "main")
	fixture.commit("main")
	fixture.git("merge", "--quiet", "--no-ff", "-m", "merge", "feature")
	commit := fixture.git("rev-parse", "HEAD")
	got := fixture.resolve(commit)
	// Not in v1.1.0: main + merge = 2. Not in v1.0.0: feature + main + merge = 3.
	if got.Status != SourceTagAfterTag || got.Tag != "v1.1.0" || got.CommitsAfterTag != 2 {
		t.Fatalf("nearest reachable tag must be chosen by describe distance: %+v", got)
	}
}

func TestSourceTagTagListFailureIsUnknown(t *testing.T) {
	previousRemote, previousProtocol := sourceTagRemote, sourceTagAllowedProtocol
	sourceTagRemote = func(string, string) (string, error) { return "file:///nonexistent/skillhub-fixture", nil }
	sourceTagAllowedProtocol = "file"
	defer func() { sourceTagRemote, sourceTagAllowedProtocol = previousRemote, previousProtocol }()
	got := ResolveSourceTag(context.Background(), "fixture", "repo", strings.Repeat("a", 40), t.TempDir(), 64<<20, 0)
	if got.Status != SourceTagUnknown || got.Reason != "TAG_LIST_FAILED" {
		t.Fatalf("tag listing failure must be UNKNOWN: %+v", got)
	}
}

func TestSourceTagMissingCommitIsUnknown(t *testing.T) {
	fixture := newTagFixture(t)
	fixture.commit("base")
	fixture.git("tag", "v1.0.0")
	got := fixture.resolve(strings.Repeat("f", 40))
	if got.Status != SourceTagUnknown || got.Reason != "HISTORY_FETCH_FAILED" {
		t.Fatalf("unfetchable commit must be UNKNOWN: %+v", got)
	}
}

func TestSourceTagCancelledContextIsUnknown(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	got := ResolveSourceTag(ctx, "fixture", "repo", strings.Repeat("a", 40), t.TempDir(), 64<<20, 0)
	if got.Status != SourceTagUnknown {
		t.Fatalf("cancelled resolution must be UNKNOWN: %+v", got)
	}
}

func TestSourceTagWorkspaceBudgetIsUnknown(t *testing.T) {
	got := ResolveSourceTag(context.Background(), "fixture", "repo", strings.Repeat("a", 40), t.TempDir(), 1, 0)
	if got.Status != SourceTagUnknown || got.Reason != "WORKSPACE_BUDGET" {
		t.Fatalf("a workspace below the Git reserve must be UNKNOWN: %+v", got)
	}
}

func TestRelateSourceTagRejectsIncompleteHistory(t *testing.T) {
	commit, parent := strings.Repeat("a", 40), strings.Repeat("b", 40)
	graph := commitGraph{commit: {parent}}
	got := RelateSourceTag(commit, []SemverTag{{Name: "v1.0.0", Commit: commit}}, graph)
	if got.Status != SourceTagUnknown || got.Reason != "SHALLOW_HISTORY" {
		t.Fatalf("missing parents must not produce a distance: %+v", got)
	}
}

func TestParseSemverTagsPrefersPeeledCommit(t *testing.T) {
	object, peeled, light := strings.Repeat("1", 40), strings.Repeat("2", 40), strings.Repeat("3", 40)
	listing := object + "\trefs/tags/v1.0.0\n" + peeled + "\trefs/tags/v1.0.0^{}\n" +
		light + "\trefs/tags/1.1.0\n" + light + "\trefs/tags/pkg/v2.0.0\n" +
		light + "\trefs/tags/v1.2\n" + light + "\trefs/heads/v3.0.0\n" +
		light + "\trefs/tags/v01.0.0\n" + light + "\trefs/tags/v" + strings.Repeat("9", 130) + ".0.0\n"
	got := ParseSemverTags([]byte(listing))
	want := []SemverTag{{Name: "1.1.0", Commit: light}, {Name: "v1.0.0", Commit: peeled}}
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("unexpected tags %+v", got)
	}
}

func TestCompareSemverPrecedence(t *testing.T) {
	ordered := []string{"v1.0.0-alpha", "v1.0.0-alpha.1", "v1.0.0-alpha.beta", "v1.0.0-beta",
		"v1.0.0-beta.2", "v1.0.0-beta.11", "v1.0.0-rc.1", "v1.0.0", "v1.0.1", "v1.10.0", "v12345678901234567890.0.0"}
	for index := 1; index < len(ordered); index++ {
		if compareSemver(ordered[index-1], ordered[index]) >= 0 || compareSemver(ordered[index], ordered[index-1]) <= 0 {
			t.Fatalf("%s must sort before %s", ordered[index-1], ordered[index])
		}
	}
	if compareSemver("v1.0.0", "1.0.0+build."+strconv.Itoa(5)) != 0 {
		t.Fatal("prefix and build metadata must not affect precedence")
	}
	if !preferTag(SemverTag{Name: "1.0.0"}, SemverTag{Name: "v1.0.0"}) {
		t.Fatal("equal precedence must fall back to the smallest name")
	}
}

func TestLiveSourceTagPublicRepositories(t *testing.T) {
	if os.Getenv("SKILLHUB_GITHUB_SYNC_LIVE") != "1" {
		t.Skip("requires an explicit public GitHub smoke test")
	}
	for _, testCase := range []struct {
		owner, name, commit string
		want                SourceTag
	}{
		{"obra", "superpowers", "2ce8aa15d05bd57213e6f746f953aafb7ded7a8b",
			SourceTag{Status: SourceTagExact, Tag: "v7.0.1", TagCommitSHA: "2ce8aa15d05bd57213e6f746f953aafb7ded7a8b"}},
		{"mattpocock", "skills", "4ae2317ddb22b046e26455e4f9c9b3aaaf62212d",
			SourceTag{Status: SourceTagAfterTag, Tag: "v1.3.2",
				TagCommitSHA: "d1478fcb140db0b93c0c8df17bcfd311844749d0", CommitsAfterTag: 3}},
	} {
		got := ResolveSourceTag(context.Background(), testCase.owner, testCase.name, testCase.commit,
			t.TempDir(), 256<<20, 0)
		if got != testCase.want {
			t.Fatalf("%s/%s: got %+v want %+v", testCase.owner, testCase.name, got, testCase.want)
		}
	}
}

func TestSourceTagOutputLimitIsUnknown(t *testing.T) {
	fixture := newTagFixture(t)
	commit := fixture.commit("base")
	for index := 0; index < 40; index++ {
		fixture.git("tag", "v1.0."+strconv.Itoa(index))
	}
	previousRemote, previousProtocol := sourceTagRemote, sourceTagAllowedProtocol
	sourceTagRemote = func(string, string) (string, error) { return "file://" + fixture.dir, nil }
	sourceTagAllowedProtocol = "file"
	defer func() { sourceTagRemote, sourceTagAllowedProtocol = previousRemote, previousProtocol }()
	git := sourceTagGit{ctx: context.Background(), repoDir: t.TempDir(), workDir: t.TempDir(),
		maxBytes: 64 << 20}
	if _, err := git.run(0, "init", "--quiet"); err != nil {
		t.Fatal(err)
	}
	_, err := git.run(256, "ls-remote", "--tags", "file://"+fixture.dir)
	if git.reason(err, "TAG_LIST_FAILED") != "TOO_MANY_TAGS" {
		t.Fatalf("oversized tag listing must map to TOO_MANY_TAGS: %v", err)
	}
	if got := fixture.resolve(commit); got.Status != SourceTagExact || got.Tag != "v1.0.39" {
		t.Fatalf("normal resolution must still pick the highest tag: %+v", got)
	}
}
