/*
 * 文件用途：通过 SnailAI OpenAPI 查询当前 ERP 员工有权访问的对话列表、消息和图片。
 * 文件版本：1.0.0
 * 功能目的：通过 SnailAI OpenAPI 查询当前 ERP 员工有权访问的对话列表、消息和图片。
 * 设计思路：员工身份与 OpenAPI 用户映射绑定；每个历史请求都限制用户、Agent 和 conversation 三元组。
 * 实现方法：请求上游会话/消息接口，规范化 ID 与附件元数据，并在读取图片时再次校验资源所有权。
 * 关键约束：不接受前端指定其他平台用户；图片必须属于目标对话且类型、大小符合限制。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md；RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailai-server/src/main/java/org/dromara/snailai/history/EmployeeChatHistoryController.java
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

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationIdentityRequest;
import com.aizuda.snail.ai.openapi.client.core.annotation.OpenApiMapping;

import java.util.List;
import java.util.Map;

/** 通过智能体后台读取员工对话及图片。 / Read employee history from the agent service. */
public interface SnailAiHistoryClient {
    /** 按管理员诊断范围查询每个对话中可关联的图片附件。 */
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/trace-images", method = OpenApiMapping.HttpMethod.POST)
    Result<List<Map<String, String>>> traceImages(Map<String, Object> request);
    /** 按当前员工映射身份分页查询其智能体会话列表。 */
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/conversations", method = OpenApiMapping.HttpMethod.POST)
    Result<Map<String, Object>> conversations(Map<String, Object> request);
    /** 读取员工、智能体和会话三元组精确匹配的消息及附件元信息。 */
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/messages", method = OpenApiMapping.HttpMethod.POST)
    Result<List<Map<String, Object>>> messages(OpenApiConversationIdentityRequest request);

    /** 返回当前消息会话拥有的单张图片，不接受跨会话资源编号。 */
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/image", method = OpenApiMapping.HttpMethod.POST)
    Result<Map<String, String>> image(Map<String, Object> request);
}
