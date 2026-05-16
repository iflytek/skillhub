package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.media.MediaAsset;
import com.iflytek.skillhub.domain.media.MediaAssetRole;
import com.iflytek.skillhub.domain.media.MediaAssetService;
import com.iflytek.skillhub.domain.media.MediaOwnerType;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.media.MediaAssetResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Public media endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/v1/media} — upload a single asset (login required).</li>
 *   <li>{@code GET /api/v1/media/{id}} — stream the asset body. Public read for now,
 *       owner-content visibility tightening is left to the caller's auth filter.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController extends BaseApiController {

    private final MediaAssetService mediaAssetService;

    public MediaController(MediaAssetService mediaAssetService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.mediaAssetService = mediaAssetService;
    }

    @PostMapping
    public ApiResponse<MediaAssetResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("ownerType") MediaOwnerType ownerType,
                                                  @RequestParam("ownerId") Long ownerId,
                                                  @RequestParam("role") MediaAssetRole role,
                                                  @RequestParam(value = "altText", required = false) String altText,
                                                  @RequestAttribute("userId") String userId,
                                                  HttpServletRequest httpRequest) throws IOException {
        MediaAssetService.UploadCommand command = new MediaAssetService.UploadCommand(
                ownerType, ownerId, role, file.getBytes(), file.getContentType(),
                file.getOriginalFilename(), altText, userId);
        MediaAsset asset = mediaAssetService.upload(command);
        return ok("response.success.created", MediaAssetResponse.from(asset));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getAsset(@PathVariable Long id) {
        MediaAsset asset = mediaAssetService.get(id);
        byte[] body = mediaAssetService.read(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(asset.getContentType()));
        headers.setContentLength(body.length);
        // Long-cache GIFs/images keyed by id since assets are immutable once stored.
        headers.add(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        if (asset.getAltText() != null) {
            headers.add("X-Media-Alt-Text", asset.getAltText());
        }
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
