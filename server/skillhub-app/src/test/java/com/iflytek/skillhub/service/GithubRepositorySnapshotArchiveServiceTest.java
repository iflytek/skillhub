package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.repository.GithubRepositorySnapshotQueryRepository;
import com.iflytek.skillhub.repository.GithubRepositorySnapshotQueryRepository.ArchiveMember;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubRepositorySnapshotArchiveServiceTest {
    private final GithubRepositorySnapshotQueryRepository snapshots = mock(GithubRepositorySnapshotQueryRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ArchiveMember> members = List.of(new ArchiveMember(41, 81, "skill-one", "1.0.0", "a".repeat(40)));
    private final byte[] payload = {1, 2, 3, 4};
    private GithubRepositorySnapshotArchiveService service;

    @BeforeEach
    void setUp() {
        service = new GithubRepositorySnapshotArchiveService(snapshots, storage, mapper);
        when(snapshots.archiveMembers(901, 7, Set.of(), 251)).thenReturn(members);
        when(storage.exists("packages/41/81/bundle.zip")).thenReturn(true);
        when(storage.getObject("packages/41/81/bundle.zip")).thenAnswer(ignored -> new ByteArrayInputStream(payload));
    }

    @Test
    void archivePinsContentAndChecksumAndRemovesTemporaryFile() throws Exception {
        var file = new AtomicReference<Path>();
        String expectedChecksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        service.write(901, 7, Set.of(), path -> {
            file.set(path);
            try (var zip = new ZipFile(path.toFile())) {
                assertThat(zip.size()).isEqualTo(2);
                assertThat(zip.getInputStream(zip.getEntry("skills/41-81.zip")).readAllBytes()).isEqualTo(payload);
                var manifest = mapper.readTree(zip.getInputStream(zip.getEntry("snapshot.json")));
                assertThat(manifest.get("repositoryId").asText()).isEqualTo("901");
                assertThat(manifest.get("snapshotId").asText()).isEqualTo("7");
                assertThat(manifest.get("commit").asText()).isEqualTo("a".repeat(40));
                var member = manifest.get("skills").get(0);
                assertThat(member.get("version").asText()).isEqualTo("1.0.0");
                assertThat(member.get("sha256").asText()).isEqualTo(
                        expectedChecksum);
            }
        });
        assertThat(Files.exists(file.get())).isFalse();
    }

    @Test
    void missingMemberDoesNotStartDownload() {
        when(storage.exists("packages/41/81/bundle.zip")).thenReturn(false);
        assertThatThrownBy(() -> service.write(901, 7, Set.of(), path -> fail("Partial download")))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void revokedSnapshotDoesNotStartDownload() {
        when(snapshots.archiveMembers(901, 7, Set.of(), 251)).thenReturn(members, List.of());
        assertThatThrownBy(() -> service.write(901, 7, Set.of(), path -> fail("Revoked download")))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void tooManyMembersAreRejectedBeforeStorageReads() {
        when(snapshots.archiveMembers(901, 7, Set.of(), 251))
                .thenReturn(java.util.Collections.nCopies(251, members.getFirst()));
        assertThatThrownBy(() -> service.write(901, 7, Set.of(), path -> fail("Oversized download")))
                .isInstanceOf(DomainBadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void failedDeliveryCleansFileAndReleasesSlot() throws Exception {
        var file = new AtomicReference<Path>();
        assertThatThrownBy(() -> service.write(901, 7, Set.of(), path -> {
            file.set(path);
            throw new IOException("client disconnected");
        })).isInstanceOf(IOException.class);
        assertThat(Files.exists(file.get())).isFalse();
        service.write(901, 7, Set.of(), path -> assertThat(Files.exists(path)).isTrue());
    }

    @Test
    void thirdConcurrentDeliveryIsRejectedAndCapacityRecovers() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var pending = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            try {
                for (int i = 0; i < 2; i++) {
                    pending.add(pool.submit(() -> {
                        try {
                            service.write(901, 7, Set.of(), path -> {
                                entered.countDown();
                                try {
                                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("test deadline");
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(exception);
                                }
                            });
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    }));
                }
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.write(901, 7, Set.of(), path -> fail("Third concurrent download")))
                        .isInstanceOf(DomainBadRequestException.class);
            } finally {
                release.countDown();
            }
            for (var task : pending) task.get(10, TimeUnit.SECONDS);
        }
        service.write(901, 7, Set.of(), path -> assertThat(Files.exists(path)).isTrue());
    }
}
