package com.iflytek.skillhub.controller.authoring;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.CreateDraftRequest;
import com.iflytek.skillhub.dto.DraftFileContentResponse;
import com.iflytek.skillhub.dto.DraftFileResponse;
import com.iflytek.skillhub.dto.DraftResponse;
import com.iflytek.skillhub.dto.SaveDraftFileRequest;
import com.iflytek.skillhub.dto.SaveDraftFileResponse;
import com.iflytek.skillhub.dto.ValidationEventResponse;
import com.iflytek.skillhub.dto.ValidationFindingResponse;
import com.iflytek.skillhub.dto.ValidationRunResponse;
import com.iflytek.skillhub.service.authoring.ValidationRunOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for skill draft authoring and structure validation. Drafts
 * own a SKILL.md scaffold and arbitrary resource files; validation runs
 * execute the STRUCTURE layer and report findings with fix suggestions.
 * Runtime binding, behavior execution and the publish integration arrive with
 * the later layers.
 */
@RestController
@RequestMapping({"/api/v1/authoring", "/api/web/authoring"})
public class SkillAuthoringController extends BaseApiController {

    private final SkillDraftService draftService;
    private final ValidationRunService runService;
    private final ValidationRunOrchestrator orchestrator;

    public SkillAuthoringController(com.iflytek.skillhub.dto.ApiResponseFactory responseFactory,
                                    SkillDraftService draftService,
                                    ValidationRunService runService,
                                    ValidationRunOrchestrator orchestrator) {
        super(responseFactory);
        this.draftService = draftService;
        this.runService = runService;
        this.orchestrator = orchestrator;
    }

    // ---------------------------------------------------------------- drafts

    @Operation(operationId = "createAuthoringDraft", summary = "Create a draft seeded with a SKILL.md scaffold")
    @PostMapping("/drafts")
    public ApiResponse<DraftResponse> createDraft(@Valid
                                                  @RequestBody CreateDraftRequest request,
                                                  @RequestAttribute("userId") String userId,
                                                  @RequestAttribute(value = "platformRoles", required = false)
                                                  Set<String> platformRoles) {
        SkillDraft draft = draftService.createDraft(
                request.namespaceSlug(), userId, request.name(), request.requirement(), platformRoles);
        return ok("response.success.created", DraftResponse.from(draft));
    }

    @Operation(operationId = "listAuthoringDrafts", summary = "List the current user's drafts")
    @GetMapping("/drafts")
    public ApiResponse<List<DraftResponse>> listDrafts(@RequestAttribute("userId") String userId) {
        return ok("response.success", draftService.listDrafts(userId).stream()
                .map(DraftResponse::from)
                .toList());
    }

    @Operation(operationId = "getAuthoringDraft", summary = "Get one draft owned by the current user")
    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftResponse> getDraft(@PathVariable Long draftId,
                                               @RequestAttribute("userId") String userId,
                                               @RequestAttribute(value = "platformRoles", required = false)
                                               Set<String> platformRoles) {
        return ok("response.success",
                DraftResponse.from(draftService.getOwnedDraft(draftId, userId, platformRoles)));
    }

    @Operation(operationId = "deleteAuthoringDraft", summary = "Delete a draft and its files")
    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long draftId,
                                         @RequestAttribute("userId") String userId,
                                         @RequestAttribute(value = "platformRoles", required = false)
                                         Set<String> platformRoles) {
        draftService.deleteDraft(draftId, userId, platformRoles);
        return ok("response.success.deleted", null);
    }

    // ---------------------------------------------------------------- draft files

    @Operation(operationId = "listDraftFiles", summary = "List a draft's files with metadata")
    @GetMapping("/drafts/{draftId}/files")
    public ApiResponse<List<DraftFileResponse>> listFiles(@PathVariable Long draftId) {
        return ok("response.success", draftService.listFiles(draftId).stream()
                .map(DraftFileResponse::from)
                .toList());
    }

    @Operation(operationId = "saveDraftFile", summary = "Create or update one draft file")
    @PutMapping("/drafts/{draftId}/files")
    public ApiResponse<SaveDraftFileResponse> saveFile(@PathVariable Long draftId,
                                                       @Valid
                                                       @RequestBody SaveDraftFileRequest request,
                                                       @RequestAttribute("userId") String userId,
                                                       @RequestAttribute(value = "platformRoles", required = false)
                                                       Set<String> platformRoles) {
        byte[] content = request.isBase64()
                ? Base64.getDecoder().decode(request.content())
                : request.content().getBytes(StandardCharsets.UTF_8);
        return ok("response.success.updated", SaveDraftFileResponse.from(
                draftService.saveFile(draftId, userId, request.path(), content,
                        request.contentType(), request.expectedRevision(), platformRoles)));
    }

    @Operation(operationId = "readDraftFile", summary = "Read one draft file's content")
    @GetMapping("/drafts/{draftId}/files/content")
    public ApiResponse<DraftFileContentResponse> readFile(@PathVariable Long draftId,
                                                          @RequestParam("path") String path,
                                                          @RequestAttribute("userId") String userId,
                                                          @RequestAttribute(value = "platformRoles", required = false)
                                                          Set<String> platformRoles) {
        SkillDraftService.FileContent content =
                draftService.readFile(draftId, userId, path, platformRoles);
        return ok("response.success", new DraftFileContentResponse(
                content.file().getFilePath(),
                content.file().getSha256(),
                content.file().getSize(),
                content.file().getContentType(),
                content.asText()));
    }

    @Operation(operationId = "deleteDraftFile", summary = "Delete one draft file")
    @DeleteMapping("/drafts/{draftId}/files")
    public ApiResponse<Void> deleteFile(@PathVariable Long draftId,
                                        @RequestParam("path") String path,
                                        @RequestParam(value = "expectedRevision", required = false)
                                        Integer expectedRevision,
                                        @RequestAttribute("userId") String userId,
                                        @RequestAttribute(value = "platformRoles", required = false)
                                        Set<String> platformRoles) {
        draftService.deleteFile(draftId, userId, path, expectedRevision, platformRoles);
        return ok("response.success.deleted", null);
    }

    // ---------------------------------------------------------------- validation runs

    @Operation(operationId = "startValidationRun", summary = "Start a validation run for the draft's current revision")
    @PostMapping("/drafts/{draftId}/runs")
    public ApiResponse<ValidationRunResponse> startRun(@PathVariable Long draftId,
                                                       @RequestAttribute("userId") String userId,
                                                       @RequestAttribute(value = "platformRoles", required = false)
                                                       Set<String> platformRoles) {
        ValidationRun run = runService.startRun(draftId, userId, platformRoles);
        orchestrator.submit(run.getId());
        return ok("response.success.created", ValidationRunResponse.from(run));
    }

    @Operation(operationId = "listValidationRuns", summary = "List the draft's validation runs")
    @GetMapping("/drafts/{draftId}/runs")
    public ApiResponse<List<ValidationRunResponse>> listRuns(@PathVariable Long draftId) {
        return ok("response.success", runService.listRuns(draftId).stream()
                .map(ValidationRunResponse::from)
                .toList());
    }

    @Operation(operationId = "getValidationRun", summary = "Get one validation run")
    @GetMapping("/runs/{runId}")
    public ApiResponse<ValidationRunResponse> getRun(@PathVariable Long runId,
                                                     @RequestAttribute("userId") String userId,
                                                     @RequestAttribute(value = "platformRoles", required = false)
                                                     Set<String> platformRoles) {
        ValidationRun run = runService.getOwnedRun(runId, userId, platformRoles);
        return ok("response.success", ValidationRunResponse.from(run));
    }

    @Operation(operationId = "cancelValidationRun", summary = "Request cooperative cancellation of a run")
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<ValidationRunResponse> cancelRun(@PathVariable Long runId,
                                                        @RequestAttribute("userId") String userId,
                                                        @RequestAttribute(value = "platformRoles", required = false)
                                                        Set<String> platformRoles) {
        ValidationRun run = orchestrator.requestCancel(runId, userId, platformRoles);
        return ok("response.success.updated", ValidationRunResponse.from(run));
    }

    // ---------------------------------------------------------------- events & findings

    @Operation(operationId = "listValidationEvents", summary = "List run events after a sequence cursor (polling)")
    @GetMapping("/runs/{runId}/events")
    public ApiResponse<List<ValidationEventResponse>> listEvents(
            @PathVariable Long runId,
            @RequestParam(value = "afterSeq", required = false) Integer afterSeq,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "platformRoles", required = false)
            Set<String> platformRoles) {
        runService.getOwnedRun(runId, userId, platformRoles);
        return ok("response.success", runService.listEvents(runId, afterSeq).stream()
                .map(ValidationEventResponse::from)
                .toList());
    }

    @Operation(operationId = "listValidationFindings", summary = "List a run's findings with fix suggestions")
    @GetMapping("/runs/{runId}/findings")
    public ApiResponse<List<ValidationFindingResponse>> listFindings(
            @PathVariable Long runId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "platformRoles", required = false)
            Set<String> platformRoles) {
        runService.getOwnedRun(runId, userId, platformRoles);
        return ok("response.success", runService.listFindings(runId).stream()
                .map(ValidationFindingResponse::from)
                .toList());
    }
}
