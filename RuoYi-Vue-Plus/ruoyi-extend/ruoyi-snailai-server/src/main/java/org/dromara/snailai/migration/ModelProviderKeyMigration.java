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

    @Override
    public void run(ApplicationArguments args) {
        Boolean tableExists = jdbcTemplate.queryForObject(
            "SELECT to_regclass('sai_model_provider') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(tableExists)) {
            log.info("SnailAI provider table is not initialized yet; provider key migration will run on the next startup");
            return;
        }

        jdbcTemplate.execute("ALTER TABLE sai_model_provider ALTER COLUMN provider_key TYPE TEXT");
        log.info("SnailAI provider key column is now PostgreSQL TEXT");
    }
}
