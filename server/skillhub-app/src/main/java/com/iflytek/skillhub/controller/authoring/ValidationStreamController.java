package com.iflytek.skillhub.controller.authoring;

import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.service.authoring.ValidationEventBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-sent events for validation runs. Browsers authenticate via the session
 * cookie (EventSource cannot send headers); token clients use the /api/v1 variant.
 * Event ids are the per-run sequence numbers, so reconnecting clients resume from
 * Last-Event-ID without gaps or duplicates.
 */
@Tag(name = "Skill authoring")
@RestController
@RequestMapping({"/api/v1/authoring", "/api/web/authoring"})
public class ValidationStreamController {

    private final ValidationRunService runService;
    private final ValidationEventBroadcaster broadcaster;

    public ValidationStreamController(ValidationRunService runService,
                                      ValidationEventBroadcaster broadcaster) {
        this.runService = runService;
        this.broadcaster = broadcaster;
    }

    @Operation(operationId = "streamValidationEvents",
            summary = "Stream run events over SSE; resumes from Last-Event-ID")
    @GetMapping(value = "/runs/{runId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable Long runId,
                                   @RequestParam(value = "afterSeq", required = false) Integer afterSeq,
                                   @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                   @RequestAttribute("userId") String userId,
                                   @RequestAttribute(value = "platformRoles", required = false)
                                   Set<String> platformRoles) {
        ValidationRun run = runService.getOwnedRun(runId, userId, platformRoles);
        int lastSeq = resolveCursor(afterSeq, lastEventId);
        List<ValidationEvent> replay = runService.listEvents(run.getId(), lastSeq);
        return broadcaster.subscribe(run.getId(), lastSeq, replay);
    }

    private int resolveCursor(Integer afterSeq, String lastEventId) {
        int fromParam = afterSeq == null ? 0 : Math.max(0, afterSeq);
        int fromHeader = 0;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                fromHeader = Math.max(0, Integer.parseInt(lastEventId.trim()));
            } catch (NumberFormatException ignored) {
                // malformed Last-Event-ID restarts from the beginning
            }
        }
        return Math.max(fromParam, fromHeader);
    }
}
