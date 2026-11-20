/*
 * 文件用途：将 SnailAI 的系统消息、历史对话、用户文本和图片附件转换为 Spring AI Prompt。
 * 文件版本：1.0.0
 * 功能目的：将 SnailAI 的系统消息、历史对话、用户文本和图片附件转换为 Spring AI Prompt。
 * 设计思路：兼容 Spring AI 2.0 媒体 API；跳过缺失字段附件但保留文本消息的基础语义。
 * 实现方法：按角色创建 System/Assistant/User 消息，解码有效媒体附件并构造多模态用户消息。
 * 关键约束：图片只在有 MIME 与内容时传入视觉模型；媒体格式错误不得静默篡改业务文本。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/pom.xml
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

import com.aizuda.snail.ai.agent.core.executor.prompt.PromptFactory;
import com.aizuda.snail.ai.common.dto.agent.ChatDispatchRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 兼容 Spring AI 2.0 的 SnailAI 消息构造。 / Build SnailAI prompts using the Spring AI 2.0 media API. */
public class SpringAiCompatiblePromptFactory implements PromptFactory {

    /**
     * 将上游 ChatDispatchRequest 映射为 Spring AI Prompt，保留系统提示、历史角色、用户文本和有效媒体附件。
     * 无效/缺字段的单个附件被跳过，不会丢弃同轮文本消息。
     */
    @Override
    public Prompt build(ChatDispatchRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        // 历史消息按来源角色恢复为 assistant/user；空内容记录不进入模型上下文。
        if (request.getHistoryMessages() != null) {
            for (ChatDispatchRequest.HistoryMessage history : request.getHistoryMessages()) {
                if (history == null || history.getContent() == null || history.getContent().isEmpty()) continue;
                messages.add("assistant".equals(history.getRole())
                    ? new AssistantMessage(history.getContent()) : new UserMessage(history.getContent()));
            }
        }
        if (request.getUserMessage() != null && !request.getUserMessage().isEmpty()) {
            List<Media> media = new ArrayList<>();
            // 只有 MIME、Base64 内容齐全的附件才转成模型媒体对象，避免无效附件使整轮提示构造失败。
            if (request.getMediaAttachments() != null) {
                for (ChatDispatchRequest.MediaAttachment attachment : request.getMediaAttachments()) {
                    if (attachment == null || attachment.getMimeType() == null || attachment.getBase64() == null
                        || attachment.getBase64().isEmpty()) continue;
                    media.add(Media.builder().mimeType(MimeTypeUtils.parseMimeType(attachment.getMimeType()))
                        .data(Base64.getDecoder().decode(attachment.getBase64()))
                        .name(attachment.getFileName()).build());
                }
            }
            messages.add(media.isEmpty() ? new UserMessage(request.getUserMessage())
                : UserMessage.builder().text(request.getUserMessage()).media(media).build());
        }
        return new Prompt(messages);
    }
}
