package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.AgentRuntimeType;
import com.iflytek.skillhub.domain.authoring.RuntimeBinding;
import com.iflytek.skillhub.domain.authoring.RuntimeBindingRepository;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for a draft's runtime binding: persists the agent type, adapter
 * configuration, tool allowlist, and MCP server declarations. Validation findings are
 * the responsibility of {@link RuntimeBindingValidator}; this service only rejects
 * structurally invalid input at save time.
 */
@Service
public class RuntimeBindingService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final RuntimeBindingRepository bindingRepository;
    private final SkillDraftRepository draftRepository;
    private final RuntimeBindingValidator bindingValidator;

    public RuntimeBindingService(RuntimeBindingRepository bindingRepository,
                                 SkillDraftRepository draftRepository,
                                 RuntimeBindingValidator bindingValidator) {
        this.bindingRepository = bindingRepository;
        this.draftRepository = draftRepository;
        this.bindingValidator = bindingValidator;
    }

    /**
     * Saves the runtime binding. Structurally invalid input (unknown agent type,
     * embedded credentials) is rejected immediately; the full validation report is
     * produced again as findings whenever a run starts.
     */
    @Transactional
    public RuntimeBinding saveBinding(Long draftId, String userId, String agentType,
                                      Map<String, Object> adapterConfig,
                                      List<String> toolAllowlist,
                                      List<Map<String, Object>> mcpServers,
                                      Set<String> platformRoles) {
        SkillDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.draft.notFound", draftId));
        assertOwner(draft, userId, platformRoles);

        AgentRuntimeType type;
        try {
            type = AgentRuntimeType.fromIdentifier(agentType == null ? "" : agentType);
        } catch (IllegalArgumentException exception) {
            throw new DomainBadRequestException("error.authoring.binding.agentType.unknown", agentType);
        }

        List<FindingDraft> findings = bindingValidator.validate(
                type.identifier(), adapterConfig, toolAllowlist, mcpServers);
        boolean hasErrors = findings.stream()
                .anyMatch(finding -> finding.severity() == FindingSeverity.ERROR);
        if (hasErrors) {
            String first = findings.stream()
                    .filter(finding -> finding.severity() == FindingSeverity.ERROR)
                    .findFirst().map(FindingDraft::message).orElse("invalid binding");
            throw new DomainBadRequestException("error.authoring.binding.invalid", first);
        }

        RuntimeBinding binding = bindingRepository.findByDraftId(draftId)
                .orElseGet(() -> new RuntimeBinding(draftId, type));
        binding.update(type, adapterConfig, toolAllowlist, mcpServers, userId);
        return bindingRepository.save(binding);
    }

    @Transactional(readOnly = true)
    public RuntimeBinding getBinding(Long draftId) {
        return bindingRepository.findByDraftId(draftId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.binding.notFound", draftId));
    }

    /** Optional accessor for callers that treat "not configured yet" as normal state. */
    @Transactional(readOnly = true)
    public java.util.Optional<RuntimeBinding> findBinding(Long draftId) {
        return bindingRepository.findByDraftId(draftId);
    }

    private void assertOwner(SkillDraft draft, String userId, Set<String> platformRoles) {
        if (draft.getOwnerId().equals(userId)
                || (platformRoles != null && platformRoles.contains(SUPER_ADMIN))) {
            return;
        }
        throw new DomainForbiddenException("error.authoring.draft.forbidden", draft.getId());
    }
}
