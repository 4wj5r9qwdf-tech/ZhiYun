package org.dromara.ai.snailai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Read-only bridge for employee-facing ERP Skill documents. / Exposes only local ERP Skill text. */
@Component
@RequiredArgsConstructor
public class ReadZhiyunSkillTool {

    private final SnailAiSkillCatalog catalog;

    @Tool(name = "read_zhiyun_skill", description = "按织云 ERP Skill 名称读取该业务流程的完整指引")
    public String readSkill(@ToolParam(description = "织云 ERP Skill 名称，应与可用 Skill 列表完全一致") String skillName) {
        return catalog.read(skillName);
    }
}
