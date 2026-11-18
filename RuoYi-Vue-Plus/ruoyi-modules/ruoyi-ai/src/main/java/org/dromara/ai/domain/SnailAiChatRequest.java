package org.dromara.ai.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ERP 员工侧智能体聊天请求。 / Chat request from an authenticated ERP employee. */
@Data
public class SnailAiChatRequest {

    @NotNull
    private Long agentId;

    @NotBlank
    @Size(max = 100)
    private String conversationId;

    @Size(max = 10000)
    private String content;

    /** Current-user upload tickets; raw SnailAI resource IDs are never accepted from the browser. */
    @Size(max = 4)
    private java.util.List<String> attachmentIds;
}
