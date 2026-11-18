package org.dromara.ai.domain;

import com.aizuda.snail.ai.common.openapi.dto.OpenApiAgentVO;

import java.util.List;

/** Only employee-facing agent fields are returned. / Expose only fields needed by the ERP chat UI. */
public record SnailAiAgentOption(
    Long id,
    String name,
    String description,
    String avatar,
    String greeting,
    List<String> presetQuestions
) {

    public static SnailAiAgentOption from(OpenApiAgentVO agent) {
        return new SnailAiAgentOption(
            agent.getId(), agent.getName(), agent.getDescription(), agent.getAvatar(),
            agent.getGreeting(), agent.getPresetQuestions()
        );
    }
}
