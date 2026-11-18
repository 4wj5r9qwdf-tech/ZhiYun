package org.dromara.ai.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Start a conversation with an employee-accessible SnailAI agent. */
@Data
public class SnailAiConversationRequest {

    @NotNull
    private Long agentId;

    @Size(max = 100)
    private String title;
}
