package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 建筑档案管理（物理资产/固定资产，非部门）。
 * 口径（2026-10-25 定稿）：建筑=一栋楼/固定资产（面积/楼层/地址/建成年份），是"地"；
 *   部门(车间/组)=管人的组织，是"人"，坐落在建筑里。用途类别：厂房/行政楼/宿舍楼（仓库是建筑内区域不在此列）。
 *   厂房一建筑=一一车间。关联：sys_dept 车间节点加 building_id 指向本表（只车间层关联，下面区域/组继承）。
 * 表：sys_building。基础字段（编号/名称/楼层/占地/建筑面积/地址/建成年份/状态/备注）。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/building")
public class BuildingController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 建筑列表（含占用车间数，供删除校验/展示）。可选 buildingType 筛选用途类别。
     */
    @SaCheckPermission("system:building:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String buildingType) {
        StringBuilder sql = new StringBuilder(
            "SELECT b.*, " +
            "  (SELECT count(*) FROM sys_dept d WHERE d.building_id = b.plant_id AND d.del_flag='0') AS dept_count " +
            "FROM sys_building b WHERE b.del_flag='0'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (buildingType != null && !buildingType.isBlank()) {
            sql.append(" AND b.building_type = ?");
            args.add(buildingType.trim());
        }
        sql.append(" ORDER BY b.building_type, b.plant_id");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return R.ok(rows);
    }

    /**
     * 厂房详情。
     */
    @SaCheckPermission("system:plant:query")
    @GetMapping("/{plantId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long plantId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM sys_building WHERE plant_id=?", plantId);
        return R.ok(row);
    }

    /**
     * 新增建筑。
     * body: buildingType、plantCode、plantName(必填)、floorInfo、landArea、buildingArea、address、builtYear、status、remark
     */
    @SaCheckPermission("system:building:add")
    @Log(title = "建筑档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String name = str(body.get("plantName"));
        if (name == null || name.isBlank()) {
            return R.fail("建筑名称不能为空");
        }
        Long plantId = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO sys_building (plant_id, building_type, plant_code, plant_name, floor_info, land_area, building_area, " +
            "  address, built_year, status, remark, del_flag, create_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?::int,?,?,'0',now())",
            plantId, str(body.getOrDefault("buildingType", "厂房")), str(body.get("plantCode")), name, str(body.get("floorInfo")),
            body.get("landArea"), body.get("buildingArea"), str(body.get("address")),
            body.get("builtYear"), str(body.getOrDefault("status", "0")), str(body.get("remark")));
        return R.ok();
    }

    /**
     * 编辑建筑。
     */
    @SaCheckPermission("system:building:edit")
    @Log(title = "建筑档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Object idRaw = body.get("plantId");
        if (!(idRaw instanceof Number)) {
            return R.fail("建筑ID不能为空");
        }
        Long plantId = ((Number) idRaw).longValue();
        String name = str(body.get("plantName"));
        if (name == null || name.isBlank()) {
            return R.fail("建筑名称不能为空");
        }
        jdbcTemplate.update(
            "UPDATE sys_building SET building_type=?, plant_code=?, plant_name=?, floor_info=?, land_area=?, building_area=?, " +
            "  address=?, built_year=?::int, status=?, remark=?, update_time=now() WHERE plant_id=?",
            str(body.getOrDefault("buildingType", "厂房")), str(body.get("plantCode")), name, str(body.get("floorInfo")),
            body.get("landArea"), body.get("buildingArea"), str(body.get("address")),
            body.get("builtYear"), str(body.getOrDefault("status", "0")), str(body.get("remark")), plantId);
        return R.ok();
    }

    /**
     * 删除建筑（软删）：有车间占用时不允许删。
     */
    @SaCheckPermission("system:building:remove")
    @Log(title = "建筑档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{plantId}")
    public R<Void> remove(@PathVariable Long plantId) {
        Long used = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_dept WHERE building_id=? AND del_flag='0'", Long.class, plantId);
        if (used != null && used > 0) {
            return R.fail("该建筑下还关联着车间/部门，先解除关联再删除");
        }
        jdbcTemplate.update("UPDATE sys_building SET del_flag='1', update_time=now() WHERE plant_id=?", plantId);
        return R.ok();
    }

    /** 安全取字符串（null→null，数字→字符串）。 */
    private String str(Object o) {
        if (o == null) return null;
        return o instanceof Number ? o.toString() : String.valueOf(o);
    }
}
