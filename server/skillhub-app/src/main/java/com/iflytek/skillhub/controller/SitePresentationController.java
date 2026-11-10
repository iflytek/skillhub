package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SitePresentationResponse;
import com.iflytek.skillhub.service.SitePresentationAssetService;
import com.iflytek.skillhub.service.SitePresentationService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/site-presentation")
public class SitePresentationController extends BaseApiController {
    private final SitePresentationService service;
    private final SitePresentationAssetService assets;

    public SitePresentationController(SitePresentationService service, SitePresentationAssetService assets,
                                      ApiResponseFactory responses) {
        super(responses);
        this.service = service;
        this.assets = assets;
    }

    @GetMapping
    @Operation(summary = "Read public site presentation settings")
    public ResponseEntity<ApiResponse<SitePresentationResponse>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ok("response.success.read", service.current()));
    }

    @GetMapping("/assets/{assetId}")
    @Operation(summary = "Read a normalized public branding image")
    public ResponseEntity<byte[]> asset(@PathVariable String assetId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff").body(assets.read(assetId));
    }
}
