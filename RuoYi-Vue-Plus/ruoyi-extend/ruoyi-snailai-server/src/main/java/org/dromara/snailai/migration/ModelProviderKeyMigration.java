/*
 * 文件用途：实现织云内嵌智能体的模型服务商密钥配置迁移服务能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云内嵌智能体的模型服务商密钥配置迁移服务能力。
 * 设计思路：模型和文件服务只处理经 ERP 身份验证的请求；模型输出不能直接成为正式业务数据。
 * 实现方法：校验会话/员工/文件/配置后调用分析、存储或提议逻辑并返回结构化结果。
 * 关键约束：文件访问、敏感数据和知识发布遵守服务端权限与人工确认边界。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；Docs/API/API文档.md
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
package org.dromara.snailai.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Aligns SnailAI provider keys with PostgreSQL text storage so long, valid
 * provider keys are not rejected by the upstream 50-character column limit.
 */
public class ModelProviderKeyMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderKeyMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public ModelProviderKeyMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动时将上游模型服务商密钥列扩为 PostgreSQL TEXT，兼容长度超过旧 VARCHAR 上限的合法密钥。
     * 上游表尚未初始化时延后到下次启动重试；修改的是列类型，不读取或输出密钥值。
     */
    @Override
    public void run(ApplicationArguments args) {
        // SnailAI 初始化时序可能晚于本迁移；表不存在时不阻断应用启动，后续启动会再次检查。
        Boolean tableExists = jdbcTemplate.queryForObject(
            "SELECT to_regclass('sai_model_provider') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(tableExists)) {
            log.info("SnailAI provider table is not initialized yet; provider key migration will run on the next startup");
            return;
        }

        // TEXT 不设置字符长度上限，避免有效供应商凭据被旧列宽拒绝。
        jdbcTemplate.execute("ALTER TABLE sai_model_provider ALTER COLUMN provider_key TYPE TEXT");
        log.info("SnailAI provider key column is now PostgreSQL TEXT");
    }
}
