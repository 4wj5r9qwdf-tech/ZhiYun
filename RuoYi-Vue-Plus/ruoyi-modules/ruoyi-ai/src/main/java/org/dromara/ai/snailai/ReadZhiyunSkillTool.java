/*
 * 文件用途：为 ERP 智能体按需读取项目正式维护的业务 Skill 正文。
 * 文件版本：1.0.0
 * 功能目的：为 ERP 智能体按需读取项目正式维护的业务 Skill 正文。
 * 设计思路：目录发现只作为提示摘要，完整流程通过工具按需加载，减少每轮提示词携带全部规范。
 * 实现方法：从允许的 Skill 根目录枚举和读取 SKILL.md，并解析 name/description 元数据。
 * 关键约束：限制路径在配置根目录内；目录不可读时返回空目录或明确提示，不泄露任意本机文件。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/Skills/README.md；Docs/AI/项目上下文索引.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
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
