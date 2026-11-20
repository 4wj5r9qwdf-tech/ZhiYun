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
 * 通用属性标签档案接口，标签按 {@code module_key} 功能命名空间隔离。
 * 导航模块由前端功能目录映射；数据库目录只登记实际使用标签的功能，并为每个功能保留默认分组。
 * 停用只修改状态并保留历史关联；只有未被业务记录引用的标签允许显式删除。
 * Generic tag catalog API scoped by the {@code module_key} business-feature namespace.
 * Navigation modules are mapped by the frontend; the catalog stores used features and maintains one default group per feature.
 * Disabling preserves historical links, while deletion is limited to tags with no business references.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/tag")
public class TagController {

    /** 执行本控制器的 PostgreSQL 查询和写入。 / Execute this controller's PostgreSQL queries and updates. */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询已登记的标签功能，供管理端选择目标命名空间并维护其分组。
     * List registered tag features so administrators can choose a namespace and maintain its groups.
     * @return 功能标识、显示名称和标签数量 / Feature key, display name and tag count.
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

    /** 查询功能内标签分组及数量；每个功能均保留不可删除的默认分组。 / List feature-local groups and counts; each feature retains a protected default group. */
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
     * 查询单个功能命名空间内的标签，可选按名称、状态和分组筛选。
     * List tags from one feature namespace with optional name, status and group filters.
     * @param moduleKey 功能标签命名空间 / Feature tag namespace; cross-feature reads are rejected.
     * @param keyword 可选名称关键词 / Optional tag-name keyword.
     * @param status 可选状态：0 启用、1 停用 / Optional status: 0 enabled, 1 disabled.
     * @param groupId 可选功能内分组 ID / Optional group ID within the feature.
     * @return 按分组和常用标记排序的标签行 / Tag rows ordered by group and common marker.
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
     * 为业务表单返回某功能可新选的启用标签；业务保存接口仍须校验记录访问权限。
     * Return active tag options for a business form; the caller must still enforce access to its business record.
     * @param moduleKey 标签命名空间 / Tag namespace.
     * @return 启用标签选项，常用项优先 / Active tag options with common entries first.
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
     * 在业务录入标签时幂等登记功能及默认分组中的标签；只维护目录，不建立业务记录关联。
     * Idempotently register the feature and its default-group tag during business entry; this does not link a business record.
     * @param body 包含 moduleKey、moduleName、tagName 的对象 / Object containing moduleKey, moduleName and tagName.
     * @return 已存在或新建的规范标签行 / Canonical existing or newly created tag row.
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

        // 稳定 moduleKey 唯一对应一个功能；重试只更新显示名，不重复创建功能目录。
        // A stable moduleKey identifies one feature; retries update its label without creating duplicate catalog rows.
        jdbcTemplate.update(
            "INSERT INTO sys_tag_module (module_key, module_name, create_time) VALUES (?, ?, now()) " +
            "ON CONFLICT (module_key) DO UPDATE SET module_name=EXCLUDED.module_name, update_time=now()",
            moduleKey, moduleName);
        long defaultGroupId = ensureDefaultGroup(moduleKey);

        Long oldId = jdbcTemplate.query(
            "SELECT tag_id FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?) FOR UPDATE",
            rs -> rs.next() ? rs.getLong(1) : null,moduleKey,defaultGroupId,tagName);
        Map<String,Object> before = oldId == null ? Map.of() : auditSnapshot(oldId,false);
        // PostgreSQL 唯一约束负责并发去重；冲突时复用规范记录并恢复启用状态。
        // The PostgreSQL unique constraint handles races; conflicts reuse the canonical row and reactivate it.
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

    /** 创建启用标签并校验功能、分组和名称唯一性；管理端调用须有新增权限。 / Create an enabled tag after validating its feature, group and unique name. */
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
        Object commonValue = body.get("isCommon");
        if (commonValue != null && !isBinaryFlag(commonValue)) return R.fail("常用标记只能是字符串0或1");
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

    /** 修改标签名称、所属分组、常用提示或状态；只允许功能内改组，不在编辑接口中迁移功能归属。 / Update a tag within its existing feature namespace and require explicit binary flag values. */
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
        if (!isBinaryFlag(body.get("isCommon")) || !isBinaryFlag(body.get("status"))) {
            return R.fail("常用标记和标签状态均只能是字符串0或1");
        }
        tagName = tagName.trim();
        if (tagName.length() > 100) return R.fail("标签名不能超过100个字符");
        if (existsOther(moduleKey, groupId, tagName, tagId)) return R.fail("该分组中已存在同名标签");
        var auditBefore = auditSnapshot(tagId,true);
        if (auditBefore.isEmpty()) return R.fail("标签不存在");
        jdbcTemplate.update(
            "UPDATE sys_tag SET tag_group_id=?, tag_name=?, is_common=?, status=?, update_time=now() " +
            "WHERE tag_id=? AND module_key=?",
            groupId, tagName, flag(body.get("isCommon")), flag(body.get("status")), tagId, moduleKey);
        BusinessAudit.record("修改标签",tagId,"名称",auditBefore,auditSnapshot(tagId,false));
        return R.ok();
    }

    /** 软停用标签，保留历史关系；停用后 options 接口不再返回该标签。 / Disable a tag while preserving history and hiding it from new-use options. */
    @SaCheckPermission("system:tag:remove")
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{tagId}/disable")
    public R<Void> disable(@PathVariable Long tagId) {
        var auditBefore = auditSnapshot(tagId,true);
        if (auditBefore.isEmpty()) return R.fail("标签不存在");
        jdbcTemplate.update("UPDATE sys_tag SET status='1', update_time=now() WHERE tag_id=?", tagId);
        BusinessAudit.record("停用标签",tagId,"名称",auditBefore,auditSnapshot(tagId,false));
        return R.ok();
    }

    /** 仅删除没有业务引用的标签；已被使用时保留历史目录并改为软停用。 / Delete only unreferenced tags; preserve referenced history and use soft disable. */
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

    /** 在已登记功能中新增自定义分组；默认分组由系统自动维护且不可重复命名。 / Add a custom group to a registered feature while preserving the managed default group. */
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

    /** 修改自定义分组名称；默认分组名称固定，用于接收未指定分组的新标签。 / Rename a custom group; the fixed default group remains the fallback for new tags. */
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

    /** 删除空的自定义分组；非空分组必须先迁出标签，默认分组始终保留。 / Delete an empty custom group; move tags first and always retain the default group. */
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

    /** 校验稳定的业务功能键格式；允许集合由业务接入动态登记，不在控制器中硬编码。 / Validate the feature-key format while allowing the catalog to grow dynamically. */
    private boolean validModuleKey(String moduleKey) {
        return moduleKey != null && moduleKey.matches("[a-z][a-z0-9_]{0,49}");
    }

    /** 检查目标功能已登记，避免管理端创建没有业务归属的空目录。 / Check registration before allowing administrators to add tags to a feature. */
    private boolean moduleExists(String moduleKey) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag_module WHERE module_key=?", Long.class, moduleKey);
        return count != null && count > 0;
    }

    /** 按功能、分组和大小写不敏感名称检查重复；数据库唯一约束仍负责并发兜底。 / Check case-insensitive duplicates before relying on the database constraint for race safety. */
    private boolean exists(String moduleKey, Long groupId, String tagName) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?)",
            Long.class, moduleKey, groupId, tagName);
        return count != null && count > 0;
    }

    /** 编辑时排除当前标签后检查目标功能和分组内的重名项。 / Check duplicate names in the target feature and group while excluding the edited row. */
    private boolean existsOther(String moduleKey, Long groupId, String tagName, Long tagId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_tag WHERE module_key=? AND tag_group_id=? AND lower(tag_name)=lower(?) AND tag_id<>?",
            Long.class, moduleKey, groupId, tagName, tagId);
        return count != null && count > 0;
    }

    /** 并发安全地确保功能默认分组存在并返回其数据库 ID。 / Ensure the feature's default group exists under concurrency and return its ID. */
    private long ensureDefaultGroup(String moduleKey) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO sys_tag_group(module_key,group_name,is_default,sort_order,create_time) VALUES(?,'默认分组','1',0,now()) " +
            "ON CONFLICT (module_key, lower(group_name)) DO UPDATE SET is_default='1' RETURNING group_id",
            Long.class,moduleKey);
    }

    /** 检查分组 ID 是否属于指定功能，阻止跨功能移动标签。 / Verify group ownership to prevent cross-feature tag moves. */
    private boolean groupBelongsToModule(Long groupId,String moduleKey) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE group_id=? AND module_key=?",Long.class,groupId,moduleKey);
        return count!=null && count>0;
    }

    /** 按功能检查大小写不敏感的分组名重复。 / Check case-insensitive group-name uniqueness within a feature. */
    private boolean groupExists(String moduleKey,String name) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE module_key=? AND lower(group_name)=lower(?)",Long.class,moduleKey,name);
        return count!=null && count>0;
    }

    /** 编辑分组时排除自身后检查重名。 / Check group-name uniqueness while excluding the current group. */
    private boolean groupExistsOther(String moduleKey,String name,Long groupId) {
        Long count=jdbcTemplate.queryForObject("SELECT count(*) FROM sys_tag_group WHERE module_key=? AND lower(group_name)=lower(?) AND group_id<>?",Long.class,moduleKey,name,groupId);
        return count!=null && count>0;
    }

    /** 读取审计用的分组快照；写操作可选择对记录加锁。 / Read a group audit snapshot and optionally lock it for writes. */
    private Map<String,Object> groupSnapshot(Long groupId,boolean lock) {
        var rows=jdbcTemplate.queryForList("SELECT group_id,module_key,group_name AS \"分组名称\",is_default,sort_order FROM sys_tag_group WHERE group_id=?"+(lock?" FOR UPDATE":""),groupId);
        return rows.isEmpty()?Map.of():rows.get(0);
    }

    /** 将可选字段规范化为去除首尾空白的文本。 / Normalize an optional value to trimmed text. */
    private String str(Object value) {
        return value == null ? null : value.toString().trim();
    }

    /** 将已校验的开关值写成数据库约定的字符标志。 / Return a validated database flag in its canonical string form. */
    private String flag(Object value) {
        return "1".equals(value == null ? null : value.toString()) ? "1" : "0";
    }

    /** 检查标签开关是否以契约规定的字符串0或1传入。 / Check that tag flags use the documented string values. */
    private boolean isBinaryFlag(Object value) {
        return value instanceof String flag && ("0".equals(flag) || "1".equals(flag));
    }

    /** 安全解析接口主键；格式不合法时返回 null，由上层按接口语义拒绝。 / Parse an identifier safely and let the caller reject invalid values. */
    private Long longValue(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    /** 读取标签审计前后快照；锁定前态时避免并发修改导致日志内容失真。 / Read tag audit state and optionally lock the row to keep concurrent changes out of the snapshot. */
    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(jdbcTemplate,"SELECT t.tag_name AS \"名称\", t.module_key AS \"归属模块\", g.group_name AS \"标签分组\", t.is_common AS \"常用\", t.status AS \"状态\" FROM sys_tag t JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE t.tag_id=?"+(lock ? " FOR UPDATE OF t" : ""),id);
        return row;
    }
}
