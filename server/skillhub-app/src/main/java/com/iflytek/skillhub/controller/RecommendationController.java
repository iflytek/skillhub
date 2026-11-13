package com.iflytek.skillhub.controller;
import com.iflytek.skillhub.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import com.iflytek.skillhub.service.RecommendationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site-recommendations")
public class RecommendationController extends BaseApiController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service, ApiResponseFactory responses) { super(responses); this.service = service; }
    @GetMapping
    @Operation(operationId = "getSiteRecommendations", summary = "Read published homepage recommendations")
    public ResponseEntity<ApiResponse<RecommendationPublicResponse>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ok("response.success.read", service.published()));
    }
    @GetMapping("/topics/{topicId}")
    @Operation(operationId = "getRecommendationTopic", summary = "Read an available published collection")
    public ResponseEntity<ApiResponse<RecommendationTopicResponse>> topic(@PathVariable String topicId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ok("response.success.read", service.topic(topicId)));
    }
}
