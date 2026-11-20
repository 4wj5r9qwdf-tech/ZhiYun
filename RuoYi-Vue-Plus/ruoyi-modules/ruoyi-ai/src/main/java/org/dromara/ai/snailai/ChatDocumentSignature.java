/*
 * 文件用途：为 ERP 与本机 SnailAI 之间的文件工具请求生成 HMAC 签名。
 * 文件版本：1.0.0
 * 功能目的：为 ERP 与本机 SnailAI 之间的文件工具请求生成 HMAC 签名。
 * 设计思路：用外部密钥文件保存共享密钥，并以消息认证码绑定完整请求范围，避免客户端伪造本机调用。
 * 实现方法：读取密钥文件后对 scope 计算 HMAC-SHA256，输出 URL 安全编码签名。
 * 关键约束：密钥不得写入代码、请求日志或智能体提示词；调用方还须复核回环来源、时间窗和会话归属。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；sql/SnailAI聊天文件归属_20261118.sql；RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailai-server/src/main/java/org/dromara/snailai/files/ChatDocumentToolBridge.java
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

/** 使用仅本机服务可读的密钥签名请求，不把密钥或签名交给模型。 */
public final class ChatDocumentSignature {
    private ChatDocumentSignature() {}

    /**
     * 使用受保护的 32 字节本机密钥对完整会话 scope 计算 HMAC-SHA256 签名。
     * 密钥文件若对组或其他用户开放、缺失或长度错误，立即失败且不降级为无签名请求。
     */
    public static String sign(Path path, String sid) {
        try {
            // 避免本机服务共享密钥被同组或其他系统用户读取；仅检查权限不改变文件权限。
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) {
                throw new IllegalStateException("文件工具密钥权限不安全");
            }
            byte[] key = Files.readAllBytes(path);
            if (key.length != 32) throw new IllegalStateException("文件工具密钥无效");
            // 固定密钥长度后再初始化 MAC，签名输入覆盖完整 scope 字节序列。
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(sid.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("文件工具签名不可用", e);
        }
    }
}
