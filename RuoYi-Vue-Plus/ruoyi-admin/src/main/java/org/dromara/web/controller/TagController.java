package org.dromara.web.controller;

import org.dromara.system.controller.system.BusinessAudit;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用属性标签档案接口。
 *
 * <p>标签按 {@code module_key} 功能命名空间隔离，同名标签可分别属于不同业务功能。
 * 导航模块由前端功能目录映射，数据库目录只动态登记实际使用标签的功能；
 * 功能首次自动收录标签时，同时创建功能归属、默认分组和标签档案。具体业务记录关联由各业务保存接口负责。
     * 停用只修改状态，不删除标签及其历史关联；未被业务记录引用的标签允许显式删除。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/tag")
public class TagController {

    /** 使用 JDBC 执行本控制器的轻量 PostgreSQL 查询与写入。 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询已登记的功能归属，允许在新增首个标签前维护分组。
     * 功能可通过业务接入登记，也可在首次收录标签时自动登记。
     *
     * @return 模块标识、显示名称及当前标签数量
     */
    @SaCheckPermission("system:tag:list")
    @GetMapping("/modules")
    public R<List<Map<String, Object>>> modules() {
        return R.ok(jdbcTemplate.queryForList(
            "SELECT m.module_key AS \"moduleKey\", m.module_name AS \"moduleName\", " +
            "COUNT(t.tag_id) AS \"tagCount\" FROM sys_tag_module m " +
            "LEFT JOIN sys_tag t ON t.module_key=m.module_key " +
            "GROUP BY m.module_key, m.module_name ORDER BY m.module_name, m.module_key"));
    }

    /** 查询指定业务功能的标签分组及数量；每个功能至少有一个不可删除的默认分组。 */
    @SaCheckLogin
    @GetMapping("/groups")
    public R<List<Map<String, Object>>> groups(@RequestParam String moduleKey) {
        if (!validModuleKey(moduleKey)) return R.fail("标签模块标识无效");
        return R.ok(jdbcTemplate.queryForList(
            "SELECT g.group_id::text AS \"groupId\", g.module_key AS \"moduleKey\", g.group_name AS \"groupName\", " +
            "g.is_default AS \"isDefault\", g.sort_order AS \"sortOrder\", COUNT(t.tag_id) AS \"tagCount\" " +
            "FROM sys_tag_group g LEFT JOIN sys_tag t ON t.tag_group_id=g.group_id " +
            "WHERE g.module_key=? GROUP BY g.group_id ORDER BY g.is_default DESC, g.sort_order, g.group_id", moduleKey));
    }

    /**
     * 查询一个业务功能下的标签档案，可进一步按分组筛选。
     *
     * @param moduleKey 功能标签命名空间；不允许跨功能查询
     * @param keyword 可选标签名模糊查询词
     * @param status 可选状态，0 为启用、1 为停用
     * @return 包含分组的标签行，分组顺序优先、常用标签随后，字段别名与前端类型保持 camelCase 一致
     */
    @SaCheckPermission("system:tag:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam String moduleKey,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String groupId) {
        if (!validModuleKey(moduleKey)) return R.fail("标签模块标识无效");
        Long parsedGroupId = groupId == null || groupId.isBlank() ? null : longValue(groupId);
        if (groupId != null && !groupId.isBlank()
            && (parsedGroupId == null || !groupBelongsToModule(parsedGroupId, moduleKey))) return R.fail("标签分组无效");
        StringBuilder sql = new StringBuilder(
            "SELECT t.tag_id::text AS \"tagId\", t.module_key AS \"moduleKey\", m.module_name AS \"moduleName\", " +
            "t.tag_name AS \"tagName\", t.tag_group_id::text AS \"groupId\", g.group_name AS \"groupName\", " +
            "t.is_common AS \"isCommon\", t.status, " +
            "t.create_time AS \"createTime\", t.update_time AS \"updateTime\" " +
            "FROM sys_tag t JOIN sys_tag_module m ON m.module_key=t.module_key " +
            "JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE t.module_key=?");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(moduleKey);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND t.tag_name ILIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status=?");
            args.add(status.trim());
        }
        if (parsedGroupId != null) {
            sql.append(" AND t.tag_group_id=?");
            args.add(parsedGroupId);
        }
        sql.append(" ORDER BY g.is_default DESC, g.sort_order, g.group_name, t.is_common DESC, t.tag_name");
        return R.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    /**
     * 供业务表单读取一个功能内的启用标签。
     * 该接口要求登录；调用方业务接口仍须按自身规则校验业务记录的访问权限。
     *
     * @param moduleKey 标签命名空间
     * @return 启用标签选项，常用标签排在前面
     */
    @SaCheckLogin
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options(@RequestParam String moduleKey) {
        if (!validModuleKey(moduleKey)) return R.fail("标签模块标识无效");
        return R.ok(jdbcTemplate.queryForList(
            "SELECT t.tag_id::text AS \"tagId\", t.module_key AS \"moduleKey\", " +
            "m.module_name AS \"moduleName\", t.tag_name AS \"tagName\", t.tag_group_id::text AS \"groupId\", " +
            "g.group_name AS \"groupName\", t.is_common AS \"isCommon\" " +
            "FROM sys_tag t JOIN sys_tag_module m ON m.module_key=t.module_key " +
            "JOIN sys_tag_group g ON g.group_id=t.tag_group_id " +
            "WHERE t.module_key=? AND t.status='0' ORDER BY g.is_default DESC, g.sort_order, g.group_name, t.is_common DESC, t.tag_name",
            moduleKey));
    }

    /**
     * 在业务表单录入标签时幂等登记功能归属并收录标签目录项。
     * 功能元数据只在首次出现时创建；同一功能默认分组内按不区分大小写的名称去重；
     * 新标签默认归入“默认分组”；如果命中该分组内已停用的同名项，则恢复启用并返回规范记录。
     * 此接口只维护目录，不写业务记录与标签的关联。
     *
     * @param body 包含 moduleKey、moduleName 与 tagName 的请求体
     * @return 已存在或新建的规范标签行，包含标签归属模块信息
     */
    @SaCheckLogin
    @Transactional
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @PostMapping("/ensure")
    public R<Map<String, Object>> ensure(@RequestBody Map<String, Object> body) {
        String moduleKey = str(body.get("moduleKey"));
        String moduleName = str(body.get("moduleName"));
        String tagName = str(body.get("tagName"));
        if (!validModuleKey(moduleKey)) return R.fail("标签模块标识无效");
        if (moduleName == null || moduleName.isBlank()) return R.fail("标签归属模块名称不能为空");
        if (moduleName.length() > 100) return R.fail("模块名称不能超过100个字符");
        if (tagName == null || tagName.isBlank()) return R.fail("标签名不能为空");
        tagName = tagName.trim();
        if (tagName.length() > 100) return R.fail("标签名不能超过100个字符");

        // 模块标识稳定地对应一个业务归属；重试时同步最新显示名称，不创建重复模块。
        jdbcTemplate.update(
            "INSERT INTO sys_tag_module (module_key, module_name, create_time) VALUES (?, ?, now()) " +
            "ON CONFLICT (module_key) DO UPDATE SET module_name=EXCLUDED.module_name, update_time=now()",
            moduleKey, moduleName);
        long defaultGroupId = ensureDefaultGroup(moduleKey);

        Long oldId = jdbcTemplate.query(
            "SELECT tag_id FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?) FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,moduleKey,defaultGroupId,tagName);
        Map<String,Object> before = oldId == null ? Map.of() : auditSnapshot(oldId,false);
        // 标签主键由 PostgreSQL 序列生成；冲突时复用原记录并恢复启用状态。
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "INSERT INTO sys_tag (module_key, tag_group_id, tag_name, is_common, status, create_time) " +
            "VALUES (?, ?, ?, '0', '0', now()) " +
            "ON CONFLICT (module_key, tag_group_id, lower(tag_name)) DO UPDATE SET status='0', update_time=now() " +
            "RETURNING tag_id::text AS \"tagId\", module_key AS \"moduleKey\", tag_group_id::text AS \"groupId\", tag_name AS \"tagName\", " +
            "is_common AS \"isCommon\", status",
            moduleKey, defaultGroupId, tagName);
        row.put("moduleName", moduleName);
        row.put("groupName", "默认分组");
        Long tagId = Long.valueOf(row.get("tagId").toString());
        BusinessAudit.record(oldId == null ? "自动新增标签" : "确认标签",tagId,"名称",before,auditSnapshot(tagId,false));
        return R.ok(row);
    }

    /** 创建启用状态的标签；正式管理操作必须具备标签新增权限。 */
    @SaCheckPermission("system:tag:add")
    @Log(title = "标签管理", businessType = BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String moduleKey = str(body.get("moduleKey"));
        String moduleName = str(body.get("moduleName"));
        String tagName = str(body.get("tagName"));
        Long groupId = longValue(body.get("groupId"));
        if (!validModuleKey(moduleKey)) return R.fail("标签模块标识无效");
        if (moduleName == null || moduleName.isBlank()) return R.fail("标签归属模块名称不能为空");
        if (moduleName.length() > 100) return R.fail("模块名称不能超过100个字符");
        if (tagName == null || tagName.isBlank()) return R.fail("标签名不能为空");
        tagName = tagName.trim();
        if (tagName.length() > 100) return R.fail("标签名不能超过100个字符");
        if (moduleExists(moduleKey)) {
            if (groupId == null) groupId = ensureDefaultGroup(moduleKey);
            if (!groupBelongsToModule(groupId, moduleKey)) return R.fail("标签分组无效");
            if (exists(moduleKey, groupId, tagName)) return R.fail("该分组中已存在同名标签");
        } else if (groupId != null) {
            return R.fail("标签分组无效");
        }
        // 管理员手动建标签时同步登记模块；业务表单自动登记仍走 ensure 接口。
        jdbcTemplate.update(
            "INSERT INTO sys_tag_module (module_key, module_name, create_time) VALUES (?, ?, now()) " +
            "ON CONFLICT (module_key) DO UPDATE SET module_name=EXCLUDED.module_name, update_time=now()",
            moduleKey, moduleName);
        ensureDefaultGroup(moduleKey);
        if (groupId == null) groupId = ensureDefaultGroup(moduleKey);
        Long tagId = jdbcTemplate.queryForObject(
            "INSERT INTO sys_tag (module_key, tag_group_id, tag_name, is_common, status, create_time) " +
            "VALUES (?, ?, ?, ?, '0', now()) RETURNING tag_id",
            Long.class,moduleKey,groupId,tagName,flag(body.get("isCommon")));
        BusinessAudit.record("新增标签",tagId,"名称",Map.of(),auditSnapshot(tagId,false));
        return R.ok();
    }

    /** 修改标签名称、所属分组、常用提示或状态；只允许功能内改组，不在编辑接口中迁移功能归属。 */
    @SaCheckPermission("system:tag:edit")
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long tagId = longValue(body.get("tagId"));
        String tagName = str(body.get("tagName"));
        String moduleKey = str(body.get("moduleKey"));
        Long groupId = longValue(body.get("groupId"));
        if (tagId == null || !validModuleKey(moduleKey) || !moduleExists(moduleKey)) return R.fail("标签信息无效");
        if (groupId == null || !groupBelongsToModule(groupId, moduleKey)) return R.fail("标签分组无效");
        if (tagName == null || tagName.isBlank()) return R.fail("标签名不能为空");
        tagName = tagName.trim();
        if (tagName.length() > 100) return R.fail("标签名不能超过100个字符");
        if (existsOther(moduleKey, groupId, tagName, tagId)) return R.fail("该分组中已存在同名标签");
        var auditBefore = auditSnapshot(tagId,true);
        jdbcTemplate.update(
            "UPDATE sys_tag SET tag_group_id=?, tag_name=?, is_common=?, status=?, update_time=now() " +
            "WHERE tag_id=? AND module_key=?",
            groupId, tagName, flag(body.get("isCommon")), flag(body.get("status")), tagId, moduleKey);
        BusinessAudit.record("修改标签",tagId,"名称",auditBefore,auditSnapshot(tagId,false));
        return R.ok();
    }

    /** 软停用标签，保留历史关系；停用后 options 接口不再返回该标签。 */
    @SaCheckPermission("system:tag:remove")
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{tagId}/disable")
    public R<Void> disable(@PathVariable Long tagId) {
        var auditBefore = auditSnapshot(tagId,true);
        jdbcTemplate.update("UPDATE sys_tag SET status='1', update_time=now() WHERE tag_id=?", tagId);
        BusinessAudit.record("停用标签",tagId,"名称",auditBefore,auditSnapshot(tagId,false));
        return R.ok();
    }

    /** 删除从未被业务记录使用的标签；已被引用时保留目录并要求软停用。 */
    @SaCheckPermission("system:tag:remove")
    @Log(title = "标签管理", businessType = BusinessType.DELETE)
    @Transactional
    @DeleteMapping("/{tagId}")
    public R<Void> delete(@PathVariable Long tagId) {
        var before = auditSnapshot(tagId, true);
        if (before.isEmpty()) return R.fail("标签不存在");
        Long relationCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag_relation WHERE tag_id=?", Long.class, tagId);
        if (relationCount != null && relationCount > 0) return R.fail("标签已被业务记录使用，不能删除；可改为停用");
        jdbcTemplate.update("DELETE FROM sys_tag WHERE tag_id=?", tagId);
        BusinessAudit.record("删除标签", tagId, "名称", before, Map.of());
        return R.ok();
    }

    /** 新增功能内的自定义标签分组；每个功能的默认分组由系统自动维护。 */
    @SaCheckPermission("system:tag:add")
    @Log(title = "标签分组", businessType = BusinessType.INSERT)
    @Transactional
    @PostMapping("/groups")
    public R<Map<String, Object>> addGroup(@RequestBody Map<String, Object> body) {
        String moduleKey = str(body.get("moduleKey"));
        String groupName = str(body.get("groupName"));
        if (!validModuleKey(moduleKey) || !moduleExists(moduleKey)) return R.fail("标签模块无效");
        if (groupName == null || groupName.isBlank()) return R.fail("分组名称不能为空");
        if (groupName.length() > 100) return R.fail("分组名称不能超过100个字符");
        if (groupExists(moduleKey, groupName)) return R.fail("该模块下已存在同名分组");
        Map<String,Object> group = jdbcTemplate.queryForMap(
            "INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time) " +
            "VALUES(?,?,'0',COALESCE((SELECT MAX(sort_order)+1 FROM sys_tag_group WHERE module_key=?),10),now()) " +
            "RETURNING group_id::text AS \"groupId\",module_key AS \"moduleKey\",group_name AS \"groupName\",is_default AS \"isDefault\",sort_order AS \"sortOrder\"",
            moduleKey,groupName,moduleKey);
        BusinessAudit.record("新增标签分组",group.get("groupId"),"分组名称",Map.of(),Map.of("分组名称",groupName,"功能",moduleKey));
        return R.ok(group);
    }

    /** 修改分组名称；默认分组保留固定名称，避免失去兜底归属。 */
    @SaCheckPermission("system:tag:edit")
    @Log(title = "标签分组", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping("/groups")
    public R<Void> editGroup(@RequestBody Map<String, Object> body) {
        Long groupId = longValue(body.get("groupId"));
        String moduleKey = str(body.get("moduleKey"));
        String groupName = str(body.get("groupName"));
        if (groupId == null || !validModuleKey(moduleKey) || !groupBelongsToModule(groupId,moduleKey)) return R.fail("标签分组无效");
        if (groupName == null || groupName.isBlank()) return R.fail("分组名称不能为空");
        if (groupName.length() > 100) return R.fail("分组名称不能超过100个字符");
        if (groupExistsOther(moduleKey,groupName,groupId)) return R.fail("该模块下已存在同名分组");
        Map<String,Object> before = groupSnapshot(groupId,true);
        if ("1".equals(String.valueOf(before.get("is_default")))) return R.fail("默认分组名称不能修改");
        jdbcTemplate.update("UPDATE sys_tag_group SET group_name=?,update_time=now() WHERE group_id=? AND module_key=?",groupName,groupId,moduleKey);
        BusinessAudit.record("修改标签分组",groupId,"分组名称",before,groupSnapshot(groupId,false));
        return R.ok();
    }

    /** 只允许删除空的自定义分组；先将标签移至其他分组，默认分组不可删除。 */
    @SaCheckPermission("system:tag:remove")
    @Log(title = "标签分组", businessType = BusinessType.DELETE)
    @Transactional
    @DeleteMapping("/groups/{groupId}")
    public R<Void> deleteGroup(@PathVariable Long groupId) {
        Map<String,Object> before = groupSnapshot(groupId,true);
        if (before.isEmpty()) return R.fail("标签分组不存在");
        if ("1".equals(String.valueOf(before.get("is_default")))) return R.fail("默认分组不能删除");
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag WHERE tag_group_id=?",Long.class,groupId);
        if (count != null && count > 0) return R.fail("分组中还有标签，请先将标签移至其他分组");
        jdbcTemplate.update("DELETE FROM sys_tag_group WHERE group_id=?",groupId);
        BusinessAudit.record("删除标签分组",groupId,"分组名称",before,Map.of());
        return R.ok();
    }

    /** 校验业务侧提供的稳定模块键格式；具体模块集合不硬编码，由首次收录动态建立。 */
    private boolean validModuleKey(String moduleKey) {
        return moduleKey != null && moduleKey.matches("[a-z][a-z0-9_]{0,49}");
    }

    /** 管理端只能给已经由业务标签自动登记的模块手工补充标签。 */
    private boolean moduleExists(String moduleKey) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag_module WHERE module_key=?", Long.class, moduleKey);
        return count != null && count > 0;
    }

    /** 按模块和大小写不敏感的名称检查重复，配合数据库唯一索引进行业务层提示。 */
    private boolean exists(String moduleKey, Long groupId, String tagName) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?)",
            Long.class, moduleKey, groupId, tagName);
        return count != null && count > 0;
    }

    /** 编辑时排除当前记录后检查同模块是否已有同名标签。 */
    private boolean existsOther(String moduleKey, Long groupId, String tagName, Long tagId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?) AND tag_id<>?",
            Long.class, moduleKey, groupId, tagName, tagId);
        return count != null && count > 0;
    }

    private long ensureDefaultGroup(String moduleKey) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time) VALUES(?,'默认分组','1',0,now()) " +
            "ON CONFLICT (module_key, lower(group_name)) DO UPDATE SET is_default='1' RETURNING group_id",
            Long.class,moduleKey);
    }

    private boolean groupBelongsToModule(Long groupId,String moduleKey) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE group_id=? AND module_key=?",Long.class,groupId,moduleKey);
        return count!=null && count>0;
    }

    private boolean groupExists(String moduleKey,String name) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE module_key=? AND lower(group_name)=lower(?)",Long.class,moduleKey,name);
        return count!=null && count>0;
    }

    private boolean groupExistsOther(String moduleKey,String name,Long groupId) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE module_key=? AND lower(group_name)=lower(?) AND group_id<>?",Long.class,moduleKey,name,groupId);
        return count!=null && count>0;
    }

    private Map<String,Object> groupSnapshot(Long groupId,boolean lock) {
        var rows=jdbcTemplate.queryForList("SELECT group_id,module_key,group_name AS \"分组名称\",is_default,sort_order FROM sys_tag_group WHERE group_id=?"+(lock?" FOR UPDATE":""),groupId);
        return rows.isEmpty()?Map.of():rows.get(0);
    }

    private String str(Object value) {
        return value == null ? null : value.toString().trim();
    }

    /** 将前端开关值归一为数据库约定的字符标志 0/1。 */
    private String flag(Object value) {
        return "1".equals(value == null ? null : value.toString()) ? "1" : "0";
    }

    /** 安全解析 JSON 中的 bigint 主键；格式不合法时返回 null 交由接口参数校验处理。 */
    private Long longValue(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(jdbcTemplate,"SELECT t.tag_name AS \"名称\", t.module_key AS \"归属模块\", g.group_name AS \"标签分组\", t.is_common AS \"常用\", t.status AS \"状态\" FROM sys_tag t JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE t.tag_id=?"+(lock ? " FOR UPDATE OF t" : ""),id);
        return row;
    }
}
