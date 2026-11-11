package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.StorageAccessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SitePresentationAssetServiceTest {
    private static final String ID = "15e3ed2a-79fa-450c-986a-cc04c9b790c4";
    @Mock private ObjectStorageService storage;
    private SitePresentationAssetService service;

    @BeforeEach
    void setup() { service = new SitePresentationAssetService(storage, new SkillAvatarImageProcessor()); }

    @Test
    void rectangularJpegIsReencodedAsPngInBrandingPrefix() throws Exception {
        var uploaded = service.upload(new MockMultipartFile("file", "logo.jpg", "image/jpeg", image("jpeg", 96, 32)));
        assertThat(uploaded.url()).matches("/api/v1/site-presentation/assets/[0-9a-f-]{36}");
        String id = uploaded.url().substring(uploaded.url().lastIndexOf('/') + 1);
        var input = ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(storage).putObject(eq("site-presentation/assets/" + id + ".png"), input.capture(), anyLong(), eq("image/png"));
        byte[] bytes = input.getValue().readAllBytes();
        assertThat(bytes).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(96);
        assertThat(decoded.getHeight()).isEqualTo(32);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>", "not-an-image"})
    void rejectsSpoofedAndActiveContent(String content) {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "logo.png", "image/png", content.getBytes())))
                .isInstanceOf(DomainBadRequestException.class);
        verify(storage, never()).putObject(any(), any(), anyLong(), any());
    }

    @Test
    void rejectsEmptyOversizedAndExcessiveDimensions() throws Exception {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", image("png", 16, 1025))))
                .isInstanceOf(DomainBadRequestException.class);
        verify(storage, never()).putObject(any(), any(), anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/logo.png", "javascript:alert(1)", "/api/v1/site-presentation/assets/../../skills/private", "/api/v1/site-presentation/assets/1-1-1-1-1", "/api/v1/site-presentation/assets/15e3ed2a-79fa-450c-986a-cc04c9b790c4?download=1"})
    void rejectsForeignAndMalformedReferencesWithoutStorageLookup(String url) {
        assertThatThrownBy(() -> service.validateReference(url)).isInstanceOf(DomainBadRequestException.class);
        verify(storage, never()).exists(anyString());
    }

    @Test
    void rejectsJpegWhoseNormalizedPngExceedsLimit() throws Exception {
        var image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(7);
        for (int y = 0; y < 1024; y++) {
            for (int x = 0; x < 1024; x++) image.setRGB(x, y, random.nextInt());
        }
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", encoded);
        assertThat(encoded.size()).isLessThan(SkillAvatarImageProcessor.MAX_BYTES);
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "noise.jpg", "image/jpeg", encoded.toByteArray())))
                .isInstanceOf(DomainBadRequestException.class);
        verify(storage, never()).putObject(any(), any(), anyLong(), any());
    }

    @Test
    void validatesOnlyExistingIsolatedAssetKeys() {
        when(storage.exists("site-presentation/assets/" + ID + ".png")).thenReturn(true);
        service.validateReference("/api/v1/site-presentation/assets/" + ID);
        service.validateReference(null);
        verify(storage).exists("site-presentation/assets/" + ID + ".png");
    }

    @Test
    void unknownAssetCannotReadOtherStorageObjects() {
        assertThatThrownBy(() -> service.read("../../skills/private")).isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.read(ID)).isInstanceOf(DomainNotFoundException.class);
        verify(storage, never()).getObject(any());
    }

    @Test
    void publicReadReturnsOnlyKnownBrandingAsset() {
        when(storage.exists("site-presentation/assets/" + ID + ".png")).thenReturn(true);
        when(storage.getObject("site-presentation/assets/" + ID + ".png")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2}));
        assertThat(service.read(ID)).containsExactly((byte) 1, (byte) 2);
    }

    @Test
    void storageFailurePropagates() {
        when(storage.exists("site-presentation/assets/" + ID + ".png"))
                .thenThrow(new StorageAccessException("exists", "redacted", new IllegalStateException("offline")));
        assertThatThrownBy(() -> service.read(ID)).isInstanceOf(StorageAccessException.class);
    }

    private byte[] image(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
