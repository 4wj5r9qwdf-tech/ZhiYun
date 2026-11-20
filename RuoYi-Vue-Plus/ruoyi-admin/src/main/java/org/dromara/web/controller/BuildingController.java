/*
 * 文件用途：为织云 ERP 的厂房档案接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的厂房档案接口提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.web.controller;

import org.dromara.system.controller.system.BusinessAudit;

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
 *   厂房一建筑=一一车间。关联：sys_org 车间节点加 building_id 指向本表（只车间层关联，下面区域/组继承）。
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
     * 查询建筑列表并返回占用车间数；状态筛选只接受0/1，避免非法值被误当作有效查询。
     * Query buildings with their workshop counts; accept only 0/1 status filters to reject malformed queries.
     */
    @SaCheckPermission("system:building:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String buildingType,
                                             @RequestParam(required = false) String status) {
        String statusFilter = status == null ? "" : status.trim();
        if (!statusFilter.isEmpty() && !java.util.Set.of("0", "1").contains(statusFilter)) {
            return R.fail("建筑状态筛选只能是0（启用）或1（停用）");
        }
        StringBuilder sql = new StringBuilder(
            "SELECT b.*, " +
            "  (SELECT count(*) FROM sys_org d WHERE d.building_id = b.plant_id AND d.del_flag='0') AS org_count " +
            "FROM sys_building b WHERE b.del_flag='0'");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (b.plant_code LIKE ? OR b.plant_name LIKE ? OR b.address LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw); args.add(kw); args.add(kw);
        }
        if (buildingType != null && !buildingType.isBlank()) {
            sql.append(" AND b.building_type = ?");
            args.add(buildingType.trim());
        }
        if (!statusFilter.isEmpty()) {
            sql.append(" AND b.status = ?");
            args.add(statusFilter);
        }
        sql.append(" ORDER BY b.building_type, b.plant_id");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return R.ok(rows);
    }

    /** 查询单栋建筑档案，包含用途、面积、楼层和地址信息。 / Read one building with its usage, area, floor and address details. */
    @SaCheckPermission("system:building:query")
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
    @org.springframework.transaction.annotation.Transactional
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
        BusinessAudit.record("新增建筑",plantId,"名称",java.util.Map.of(),auditSnapshot(plantId,false));
        return R.ok();
    }

    /** 更新建筑档案并记录前后快照供业务审计。 / Update a building and retain before/after snapshots for business audit. */
    @SaCheckPermission("system:building:edit")
    @Log(title = "建筑档案", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
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
        var auditBefore = auditSnapshot(plantId,true);
        jdbcTemplate.update(
            "UPDATE sys_building SET building_type=?, plant_code=?, plant_name=?, floor_info=?, land_area=?, building_area=?, " +
            "  address=?, built_year=?::int, status=?, remark=?, update_time=now() WHERE plant_id=?",
            str(body.getOrDefault("buildingType", "厂房")), str(body.get("plantCode")), name, str(body.get("floorInfo")),
            body.get("landArea"), body.get("buildingArea"), str(body.get("address")),
            body.get("builtYear"), str(body.getOrDefault("status", "0")), str(body.get("remark")), plantId);
        BusinessAudit.record("修改建筑",plantId,"名称",auditBefore,auditSnapshot(plantId,false));
        return R.ok();
    }

    /** 软删除未被车间使用的建筑；仍有关联时拒绝删除以保护组织数据。 / Soft-delete only unused buildings and preserve linked organization data. */
    @SaCheckPermission("system:building:remove")
    @Log(title = "建筑档案", businessType = BusinessType.DELETE)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{plantId}")
    public R<Void> remove(@PathVariable Long plantId) {
        Long used = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_org WHERE building_id=? AND del_flag='0'", Long.class, plantId);
        if (used != null && used > 0) {
            return R.fail("该建筑下还关联着车间/部门，先解除关联再删除");
        }
        var auditBefore = auditSnapshot(plantId,true);
        jdbcTemplate.update("UPDATE sys_building SET del_flag='1', update_time=now() WHERE plant_id=?", plantId);
        BusinessAudit.record("删除建筑",plantId,"名称",auditBefore,auditSnapshot(plantId,false));
        return R.ok();
    }

    /**
     * 将请求值转换为可空字符串；保留数字文本，供表单与审计字段统一处理。
     * Convert a request value to a nullable string while preserving numeric text for form and audit handling.
     */
    private String str(Object o) {
        if (o == null) return null;
        return o instanceof Number ? o.toString() : String.valueOf(o);
    }
    /**
     * 查询建筑业务审计快照；更新场景可锁定行，避免并发修改时前后状态错配。
     * Load a building audit snapshot and optionally lock its row to prevent mismatched concurrent before/after states.
     */
    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(jdbcTemplate,"SELECT plant_name AS \"名称\", building_type AS \"建筑类型\", plant_code AS \"建筑编码\", floor_info AS \"楼层\", land_area AS \"占地面积\", building_area AS \"建筑面积\", address AS \"地址\", built_year AS \"建成年份\", status AS \"状态\", remark AS \"备注\", del_flag AS \"删除标记\" FROM sys_building WHERE plant_id=?"+(lock ? " FOR UPDATE" : ""),id);
        return row;
    }
}
