package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仓库管理接口，维护库存业务使用的仓库档案。
 * 仓库是独立仓储地点而非组织节点或建筑；建筑归属和楼层选择依赖建筑档案。
 * 仓库类型复用通用标签（module_key=warehouse_master），关联保存在 sys_tag_relation。
 * 列表以仓库主档为粒度聚合信息，避免多标签造成重复行。
 * Warehouse master-data API for inventory operations. A warehouse is a storage location, not an organization or building record.
 * Building ownership and floor choices come from the building catalog; warehouse types reuse feature-scoped tags.
 * List results stay at warehouse-record granularity to avoid duplicate rows from multiple tags.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/warehouse")
public class WarehouseController {
    private static final int LOCATION_LEVEL_WAREHOUSE = 0;
    private static final int LOCATION_LEVEL_ZONE = 1;
    private static final int LOCATION_LEVEL_RACK = 2;
    private static final int LOCATION_LEVEL_BIN = 3;
    private static final String TAG_MODULE = "warehouse_master";
    private static final Pattern FLOOR_COUNT = Pattern.compile("(\\d+|单|一|双|两|二|三|四|五|六|七|八|九|十)\\s*(?:层|楼)?");
    private final JdbcTemplate jdbc;

    /** 查询仓库档案；状态限定0/1，标签筛选必须使用有效正 ID。 / Query warehouses with strict 0/1 status and positive tag-ID filters. */
    @SaCheckPermission("inventory:warehouse:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String tagId) {
        String statusFilter = status == null ? "" : status.trim();
        if (!statusFilter.isEmpty() && !Set.of("0", "1").contains(statusFilter)) {
            return R.fail("仓库状态筛选只能是0（启用）或1（停用）");
        }
        String tagFilter = tagId == null ? "" : tagId.trim();
        Long parsedTagId = null;
        if (!tagFilter.isEmpty()) {
            try {
                parsedTagId = Long.valueOf(tagFilter);
            } catch (NumberFormatException error) {
                return R.fail("仓库类型标签ID必须是正十进制字符串");
            }
            if (parsedTagId <= 0) return R.fail("仓库类型标签ID必须大于0");
        }
        StringBuilder sql = new StringBuilder("SELECT w.warehouse_id,w.warehouse_code,w.warehouse_name,w.building_id,b.plant_name AS building_name,w.floor_name,w.area_name,w.manager_user_id,w.inbound_location_level,COALESCE(NULLIF(u.nick_name,''),u.user_name) AS manager_name,w.status,w.remark,COALESCE((SELECT string_agg(DISTINCT t.tag_name,', ' ORDER BY t.tag_name) FROM sys_tag_relation tr JOIN sys_tag t ON t.tag_id=tr.tag_id WHERE tr.module_key=? AND tr.record_id=w.warehouse_id),'') AS tag_names FROM inv_warehouse w LEFT JOIN sys_building b ON b.plant_id=w.building_id LEFT JOIN sys_user u ON u.user_id=w.manager_user_id WHERE w.del_flag='0'");
        List<Object> args = new ArrayList<>();
        args.add(TAG_MODULE);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.warehouse_code ILIKE ? OR w.warehouse_name ILIKE ? OR w.area_name ILIKE ? OR b.plant_name ILIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) args.add(pattern);
        }
        if (!statusFilter.isEmpty()) { sql.append(" AND w.status=?"); args.add(statusFilter); }
        if (parsedTagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM sys_tag_relation x WHERE x.module_key=? AND x.record_id=w.warehouse_id AND x.tag_id=?::bigint)");
            args.add(TAG_MODULE); args.add(parsedTagId);
        }
        sql.append(" ORDER BY w.warehouse_code,w.warehouse_id");
        return R.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    /** 查询仓库表单需要的在用建筑和负责人；只要求登录，不借此授予建筑或人员管理权限。 / Return active buildings and managers for warehouse forms without granting their management permissions. */
    @SaCheckLogin
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.of(
            "buildings", jdbc.queryForList("SELECT plant_id,plant_code,plant_name,floor_info FROM sys_building WHERE del_flag='0' AND status='0' ORDER BY plant_name"),
            "managers", jdbc.queryForList("SELECT user_id,user_name, nick_name FROM sys_user WHERE del_flag='0' AND status='0' ORDER BY user_name")
        ));
    }

    /** 返回可编辑的仓库编码建议；保存时仍会在事务锁内重新分配未修改的建议值。 / Return an editable warehouse-code suggestion and reallocate unchanged suggestions under the save lock. */
    @SaCheckPermission("inventory:warehouse:add")
    @GetMapping("/next-code")
    public R<String> nextCode() {
        WarehouseCodeRule rule = warehouseCodeRule();
        if (!rule.enabled()) return R.fail("仓库自动编码已关闭");
        return R.data(nextWarehouseCode(rule));
    }

    /** 查询仓库主档和关联类型标签，用于回填编辑表单。 / Read a warehouse and its type tags to populate the edit form. */
    @SaCheckPermission("inventory:warehouse:query")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0'", id);
        if (rows.isEmpty()) return R.fail("仓库不存在或已删除");
        Map<String, Object> data = rows.get(0);
        data.put("tagIds", jdbc.queryForList("SELECT tag_id::text AS id FROM sys_tag_relation WHERE module_key=? AND record_id=? ORDER BY tag_id", TAG_MODULE, id).stream().map(r -> r.get("id")).toList());
        data.put("tagOptions", jdbc.queryForList("SELECT t.tag_id::text AS value,t.tag_name AS label,g.group_name AS \"groupName\",t.status FROM sys_tag_relation tr JOIN sys_tag t ON t.tag_id=tr.tag_id JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE tr.module_key=? AND tr.record_id=?", TAG_MODULE, id).stream().map(r -> Map.of("value", r.get("value"), "label", r.get("label"), "groupName", r.get("groupName"), "disabled", "1".equals(String.valueOf(r.get("status"))))).toList());
        return R.ok(data);
    }

    /** 校验仓库主档及其基础资料和标签关联后新增，并记录业务审计。 / Validate the warehouse and references before creating it and recording business audit. */
    @SaCheckPermission("inventory:warehouse:add")
    @Log(title="仓库管理", businessType=BusinessType.INSERT)
    @Transactional
    @PostMapping
    public R<String> add(@RequestBody Map<String, Object> body) {
        WarehouseCodeRule rule = warehouseCodeRule();
        String code = value(body, "warehouseCode");
        String suggestedCode = value(body, "autoGeneratedCode");
        if (rule.enabled() && (code.isBlank() || code.equals(suggestedCode))) {
            jdbc.execute("SELECT pg_advisory_xact_lock(2026110501)");
            code = nextWarehouseCode(rule);
            body.put("warehouseCode", code);
        } else if (code.isBlank()) return R.fail("仓库自动编码已关闭，请手动填写仓库编码");
        String error = validate(body, null);
        if (error != null) return R.fail(error);
        error = validateReferences(body);
        if (error != null) return R.fail(error);
        error = validateTags(body.get("tagIds"), null);
        if (error != null) return R.fail(error);
        Long id = jdbc.queryForObject("INSERT INTO inv_warehouse(warehouse_code,warehouse_name,building_id,floor_name,area_name,manager_user_id,inbound_location_level,status,remark,create_by) VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING warehouse_id",
            Long.class, value(body,"warehouseCode"), value(body,"warehouseName"), longValue(body.get("buildingId")), value(body,"floorName"), value(body,"areaName"), longValue(body.get("managerUserId")), inboundLevel(body.get("inboundLocationLevel")), status(body.get("status")), value(body,"remark"), LoginHelper.getUserId());
        saveTags(id, body.get("tagIds"));
        BusinessAudit.record("新增仓库", id, "仓库名称", Map.of(), snapshot(id));
        return R.data(code);
    }

    /**
     * 读取仓库编码自动生成参数并校验前缀、序号长度；配置缺失时采用 WH 加四位序号。
     * Load and validate the warehouse-code rule; use the WH prefix and four digits when no setting exists.
     */
    private WarehouseCodeRule warehouseCodeRule() {
        boolean enabled = true;
        String prefix = "WH";
        int digits = 4;
        try {
            String value = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?", String.class, "inventory.warehouse.codeRule");
            Map<String, Object> config = JsonUtils.parseMap(value);
            if (config.get("enabled") instanceof Boolean flag) enabled = flag;
            if (config.get("prefix") instanceof String text) prefix = text.trim();
            if (config.get("digits") instanceof Number number) digits = number.intValue();
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) { /* 默认开启，WH + 4位 */ }
        catch (Exception e) { throw new IllegalStateException("仓库自动编码规则无效，请在参数设置中修复", e); }
        if (!prefix.matches("[A-Za-z0-9]{1,12}") || digits < 1 || digits > 10)
            throw new IllegalStateException("仓库自动编码规则无效，请检查前缀和序号位数");
        return new WarehouseCodeRule(enabled, prefix.toUpperCase(java.util.Locale.ROOT), digits);
    }

    /**
     * 根据规则扫描已用编码并生成一个可编辑的下一个编码建议。
     * Scan existing codes and return the next editable suggestion for the configured rule.
     */
    private String nextWarehouseCode(WarehouseCodeRule rule) {
        long max = 0;
        for (String existing : jdbc.queryForList("SELECT warehouse_code FROM inv_warehouse", String.class)) {
            if (!existing.regionMatches(true, 0, rule.prefix(), 0, rule.prefix().length())) continue;
            String suffix = existing.substring(rule.prefix().length());
            if (suffix.matches("[0-9]+")) try { max = Math.max(max, Long.parseLong(suffix)); } catch (NumberFormatException ignored) { }
        }
        String number = Long.toString(max + 1);
        String code = rule.prefix() + "0".repeat(Math.max(0, rule.digits() - number.length())) + number;
        if (code.length() > 50) throw new IllegalStateException("生成的仓库编码超过50位，请缩短前缀或序号位数");
        return code;
    }

    /**
     * 仓库编码生成规则的不可变快照，避免同一次请求中参数被重复读取。
     * Immutable snapshot of warehouse-code settings so one request uses a consistent rule.
     */
    private record WarehouseCodeRule(boolean enabled, String prefix, int digits) {}

    /** 修改仓库主档及完整标签集合，并保留前后快照用于业务审计。 / Update the warehouse and final tag set while preserving audit snapshots. */
    @SaCheckPermission("inventory:warehouse:edit")
    @Log(title="仓库管理", businessType=BusinessType.UPDATE)
    @Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long id = longValue(body.get("warehouseId"));
        if (id == null) return R.fail("仓库ID不能为空");
        String error = validate(body, id);
        if (error != null) return R.fail(error);
        error = validateReferences(body);
        if (error != null) return R.fail(error);
        error = validateTags(body.get("tagIds"), id);
        if (error != null) return R.fail(error);
        Map<String, Object> before = snapshot(id);
        String oldCode = jdbc.queryForObject("SELECT warehouse_code FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0' FOR UPDATE", String.class, id);
        String nextCode = value(body, "warehouseCode");
        if (!oldCode.equals(nextCode) && jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE warehouse_id=? AND del_flag='0'", Long.class, id) > 0)
            return R.fail("仓库下已有布局档案，仓库编码不可修改，以免破坏下级层级编码");
        int count = jdbc.update("UPDATE inv_warehouse SET warehouse_code=?,warehouse_name=?,building_id=?,floor_name=?,area_name=?,manager_user_id=?,inbound_location_level=?,status=?,remark=?,update_by=?,update_time=now() WHERE warehouse_id=? AND del_flag='0'",
            value(body,"warehouseCode"), value(body,"warehouseName"), longValue(body.get("buildingId")), value(body,"floorName"), value(body,"areaName"), longValue(body.get("managerUserId")), inboundLevel(body.get("inboundLocationLevel")), status(body.get("status")), value(body,"remark"), LoginHelper.getUserId(), id);
        if (count == 0) return R.fail("仓库不存在或已删除");
        saveTags(id, body.get("tagIds"));
        BusinessAudit.record("修改仓库", id, "仓库名称", before, snapshot(id));
        return R.ok();
    }

    /** 启停仓库；停用后从新业务选择项中排除，但保留历史档案。 / Change warehouse availability while retaining historical records. */
    @SaCheckPermission("inventory:warehouse:edit")
    @Log(title="仓库状态", businessType=BusinessType.UPDATE)
    @Transactional
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String next = status(body.get("status"));
        if (!Set.of("0", "1").contains(next)) return R.fail("仓库状态无效");
        Map<String, Object> before = snapshot(id);
        if (jdbc.update("UPDATE inv_warehouse SET status=?,update_by=?,update_time=now() WHERE warehouse_id=? AND del_flag='0'", next, LoginHelper.getUserId(), id) == 0) return R.fail("仓库不存在或已删除");
        BusinessAudit.record("修改仓库状态", id, "仓库名称", before, snapshot(id));
        return R.ok();
    }

    /** 软删除仓库并清除标签关联，保留主档字段以支持审计追溯。 / Soft-delete a warehouse, clear its tag links and retain the master row for audit history. */
    @SaCheckPermission("inventory:warehouse:remove")
    @Log(title="仓库管理", businessType=BusinessType.DELETE)
    @Transactional
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        if (jdbc.queryForList("SELECT warehouse_id FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0' FOR UPDATE", id).isEmpty()) return R.fail("仓库不存在或已删除");
        Long locations = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE warehouse_id=? AND del_flag='0'", Long.class, id);
        if (locations != null && locations > 0) return R.fail("仓库下仍有库区、货架或货位，请先处理这些档案；保留档案时可停用仓库");
        Map<String, Object> before = snapshot(id);
        if (jdbc.update("UPDATE inv_warehouse SET del_flag='1',status='1',update_by=?,update_time=now() WHERE warehouse_id=? AND del_flag='0'", LoginHelper.getUserId(), id) == 0) return R.fail("仓库不存在或已删除");
        jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?", TAG_MODULE, id);
        BusinessAudit.record("删除仓库", id, "仓库名称", before, Map.of("状态", "已删除"));
        return R.ok();
    }

    /** 校验必填字段、长度、仓库编码唯一性和启停状态；currentId 用于排除当前编辑记录。 */
    private String validate(Map<String,Object> body, Long currentId) {
        String code = value(body,"warehouseCode"), name = value(body,"warehouseName");
        if (code == null || code.isBlank()) return "仓库编码不能为空";
        if (code.length() > 50) return "仓库编码不能超过50个字符";
        if (name == null || name.isBlank()) return "仓库名称不能为空";
        if (name.length() > 200) return "仓库名称不能超过200个字符";
        Long duplicate = currentId == null
            ? jdbc.queryForObject("SELECT count(*) FROM inv_warehouse WHERE lower(warehouse_code)=lower(?) AND del_flag='0'", Long.class, code.trim())
            : jdbc.queryForObject("SELECT count(*) FROM inv_warehouse WHERE lower(warehouse_code)=lower(?) AND del_flag='0' AND warehouse_id<>?", Long.class, code.trim(), currentId);
        if (duplicate != null && duplicate > 0) return "仓库编码已存在";
        String state = status(body.get("status"));
        if (!Set.of("0","1").contains(state)) return "仓库状态无效";
        Integer locationLevel = inboundLevel(body.get("inboundLocationLevel"));
        if (locationLevel == null || locationLevel < LOCATION_LEVEL_WAREHOUSE || locationLevel > LOCATION_LEVEL_BIN) return "入库定位层级必须为0至3";
        // 新仓库允许按默认层级先建档；编辑时只有提高定位深度才要求该层级已有可用位置。
        if (currentId != null) {
            Integer oldLevel = jdbc.queryForObject("SELECT inbound_location_level FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0'", Integer.class, currentId);
            if (oldLevel != null && locationLevel > oldLevel) {
                Long available = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE warehouse_id=? AND location_level=? AND location_status='active' AND del_flag='0'", Long.class, currentId, locationLevel);
                if (available == null || available == 0) return "请先建立并启用对应层级的位置，再提高入库定位深度";
            }
        }
        return null;
    }

    /**
     * 校验建筑、楼层和负责人引用仍然有效，并使用建筑档案的楼层数限制仓库楼层。
     * Validate building, floor and manager references, using the building profile as the floor-count authority.
     */
    private String validateReferences(Map<String,Object> body) {
        Long buildingId = longValue(body.get("buildingId"));
        String floorName = value(body, "floorName");
        if (buildingId != null) {
            List<Map<String,Object>> buildings = jdbc.queryForList("SELECT floor_info FROM sys_building WHERE plant_id=? AND del_flag='0' AND status='0'", buildingId);
            if (buildings.isEmpty()) return "所属建筑不存在或已停用";
            if (floorName != null) {
                int floor = floorNumber(floorName);
                int count = floorCount(buildings.get(0).get("floor_info"));
                if (floor < 1) return "所在楼层格式无效，请从建筑楼层选项中选择";
                if (count < 1) return "请先在建筑档案中维护楼层信息，再设置仓库所在楼层";
                if (floor > count) return "仓库所在楼层不能超过所属建筑的楼层数";
            }
        } else if (floorName != null) {
            return "请选择仓库所属建筑后再设置所在楼层";
        }
        Long managerId = longValue(body.get("managerUserId"));
        if (managerId != null && jdbc.queryForObject("SELECT count(*) FROM sys_user WHERE user_id=? AND del_flag='0' AND status='0'", Long.class, managerId) == 0) return "仓库负责人不存在或已停用";
        return null;
    }

    /**
     * 确认标签属于仓库类型命名空间；编辑时允许保留已经关联但后来停用的标签。
     * Ensure tags belong to the warehouse namespace; edits may retain previously linked tags that were later disabled.
     */
    private String validateTags(Object raw, Long warehouseId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raw instanceof Collection<?> items) for (Object item : items) { Long id = longValue(item); if (id != null) ids.add(id); }
        for (Long tagId : ids) {
            Long valid = jdbc.queryForObject("SELECT count(*) FROM sys_tag WHERE tag_id=? AND module_key=? AND status='0' OR tag_id=? AND module_key=? AND EXISTS (SELECT 1 FROM sys_tag_relation WHERE module_key=? AND record_id=? AND tag_id=?)", Long.class, tagId, TAG_MODULE, tagId, TAG_MODULE, TAG_MODULE, warehouseId == null ? -1L : warehouseId, tagId);
            if (valid == null || valid == 0) return "仓库类型标签无效，请刷新后重试";
        }
        return null;
    }

    /**
     * 将仓库类型标签关系整体替换为本次提交的集合，避免旧关系残留。
     * Replace the warehouse tag relations with the submitted set so stale links cannot remain.
     */
    private void saveTags(Long warehouseId, Object raw) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raw instanceof Collection<?> items) for (Object item : items) { Long id = longValue(item); if (id != null) ids.add(id); }
        jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?", TAG_MODULE, warehouseId);
        for (Long tagId : ids) jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by,create_time) VALUES(?,?,?,?,now())", TAG_MODULE, warehouseId, tagId, LoginHelper.getUserId());
    }

    /**
     * 读取仓库及其关联展示值，生成业务审计使用的前后状态快照。
     * Read warehouse fields and related display values for before-and-after business audit snapshots.
     */
    private Map<String,Object> snapshot(Long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT w.warehouse_code AS \"仓库编码\",w.warehouse_name AS \"仓库名称\",w.status AS \"状态\",w.inbound_location_level AS \"入库定位层级\",w.floor_name AS \"所在楼层\",w.area_name AS \"区域\",b.plant_name AS \"所属建筑\",u.user_name AS \"负责人\",(SELECT string_agg(DISTINCT t.tag_name,', ' ORDER BY t.tag_name) FROM sys_tag_relation tr JOIN sys_tag t ON t.tag_id=tr.tag_id WHERE tr.module_key=? AND tr.record_id=w.warehouse_id) AS \"仓库类型\",w.remark AS \"备注\",w.del_flag AS \"删除标记\" FROM inv_warehouse w LEFT JOIN sys_building b ON b.plant_id=w.building_id LEFT JOIN sys_user u ON u.user_id=w.manager_user_id WHERE w.warehouse_id=?", TAG_MODULE, id);
        return rows.isEmpty() ? Map.of("仓库ID", id) : rows.get(0);
    }

    /**
     * 从建筑档案解析总层数，兼容数字、中文数字、单层/双层及“3层”等既有写法。
     * Parse the building floor count from numeric, Chinese-numeric, single/double-floor and suffixed forms.
     */
    private static int floorCount(Object raw) {
        if (raw == null) return 0;
        Matcher matcher = FLOOR_COUNT.matcher(raw.toString().trim());
        if (!matcher.matches()) return 0;
        return floorTokenNumber(matcher.group(1));
    }

    /**
     * 解析仓库所在楼层，接受“1楼”“1层”“1”及受支持的中文数字。
     * Parse a warehouse floor from forms such as "1楼", "1层", "1" or a supported Chinese numeral.
     */
    private static int floorNumber(String raw) {
        Matcher matcher = FLOOR_COUNT.matcher(raw.trim());
        return matcher.matches() ? floorTokenNumber(matcher.group(1)) : 0;
    }

    /**
     * 将楼层数字词转换为整数；当前兼容范围为一至十层。
     * Convert a floor-number token to an integer; supported legacy forms cover floors one through ten.
     */
    private static int floorTokenNumber(String token) {
        try { return Integer.parseInt(token); } catch (NumberFormatException ignored) { }
        return switch (token) {
            case "单", "一" -> 1;
            case "双", "两", "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    /**
     * 将请求中的可空 ID 安全转换为 Long；缺失或格式非法时返回 null，由业务校验给出明确提示。
     * Safely convert an optional request ID; return null for missing or malformed values for business validation.
     */
    private static Long longValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return Long.valueOf(value.toString()); } catch (NumberFormatException e) { return null; } }
    /**
     * 读取并清理请求文本；空白内容统一转换为 null，避免保存无意义空字符串。
     * Read and trim request text, normalizing blank values to null instead of persisting meaningless empty strings.
     */
    private static String value(Map<String,Object> body, String key) { Object value=body.get(key); return value==null || value.toString().isBlank() ? null : value.toString().trim(); }
    /**
     * 解析入库定位深度；默认到货架级，并将具体档位交由业务校验限定。
     * Parse inbound location depth, defaulting to rack level and leaving accepted levels to business validation.
     */
    private static Integer inboundLevel(Object value) { if (value == null || value.toString().isBlank()) return 2; try { return Integer.valueOf(value.toString()); } catch (NumberFormatException e) { return null; } }
    /**
     * 解析仓库状态；缺省为启用，最终合法性由仓库业务校验统一判断。
     * Resolve warehouse status, defaulting to enabled and delegating final validation to warehouse rules.
     */
    private static String status(Object value) { return value == null || value.toString().isBlank() ? "0" : value.toString(); }
}
