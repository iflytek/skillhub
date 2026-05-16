package com.iflytek.skillhub.domain.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link MediaAssetService}: validation, hashing, storage layout, and
 * read-through error handling.
 */
@ExtendWith(MockitoExtension.class)
class MediaAssetServiceTest {

    private MediaAssetRepository repository;
    private MediaValidator validator;
    private MediaAssetService.MediaStorage storage;
    private MediaAssetService.MediaHasher hasher;
    private MediaAssetService service;

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        validator = new MediaValidator(10_000, 5_000);
        storage = mock(MediaAssetService.MediaStorage.class);
        hasher = mock(MediaAssetService.MediaHasher.class);
        service = new MediaAssetService(repository, validator, storage, hasher);
    }

    @Test
    void upload_storesGifWithSignaturePathAndContentTypeAndPersistsAsset() {
        byte[] gifBody = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x02};
        given(hasher.sha256(gifBody)).willReturn("abcd1234");
        given(repository.save(any(MediaAsset.class))).willAnswer(invocation -> invocation.getArgument(0));

        MediaAssetService.UploadCommand command = new MediaAssetService.UploadCommand(
                MediaOwnerType.SKILL_VERSION, 7L, MediaAssetRole.DEMO,
                gifBody, "image/gif", "demo.gif", "演示效果", "alice");

        MediaAsset stored = service.upload(command);

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(repository).save(captor.capture());
        MediaAsset captured = captor.getValue();
        assertThat(captured.getMediaType()).isEqualTo(MediaType.GIF);
        assertThat(captured.getRole()).isEqualTo(MediaAssetRole.DEMO);
        assertThat(captured.getObjectKey()).isEqualTo("media/skill_version/7/abcd1234.gif");
        assertThat(captured.getContentType()).isEqualTo("image/gif");
        assertThat(captured.getSizeBytes()).isEqualTo(8);
        assertThat(captured.getAltText()).isEqualTo("演示效果");
        assertThat(captured.getFilePath()).isEqualTo("demo.gif");
        verify(storage).put("media/skill_version/7/abcd1234.gif", gifBody, "image/gif");
        assertThat(stored).isSameAs(captured);
    }

    @Test
    void upload_rejectsEmptyBody() {
        MediaAssetService.UploadCommand command = new MediaAssetService.UploadCommand(
                MediaOwnerType.SKILL_VERSION, 7L, MediaAssetRole.DEMO,
                new byte[0], "image/gif", "demo.gif", null, "alice");

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(MediaException.class)
                .hasMessage("error.media.empty");

        verify(storage, never()).put(anyString(), any(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void upload_rejectsHeaderMismatch() {
        byte[] body = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MediaAssetService.UploadCommand command = new MediaAssetService.UploadCommand(
                MediaOwnerType.SKILL_VERSION, 7L, MediaAssetRole.DEMO,
                body, "image/gif", "demo.gif", null, "alice");

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(MediaException.class)
                .hasMessage("error.media.gif.invalidSignature");
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void read_throwsWhenAssetMissing() {
        given(repository.findById(99L)).willReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.read(99L))
                .isInstanceOf(MediaException.class)
                .hasMessage("error.media.notFound");
    }

    @Test
    void read_returnsBytesFromStorageForExistingAsset() {
        MediaAsset asset = new MediaAsset(MediaOwnerType.SKILL_VERSION, 7L, MediaType.GIF,
                MediaAssetRole.DEMO, "media/skill_version/7/abcd1234.gif", "image/gif",
                4, "abcd1234", "alice");
        given(repository.findById(99L)).willReturn(java.util.Optional.of(asset));
        given(storage.get("media/skill_version/7/abcd1234.gif")).willReturn(new byte[] {1, 2, 3, 4});

        byte[] data = service.read(99L);

        assertThat(data).containsExactly(1, 2, 3, 4);
    }
}
