/*
 * 文件用途：将上游智能体对象投影为 ERP 员工对话界面所需的最小字段集合。
 * 文件版本：1.0.0
 * 功能目的：将上游智能体对象投影为 ERP 员工对话界面所需的最小字段集合。
 * 设计思路：不直接序列化上游完整 Agent 对象，减少后台配置、凭据和内部字段意外暴露。
 * 实现方法：由 from 方法提取编号、名称、说明、头像、欢迎语和预设问题。
 * 关键约束：仅输出员工界面显示字段；是否可访问智能体由服务端列表接口授权。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/controller/SnailAiController.java
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

    /** 从上游 Agent 投影员工界面允许展示的字段，不复制供应商配置或内部扩展属性。 */
    public static SnailAiAgentOption from(OpenApiAgentVO agent) {
        return new SnailAiAgentOption(
            agent.getId(), agent.getName(), agent.getDescription(), agent.getAvatar(),
            agent.getGreeting(), agent.getPresetQuestions()
        );
    }
}
