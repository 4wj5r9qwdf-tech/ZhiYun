package org.dromara.ai.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** A one-time employee decision for a pending MCP write. / One-time approval for a pending MCP write. */
@Data
public class SnailAiApprovalRequest {

    @NotBlank
    private String approvalId;

    @NotNull
    private Boolean approved;
}
