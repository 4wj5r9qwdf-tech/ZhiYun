/*
 * 文件用途：承载 ERP 员工读取智能体会话历史时使用的分页和会话定位参数。
 * 文件版本：1.0.0
 * 功能目的：承载 ERP 员工读取智能体会话历史时使用的分页和会话定位参数。
 * 设计思路：仅传输查询条件；员工身份来自认证上下文，服务端据此限制历史数据范围。
 * 实现方法：由历史客户端向 SnailAI OpenAPI 查询后转换为 ERP 前端结构。
 * 关键约束：不能仅凭会话编号读取其他员工历史。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiHistoryClient.java
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
