-- SnailAI 使用独立 PostgreSQL 数据库；将供应商标识从 VARCHAR(50) 扩为 TEXT。
-- 原有值保持不变，重复执行安全。
ALTER TABLE IF EXISTS sai_model_provider
    ALTER COLUMN provider_key TYPE TEXT;
