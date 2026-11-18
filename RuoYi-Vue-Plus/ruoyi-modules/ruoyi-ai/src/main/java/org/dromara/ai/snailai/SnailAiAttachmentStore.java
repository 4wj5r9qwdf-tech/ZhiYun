package org.dromara.ai.snailai;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps short-lived, user-bound upload tickets; document bytes are never retained. */
@Component
public class SnailAiAttachmentStore {

    private static final int MAX_TICKETS = 1000;
    private final Map<String, UploadedAttachment> tickets = new ConcurrentHashMap<>();

    public String put(Long userId, String name, String mimeType, long size,
                      Long resourceId, String extractedText) {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        if (tickets.size() >= MAX_TICKETS) {
            throw new IllegalStateException("待处理附件过多，请稍后重试");
        }
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new UploadedAttachment(userId, name, mimeType, size, resourceId,
            extractedText, now.plusSeconds(3600)));
        return ticket;
    }

    public UploadedAttachment getValid(String ticket, Long userId) {
        UploadedAttachment attachment = ticket == null ? null : tickets.get(ticket);
        if (attachment == null || !attachment.expiresAt().isAfter(Instant.now())
            || !attachment.userId().equals(userId)) {
            return null;
        }
        return attachment;
    }

    public record UploadedAttachment(Long userId, String name, String mimeType, long size,
                                     Long resourceId, String extractedText, Instant expiresAt) {
        public boolean isImage() {
            return resourceId != null;
        }
    }
}
