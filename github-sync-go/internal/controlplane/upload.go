package controlplane

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

type UploadAuthorization struct {
	UploadID     string            `json:"uploadId"`
	Method       string            `json:"method"`
	Mode         string            `json:"mode"`
	UploadURL    string            `json:"uploadUrl"`
	UploadTicket string            `json:"uploadTicket,omitempty"`
	ExpiresAt    time.Time         `json:"expiresAt"`
	MaxBytes     int64             `json:"maxBytes"`
	Headers      map[string]string `json:"headers,omitempty"`
}

func (c *Client) AuthorizeUpload(ctx context.Context, taskID string, lease LeaseProof,
	admissionID, commitSHA, skillPath string, sizeBytes int64, digest string) (UploadAuthorization, error) {
	if taskID == "" || admissionID == "" || sizeBytes < 1 || len(digest) != 64 {
		return UploadAuthorization{}, errors.New("invalid package upload request")
	}
	var authorization UploadAuthorization
	_, err := c.post(ctx, "/tasks/"+url.PathEscape(taskID)+"/upload-authorizations", map[string]any{
		"lease": lease, "admissionId": admissionID, "commitSha": commitSHA,
		"skillPath": skillPath, "sizeBytes": sizeBytes, "sha256": digest,
	}, &authorization)
	if err != nil {
		return UploadAuthorization{}, err
	}
	if authorization.UploadID == "" || authorization.Method != http.MethodPut ||
		(authorization.Mode != "LOCAL_STREAM" && authorization.Mode != "S3_PRESIGNED_PUT") ||
		authorization.UploadURL == "" || authorization.ExpiresAt.IsZero() ||
		authorization.MaxBytes < sizeBytes || authorization.ExpiresAt.Before(time.Now()) {
		return UploadAuthorization{}, errors.New("internal API returned an invalid upload authorization")
	}
	return authorization, nil
}

// SourceTag is optional read-only provenance; Java stores it with the ingestion.
type SourceTag struct {
	Status          string `json:"status"`
	Tag             string `json:"tag,omitempty"`
	TagCommitSHA    string `json:"tagCommitSha,omitempty"`
	CommitsAfterTag *int   `json:"commitsAfterTag,omitempty"`
	Reason          string `json:"reason,omitempty"`
}

func (c *Client) ReportPackage(ctx context.Context, taskID string, lease LeaseProof,
	admissionID, commitSHA, skillPath string, authorization UploadAuthorization,
	sizeBytes int64, digest string, sourceTag *SourceTag) (string, error) {
	if taskID == "" || admissionID == "" || authorization.UploadID == "" ||
		sizeBytes < 1 || len(digest) != 64 {
		return "", errors.New("invalid package report")
	}
	var response struct {
		IngestionID string `json:"ingestionId"`
		Status      string `json:"status"`
	}
	request := map[string]any{
		"lease": lease, "admissionId": admissionID, "commitSha": commitSHA,
		"skillPath": skillPath, "uploadId": authorization.UploadID,
		"sizeBytes": sizeBytes, "sha256": digest,
	}
	if sourceTag != nil {
		request["sourceTag"] = sourceTag
	}
	_, err := c.post(ctx, "/tasks/"+url.PathEscape(taskID)+"/packages/report", request, &response)
	if err != nil {
		return "", err
	}
	if response.IngestionID == "" || response.Status != "RECEIVED" {
		return "", errors.New("internal API returned an invalid package receipt")
	}
	return response.IngestionID, nil
}

// UploadPackage never sends the Java service token to an object-storage URL.
// S3 hosts must be explicitly configured by the deployment, including their port.
func (c *Client) UploadPackage(ctx context.Context, authorization UploadAuthorization,
	archivePath string, allowedS3Hosts []string) error {
	archive, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer archive.Close()
	info, err := archive.Stat()
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() < 1 || info.Size() > authorization.MaxBytes {
		return errors.New("archive does not match upload size policy")
	}
	parsed, err := url.Parse(authorization.UploadURL)
	if err != nil || parsed.User != nil || parsed.Fragment != "" {
		return errors.New("invalid upload URL")
	}
	var target string
	var client *http.Client
	local := authorization.Mode == "LOCAL_STREAM"
	if local {
		expected := "/internal/github-sync/v1/uploads/" + authorization.UploadID + "/content"
		if parsed.IsAbs() || parsed.Host != "" || parsed.Path != expected || parsed.RawQuery != "" ||
			authorization.UploadTicket == "" {
			return errors.New("invalid local upload target")
		}
		base, err := url.Parse(c.baseURL)
		if err != nil {
			return err
		}
		target = base.Scheme + "://" + base.Host + expected
		client = directUploadClient()
	} else {
		if authorization.Mode != "S3_PRESIGNED_PUT" || (parsed.Scheme != "https" && parsed.Scheme != "http") ||
			parsed.Host == "" || len(allowedS3Hosts) == 0 {
			return errors.New("invalid object-storage upload target")
		}
		allowed := false
		for _, host := range allowedS3Hosts {
			allowed = allowed || strings.EqualFold(parsed.Host, host)
		}
		if !allowed {
			return errors.New("object-storage host is not allowed")
		}
		target = authorization.UploadURL
		client = directUploadClient()
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPut, target, archive)
	if err != nil {
		return errors.New("cannot prepare package upload request")
	}
	request.ContentLength = info.Size()
	for name, value := range authorization.Headers {
		if strings.EqualFold(name, "authorization") || strings.EqualFold(name, "host") ||
			strings.EqualFold(name, "x-upload-ticket") {
			return errors.New("upload authorization contains a forbidden header")
		}
		request.Header.Set(name, value)
	}
	if local {
		request.Header.Set("Authorization", "Bearer "+c.token)
		request.Header.Set("X-Upload-Ticket", authorization.UploadTicket)
		request.Header.Set("Content-Type", "application/octet-stream")
		requestID := make([]byte, 16)
		if _, err := rand.Read(requestID); err != nil {
			return err
		}
		request.Header.Set("X-Request-Id", hex.EncodeToString(requestID))
	}
	response, err := client.Do(request)
	if err != nil {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		return errors.New("package upload transport failed")
	}
	defer response.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
	if response.StatusCode != http.StatusNoContent && response.StatusCode != http.StatusOK {
		return fmt.Errorf("package upload returned HTTP %d", response.StatusCode)
	}
	return nil
}

func directUploadClient() *http.Client {
	return &http.Client{
		Timeout:       10 * time.Minute,
		Transport:     &http.Transport{Proxy: nil, MaxIdleConnsPerHost: 2},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
}
