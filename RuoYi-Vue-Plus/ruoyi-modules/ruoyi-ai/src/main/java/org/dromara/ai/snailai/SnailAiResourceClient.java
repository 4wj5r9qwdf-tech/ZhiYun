/*
 * 文件用途：把 ERP 附件上传、查询及下载操作适配为 SnailAI 会话可使用的资源接口。
 * 文件版本：1.0.0
 * 功能目的：把 ERP 附件上传、查询及下载操作适配为 SnailAI 会话可使用的资源接口。
 * 设计思路：资源访问以当前员工及短期附件票据为条件，避免暴露 SnailAI 内部资源服务给前端。
 * 实现方法：校验附件票据后调用对象存储/资源服务并返回受限元信息或输入流。
 * 关键约束：用户、资源和会话归属必须一致；大文件和异常 MIME 按附件规则拒绝。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiAttachmentStore.java
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
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceResponse;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceUploadRequest;
import com.aizuda.snail.ai.openapi.client.core.annotation.OpenApiMapping;

/** 调用 SnailAI 原生资源上传接口。 / Client for SnailAI's native resource upload API. */
public interface SnailAiResourceClient {

    /** 将经过员工身份校验的图片上传到 SnailAI 资源存储，并返回服务端资源编号。 */
    @OpenApiMapping(path = "/openapi/v1/resource/upload", method = OpenApiMapping.HttpMethod.POST)
    Result<OpenApiResourceResponse> upload(OpenApiResourceUploadRequest request);
}
