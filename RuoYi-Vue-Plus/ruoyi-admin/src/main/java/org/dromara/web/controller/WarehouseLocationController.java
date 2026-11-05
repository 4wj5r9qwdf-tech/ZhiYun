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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库布局：按仓库维护库区、货架、货位三级档案。
 * 仓储位置主键供后续库存单据稳定引用；AGV 字段只保存位置能力和外部映射，不在本期执行调度。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/warehouse-location")
public class WarehouseLocationController {
    private static final int LOCATION_LEVEL_ZONE = 1;
    private static final int LOCATION_LEVEL_RACK = 2;
    private static final int LOCATION_LEVEL_BIN = 3;
    private static final Set<String> LOCATION_STATUSES = Set.of("active", "disabled", "maintenance");
    private final JdbcTemplate jdbc;

    /** 返回可选择的在用仓库及其入库定位深度。 */
    @SaCheckLogin
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(jdbc.queryForList("""
            SELECT warehouse_id::text AS warehouse_id, warehouse_code, warehouse_name, inbound_location_level, status
            FROM inv_warehouse WHERE del_flag='0'
            ORDER BY warehouse_code, warehouse_id
            """));
    }

    /** 按仓库读取位置树；ID 以字符串返回，避免前端 JavaScript 大整数精度损失。 */
    @SaCheckPermission("inventory:location:list")
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree(@RequestParam(required = false) Long warehouseId) {
        if (warehouseId != null && !warehouseExists(warehouseId, true)) return R.fail("仓库不存在或已删除");
        List<Map<String, Object>> rows = locationRows(warehouseId, null, null, null, null);
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            row.put("children", new ArrayList<Map<String, Object>>());
            byId.put(String.valueOf(row.get("location_id")), row);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object parent = row.get("parent_location_id");
            Map<String, Object> parentRow = parent == null ? null : byId.get(String.valueOf(parent));
            if (parentRow == null) roots.add(row);
            else children(parentRow).add(row);
        }
        return R.ok(roots);
    }

    /** 查询仓库内位置，可按父级、层级、关键字和状态过滤。 */
    @SaCheckPermission("inventory:location:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam Long warehouseId,
                                              @RequestParam(required = false) Long parentId,
                                              @RequestParam(required = false) Integer level,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        if (!warehouseExists(warehouseId, true)) return R.fail("仓库不存在或已删除");
        if (level != null && (level < 1 || level > 3)) return R.fail("仓储位置类型无效");
        if (status != null && !status.isBlank() && !LOCATION_STATUSES.contains(status)) return R.fail("仓储位置状态无效");
        if (parentId != null && locationInWarehouse(parentId, warehouseId) == null) return R.fail("上级位置不存在或不属于当前仓库");
        return R.ok(locationRows(warehouseId, parentId, level, blankToNull(keyword), blankToNull(status)));
    }

    /** 查询单条仓储位置及其父级路径。 */
    @SaCheckPermission("inventory:location:query")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.location_id::text AS location_id, l.warehouse_id::text AS warehouse_id,
                   l.parent_location_id::text AS parent_location_id, l.location_code, l.location_name,
                   l.location_level, l.agv_reachable, l.capacity_limit, l.capacity_unit,
                   l.location_status, l.external_location_code, l.remark,
                   p.location_code AS parent_location_code, p.location_name AS parent_location_name
            FROM inv_warehouse_location l
            LEFT JOIN inv_warehouse_location p ON p.location_id=l.parent_location_id AND p.del_flag='0'
            WHERE l.location_id=? AND l.del_flag='0'
            """, id);
        return rows.isEmpty() ? R.fail("仓储位置不存在或已删除") : R.ok(rows.get(0));
    }

    /** 新增位置并记录审计快照。 */
    @SaCheckPermission("inventory:location:add")
    @Log(title = "仓库布局", businessType = BusinessType.INSERT)
    @Transactional
    @PostMapping
    public R<String> add(@RequestBody Map<String, Object> body) {
        Long warehouseId = longValue(body.get("warehouseId"));
        lockWarehouse(warehouseId);
        Long parentId = longValue(body.get("parentLocationId"));
        Integer level = intValue(body.get("locationLevel"));
        LocationCodeRule rule = locationCodeRule();
        String code = value(body, "locationCode");
        String suggestedCode = value(body, "autoGeneratedCode");
        if (rule.enabled() && (code.isBlank() || code.equals(suggestedCode))) {
            code = nextLocationCode(warehouseId, parentId, level, rule);
        } else if (code.isBlank()) return R.fail("自动编码已关闭，请手动填写编码");
        body.put("locationCode", code);
        String error = validate(body, warehouseId, parentId, level, null);
        if (error != null) return R.fail(error);
        Long id = jdbc.queryForObject("""
            INSERT INTO inv_warehouse_location(warehouse_id,parent_location_id,location_code,location_name,location_level,
                agv_reachable,capacity_limit,capacity_unit,location_status,external_location_code,remark,create_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING location_id
            """, Long.class, warehouseId, parentId, value(body, "locationCode"), value(body, "locationName"), level,
            boolValue(body.get("agvReachable"), true), decimalValue(body.get("capacityLimit")), value(body, "capacityUnit"),
            status(body.get("locationStatus")), value(body, "externalLocationCode"), value(body, "remark"), LoginHelper.getUserId());
        recordAudit("csv_import".equals(value(body, "entrySource")) ? "导入" : "新增", id, Map.of(), auditSnapshot(id));
        return R.data(code);
    }

    /** 新增表单的建议编码；最终提交仍会在仓库锁内重新分配未编辑的建议值。 */
    @SaCheckPermission("inventory:location:add")
    @GetMapping("/next-code")
    public R<String> nextCode(@RequestParam Long warehouseId,
                              @RequestParam(required = false) Long parentLocationId,
                              @RequestParam Integer locationLevel) {
        LocationCodeRule rule = locationCodeRule();
        if (!rule.enabled()) return R.fail("自动编码已关闭");
        return R.data(nextLocationCode(warehouseId, parentLocationId, locationLevel, rule));
    }

    /** 编辑位置属性；所属仓库、层级和父级建立后固定，避免移动位置破坏库存与 AGV 映射。 */
    @SaCheckPermission("inventory:location:edit")
    @Log(title = "仓库布局", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long id = longValue(body.get("locationId"));
        if (id == null) return R.fail("仓储位置ID不能为空");
        Map<String, Object> before = auditSnapshot(id);
        if (!before.isEmpty()) {
            lockWarehouse(longValue(before.get("warehouse_id")));
            before = auditSnapshot(id);
        }
        if (before.isEmpty()) return R.fail("仓储位置不存在或已删除");
        Long warehouseId = longValue(before.get("warehouse_id"));
        Long parentId = longValue(before.get("parent_location_id"));
        Integer level = intValue(before.get("location_level"));
        if ((body.containsKey("warehouseId") && !java.util.Objects.equals(warehouseId, longValue(body.get("warehouseId"))))
            || (body.containsKey("parentLocationId") && !java.util.Objects.equals(parentId, longValue(body.get("parentLocationId"))))
            || (body.containsKey("locationLevel") && !java.util.Objects.equals(level, intValue(body.get("locationLevel"))))) {
            return R.fail("所属仓库、类型和父级建立后不可修改，请新增档案");
        }
        String error = validate(body, warehouseId, parentId, level, id);
        if (error != null) return R.fail(error);
        String oldCode = String.valueOf(before.get("location_code"));
        String nextCode = value(body, "locationCode");
        if (!oldCode.equals(nextCode)) return R.fail("档案编码创建后不可修改，以免破坏仓库层级编码和业务引用");
        jdbc.update("""
            UPDATE inv_warehouse_location SET location_code=?,location_name=?,agv_reachable=?,capacity_limit=?,capacity_unit=?,
                location_status=?,external_location_code=?,remark=?,update_by=?,update_time=now()
            WHERE location_id=? AND del_flag='0'
            """, nextCode, value(body, "locationName"), boolValue(body.get("agvReachable"), true),
            decimalValue(body.get("capacityLimit")), value(body, "capacityUnit"), status(body.get("locationStatus")),
            value(body, "externalLocationCode"), value(body, "remark"), LoginHelper.getUserId(), id);
        recordAudit("修改", id, before, auditSnapshot(id));
        return R.ok();
    }

    private LocationCodeRule locationCodeRule() {
        boolean enabled = true;
        String zonePrefix = "Z", rackPrefix = "R", binPrefix = "B";
        int zoneDigits = 2, rackDigits = 2, binDigits = 3;
        try {
            String value = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?", String.class, "inventory.warehouseLayout.codeRule");
            Map<String, Object> config = JsonUtils.parseMap(value);
            if (config.get("enabled") instanceof Boolean flag) enabled = flag;
            if (config.get("zonePrefix") instanceof String text) zonePrefix = text.trim();
            if (config.get("rackPrefix") instanceof String text) rackPrefix = text.trim();
            if (config.get("binPrefix") instanceof String text) binPrefix = text.trim();
            if (config.get("zoneDigits") instanceof Number number) zoneDigits = number.intValue();
            if (config.get("rackDigits") instanceof Number number) rackDigits = number.intValue();
            if (config.get("binDigits") instanceof Number number) binDigits = number.intValue();
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) { /* 默认开启，Z/R/B + 2/2/3位 */ }
        catch (Exception e) { throw new IllegalStateException("仓库布局自动编码规则无效，请在参数设置中修复", e); }
        if (!zonePrefix.matches("[A-Za-z0-9]{1,8}") || !rackPrefix.matches("[A-Za-z0-9]{1,8}") || !binPrefix.matches("[A-Za-z0-9]{1,8}")
            || zoneDigits < 1 || zoneDigits > 6 || rackDigits < 1 || rackDigits > 6 || binDigits < 1 || binDigits > 6) {
            throw new IllegalStateException("仓库布局自动编码规则无效，请在参数设置中修复");
        }
        return new LocationCodeRule(enabled, zonePrefix.toUpperCase(java.util.Locale.ROOT), zoneDigits,
            rackPrefix.toUpperCase(java.util.Locale.ROOT), rackDigits, binPrefix.toUpperCase(java.util.Locale.ROOT), binDigits);
    }

    private String nextLocationCode(Long warehouseId, Long parentId, Integer level, LocationCodeRule rule) {
        if (warehouseId == null || level == null || level < LOCATION_LEVEL_ZONE || level > LOCATION_LEVEL_BIN)
            throw new IllegalArgumentException("请选择仓库及有效的布局层级");
        String warehouseCode = jdbc.queryForObject("SELECT warehouse_code FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0'", String.class, warehouseId);
        String parentCode = "";
        if (level == LOCATION_LEVEL_ZONE) {
            if (parentId != null) throw new IllegalArgumentException("库区不能设置上级位置");
            parentCode = warehouseCode;
        } else {
            if (parentId == null) throw new IllegalArgumentException("请选择上级位置");
            Map<String, Object> parent = locationInWarehouse(parentId, warehouseId);
            if (parent == null || intValue(parent.get("location_level")) != level - 1)
                throw new IllegalArgumentException("上级位置与当前层级不匹配");
            parentCode = String.valueOf(parent.get("location_code"));
        }
        String segmentPrefix = level == LOCATION_LEVEL_ZONE ? rule.zonePrefix() : level == LOCATION_LEVEL_RACK ? rule.rackPrefix() : rule.binPrefix();
        int digits = level == LOCATION_LEVEL_ZONE ? rule.zoneDigits() : level == LOCATION_LEVEL_RACK ? rule.rackDigits() : rule.binDigits();
        String prefix = parentCode + "-" + segmentPrefix;
        String siblingSql = "SELECT location_code FROM inv_warehouse_location WHERE warehouse_id=? AND location_level=? AND " + (parentId == null ? "parent_location_id IS NULL" : "parent_location_id=?");
        List<String> siblings = parentId == null
            ? jdbc.queryForList(siblingSql, String.class, warehouseId, level)
            : jdbc.queryForList(siblingSql, String.class, warehouseId, level, parentId);
        long max = 0;
        for (String sibling : siblings) {
            if (sibling.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String suffix = sibling.substring(prefix.length());
                if (suffix.matches("[0-9]+")) try { max = Math.max(max, Long.parseLong(suffix)); } catch (NumberFormatException ignored) { }
            }
        }
        String code = prefix + "0".repeat(Math.max(0, digits - Long.toString(max + 1).length())) + (max + 1);
        if (code.length() > 80) throw new IllegalStateException("生成的层级编码超过80位，请缩短仓库编码或前缀");
        return code;
    }

    private record LocationCodeRule(boolean enabled, String zonePrefix, int zoneDigits, String rackPrefix, int rackDigits, String binPrefix, int binDigits) {}

    /** 切换启用、停用或维护状态；父级停用前须先处理有效子级。 */
    @SaCheckPermission("inventory:location:edit")
    @Log(title = "仓库布局状态", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String next = status(body.get("locationStatus"));
        if (!LOCATION_STATUSES.contains(next)) return R.fail("仓储位置状态无效");
        Map<String, Object> before = auditSnapshot(id);
        if (!before.isEmpty()) {
            lockWarehouse(longValue(before.get("warehouse_id")));
            before = auditSnapshot(id);
        }
        if (before.isEmpty()) return R.fail("仓储位置不存在或已删除");
        String error = validateState(id, longValue(before.get("warehouse_id")), longValue(before.get("parent_location_id")), next);
        if (error != null) return R.fail(error);
        jdbc.update("UPDATE inv_warehouse_location SET location_status=?,update_by=?,update_time=now() WHERE location_id=? AND del_flag='0'", next, LoginHelper.getUserId(), id);
        recordAudit("修改状态：", id, before, auditSnapshot(id));
        return R.ok();
    }

    /** 逻辑删除无子级位置；后续库存单据应以外键保护已被引用的位置。 */
    @SaCheckPermission("inventory:location:remove")
    @Log(title = "仓库布局", businessType = BusinessType.DELETE)
    @Transactional
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        Map<String, Object> before = auditSnapshot(id);
        if (!before.isEmpty()) {
            lockWarehouse(longValue(before.get("warehouse_id")));
            before = auditSnapshot(id);
        }
        if (before.isEmpty()) return R.fail("仓储位置不存在或已删除");
        Long children = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE parent_location_id=? AND del_flag='0'", Long.class, id);
        if (children != null && children > 0) return R.fail("该位置仍有子级，请先处理子级或将位置停用");
        if (hasBusinessReference(id)) return R.fail("该档案已被业务引用，不能删除，可改为停用");
        jdbc.update("UPDATE inv_warehouse_location SET del_flag='1',location_status='disabled',update_by=?,update_time=now() WHERE location_id=? AND del_flag='0'", LoginHelper.getUserId(), id);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("del_flag", "1");
        after.put("location_status", "disabled");
        recordAudit("删除", id, before, after);
        return R.ok();
    }

    /** 校验仓库、父级层级、文本字段、容量和仓库内唯一编码。 */
    private String validate(Map<String, Object> body, Long warehouseId, Long parentId, Integer level, Long currentId) {
        if (warehouseId == null || !warehouseExists(warehouseId, currentId != null)) return "请选择有效的在用仓库";
        if (level == null || level < LOCATION_LEVEL_ZONE || level > LOCATION_LEVEL_BIN) return "仓储位置类型无效";
        String code = value(body, "locationCode"), name = value(body, "locationName");
        if (code == null || code.isBlank()) return "位置编码不能为空";
        if (code.length() > 80) return "位置编码不能超过80个字符";
        if (name == null || name.isBlank()) return "位置名称不能为空";
        if (name.length() > 200) return "位置名称不能超过200个字符";
        if (parentId == null && level != LOCATION_LEVEL_ZONE) return "货架必须归属库区，货位必须归属货架";
        if (level == LOCATION_LEVEL_ZONE && parentId != null) return "库区直接归属仓库，不能设置上级位置";
        if (parentId != null) {
            Map<String, Object> parent = locationInWarehouse(parentId, warehouseId);
            if (parent == null) return "上级位置不存在或不属于当前仓库";
            if ((level == LOCATION_LEVEL_RACK && intValue(parent.get("location_level")) != LOCATION_LEVEL_ZONE)
                || (level == LOCATION_LEVEL_BIN && intValue(parent.get("location_level")) != LOCATION_LEVEL_RACK)) {
                return "位置层级不正确：货架须挂在库区下，货位须挂在货架下";
            }
            if (currentId == null && !"active".equals(String.valueOf(parent.get("location_status")))) return "上级位置未启用，不能新增子级";
        }
        String nextStatus = status(body.get("locationStatus"));
        if (!LOCATION_STATUSES.contains(nextStatus)) return "仓储位置状态无效";
        String stateError = validateState(currentId, warehouseId, parentId, nextStatus);
        if (stateError != null) return stateError;
        BigDecimal capacity = decimalValue(body.get("capacityLimit"));
        if (body.get("capacityLimit") != null && !body.get("capacityLimit").toString().isBlank() && (capacity == null || capacity.signum() < 0)) return "容量上限必须为不小于0的数字";
        String unit = value(body, "capacityUnit");
        if (capacity != null && unit == null) return "填写容量上限时请同时填写容量单位";
        if (unit != null && unit.length() > 30) return "容量单位不能超过30个字符";
        String external = value(body, "externalLocationCode");
        if (external != null && external.length() > 120) return "外部位置映射码不能超过120个字符";
        if (value(body, "remark") != null && value(body, "remark").length() > 1000) return "备注不能超过1000个字符";
        String duplicateSql = "SELECT count(*) FROM inv_warehouse_location WHERE warehouse_id=? AND lower(location_code)=lower(?) AND del_flag='0'" + (currentId == null ? "" : " AND location_id<>?");
        Long duplicate = currentId == null ? jdbc.queryForObject(duplicateSql, Long.class, warehouseId, code.trim()) : jdbc.queryForObject(duplicateSql, Long.class, warehouseId, code.trim(), currentId);
        if (duplicate != null && duplicate > 0) return "该仓库内的位置编码已存在";
        if (external != null) {
            String externalSql = "SELECT count(*) FROM inv_warehouse_location WHERE warehouse_id=? AND lower(external_location_code)=lower(?) AND del_flag='0'" + (currentId == null ? "" : " AND location_id<>?");
            Long externalDuplicate = currentId == null ? jdbc.queryForObject(externalSql, Long.class, warehouseId, external) : jdbc.queryForObject(externalSql, Long.class, warehouseId, external, currentId);
            if (externalDuplicate != null && externalDuplicate > 0) return "该仓库内的外部位置映射码已存在";
        }
        return null;
    }

    /** 读取位置列表；父级 ID 和位置 ID 均以文本传输。 */
    private List<Map<String, Object>> locationRows(Long warehouseId, Long parentId, Integer level, String keyword, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT l.location_id::text AS location_id, l.warehouse_id::text AS warehouse_id,
                   l.parent_location_id::text AS parent_location_id, l.location_code, l.location_name,
                   l.location_level, l.agv_reachable, l.capacity_limit, l.capacity_unit,
                   l.location_status, l.external_location_code, l.remark, w.warehouse_code, w.warehouse_name,
                   p.location_code AS parent_location_code, p.location_name AS parent_location_name
            FROM inv_warehouse_location l
            JOIN inv_warehouse w ON w.warehouse_id=l.warehouse_id
            LEFT JOIN inv_warehouse_location p ON p.location_id=l.parent_location_id AND p.del_flag='0'
            WHERE l.del_flag='0' AND w.del_flag='0'
            """);
        List<Object> args = new ArrayList<>();
        if (warehouseId != null) { sql.append(" AND l.warehouse_id=?"); args.add(warehouseId); }
        if (parentId != null) { sql.append(" AND l.parent_location_id=?"); args.add(parentId); }
        if (level != null) { sql.append(" AND l.location_level=?"); args.add(level); }
        if (keyword != null) { sql.append(" AND (l.location_code ILIKE ? OR l.location_name ILIKE ? OR l.external_location_code ILIKE ?)"); String q = "%" + keyword + "%"; args.add(q); args.add(q); args.add(q); }
        if (status != null) { sql.append(" AND l.location_status=?"); args.add(status); }
        sql.append(" ORDER BY w.warehouse_code,l.location_level,l.location_code,l.location_id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 查找同一仓库内的未删除父级，阻止跨仓引用。 */
    private Map<String, Object> locationInWarehouse(Long locationId, Long warehouseId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT location_id::text AS location_id,warehouse_id::text AS warehouse_id,parent_location_id::text AS parent_location_id,location_level,location_status FROM inv_warehouse_location WHERE location_id=? AND warehouse_id=? AND del_flag='0'", locationId, warehouseId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 检查仓库是否存在；不同接口按需排除已停用仓库。 */
    private boolean warehouseExists(Long warehouseId, boolean includeDisabled) {
        if (warehouseId == null) return false;
        String sql = "SELECT count(*) FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0'" + (includeDisabled ? "" : " AND status='0'");
        Long count = jdbc.queryForObject(sql, Long.class, warehouseId);
        return count != null && count > 0;
    }

    /** 生成位置审计快照，含层级、父级和 AGV 兼容字段。 */
    private Map<String, Object> auditSnapshot(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.location_id::text AS location_id,l.warehouse_id::text AS warehouse_id,
                   l.parent_location_id::text AS parent_location_id,l.location_code,l.location_name,l.location_level,
                   l.agv_reachable,l.capacity_limit,l.capacity_unit,l.location_status,l.external_location_code,l.remark,l.del_flag,
                   w.warehouse_name,p.location_name AS parent_location_name
            FROM inv_warehouse_location l
            JOIN inv_warehouse w ON w.warehouse_id=l.warehouse_id
            LEFT JOIN inv_warehouse_location p ON p.location_id=l.parent_location_id
            WHERE l.location_id=? AND l.del_flag='0'
            """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 同一仓库内串行维护位置树，避免父级停用/删除与新增子级并发穿透。 */
    private void lockWarehouse(Long warehouseId) {
        if (warehouseId != null) jdbc.queryForList("SELECT warehouse_id FROM inv_warehouse WHERE warehouse_id=? FOR UPDATE", warehouseId);
    }

    /** 新增、编辑和单独改状态均使用相同的父子状态约束。 */
    private String validateState(Long id, Long warehouseId, Long parentId, String next) {
        if ("active".equals(next)) {
            if (!warehouseExists(warehouseId, false)) return "请先启用所属仓库";
            if (parentId != null) {
                Map<String, Object> parent = locationInWarehouse(parentId, warehouseId);
                if (parent == null || !"active".equals(String.valueOf(parent.get("location_status")))) return "请先启用所属库区或货架";
            }
        } else if (id != null) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE parent_location_id=? AND del_flag='0' AND location_status='active'", Long.class, id);
            if (count != null && count > 0) return "请先停用或维护该档案下的有效子级";
        }
        return null;
    }

    /** 保护声明为外键的业务引用；位置子级已由独立规则检查。 */
    private boolean hasBusinessReference(Long id) {
        List<Map<String, Object>> refs = jdbc.queryForList("""
            SELECT n.nspname AS schema_name,t.relname AS table_name,a.attname AS column_name,
                   cardinality(c.conkey) AS key_count
            FROM pg_constraint c
            JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
            JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=c.conkey[1]
            WHERE c.contype='f' AND c.confrelid='public.inv_warehouse_location'::regclass
              AND c.conrelid<>'public.inv_warehouse_location'::regclass
            """);
        for (Map<String, Object> ref : refs) {
            if (((Number) ref.get("key_count")).intValue() != 1) return true;
            String sql = "SELECT EXISTS(SELECT 1 FROM " + quoteIdentifier(ref.get("schema_name")) + "."
                + quoteIdentifier(ref.get("table_name")) + " WHERE " + quoteIdentifier(ref.get("column_name")) + "=?)";
            if (Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, id))) return true;
        }
        return false;
    }

    private static String quoteIdentifier(Object value) { return "\"" + value.toString().replace("\"", "\"\"") + "\""; }
    private static String typeName(Object level) { return switch (intValue(level)) { case 1 -> "库区"; case 2 -> "货架"; case 3 -> "货位"; default -> "仓储位置"; }; }

    /** 日志使用业务对象名称与中文字段，并从实际落库值产生前后差异。 */
    private void recordAudit(String action, Long id, Map<String, Object> before, Map<String, Object> after) {
        Object level = after.getOrDefault("location_level", before.get("location_level"));
        BusinessAudit.record(action + typeName(level), id, "名称", businessSnapshot(before), businessSnapshot(after));
    }
    private Map<String, Object> businessSnapshot(Map<String, Object> raw) {
        if (raw.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        String[][] fields = {{"location_id","档案ID"},{"warehouse_id","仓库ID"},{"warehouse_name","所属仓库"},
            {"parent_location_id","父级ID"},{"parent_location_name","父级名称"},{"location_code","编码"},{"location_name","名称"},
            {"agv_reachable","AGV可达"},{"capacity_limit","容量上限"},{"capacity_unit","容量单位"},
            {"external_location_code","外部位置映射码"},{"remark","备注"}};
        for (String[] field : fields) result.put(field[1], raw.get(field[0]));
        result.put("类型", typeName(raw.get("location_level")));
        result.put("状态", switch (String.valueOf(raw.get("location_status"))) { case "active" -> "可用"; case "disabled" -> "停用"; case "maintenance" -> "维护中"; default -> "未知"; });
        result.put("删除标记", "1".equals(String.valueOf(raw.get("del_flag"))) ? "已删除" : "正常");
        return result;
    }

    /** 取得可修改的子级集合并按泛型保持类型安全。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> parent) { return (List<Map<String, Object>>) parent.get("children"); }
    private static String value(Map<String, Object> body, String key) { Object v = body.get(key); return v == null || v.toString().isBlank() ? null : v.toString().trim(); }
    private static Long longValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return Long.valueOf(value.toString()); } catch (NumberFormatException ex) { return null; } }
    private static Integer intValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return Integer.valueOf(value.toString()); } catch (NumberFormatException ex) { return null; } }
    private static BigDecimal decimalValue(Object value) { if (value == null || value.toString().isBlank()) return null; try { return new BigDecimal(value.toString()); } catch (NumberFormatException ex) { return null; } }
    private static Boolean boolValue(Object value, boolean defaultValue) { return value == null ? defaultValue : Boolean.parseBoolean(value.toString()); }
    private static String status(Object value) { return value == null || value.toString().isBlank() ? "active" : value.toString(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
