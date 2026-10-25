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
 * 厂房档案管理（物理资产/固定资产，非部门）。
 * 口径（2026-10-25 定稿）：厂房=一栋建筑/固定资产（面积/楼层/地址/建成年份），是"地"；
 *   部门(车间/组)=管人的组织，是"人"，坐落在厂房里。一厂房=一一车间。
 *   关联：sys_dept 车间节点加 plant_id 指向本表（只车间层关联，下面区域/组继承）。
 * 表：sys_plant。基础字段先做（编号/名称/楼层/占地/建筑面积/地址/建成年份/状态/备注）。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/plant")
public class PlantController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 厂房列表（含占用车间数，供删除校验/展示）。
     */
    @SaCheckPermission("system:plant:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT p.*, " +
            "  (SELECT count(*) FROM sys_dept d WHERE d.plant_id = p.plant_id AND d.del_flag='0') AS dept_count " +
            "FROM sys_plant p WHERE p.del_flag='0' ORDER BY p.plant_id");
        return R.ok(rows);
    }

    /**
     * 厂房详情。
     */
    @SaCheckPermission("system:plant:query")
    @GetMapping("/{plantId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long plantId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM sys_plant WHERE plant_id=?", plantId);
        return R.ok(row);
    }

    /**
     * 新增厂房。
     * body: plantCode、plantName(必填)、floorInfo、landArea、buildingArea、address、builtYear、status、remark
     */
    @SaCheckPermission("system:plant:add")
    @Log(title = "厂房档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String name = str(body.get("plantName"));
        if (name == null || name.isBlank()) {
            return R.fail("厂房名称不能为空");
        }
        Long plantId = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO sys_plant (plant_id, plant_code, plant_name, floor_info, land_area, building_area, " +
            "  address, built_year, status, remark, del_flag, create_time) " +
            "VALUES (?,?,?,?,?,?,?,?::int,?,?,'0',now())",
            plantId, str(body.get("plantCode")), name, str(body.get("floorInfo")),
            body.get("landArea"), body.get("buildingArea"), str(body.get("address")),
            body.get("builtYear"), str(body.getOrDefault("status", "0")), str(body.get("remark")));
        return R.ok();
    }

    /**
     * 编辑厂房。
     */
    @SaCheckPermission("system:plant:edit")
    @Log(title = "厂房档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> body) {
        Object idRaw = body.get("plantId");
        if (!(idRaw instanceof Number)) {
            return R.fail("厂房ID不能为空");
        }
        Long plantId = ((Number) idRaw).longValue();
        String name = str(body.get("plantName"));
        if (name == null || name.isBlank()) {
            return R.fail("厂房名称不能为空");
        }
        jdbcTemplate.update(
            "UPDATE sys_plant SET plant_code=?, plant_name=?, floor_info=?, land_area=?, building_area=?, " +
            "  address=?, built_year=?::int, status=?, remark=?, update_time=now() WHERE plant_id=?",
            str(body.get("plantCode")), name, str(body.get("floorInfo")),
            body.get("landArea"), body.get("buildingArea"), str(body.get("address")),
            body.get("builtYear"), str(body.getOrDefault("status", "0")), str(body.get("remark")), plantId);
        return R.ok();
    }

    /**
     * 删除厂房（软删）：有车间占用时不允许删。
     */
    @SaCheckPermission("system:plant:remove")
    @Log(title = "厂房档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{plantId}")
    public R<Void> remove(@PathVariable Long plantId) {
        Long used = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_dept WHERE plant_id=? AND del_flag='0'", Long.class, plantId);
        if (used != null && used > 0) {
            return R.fail("该厂房下还关联着车间/部门，先解除关联再删除");
        }
        jdbcTemplate.update("UPDATE sys_plant SET del_flag='1', update_time=now() WHERE plant_id=?", plantId);
        return R.ok();
    }

    /** 安全取字符串（null→null，数字→字符串）。 */
    private String str(Object o) {
        if (o == null) return null;
        return o instanceof Number ? o.toString() : String.valueOf(o);
    }
}
