package com.iflytek.skillhub.controller.authoring;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.DraftSubmitService;
import com.iflytek.skillhub.domain.authoring.service.FindingFixService;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.CreateDraftRequest;
import com.iflytek.skillhub.dto.DraftFileContentResponse;
import com.iflytek.skillhub.dto.DraftFileResponse;
import com.iflytek.skillhub.dto.DraftResponse;
import com.iflytek.skillhub.dto.RuntimeBindingRequest;
import com.iflytek.skillhub.dto.RuntimeBindingResponse;
import com.iflytek.skillhub.dto.SaveDraftFileRequest;
import com.iflytek.skillhub.dto.SaveDraftFileResponse;
import com.iflytek.skillhub.dto.SubmitDraftRequest;
import com.iflytek.skillhub.dto.SubmitDraftResponse;
import com.iflytek.skillhub.dto.ValidationEventResponse;
import com.iflytek.skillhub.dto.ValidationFindingResponse;
import com.iflytek.skillhub.dto.ValidationRunResponse;
import com.iflytek.skillhub.service.authoring.ValidationRunOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill authoring workbench API: draft CRUD and file editing, runtime binding,
 * validation run lifecycle (start, cancel, events, findings), fix application,
 * and submission of validated drafts into the publish pipeline.
 */
@Tag(name = "Skill authoring")
@RestController
@RequestMapping({"/api/v1/authoring", "/api/web/authoring"})
public class SkillAuthoringController extends BaseApiController {

    private final SkillDraftService draftService;
    private final RuntimeBindingService bindingService;
    private final ValidationRunService runService;
    private final DraftSubmitService submitService;
    private final FindingFixService fixService;
    private final ValidationRunOrchestrator orchestrator;

    public SkillAuthoringController(com.iflytek.skillhub.dto.ApiResponseFactory responseFactory,
                                    SkillDraftService draftService,
                                    RuntimeBindingService bindingService,
                                    ValidationRunService runService,
                                    DraftSubmitService submitService,
                                    FindingFixService fixService,
                                    ValidationRunOrchestrator orchestrator) {
        super(responseFactory);
        this.draftService = draftService;
        this.bindingService = bindingService;
        this.runService = runService;
        this.submitService = submitService;
        this.fixService = fixService;
        this.orchestrator = orchestrator;
    }

    // ---------------------------------------------------------------- drafts

    @Operation(operationId = "createAuthoringDraft", summary = "Create a draft seeded with a SKILL.md scaffold")
    @PostMapping("/drafts")
    public ApiResponse<DraftResponse> createDraft(@Valid @RequestBody CreateDraftRequest request,
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
                                                       @Valid @RequestBody SaveDraftFileRequest request,
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

    // ---------------------------------------------------------------- runtime binding

    @Operation(operationId = "getDraftRuntimeBinding", summary = "Get the draft's runtime binding")
    @GetMapping("/drafts/{draftId}/runtime")
    public ApiResponse<RuntimeBindingResponse> getRuntime(@PathVariable Long draftId,
                                                          @RequestAttribute("userId") String userId,
                                                          @RequestAttribute(value = "platformRoles", required = false)
                                                          Set<String> platformRoles) {
        draftService.getOwnedDraft(draftId, userId, platformRoles);
        return ok("response.success", bindingService.findBinding(draftId)
                .map(this::toRuntimeResponse)
                .orElseGet(() -> new RuntimeBindingResponse(null, Map.of(), List.of(), List.of(), null)));
    }

    @Operation(operationId = "saveDraftRuntimeBinding", summary = "Configure the agent runtime, tools, and MCP servers")
    @PutMapping("/drafts/{draftId}/runtime")
    public ApiResponse<RuntimeBindingResponse> saveRuntime(@PathVariable Long draftId,
                                                           @Valid @RequestBody RuntimeBindingRequest request,
                                                           @RequestAttribute("userId") String userId,
                                                           @RequestAttribute(value = "platformRoles", required = false)
                                                           Set<String> platformRoles) {
        return ok("response.success.updated", toRuntimeResponse(bindingService.saveBinding(
                draftId, userId, request.agentType(), request.config(),
                request.toolAllowlist(), request.mcpServers(), platformRoles)));
    }

    private RuntimeBindingResponse toRuntimeResponse(
            com.iflytek.skillhub.domain.authoring.RuntimeBinding binding) {
        return new RuntimeBindingResponse(
                binding.getAgentType().identifier(),
                binding.getConfig() == null ? Map.of() : binding.getConfig(),
                binding.getToolAllowlist() == null ? List.of() : binding.getToolAllowlist(),
                binding.getMcpServers() == null ? List.of() : binding.getMcpServers(),
                binding.getUpdatedAt());
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

    // ---------------------------------------------------------------- findings & fixes

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

    @Operation(operationId = "applyFindingFix", summary = "Apply a finding's fix suggestion to the draft")
    @PostMapping("/runs/{runId}/findings/{findingId}/apply")
    public ApiResponse<ValidationFindingResponse> applyFindingFix(
            @PathVariable Long runId,
            @PathVariable Long findingId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "platformRoles", required = false)
            Set<String> platformRoles) {
        FindingFixService.FixOutcome outcome = fixService.applyFix(findingId, userId, platformRoles);
        return ok("response.success.updated", ValidationFindingResponse.from(outcome.finding()));
    }

    @Operation(operationId = "dismissFindingFix", summary = "Dismiss a finding as not applicable")
    @PostMapping("/runs/{runId}/findings/{findingId}/dismiss")
    public ApiResponse<ValidationFindingResponse> dismissFinding(
            @PathVariable Long runId,
            @PathVariable Long findingId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "platformRoles", required = false)
            Set<String> platformRoles) {
        ValidationFinding finding = fixService.dismissFinding(findingId, userId, platformRoles);
        return ok("response.success.updated", ValidationFindingResponse.from(finding));
    }

    // ---------------------------------------------------------------- submission

    @Operation(operationId = "submitValidatedDraft", summary = "Submit a validated draft into the publish pipeline")
    @PostMapping("/drafts/{draftId}/submit")
    public ApiResponse<SubmitDraftResponse> submitDraft(@PathVariable Long draftId,
                                                        @RequestBody(required = false) SubmitDraftRequest request,
                                                        @RequestAttribute("userId") String userId,
                                                        @RequestAttribute(value = "platformRoles", required = false)
                                                        Set<String> platformRoles) {
        SkillVisibility visibility = request == null || request.visibility() == null
                ? SkillVisibility.PRIVATE
                : SkillVisibility.valueOf(request.visibility());
        Set<String> roles = request == null || request.platformRoles() == null
                ? platformRoles
                : request.platformRoles();
        return ok("response.success.created",
                SubmitDraftResponse.from(submitService.submit(draftId, userId, visibility, roles)));
    }
}
