package com.iflytek.skillhub.domain.authoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-draft runtime configuration used by behavior validation. Holds the agent type,
 * adapter-specific configuration, tool allowlist, and MCP server declarations.
 *
 * <p>Plain secrets must never be stored here: credentials are referenced by name and
 * resolved from server-side configuration at validation time. The JSON columns are
 * mapped as structured collections (not Strings) so the stored documents stay real
 * JSON objects and Hibernate's read path is symmetric with its write path.
 */
@Entity
@Table(name = "runtime_binding",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uq_runtime_binding_draft", columnNames = "draft_id"))
public class RuntimeBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentRuntimeType agentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_allowlist")
    private List<String> toolAllowlist;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mcp_servers")
    private List<Map<String, Object>> mcpServers;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RuntimeBinding() {
    }

    public RuntimeBinding(Long draftId, AgentRuntimeType agentType) {
        this.draftId = draftId;
        this.agentType = agentType;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public void update(AgentRuntimeType agentType, Map<String, Object> config,
                       List<String> toolAllowlist, List<Map<String, Object>> mcpServers,
                       String updatedBy) {
        this.agentType = agentType;
        this.config = config;
        this.toolAllowlist = toolAllowlist;
        this.mcpServers = mcpServers;
        this.updatedBy = updatedBy;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getDraftId() {
        return draftId;
    }

    public AgentRuntimeType getAgentType() {
        return agentType;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public List<String> getToolAllowlist() {
        return toolAllowlist;
    }

    public List<Map<String, Object>> getMcpServers() {
        return mcpServers;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
