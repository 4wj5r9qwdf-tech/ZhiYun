package org.dromara.snailai.files;

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

    public static String sign(Path path, String sid) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) {
                throw new IllegalStateException("文件工具密钥权限不安全");
            }
            byte[] key = Files.readAllBytes(path);
            if (key.length != 32) throw new IllegalStateException("文件工具密钥无效");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(sid.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("文件工具签名不可用", e);
        }
    }
}
