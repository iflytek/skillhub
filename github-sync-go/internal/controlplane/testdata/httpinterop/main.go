package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/iflytek/skillhub/github-sync-go/internal/controlplane"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	javaURL := os.Getenv("SKILLHUB_INTEROP_JAVA_URL")
	serviceToken := os.Getenv("SKILLHUB_INTEROP_SERVICE_TOKEN")
	taskID := os.Getenv("SKILLHUB_INTEROP_TASK_ID")
	commitSHA := os.Getenv("SKILLHUB_INTEROP_COMMIT_SHA")
	skillPath := os.Getenv("SKILLHUB_INTEROP_SKILL_PATH")
	archivePath := os.Getenv("SKILLHUB_INTEROP_ARCHIVE_PATH")
	if taskID == "" || commitSHA == "" || skillPath == "" || archivePath == "" {
		return errors.New("missing HTTP interoperability fixture")
	}
	client, err := controlplane.NewClient(javaURL, serviceToken)
	if err != nil {
		return err
	}
	archive, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer archive.Close()
	info, err := archive.Stat()
	if err != nil || !info.Mode().IsRegular() || info.Size() < 1 {
		return errors.New("invalid interoperability archive")
	}
	digest := sha256.New()
	if _, err := io.Copy(digest, archive); err != nil {
		return err
	}
	digestHex := hex.EncodeToString(digest.Sum(nil))
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	task, err := client.ClaimWithCapabilities(ctx, "interop-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		return err
	}
	if task == nil || task.TaskID != taskID || task.Mode != "FULL" ||
		task.Repository == nil || task.Repository.FixedCommitSHA != commitSHA {
		if task == nil {
			return errors.New("Java did not lease a repository task")
		}
		return fmt.Errorf("Java leased task %s in mode %s, expected task %s in FULL mode",
			task.TaskID, task.Mode, taskID)
	}
	mode := os.Getenv("SKILLHUB_INTEROP_MODE")
	if mode == "resume-only" {
		expectedIngestionID := os.Getenv("SKILLHUB_INTEROP_EXPECT_INGESTION_ID")
		if expectedIngestionID == "" || len(task.Repository.AcceptedPaths) != 1 ||
			task.Repository.AcceptedPaths[0] != skillPath || task.Lease.Generation < 2 {
			return errors.New("reclaimed lease did not return the persisted package path")
		}
		status, err := client.CompleteRepository(ctx, taskID, task.Lease,
			controlplane.RepositoryCompletion{CommitSHA: commitSHA,
				PathResults: []controlplane.RepositoryPathResult{{
					SkillPath: skillPath, Outcome: "ACCEPTED", IngestionID: expectedIngestionID,
				}}})
		if err != nil || status != "ACQUISITION_COMPLETE" {
			return errors.New("reclaimed lease could not complete the persisted package")
		}
		fmt.Println(expectedIngestionID)
		return nil
	}
	if mode != "" && mode != "report-only" && mode != "wait-for-java-outage" {
		return errors.New("invalid HTTP interoperability mode")
	}
	admission, err := client.AdmitPackage(ctx, taskID, task.Lease, commitSHA, skillPath)
	if err != nil {
		return err
	}
	authorization, err := client.AuthorizeUpload(ctx, taskID, task.Lease,
		admission.AdmissionID, commitSHA, skillPath, info.Size(), digestHex)
	if err != nil {
		return err
	}
	expectedMode := os.Getenv("SKILLHUB_INTEROP_UPLOAD_MODE")
	if expectedMode == "" {
		expectedMode = "LOCAL_STREAM"
	}
	if authorization.Mode != expectedMode {
		return errors.New("Java returned an unexpected staging upload mode")
	}
	var allowedS3Hosts []string
	if expectedMode == "S3_PRESIGNED_PUT" {
		allowedHost := os.Getenv("SKILLHUB_INTEROP_ALLOWED_S3_HOST")
		if allowedHost == "" {
			return errors.New("missing allowed S3 fixture host")
		}
		allowedS3Hosts = []string{allowedHost}
		if err := verifySignedUploadCannotWriteAnotherKey(ctx, authorization); err != nil {
			return err
		}
	}
	if err := client.UploadPackage(ctx, authorization, archivePath, allowedS3Hosts); err != nil {
		return err
	}
	// Java must keep the first report's source tag; the replay reports a different one.
	distance := 3
	sourceTag := &controlplane.SourceTag{Status: "AFTER_TAG", Tag: "v1.3.2",
		TagCommitSHA: strings.Repeat("d", 40), CommitsAfterTag: &distance}
	ingestionID, err := client.ReportPackage(ctx, taskID, task.Lease,
		admission.AdmissionID, commitSHA, skillPath, authorization, info.Size(), digestHex, sourceTag)
	if err != nil {
		return err
	}
	replayedID, err := client.ReportPackage(ctx, taskID, task.Lease,
		admission.AdmissionID, commitSHA, skillPath, authorization, info.Size(), digestHex,
		&controlplane.SourceTag{Status: "NO_TAG"})
	if err != nil || replayedID != ingestionID {
		return errors.New("Java did not replay the same package receipt")
	}
	forgedDigest := digestHex[:63] + "0"
	if digestHex[63] == '0' {
		forgedDigest = digestHex[:63] + "1"
	}
	_, err = client.ReportPackage(ctx, taskID, task.Lease,
		admission.AdmissionID, commitSHA, skillPath, authorization, info.Size(), forgedDigest, nil)
	var apiError *controlplane.APIError
	if !errors.As(err, &apiError) || apiError.Code != "RESULT_CONFLICT" {
		return errors.New("Java accepted a conflicting package report")
	}
	if mode == "report-only" {
		fmt.Println(ingestionID)
		return nil
	}
	if mode == "wait-for-java-outage" {
		if err := awaitJavaOutage(ctx, client, task, ingestionID); err != nil {
			return err
		}
		fmt.Println(ingestionID)
		return nil
	}
	status, err := client.CompleteRepository(ctx, taskID, task.Lease,
		controlplane.RepositoryCompletion{
			CommitSHA: commitSHA,
			PathResults: []controlplane.RepositoryPathResult{{
				SkillPath: skillPath, Outcome: "ACCEPTED", IngestionID: ingestionID,
			}},
		})
	if err != nil {
		return err
	}
	if status != "ACQUISITION_COMPLETE" {
		return errors.New("Java did not complete the repository acquisition")
	}
	if oversizedTaskID := os.Getenv("SKILLHUB_INTEROP_OVERSIZED_TASK_ID"); oversizedTaskID != "" {
		if err := verifyOversizedS3Object(ctx, client, oversizedTaskID, commitSHA,
			skillPath, info.Size(), digestHex, allowedS3Hosts); err != nil {
			return err
		}
	}
	fmt.Println(ingestionID)
	return nil
}

func awaitJavaOutage(ctx context.Context, client *controlplane.Client,
	task *controlplane.ClaimedTask, ingestionID string) error {
	readyPath := os.Getenv("SKILLHUB_INTEROP_READY_MARKER")
	outagePath := os.Getenv("SKILLHUB_INTEROP_OUTAGE_MARKER")
	if readyPath == "" || outagePath == "" {
		return errors.New("missing Java outage fixture markers")
	}
	if err := os.WriteFile(readyPath, []byte(ingestionID), 0600); err != nil {
		return err
	}
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		if _, err := os.Stat(outagePath); err == nil {
			break
		} else if !os.IsNotExist(err) {
			return err
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
	renewContext, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	_, err := client.Renew(renewContext, task.TaskID, task.Lease)
	if err == nil {
		return errors.New("Java accepted renewal while the service was stopped")
	}
	if ctx.Err() != nil || errors.Is(err, context.DeadlineExceeded) ||
		errors.Is(err, context.Canceled) {
		return errors.New("Java outage check expired before observing a connection failure")
	}
	var transportError *url.Error
	if !errors.As(err, &transportError) {
		return errors.New("Java outage check did not observe a transport failure")
	}
	return nil
}

func verifySignedUploadCannotWriteAnotherKey(ctx context.Context,
	authorization controlplane.UploadAuthorization) error {
	target, err := url.Parse(authorization.UploadURL)
	if err != nil || target.Scheme == "" || target.Host == "" {
		return errors.New("invalid signed upload target")
	}
	target.Path += ".forbidden"
	target.RawPath = ""
	request, err := http.NewRequestWithContext(ctx, http.MethodPut,
		target.String(), bytes.NewReader([]byte("forbidden")))
	if err != nil {
		return errors.New("could not create changed-key upload probe")
	}
	for name, value := range authorization.Headers {
		request.Header.Set(name, value)
	}
	probeClient := &http.Client{Transport: &http.Transport{Proxy: nil},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	response, err := probeClient.Do(request)
	if err != nil {
		return errors.New("changed-key upload probe did not reach object storage")
	}
	response.Body.Close()
	if response.StatusCode != http.StatusForbidden {
		return fmt.Errorf("changed-key upload unexpectedly returned HTTP %d", response.StatusCode)
	}
	return nil
}

// This deliberately bypasses UploadPackage's client-side size guard to prove
// Java rejects and removes an oversized object accepted by a signed PUT URL.
func verifyOversizedS3Object(ctx context.Context, client *controlplane.Client,
	taskID, commitSHA, skillPath string, size int64, digest string,
	allowedHosts []string) error {
	if len(allowedHosts) != 1 {
		return errors.New("oversized object fixture requires S3 staging")
	}
	task, err := client.ClaimWithCapabilities(ctx, "interop-oversize-worker", "REPOSITORY_SYNC", []string{controlplane.PlatformPauseCapability})
	if err != nil {
		return err
	}
	if task == nil || task.TaskID != taskID {
		if task == nil {
			return errors.New("Java had no repository task for the oversized object fixture")
		}
		return fmt.Errorf("Java leased repository task %s instead of oversized fixture task %s",
			task.TaskID, taskID)
	}
	admission, err := client.AdmitPackage(ctx, taskID, task.Lease, commitSHA, skillPath)
	if err != nil {
		return err
	}
	authorization, err := client.AuthorizeUpload(ctx, taskID, task.Lease,
		admission.AdmissionID, commitSHA, skillPath, size, digest)
	if err != nil {
		return err
	}
	if authorization.Mode != "S3_PRESIGNED_PUT" || authorization.MaxBytes < size ||
		authorization.MaxBytes > 1<<20 {
		return errors.New("invalid oversized object fixture authorization")
	}
	maliciousBody := bytes.NewReader(make([]byte, authorization.MaxBytes+1))
	request, err := http.NewRequestWithContext(ctx, http.MethodPut, authorization.UploadURL, maliciousBody)
	if err != nil {
		return err
	}
	for name, value := range authorization.Headers {
		request.Header.Set(name, value)
	}
	uploadClient := &http.Client{Transport: &http.Transport{Proxy: nil},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	response, err := uploadClient.Do(request)
	if err != nil {
		return err
	}
	response.Body.Close()
	if response.StatusCode != http.StatusOK && response.StatusCode != http.StatusNoContent {
		return errors.New("S3 did not accept the intentionally oversized object")
	}
	_, err = client.ReportPackage(ctx, taskID, task.Lease, admission.AdmissionID,
		commitSHA, skillPath, authorization, size, digest, nil)
	var apiError *controlplane.APIError
	if !errors.As(err, &apiError) || apiError.Status != http.StatusRequestEntityTooLarge ||
		apiError.Code != "PACKAGE_TOO_LARGE" {
		return errors.New("Java did not reject the oversized S3 object")
	}
	return nil
}
