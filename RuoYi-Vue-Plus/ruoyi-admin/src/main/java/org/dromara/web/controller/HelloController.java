/*
 * 文件用途：为织云 ERP 的HelloController提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的HelloController提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
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
