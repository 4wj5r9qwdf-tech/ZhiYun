/*
 * 文件用途：承载 ERP 内嵌智能体发起一轮对话所需的用户输入和附件票据。
 * 文件版本：1.0.0
 * 功能目的：承载 ERP 内嵌智能体发起一轮对话所需的用户输入和附件票据。
 * 设计思路：员工身份从认证上下文读取，附件票据用来关联此前已通过权限验证的上传资源。
 * 实现方法：由对话控制器校验请求并创建会话，再交给上游 Agent 运行时执行。
 * 关键约束：不得信任请求体中的 userId；附件访问须核验当前员工和会话绑定。
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
