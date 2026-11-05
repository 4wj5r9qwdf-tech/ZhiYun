package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
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
 * 库位管理：按仓库维护区域、货架、货位三级档案。
 * 库位主键供后续库存单据稳定引用；AGV 字段只保存位置能力和外部映射，不在本期执行调度。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/warehouse-location")
public class WarehouseLocationController {
    private static final Set<String> LOCATION_STATUSES = Set.of("active", "disabled", "maintenance");
    private final JdbcTemplate jdbc;

    /** 返回可选择的在用仓库及其入库定位深度。 */
    @SaCheckLogin
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(jdbc.queryForList("""
            SELECT warehouse_id::text AS warehouse_id, warehouse_code, warehouse_name, inbound_location_level
            FROM inv_warehouse WHERE del_flag='0' AND status='0'
            ORDER BY warehouse_code, warehouse_id
            """));
    }

    /** 按仓库读取位置树；ID 以字符串返回，避免前端 JavaScript 大整数精度损失。 */
    @SaCheckPermission("inventory:location:list")
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree(@RequestParam Long warehouseId) {
        if (!warehouseExists(warehouseId, false)) return R.fail("仓库不存在、已删除或已停用");
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
        if (level != null && (level < 1 || level > 3)) return R.fail("库位层级无效");
        if (status != null && !status.isBlank() && !LOCATION_STATUSES.contains(status)) return R.fail("库位状态无效");
        if (parentId != null && locationInWarehouse(parentId, warehouseId) == null) return R.fail("上级位置不存在或不属于当前仓库");
        return R.ok(locationRows(warehouseId, parentId, level, blankToNull(keyword), blankToNull(status)));
    }

    /** 查询单条库位及其父级路径。 */
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
        return rows.isEmpty() ? R.fail("库位不存在或已删除") : R.ok(rows.get(0));
    }

    /** 新增位置并记录审计快照。 */
    @SaCheckPermission("inventory:location:add")
    @Log(title = "库位管理", businessType = BusinessType.INSERT)
    @Transactional
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        Long warehouseId = longValue(body.get("warehouseId"));
        Long parentId = longValue(body.get("parentLocationId"));
        Integer level = intValue(body.get("locationLevel"));
        String error = validate(body, warehouseId, parentId, level, null);
        if (error != null) return R.fail(error);
        Long id = jdbc.queryForObject("""
            INSERT INTO inv_warehouse_location(warehouse_id,parent_location_id,location_code,location_name,location_level,
                agv_reachable,capacity_limit,capacity_unit,location_status,external_location_code,remark,create_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING location_id
            """, Long.class, warehouseId, parentId, value(body, "locationCode"), value(body, "locationName"), level,
            boolValue(body.get("agvReachable"), true), decimalValue(body.get("capacityLimit")), value(body, "capacityUnit"),
            status(body.get("locationStatus")), value(body, "externalLocationCode"), value(body, "remark"), LoginHelper.getUserId());
        BusinessAudit.record("新增库位", id, "位置编码", Map.of(), auditSnapshot(id));
        return R.ok();
    }

    /** 编辑位置属性；所属仓库、层级和父级建立后固定，避免移动位置破坏库存与 AGV 映射。 */
    @SaCheckPermission("inventory:location:edit")
    @Log(title = "库位管理", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Long id = longValue(body.get("locationId"));
        if (id == null) return R.fail("库位ID不能为空");
        Map<String, Object> before = auditSnapshot(id);
        if (before.isEmpty()) return R.fail("库位不存在或已删除");
        Long warehouseId = longValue(before.get("warehouse_id"));
        Long parentId = longValue(before.get("parent_location_id"));
        Integer level = intValue(before.get("location_level"));
        String error = validate(body, warehouseId, parentId, level, id);
        if (error != null) return R.fail(error);
        String oldCode = String.valueOf(before.get("location_code"));
        String nextCode = value(body, "locationCode");
        if (!oldCode.equalsIgnoreCase(nextCode) && value(before, "external_location_code") != null) {
            return R.fail("该库位已配置外部位置映射码，不能修改位置编码");
        }
        jdbc.update("""
            UPDATE inv_warehouse_location SET location_code=?,location_name=?,agv_reachable=?,capacity_limit=?,capacity_unit=?,
                location_status=?,external_location_code=?,remark=?,update_by=?,update_time=now()
            WHERE location_id=? AND del_flag='0'
            """, nextCode, value(body, "locationName"), boolValue(body.get("agvReachable"), true),
            decimalValue(body.get("capacityLimit")), value(body, "capacityUnit"), status(body.get("locationStatus")),
            value(body, "externalLocationCode"), value(body, "remark"), LoginHelper.getUserId(), id);
        BusinessAudit.record("修改库位", id, "位置编码", before, auditSnapshot(id));
        return R.ok();
    }

    /** 切换启用、停用或维护状态；父级停用前须先处理有效子级。 */
    @SaCheckPermission("inventory:location:edit")
    @Log(title = "库位状态", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String next = status(body.get("locationStatus"));
        if (!LOCATION_STATUSES.contains(next)) return R.fail("库位状态无效");
        Map<String, Object> before = auditSnapshot(id);
        if (before.isEmpty()) return R.fail("库位不存在或已删除");
        if ("active".equals(next)) {
            Long parentId = longValue(before.get("parent_location_id"));
            if (parentId != null) {
                Map<String, Object> parent = locationInWarehouse(parentId, longValue(before.get("warehouse_id")));
                if (parent == null || !"active".equals(String.valueOf(parent.get("location_status")))) return R.fail("请先启用上级位置，再启用当前子级");
            }
        } else {
            Long activeChildren = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE parent_location_id=? AND del_flag='0' AND location_status='active'", Long.class, id);
            if (activeChildren != null && activeChildren > 0) return R.fail("请先停用或维护该位置下的有效子级");
        }
        jdbc.update("UPDATE inv_warehouse_location SET location_status=?,update_by=?,update_time=now() WHERE location_id=? AND del_flag='0'", next, LoginHelper.getUserId(), id);
        BusinessAudit.record("修改库位状态", id, "位置编码", before, auditSnapshot(id));
        return R.ok();
    }

    /** 逻辑删除无子级位置；后续库存单据应以外键保护已被引用的位置。 */
    @SaCheckPermission("inventory:location:remove")
    @Log(title = "库位管理", businessType = BusinessType.DELETE)
    @Transactional
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        Map<String, Object> before = auditSnapshot(id);
        if (before.isEmpty()) return R.fail("库位不存在或已删除");
        Long children = jdbc.queryForObject("SELECT count(*) FROM inv_warehouse_location WHERE parent_location_id=? AND del_flag='0'", Long.class, id);
        if (children != null && children > 0) return R.fail("该位置仍有子级，请先处理子级或将位置停用");
        jdbc.update("UPDATE inv_warehouse_location SET del_flag='1',location_status='disabled',update_by=?,update_time=now() WHERE location_id=? AND del_flag='0'", LoginHelper.getUserId(), id);
        BusinessAudit.record("删除库位", id, "位置编码", before, Map.of("状态", "已删除"));
        return R.ok();
    }

    /** 校验仓库、父级层级、文本字段、容量和仓库内唯一编码。 */
    private String validate(Map<String, Object> body, Long warehouseId, Long parentId, Integer level, Long currentId) {
        if (warehouseId == null || !warehouseExists(warehouseId, currentId != null)) return "请选择有效的在用仓库";
        if (level == null || level < 1 || level > 3) return "库位层级无效";
        String code = value(body, "locationCode"), name = value(body, "locationName");
        if (code == null || code.isBlank()) return "位置编码不能为空";
        if (code.length() > 80) return "位置编码不能超过80个字符";
        if (name == null || name.isBlank()) return "位置名称不能为空";
        if (name.length() > 200) return "位置名称不能超过200个字符";
        if (parentId == null && level != 1) return "货架必须归属区域，货位必须归属货架";
        if (parentId != null) {
            Map<String, Object> parent = locationInWarehouse(parentId, warehouseId);
            if (parent == null) return "上级位置不存在或不属于当前仓库";
            if (intValue(parent.get("location_level")) != level - 1) return "位置层级不正确：货架须挂在区域下，货位须挂在货架下";
            if (!"active".equals(String.valueOf(parent.get("location_status")))) return "上级位置未启用，不能新增或修改子级";
        }
        String nextStatus = status(body.get("locationStatus"));
        if (!LOCATION_STATUSES.contains(nextStatus)) return "库位状态无效";
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
                   l.location_status, l.external_location_code, l.remark,
                   p.location_code AS parent_location_code, p.location_name AS parent_location_name
            FROM inv_warehouse_location l
            LEFT JOIN inv_warehouse_location p ON p.location_id=l.parent_location_id AND p.del_flag='0'
            WHERE l.warehouse_id=? AND l.del_flag='0'
            """);
        List<Object> args = new ArrayList<>(); args.add(warehouseId);
        if (parentId != null) { sql.append(" AND l.parent_location_id=?"); args.add(parentId); }
        if (level != null) { sql.append(" AND l.location_level=?"); args.add(level); }
        if (keyword != null) { sql.append(" AND (l.location_code ILIKE ? OR l.location_name ILIKE ? OR l.external_location_code ILIKE ?)"); String q = "%" + keyword + "%"; args.add(q); args.add(q); args.add(q); }
        if (status != null) { sql.append(" AND l.location_status=?"); args.add(status); }
        sql.append(" ORDER BY l.location_level,l.location_code,l.location_id");
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
                   l.agv_reachable,l.capacity_limit,l.capacity_unit,l.location_status,l.external_location_code,l.remark
            FROM inv_warehouse_location l WHERE l.location_id=? AND l.del_flag='0'
            """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 生成仓库设置审计快照。 */
    private Map<String, Object> warehouseAuditSnapshot(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT warehouse_id::text AS warehouse_id,warehouse_code,warehouse_name,inbound_location_level FROM inv_warehouse WHERE warehouse_id=? AND del_flag='0'", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
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
