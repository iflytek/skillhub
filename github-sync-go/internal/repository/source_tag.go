package repository

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/github"
)

// Source tag statuses describe the repository-level semver tag relation of a
// fixed acquisition commit. They are read-only provenance and never change the
// package outcome.
const (
	SourceTagExact    = "EXACT"
	SourceTagAfterTag = "AFTER_TAG"
	SourceTagNoTag    = "NO_TAG"
	SourceTagUnknown  = "UNKNOWN"
)

const (
	sourceTagTimeout          = 60 * time.Second
	maxTagListBytes           = 4 << 20
	maxSourceHistoryCommits   = 200_000
	maxSourceDistanceVisits   = 5_000_000
	maxSourceTagNameLength    = 128
	maxSourceRevListLineBytes = 64 << 10
)

var semverTagName = regexp.MustCompile(`^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)` +
	`(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?` +
	`(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$`)

// SourceTag is the provenance captured once per repository task.
type SourceTag struct {
	Status          string
	Tag             string
	TagCommitSHA    string
	CommitsAfterTag int
	Reason          string
}

func unknownSourceTag(reason string) SourceTag {
	return SourceTag{Status: SourceTagUnknown, Reason: reason}
}

// Test seams: production always uses the public HTTPS clone URL.
var (
	sourceTagRemote          = github.PublicCloneURL
	sourceTagAllowedProtocol = "https"
)

// ResolveSourceTag relates commitSHA to repository-level semver tags. It only
// returns UNKNOWN on failure: callers must not fail acquisition because of it.
// A cancelled parent context also yields UNKNOWN; the caller checks ctx itself.
func ResolveSourceTag(ctx context.Context, owner, name, commitSHA, tempRoot string,
	maxWorkspaceBytes, minFreeBytes int64) SourceTag {
	if !validGitSHA(commitSHA) || maxWorkspaceBytes <= gitMetadataReserve || minFreeBytes < 0 {
		return unknownSourceTag("WORKSPACE_BUDGET")
	}
	remote, err := sourceTagRemote(owner, name)
	if err != nil {
		return unknownSourceTag("TAG_LIST_FAILED")
	}
	if err := github.CheckAcquisitionControl(ctx); err != nil {
		return unknownSourceTag("ACQUISITION_PAUSED")
	}
	if err := ensureFreeSpace(tempRoot, maxWorkspaceBytes+minFreeBytes); err != nil {
		return unknownSourceTag("WORKSPACE_BUDGET")
	}
	workDir, err := os.MkdirTemp(tempRoot, "skillhub-github-source-tag-")
	if err != nil {
		return unknownSourceTag("WORKSPACE_BUDGET")
	}
	defer os.RemoveAll(workDir)
	repoDir := filepath.Join(workDir, "repo")
	if err := os.Mkdir(repoDir, 0700); err != nil {
		return unknownSourceTag("WORKSPACE_BUDGET")
	}
	bounded, cancel := context.WithTimeout(ctx, sourceTagTimeout)
	defer cancel()
	git := sourceTagGit{ctx: bounded, repoDir: repoDir, workDir: workDir,
		maxBytes: maxWorkspaceBytes, minFreeBytes: minFreeBytes}

	if _, err := git.run(0, "init", "--quiet"); err != nil {
		return unknownSourceTag(git.reason(err, "TAG_LIST_FAILED"))
	}
	listing, err := git.run(maxTagListBytes, "ls-remote", "--tags", remote)
	if err != nil {
		return unknownSourceTag(git.reason(err, "TAG_LIST_FAILED"))
	}
	tags := ParseSemverTags(listing)
	if len(tags) == 0 {
		// No candidate tag: skip the history fetch entirely.
		return SourceTag{Status: SourceTagNoTag}
	}
	if _, err := git.run(0, "fetch", "--quiet", "--no-tags", "--filter=tree:0", remote, commitSHA); err != nil {
		return unknownSourceTag(git.reason(err, "HISTORY_FETCH_FAILED"))
	}
	if _, err := os.Stat(filepath.Join(repoDir, ".git", "shallow")); err == nil {
		return unknownSourceTag("SHALLOW_HISTORY")
	}
	history, err := git.run(0, "rev-list", "--parents",
		"--max-count="+strconv.Itoa(maxSourceHistoryCommits+1), commitSHA)
	if err != nil {
		return unknownSourceTag(git.reason(err, "HISTORY_FETCH_FAILED"))
	}
	graph, err := parseCommitGraph(history)
	if err != nil {
		return unknownSourceTag(err.Error())
	}
	return RelateSourceTag(commitSHA, tags, graph)
}

// SemverTag is one top-level semver tag resolved to its peeled object.
type SemverTag struct {
	Name   string
	Commit string
}

// ParseSemverTags reads `git ls-remote --tags` output. Peeled `^{}` entries take
// precedence over the tag object; path-prefixed and non-semver names are ignored.
func ParseSemverTags(listing []byte) []SemverTag {
	direct := make(map[string]string)
	peeled := make(map[string]string)
	scanner := bufio.NewScanner(bytes.NewReader(listing))
	scanner.Buffer(make([]byte, 0, 4096), maxSourceRevListLineBytes)
	for scanner.Scan() {
		sha, ref, found := strings.Cut(scanner.Text(), "\t")
		if !found || !validGitSHA(sha) || !strings.HasPrefix(ref, "refs/tags/") {
			continue
		}
		name := strings.TrimPrefix(ref, "refs/tags/")
		target := direct
		if strings.HasSuffix(name, "^{}") {
			name, target = strings.TrimSuffix(name, "^{}"), peeled
		}
		if len(name) > maxSourceTagNameLength || strings.Contains(name, "/") || !semverTagName.MatchString(name) {
			continue
		}
		target[name] = sha
	}
	tags := make([]SemverTag, 0, len(direct))
	for name, sha := range direct {
		if commit, ok := peeled[name]; ok {
			sha = commit
		}
		tags = append(tags, SemverTag{Name: name, Commit: sha})
	}
	for name, sha := range peeled {
		if _, ok := direct[name]; !ok {
			tags = append(tags, SemverTag{Name: name, Commit: sha})
		}
	}
	sort.Slice(tags, func(i, j int) bool { return tags[i].Name < tags[j].Name })
	return tags
}

type commitGraph map[string][]string

func parseCommitGraph(history []byte) (commitGraph, error) {
	graph := make(commitGraph)
	scanner := bufio.NewScanner(bytes.NewReader(history))
	scanner.Buffer(make([]byte, 0, 4096), maxSourceRevListLineBytes)
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) == 0 {
			continue
		}
		for _, sha := range fields {
			if !validGitSHA(sha) {
				return nil, errors.New("HISTORY_FETCH_FAILED")
			}
		}
		graph[fields[0]] = fields[1:]
		if len(graph) > maxSourceHistoryCommits {
			return nil, errors.New("HISTORY_TOO_LARGE")
		}
	}
	if scanner.Err() != nil {
		return nil, errors.New("HISTORY_FETCH_FAILED")
	}
	return graph, nil
}

// RelateSourceTag applies the documented selection rules to a complete history.
func RelateSourceTag(commitSHA string, tags []SemverTag, graph commitGraph) SourceTag {
	if _, ok := graph[commitSHA]; !ok {
		return unknownSourceTag("HISTORY_FETCH_FAILED")
	}
	for _, parents := range graph {
		for _, parent := range parents {
			if _, ok := graph[parent]; !ok {
				// A parent outside the fetched graph means history is incomplete.
				return unknownSourceTag("SHALLOW_HISTORY")
			}
		}
	}
	var best *SemverTag
	bestDistance := -1
	visits := 0
	for index := range tags {
		tag := &tags[index]
		if _, reachable := graph[tag.Commit]; !reachable {
			continue
		}
		ancestors, ok := countAncestors(graph, tag.Commit, &visits)
		if !ok {
			return unknownSourceTag("DISTANCE_BUDGET_EXCEEDED")
		}
		distance := len(graph) - ancestors
		if best == nil || distance < bestDistance ||
			distance == bestDistance && preferTag(*tag, *best) {
			best, bestDistance = tag, distance
		}
	}
	if best == nil {
		return SourceTag{Status: SourceTagNoTag}
	}
	status := SourceTagAfterTag
	if bestDistance == 0 {
		status = SourceTagExact
	}
	return SourceTag{Status: status, Tag: best.Name, TagCommitSHA: best.Commit, CommitsAfterTag: bestDistance}
}

func countAncestors(graph commitGraph, start string, visits *int) (int, bool) {
	seen := map[string]struct{}{start: {}}
	stack := []string{start}
	for len(stack) > 0 {
		current := stack[len(stack)-1]
		stack = stack[:len(stack)-1]
		*visits++
		if *visits > maxSourceDistanceVisits {
			return 0, false
		}
		for _, parent := range graph[current] {
			if _, ok := seen[parent]; !ok {
				seen[parent] = struct{}{}
				stack = append(stack, parent)
			}
		}
	}
	return len(seen), true
}

// preferTag orders equal-distance tags by semver precedence, then by name.
func preferTag(candidate, current SemverTag) bool {
	if order := compareSemver(candidate.Name, current.Name); order != 0 {
		return order > 0
	}
	return candidate.Name < current.Name
}

func compareSemver(left, right string) int {
	l, r := semverTagName.FindStringSubmatch(left), semverTagName.FindStringSubmatch(right)
	for index := 1; index <= 3; index++ {
		if order := compareNumeric(l[index], r[index]); order != 0 {
			return order
		}
	}
	switch {
	case l[4] == "" && r[4] == "":
		return 0
	case l[4] == "":
		return 1
	case r[4] == "":
		return -1
	}
	leftParts, rightParts := strings.Split(l[4], "."), strings.Split(r[4], ".")
	for index := 0; index < len(leftParts) && index < len(rightParts); index++ {
		leftNumeric, rightNumeric := isNumeric(leftParts[index]), isNumeric(rightParts[index])
		var order int
		switch {
		case leftNumeric && rightNumeric:
			order = compareNumeric(leftParts[index], rightParts[index])
		case leftNumeric:
			order = -1
		case rightNumeric:
			order = 1
		default:
			order = strings.Compare(leftParts[index], rightParts[index])
		}
		if order != 0 {
			return order
		}
	}
	return compareNumeric(strconv.Itoa(len(leftParts)), strconv.Itoa(len(rightParts)))
}

func isNumeric(value string) bool {
	for _, char := range value {
		if char < '0' || char > '9' {
			return false
		}
	}
	return value != ""
}

// compareNumeric compares non-negative decimal strings without overflow.
func compareNumeric(left, right string) int {
	left, right = strings.TrimLeft(left, "0"), strings.TrimLeft(right, "0")
	if len(left) != len(right) {
		if len(left) < len(right) {
			return -1
		}
		return 1
	}
	return strings.Compare(left, right)
}

type sourceTagGit struct {
	ctx          context.Context
	repoDir      string
	workDir      string
	maxBytes     int64
	minFreeBytes int64
}

var (
	errSourceTagOutputTooLarge = errors.New("source tag command output exceeds limit")
	errSourceTagTimeout        = errors.New("source tag resolution timed out")
)

// run executes one bounded Git command. It deliberately bypasses the package
// request policy: a source tag failure must not trigger quota cooldowns.
func (g sourceTagGit) run(maxOutput int64, args ...string) ([]byte, error) {
	if err := checkWorkspace(g.workDir, g.maxBytes, g.minFreeBytes); err != nil {
		return nil, err
	}
	command := exec.CommandContext(g.ctx, "git", append([]string{
		"-c", "credential.helper=", "-c", "core.hooksPath=/dev/null",
		"-c", "http.followRedirects=false",
	}, args...)...)
	command.Dir = g.repoDir
	command.Env = append(sourceTagGitEnvironment(), "GIT_NO_LAZY_FETCH=1")
	var output limitedBuffer
	output.limit = maxOutput
	if maxOutput == 0 {
		output.limit = maxSourceHistoryCommits * 123
	}
	command.Stdout = &output
	command.Stderr = io.Discard
	command.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := command.Start(); err != nil {
		return nil, localDiskWriteError(err)
	}
	done := make(chan error, 1)
	go func() { done <- command.Wait() }()
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case err := <-done:
			if output.exceeded.Load() {
				return nil, errSourceTagOutputTooLarge
			}
			if checkErr := checkWorkspace(g.workDir, g.maxBytes, g.minFreeBytes); checkErr != nil {
				return nil, checkErr
			}
			if err != nil {
				if g.ctx.Err() != nil {
					return nil, errSourceTagTimeout
				}
				return nil, errors.New("Git command failed")
			}
			return output.Bytes(), nil
		case <-ticker.C:
			if err := checkWorkspace(g.workDir, g.maxBytes, g.minFreeBytes); err != nil || output.exceeded.Load() {
				_ = syscall.Kill(-command.Process.Pid, syscall.SIGKILL)
				<-done
				if err != nil {
					return nil, err
				}
				return nil, errSourceTagOutputTooLarge
			}
		case <-g.ctx.Done():
			_ = syscall.Kill(-command.Process.Pid, syscall.SIGKILL)
			<-done
			return nil, errSourceTagTimeout
		}
	}
}

func (g sourceTagGit) reason(err error, fallback string) string {
	switch {
	case errors.Is(err, ErrLocalDiskBudget) || errors.Is(err, ErrCapacity):
		return "WORKSPACE_BUDGET"
	case errors.Is(err, errSourceTagTimeout):
		return "TIMEOUT"
	case errors.Is(err, errSourceTagOutputTooLarge) && fallback == "TAG_LIST_FAILED":
		return "TOO_MANY_TAGS"
	case errors.Is(err, errSourceTagOutputTooLarge):
		return "HISTORY_TOO_LARGE"
	}
	return fallback
}

func sourceTagGitEnvironment() []string {
	environment := gitEnvironment()
	for index, entry := range environment {
		if strings.HasPrefix(entry, "GIT_ALLOW_PROTOCOL=") {
			environment[index] = "GIT_ALLOW_PROTOCOL=" + sourceTagAllowedProtocol
		}
	}
	return environment
}

// limitedBuffer is written by the os/exec copy goroutine; the overflow flag is
// polled concurrently, and the contents are read only after Wait returns. The
// buffer is a named field so io.Copy cannot bypass Write through ReadFrom.
type limitedBuffer struct {
	data     bytes.Buffer
	limit    int64
	exceeded atomic.Bool
}

func (buffer *limitedBuffer) Write(data []byte) (int, error) {
	if int64(buffer.data.Len()+len(data)) > buffer.limit {
		buffer.exceeded.Store(true)
		return 0, errSourceTagOutputTooLarge
	}
	return buffer.data.Write(data)
}

func (buffer *limitedBuffer) Bytes() []byte {
	return buffer.data.Bytes()
}
