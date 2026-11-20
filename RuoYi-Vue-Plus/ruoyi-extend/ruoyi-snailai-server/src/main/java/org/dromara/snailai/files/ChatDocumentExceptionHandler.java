/*
 * 文件用途：实现织云内嵌智能体的智能体文件异常映射服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的智能体文件异常映射服务能力。
 * 设计思路：模型和文件服务只处理经 ERP 身份验证的请求；模型输出不能直接成为正式业务数据。
 * 实现方法：校验会话/员工/文件/配置后调用分析、存储或提议逻辑并返回结构化结果。
 * 关键约束：文件访问、敏感数据和知识发布遵守服务端权限与人工确认边界。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.snailai.files;

import com.aizuda.snail.ai.common.execption.SnailAiAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import org.dromara.snailai.knowledge.KnowledgeDraftController;

/** 仅处理本接口的鉴权和参数错误，不改变框架其他接口的返回契约。 */
@RestControllerAdvice(assignableTypes = {ChatDocumentController.class, KnowledgeDraftController.class})
public class ChatDocumentExceptionHandler {
    @ExceptionHandler(SnailAiAuthenticationException.class)
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录或登录已过期"));
    }

    /** 仅把文件/知识草稿接口的参数或业务校验错误映射为 400，不泄露内部堆栈。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
