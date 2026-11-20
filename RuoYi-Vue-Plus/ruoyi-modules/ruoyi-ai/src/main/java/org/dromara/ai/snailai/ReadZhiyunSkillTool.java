package org.dromara.ai.snailai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 为员工会话提供只读的 ERP Skill 指引读取工具。 / Expose ERP Skill instructions to employee sessions through a read-only tool. */
@Component
@RequiredArgsConstructor
public class ReadZhiyunSkillTool {

    private final SnailAiSkillCatalog catalog;

    /** 按 Skill 英文标识或目录名读取完整业务指引。 / Read complete instructions by the Skill's declared name or directory name. */
    @Tool(name = "read_zhiyun_skill", description = "按织云 ERP Skill 英文标识或中文目录名读取完整指引")
    public String readSkill(@ToolParam(description = "织云 ERP Skill 的英文标识或可用技能列表括号中的中文目录名") String skillName) {
        return catalog.read(skillName);
    }
}
