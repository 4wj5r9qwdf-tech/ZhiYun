-- ============================================================
-- 织云 ERP 注释补齐脚本（2026-10-30）
-- 目标：满足《开发规范》§表/字段必须带中文注释
-- 范围：
--   A. 4 张 SnailAI 表补【表级】注释（sai_rag/sai_rag_chunk/sai_rag_document/sai_user）
--      + 各自缺失的【列级】注释
--   B. 8 张自建业务表补缺失的【列级】注释
--      sys_person_ext / sys_company / sys_building / sys_todo /
--      sys_post_role / sys_user_menu_config / sys_user_workbench / sys_org_manage
-- 说明：纯 COMMENT ON，不改结构、不动数据，可重复执行（幂等）
-- 执行：psql -U zhizhun -d zhizhun_erp -f sql/补齐注释_20261030.sql
-- ============================================================
BEGIN;

-- ------------------------------------------------------------
-- A. SnailAI（AI 模块 RAG/用户表）：表级 + 列级
-- ------------------------------------------------------------
COMMENT ON TABLE  sai_rag          IS 'SnailAI RAG知识库配置表（检索增强生成：向量模型/重排/去重/分块策略）';
COMMENT ON TABLE  sai_rag_chunk    IS 'SnailAI RAG知识库分块表（文档切块片段，向量化后供检索召回）';
COMMENT ON TABLE  sai_rag_document IS 'SnailAI RAG知识库文档表（上传文件的解析/入库状态与统计）';
COMMENT ON TABLE  sai_user         IS 'SnailAI 模块用户表（AI功能使用方：角色/额度/头像，与主 sys_user 独立）';

-- sai_rag
COMMENT ON COLUMN sai_rag.id                        IS '主键ID';
COMMENT ON COLUMN sai_rag.name                      IS '知识库名称';
COMMENT ON COLUMN sai_rag.description                IS '知识库描述';
COMMENT ON COLUMN sai_rag.icon                       IS '知识库图标（资源URL/路径）';
COMMENT ON COLUMN sai_rag.embedding_model_id        IS '向量化模型（→ sai_model_config.id）';
COMMENT ON COLUMN sai_rag.rerank_model_id           IS '重排模型（→ sai_model_config.id）';
COMMENT ON COLUMN sai_rag.search_engine_instance_id  IS '搜索引擎实例（→ sai_store_instance.id）';
COMMENT ON COLUMN sai_rag.vector_store_instance_id   IS '向量库实例（→ sai_store_instance.id）';
COMMENT ON COLUMN sai_rag.search_engine_enable       IS '是否启用搜索引擎：true开/false关';
COMMENT ON COLUMN sai_rag.delimiter                 IS '分块自定义分隔符（默认按段落）';
COMMENT ON COLUMN sai_rag.rag_enhancement           IS 'RAG增强提示词（问答前注入的上下文规则）';
COMMENT ON COLUMN sai_rag.create_dt                 IS '创建时间';
COMMENT ON COLUMN sai_rag.update_dt                 IS '更新时间';

-- sai_rag_chunk
COMMENT ON COLUMN sai_rag_chunk.id                 IS '主键ID';
COMMENT ON COLUMN sai_rag_chunk.rag_id             IS '所属知识库（→ sai_rag.id）';
COMMENT ON COLUMN sai_rag_chunk.document_id        IS '所属文档（→ sai_rag_document.id）';
COMMENT ON COLUMN sai_rag_chunk.paragraph_index    IS '段落序号（块在文档内的段落定位）';
COMMENT ON COLUMN sai_rag_chunk.chunk_index        IS '块序号（块在所属段落内的序号）';
COMMENT ON COLUMN sai_rag_chunk.content            IS '块文本内容（向量化召回的正文）';
COMMENT ON COLUMN sai_rag_chunk.token_count        IS '块token数（上下文长度预算用）';
COMMENT ON COLUMN sai_rag_chunk.vector_id          IS '向量ID（向量库中该块唯一标识）';
COMMENT ON COLUMN sai_rag_chunk.create_dt          IS '创建时间';
COMMENT ON COLUMN sai_rag_chunk.update_dt          IS '更新时间';

-- sai_rag_document
COMMENT ON COLUMN sai_rag_document.id             IS '主键ID';
COMMENT ON COLUMN sai_rag_document.rag_id        IS '所属知识库（→ sai_rag.id）';
COMMENT ON COLUMN sai_rag_document.name          IS '文档名称';
COMMENT ON COLUMN sai_rag_document.file_type     IS '文件类型（pdf/docx/md/图片等）';
COMMENT ON COLUMN sai_rag_document.source_type   IS '来源类型（上传/URL等）';
COMMENT ON COLUMN sai_rag_document.source_path   IS '原始来源路径/URL';
COMMENT ON COLUMN sai_rag_document.storage_path  IS '存储路径（解析后落地位置）';
COMMENT ON COLUMN sai_rag_document.storage_type  IS '存储类型（本地/OSS等）';
COMMENT ON COLUMN sai_rag_document.file_size     IS '文件大小（字节）';
COMMENT ON COLUMN sai_rag_document.content       IS '文档原始文本内容';
COMMENT ON COLUMN sai_rag_document.error_msg     IS '处理失败错误信息';
COMMENT ON COLUMN sai_rag_document.chunk_count   IS '分块数量（解析产出的chunk总数）';
COMMENT ON COLUMN sai_rag_document.page_count    IS '页数（PDF/Office）';
COMMENT ON COLUMN sai_rag_document.element_count IS '元素数量（解析出的文本/表格/图片总数）';
COMMENT ON COLUMN sai_rag_document.table_count   IS '表格数量';
COMMENT ON COLUMN sai_rag_document.image_count   IS '图片数量';
COMMENT ON COLUMN sai_rag_document.parse_time    IS '解析耗时（毫秒）';
COMMENT ON COLUMN sai_rag_document.md_content    IS 'Markdown化正文（解析后的结构化文本）';
COMMENT ON COLUMN sai_rag_document.doc_metadata  IS '文档元数据（JSON扩展信息）';
COMMENT ON COLUMN sai_rag_document.create_dt     IS '创建时间';
COMMENT ON COLUMN sai_rag_document.update_dt     IS '更新时间';

-- sai_user
COMMENT ON COLUMN sai_user.id             IS '主键ID';
COMMENT ON COLUMN sai_user.role           IS '用户角色（整数枚举，AI模块内权限级别）';
COMMENT ON COLUMN sai_user.totals         IS '累计配额/使用量（AI调用额度计数）';
COMMENT ON COLUMN sai_user.username       IS '用户名';
COMMENT ON COLUMN sai_user.email          IS '邮箱';
COMMENT ON COLUMN sai_user.password       IS '密码（加密存储）';
COMMENT ON COLUMN sai_user.create_dt      IS '创建时间';
COMMENT ON COLUMN sai_user.update_dt      IS '更新时间';

-- ------------------------------------------------------------
-- B. 自建业务表：缺失列级注释
--    约定：外键写关联表.字段；枚举列取值；金额写单位+精度；布尔写 0/1 含义
-- ------------------------------------------------------------

-- B1. sys_person_ext（人员扩展，一对一挂 sys_user）
COMMENT ON COLUMN sys_person_ext.marital_status      IS '婚姻状况：未婚/已婚/离异';
COMMENT ON COLUMN sys_person_ext.cert_type          IS '证件类型：身份证/护照/其他';
COMMENT ON COLUMN sys_person_ext.nation              IS '民族';
COMMENT ON COLUMN sys_person_ext.education           IS '学历';
COMMENT ON COLUMN sys_person_ext.birth_date          IS '出生日期';
COMMENT ON COLUMN sys_person_ext.graduate_school     IS '毕业院校';
COMMENT ON COLUMN sys_person_ext.native_place        IS '籍贯';
COMMENT ON COLUMN sys_person_ext.address             IS '详细地址';
COMMENT ON COLUMN sys_person_ext.cert_expire_date    IS '证件到期日（进证件预警）';
COMMENT ON COLUMN sys_person_ext.emp_type            IS '员工性质：正式/临时/劳务';
COMMENT ON COLUMN sys_person_ext.leave_date          IS '离职日期';
COMMENT ON COLUMN sys_person_ext.card_no             IS '员工卡（工牌号）';
COMMENT ON COLUMN sys_person_ext.sign_entity         IS '签约主体（合同签约公司）';
COMMENT ON COLUMN sys_person_ext.contract_years      IS '合同年限（年）';
COMMENT ON COLUMN sys_person_ext.contract_start      IS '合同签订日期';
COMMENT ON COLUMN sys_person_ext.contract_end        IS '合同截止日期';
COMMENT ON COLUMN sys_person_ext.social_amount       IS '社保缴纳金额（元/月，DECIMAL(10,2)）';
COMMENT ON COLUMN sys_person_ext.social_subsidy      IS '社保补助金额（元/月，DECIMAL(10,2)）';
COMMENT ON COLUMN sys_person_ext.commercial_type     IS '商业险种（意外险/雇主责任险等）';
COMMENT ON COLUMN sys_person_ext.social_check_date   IS '社保核对日期（留痕）';
COMMENT ON COLUMN sys_person_ext.create_time          IS '创建时间';
COMMENT ON COLUMN sys_person_ext.update_time          IS '更新时间';
COMMENT ON COLUMN sys_person_ext.group_id            IS '小组关联（→ sys_org.org_id，org_category=小组；组织树 厂→车间→区域→小组）';

-- B2. sys_company（公司档案）
COMMENT ON COLUMN sys_company.company_id   IS '公司ID（主键）';
COMMENT ON COLUMN sys_company.status       IS '状态：0正常/1停用';
COMMENT ON COLUMN sys_company.del_flag     IS '删除标志：0存在/1删除';
COMMENT ON COLUMN sys_company.create_org  IS '创建部门（→ sys_org.org_id）';
COMMENT ON COLUMN sys_company.create_by   IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_company.create_time IS '创建时间';
COMMENT ON COLUMN sys_company.update_by   IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_company.update_time IS '更新时间';
COMMENT ON COLUMN sys_company.remark      IS '备注';
COMMENT ON COLUMN sys_company.name_color  IS '公司名文本颜色（hex 色值，如 #FFFFFF，登录页/首页展示用）';

-- B3. sys_building（建筑档案）
COMMENT ON COLUMN sys_building.plant_id    IS '建筑ID（主键，列名沿用 plant_id）';
COMMENT ON COLUMN sys_building.address      IS '地址';
COMMENT ON COLUMN sys_building.built_year  IS '建成年份';
COMMENT ON COLUMN sys_building.remark      IS '备注';
COMMENT ON COLUMN sys_building.del_flag    IS '删除标志：0存在/1删除';
COMMENT ON COLUMN sys_building.create_by   IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_building.create_time IS '创建时间';
COMMENT ON COLUMN sys_building.update_by   IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_building.update_time IS '更新时间';

-- B4. sys_todo（统一待办）
COMMENT ON COLUMN sys_todo.todo_id      IS '待办ID（主键）';
COMMENT ON COLUMN sys_todo.user_id     IS '归属用户（→ sys_user.user_id；空=模块/功能级待办，任何人可见）';
COMMENT ON COLUMN sys_todo.title       IS '待办标题';
COMMENT ON COLUMN sys_todo.biz_id      IS '业务单据ID（审批/预警等，后期关联）';
COMMENT ON COLUMN sys_todo.biz_type    IS '业务类型：approve审批/alert预警/...';
COMMENT ON COLUMN sys_todo.create_time IS '创建时间';
COMMENT ON COLUMN sys_todo.handle_time IS '处理完成时间';

-- B5. sys_post_role（岗位默认角色关联）
COMMENT ON COLUMN sys_post_role.create_org  IS '创建部门（→ sys_org.org_id）';
COMMENT ON COLUMN sys_post_role.create_by   IS '创建者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_post_role.create_time IS '创建时间';
COMMENT ON COLUMN sys_post_role.update_by   IS '更新者（→ sys_user.user_id）';
COMMENT ON COLUMN sys_post_role.update_time IS '更新时间';
COMMENT ON COLUMN sys_post_role.remark      IS '备注';

-- B6. sys_user_menu_config（用户功能菜单配置）
COMMENT ON COLUMN sys_user_menu_config.user_id     IS '用户ID（→ sys_user.user_id）';
COMMENT ON COLUMN sys_user_menu_config.sort        IS '排序（拖拽后记录）';
COMMENT ON COLUMN sys_user_menu_config.create_time  IS '创建时间';
COMMENT ON COLUMN sys_user_menu_config.update_time  IS '更新时间';

-- B7. sys_user_workbench（用户工作台）
COMMENT ON COLUMN sys_user_workbench.user_id     IS '用户ID（→ sys_user.user_id）';
COMMENT ON COLUMN sys_user_workbench.menu_key    IS '功能标识（前端功能卡 key，→ config/modules.ts）';
COMMENT ON COLUMN sys_user_workbench.sort        IS '排序';
COMMENT ON COLUMN sys_user_workbench.create_time IS '创建时间';

-- B8. sys_org_manage（组织管理）
COMMENT ON COLUMN sys_org_manage.create_time IS '创建时间';

COMMIT;

-- 自查：上述 8+4 张表剩余缺注释列（预期 0 行）
-- select c.relname, a.attname from pg_attribute a join pg_class c on c.oid=a.attrelid
--  where c.relname in ('sys_person_ext','sys_company','sys_building','sys_todo','sys_post_role',
--   'sys_user_menu_config','sys_user_workbench','sys_org_manage',
--   'sai_rag','sai_rag_chunk','sai_rag_document','sai_user')
--   and a.attnum>0 and not a.attisdropped
--   and not exists (select 1 from pg_description d where d.objoid=c.oid and d.objsubid=a.attnum and d.description<>'');
