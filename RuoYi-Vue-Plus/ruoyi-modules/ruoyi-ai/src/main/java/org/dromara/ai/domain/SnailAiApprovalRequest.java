/*
 * 文件用途：承载 ERP 对话中工具执行审批确认所需的请求字段。
 * 文件版本：1.0.0
 * 功能目的：承载 ERP 对话中工具执行审批确认所需的请求字段。
 * 设计思路：将一次性审批标识与用户确认意图分开传递，具体用户身份由登录会话提供。
 * 实现方法：由对话 API 接收后交给服务端会话存储校验待审批动作。
 * 关键约束：客户端不得通过请求体指定审批员工身份或替换待确认工具参数。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiChatSessionStore.java
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
