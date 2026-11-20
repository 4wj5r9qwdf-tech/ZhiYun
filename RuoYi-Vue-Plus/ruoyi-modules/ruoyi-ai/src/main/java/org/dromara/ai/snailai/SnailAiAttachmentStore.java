/*
 * 文件用途：临时保存员工上传附件的票据和归属元数据，不长期缓存附件正文。
 * 文件版本：1.0.0
 * 功能目的：临时保存员工上传附件的票据和归属元数据，不长期缓存附件正文。
 * 设计思路：附件票据绑定员工、资源编号及可选智能体会话，设置过期时间并限制票据总量。
 * 实现方法：上传后登记短期票据；读取时校验有效期和当前员工；支持指定附件失效和资源引用检查。
 * 关键约束：附件不能跨用户复用；票据过期或用户不匹配时拒绝读取；字节正文不保存在本地 Map。
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

    /**
     * 创建一小时有效的附件票据，绑定上传员工、资源元数据和可选对话。
     * 票据 Map 不保存文件正文，实际文件仍由既有资源存储负责保存和权限校验。
     */
    public String put(Long userId, String name, String mimeType, long size,
                      Long resourceId, Document document) {
        // 每次新增时先清理过期票据，再对活跃数量设上限，避免本地短期授权无界增长。
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        if (tickets.size() >= MAX_TICKETS) {
            throw new IllegalStateException("待处理附件过多，请稍后重试");
        }
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new UploadedAttachment(userId, name, mimeType, size, resourceId,
            document, now.plusSeconds(3600)));
        return ticket;
    }

    /** 只有有效期内且员工 ID 完全匹配的票据才可用于后续资源读取。 */
    public UploadedAttachment getValid(String ticket, Long userId) {
        UploadedAttachment attachment = ticket == null ? null : tickets.get(ticket);
        if (attachment == null || !attachment.expiresAt().isAfter(Instant.now())
            || !attachment.userId().equals(userId)) {
            return null;
        }
        return attachment;
    }

    public record Document(String id, Long agentId, String conversationId) {}

    public record UploadedAttachment(Long userId, String name, String mimeType, long size,
                                     Long resourceId, Document document, Instant expiresAt) {
        /** 票据是否关联已持久化的二进制资源；未落资源库的普通文本没有资源编号。 */
        public boolean isImage() {
            return resourceId != null;
        }
    }
}
