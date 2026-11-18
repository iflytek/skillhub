package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.repository.GithubRepositorySnapshotQueryRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/** Packages the imported bundles of a single repository snapshot. */
@Service
public class GithubRepositorySnapshotArchiveService {
    private static final int MAX_MEMBERS = 250;
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private final GithubRepositorySnapshotQueryRepository snapshots;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;
    private final Semaphore slots = new Semaphore(2);

    public GithubRepositorySnapshotArchiveService(GithubRepositorySnapshotQueryRepository snapshots,
                                                  ObjectStorageService storage, ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.storage = storage;
        this.mapper = mapper;
    }

    public void write(long repositoryId, long snapshotId, Set<Long> namespaces, ArchiveOutput output) throws IOException {
        if (!slots.tryAcquire()) {
            throw new DomainBadRequestException("error.githubSync.snapshot.archiveBusy");
        }
        try {
            writeArchive(repositoryId, snapshotId, namespaces, output);
        } finally {
            slots.release();
        }
    }

    private void writeArchive(long repositoryId, long snapshotId, Set<Long> namespaces, ArchiveOutput output) throws IOException {
        var members = snapshots.archiveMembers(repositoryId, snapshotId, namespaces, MAX_MEMBERS + 1);
        if (members.isEmpty()) {
            throw new DomainNotFoundException("error.githubSync.snapshot.unavailable");
        }
        if (members.size() > MAX_MEMBERS) {
            throw new DomainBadRequestException("error.githubSync.snapshot.archiveTooLarge");
        }

        var archive = Files.createTempFile("skillhub-github-snapshot-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.setLevel(0);
                var manifest = new ArrayList<Member>();
                long remaining = MAX_BYTES;
                byte[] buffer = new byte[64 * 1024];
                for (var member : members) {
                    String key = "packages/%d/%d/bundle.zip".formatted(member.skillId(), member.versionId());
                    if (!storage.exists(key)) {
                        throw new DomainNotFoundException("error.githubSync.snapshot.unavailable");
                    }
                    String entry = "skills/%d-%d.zip".formatted(member.skillId(), member.versionId());
                    var digest = sha256();
                    long memberSize = 0;
                    zip.putNextEntry(new ZipEntry(entry));
                    try (var input = storage.getObject(key)) {
                        for (int read; (read = input.read(buffer)) != -1; ) {
                            memberSize += read;
                            remaining -= read;
                            if (remaining < 0) {
                                throw new DomainBadRequestException("error.githubSync.snapshot.archiveTooLarge");
                            }
                            digest.update(buffer, 0, read);
                            zip.write(buffer, 0, read);
                        }
                    }
                    if (memberSize == 0) {
                        throw new DomainNotFoundException("error.githubSync.snapshot.unavailable");
                    }
                    zip.closeEntry();
                    manifest.add(new Member(member.slug(), member.version(), entry, HexFormat.of().formatHex(digest.digest())));
                }
                zip.putNextEntry(new ZipEntry("snapshot.json"));
                zip.write(mapper.writeValueAsBytes(new Manifest(
                        Long.toString(repositoryId), Long.toString(snapshotId), members.getFirst().commit(), manifest)));
                zip.closeEntry();
            }
            if (!members.equals(snapshots.archiveMembers(repositoryId, snapshotId, namespaces, MAX_MEMBERS + 1))) {
                throw new DomainNotFoundException("error.githubSync.snapshot.unavailable");
            }
            output.send(archive);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    public interface ArchiveOutput {
        void send(Path archive) throws IOException;
    }

    record Manifest(String repositoryId, String snapshotId, String commit, List<Member> skills) {}
    record Member(String slug, String version, String file, String sha256) {}
}
