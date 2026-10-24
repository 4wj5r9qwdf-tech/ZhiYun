package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.GetObjectResult;
import org.dromara.common.oss.model.Options;
import org.dromara.common.oss.model.PutObjectResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 私有文件代理接口（带登录鉴权读私有桶 zhizhun-private）。
 *
 * 背景：MinIO 公开桶（zhizhun-files）浏览器直显 logo/背景等公开图；
 *   敏感文件（合同/证件/工资单）放私有桶 zhizhun-private（access_policy=0），
 *   浏览器无法直接访问，须走本接口带登录 token 代理读取。
 *
 * 用法（所有业务模块通用，不重复造轮子）：
 *   上传：POST /resource/oss/private/upload  （MultipartFile，走私有桶配置 minio-private）
 *   显示：<img :src="'/api/resource/oss/private/' + ossId" />  （axios 拦截器自动带 token）
 *   下载：同上，浏览器会按 content-type/内容处理。
 *
 * 权限：SaCheckLogin 只要求登录；若某文件要按角色控制，业务侧再加 @SaCheckPermission。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/oss/private")
public class PrivateFileController {

    private final JdbcTemplate jdbcTemplate;

    /** 私有桶的 OSS 配置 key（sys_oss_config.config_key） */
    private static final String PRIVATE_CONFIG_KEY = "minio-private";
    /** 私有桶名（与 sys_oss_config.bucket_name 一致） */
    private static final String PRIVATE_BUCKET = "zhizhun-private";

    /**
     * 按 ossId 读取私有文件（带登录鉴权），返回内容流。
     * 前端 <img src="/api/resource/oss/private/{ossId}"> 即可显示。
     */
    @SaCheckLogin
    @GetMapping("/{ossId}")
    public ResponseEntity<byte[]> getById(@PathVariable Long ossId) {
        // 从 sys_oss 查该 ossId 的 object_name（存桶里的 key）与文件名
        Map<String, Object> row = queryOss(ossId);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        String objectName = (String) row.get("object_name");
        String fileName = (String) row.get("file_name");
        return streamFile(objectName, fileName);
    }

    /**
     * 按 objectName 直接读取私有文件（上传后若只有 key 没 ossId 时用）。
     */
    @SaCheckLogin
    @GetMapping("/by-key/{objectName:.+}")
    public ResponseEntity<byte[]> getByKey(@PathVariable String objectName) {
        return streamFile(objectName, null);
    }

    /**
     * 上传文件到私有桶（合同/证件/工资单等敏感文件）。
     * 返回 ossId（存业务表），显示时用 /resource/oss/private/{ossId} 代理读取。
     */
    @SaCheckLogin
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        OssClient client = OssFactory.instance(PRIVATE_CONFIG_KEY);
        String pathKey = client.buildPathKey(original);
        PutObjectResult result;
        try (InputStream in = file.getInputStream()) {
            result = client.bucketUpload(PRIVATE_BUCKET, pathKey, in, file.getSize(),
                Options.builder().setContentType(file.getContentType()));
        } catch (IOException e) {
            throw new ServiceException("上传失败：" + e.getMessage());
        }
        // 落 sys_oss（service 记私有配置 key），供代理读取与业务引用
        long ossId = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO sys_oss (oss_id, file_name, original_name, file_suffix, url, service, create_time, create_by) " +
            "VALUES (?,?,?,?,?,? ,now(), ?)",
            ossId, result.key(), original, suffix(original), "", PRIVATE_CONFIG_KEY, LoginHelper.getUserId());
        return R.ok(Map.of(
            "ossId", String.valueOf(ossId),
            // url 故意留空：私有文件不给直链，须走代理接口
            "proxyUrl", "/resource/oss/private/" + ossId,
            "fileName", original));
    }

    private String suffix(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : "";
    }

    // ===== 内部 =====

    /** 从 sys_oss 查对象键 file_name 与原始名 original_name（RuoYi 把对象键存 file_name；sys_oss 无 del_flag）。 */
    private Map<String, Object> queryOss(Long ossId) {
        return jdbcTemplate.queryForMap(
            "SELECT file_name AS object_name, original_name AS file_name FROM sys_oss WHERE oss_id=?", ossId);
    }

    /** 用私有桶客户端下载并返回 ResponseEntity。 */
    private ResponseEntity<byte[]> streamFile(String objectName, String fileName) {
        if (objectName == null || objectName.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        OssClient client = OssFactory.instance(PRIVATE_CONFIG_KEY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GetObjectResult result = client.bucketDownload(PRIVATE_BUCKET, objectName,
            (r, in) -> {
                try { in.transferTo(out); } catch (Exception e) { throw new RuntimeException(e); }
                return r;
            });
        String contentType = result != null && result.contentType() != null
            ? result.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header("Content-Disposition", "inline; filename=\"" + sanitize(fileName) + "\"")
            .body(out.toByteArray());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
