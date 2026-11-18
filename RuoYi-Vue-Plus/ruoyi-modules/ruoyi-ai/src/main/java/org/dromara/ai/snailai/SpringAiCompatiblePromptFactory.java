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

    @Override
    public Prompt build(ChatDispatchRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getHistoryMessages() != null) {
            for (ChatDispatchRequest.HistoryMessage history : request.getHistoryMessages()) {
                if (history == null || history.getContent() == null || history.getContent().isEmpty()) continue;
                messages.add("assistant".equals(history.getRole())
                    ? new AssistantMessage(history.getContent()) : new UserMessage(history.getContent()));
            }
        }
        if (request.getUserMessage() != null && !request.getUserMessage().isEmpty()) {
            List<Media> media = new ArrayList<>();
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
