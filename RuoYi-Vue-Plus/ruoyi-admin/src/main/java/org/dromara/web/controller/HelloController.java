package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 第一刀 Hello 接口：验证 PostgreSQL + Redis + MinIO 全链路。
 *
 * <p>仅用于骨架阶段连通性自测，功能验证通过后按《智能体交接说明》四、第 3 步
 * 用户验收，再决定保留或移除（临时接口，发版前清理）。</p>
 *
 * @author 织云ERP
 */
@SaIgnore
@RequiredArgsConstructor
@RestController
public class HelloController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Hello 接口：验证 PG 连通 + Redis 读写。
     *
     * @return 各组件连通结果
     */
    @GetMapping("/hello")
    public R<Map<String, Object>> hello() {
        Map<String, Object> data = new HashMap<>();

        // 1. PostgreSQL 连通 + 表数
        try {
            Long tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname='public'", Long.class);
            data.put("pg", "OK (" + tableCount + " tables)");
        } catch (Exception e) {
            data.put("pg", "FAIL: " + e.getMessage());
        }

        // 2. Redis 写→读（RedisUtils 是静态工具类，内部持 RedissonClient）
        try {
            String key = "hello:probe";
            RedisUtils.setCacheObject(key, "zhizhun");
            Object val = RedisUtils.getCacheObject(key);
            data.put("redis", "OK (write→read = " + val + ")");
        } catch (Exception e) {
            data.put("redis", "FAIL: " + e.getMessage());
        }

        // 3. MinIO 配置读取（只验证配置在，不实际传文件）
        try {
            String bucket = jdbcTemplate.queryForObject(
                "SELECT bucket_name FROM sys_oss_config WHERE config_key='minio' AND status='Y'",
                String.class);
            data.put("minio", "OK (bucket = " + bucket + ")");
        } catch (Exception e) {
            data.put("minio", "FAIL: " + e.getMessage());
        }

        data.put("app", "织云ERP 第一刀 Hello");
        return R.ok(data);
    }
}
