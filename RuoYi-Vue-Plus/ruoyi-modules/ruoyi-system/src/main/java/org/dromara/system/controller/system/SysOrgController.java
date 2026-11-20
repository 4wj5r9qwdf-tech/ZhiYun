/*
 * 文件用途：承载织云 ERP 对上游框架“SysOrgController”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysOrgController”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
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
package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.vo.SysOrgVo;
import org.dromara.system.mapper.SysOrgManageMapper;
import org.dromara.system.service.ISysOrgService;
import org.dromara.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/org")
public class SysOrgController extends BaseController {

    private final org.springframework.jdbc.core.JdbcTemplate auditJdbc;
    private final ISysOrgService orgService;
    private final ISysPostService postService;
    private final SysOrgManageMapper orgManageMapper;

    /**
     * 查询部门列表。
     *
     * @param org 查询条件
     * @return 部门列表
     */
    @SaCheckPermission("system:org:list")
    @GetMapping("/list")
    public R<List<SysOrgVo>> list(SysOrgBo org) {
        List<SysOrgVo> orgs = orgService.selectOrgList(org);
        return R.ok(orgs);
    }

    /**
     * 查询部门列表（排除节点）
     *
     * @param orgId 部门ID
     * @return 过滤后的部门列表
     */
    @SaCheckPermission("system:org:list")
    @GetMapping("/list/exclude/{orgId}")
    public R<List<SysOrgVo>> excludeChild(@PathVariable(value = "orgId") Long orgId) {
        List<SysOrgVo> orgs = orgService.selectOrgList(new SysOrgBo());
        orgs.removeIf(d -> d.getOrgId().equals(orgId)
            || StringUtils.splitList(d.getAncestors()).contains(Convert.toStr(orgId)));
        return R.ok(orgs);
    }

    /**
     * 根据部门编号获取详细信息
     *
     * @param orgId 部门ID
     * @return 部门详情
     */
    @SaCheckPermission("system:org:query")
    @GetMapping(value = "/{orgId}")
    public R<SysOrgVo> getInfo(@PathVariable Long orgId) {
        orgService.checkOrgDataScope(orgId);
        return R.ok(orgService.selectOrgById(orgId));
    }

    /**
     * 新增部门。
     *
     * @param org 部门参数
     * @return 操作结果
     */
    @SaCheckPermission("system:org:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysOrgBo org) {
        if (!orgService.checkOrgNameUnique(org)) {
            return R.fail("新增部门'" + org.getOrgName() + "'失败，部门名称已存在");
        }
        int auditResult = orgService.insertOrg(org);
        if (auditResult > 0) BusinessAudit.record("新增组织",org.getOrgId(),"名称",java.util.Map.of(),auditSnapshot(org.getOrgId(),false));
        return toAjax(auditResult);
    }

    /**
     * 修改部门。
     *
     * @param org 部门参数
     * @return 操作结果
     */
    @SaCheckPermission("system:org:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysOrgBo org) {
        Long orgId = org.getOrgId();
        orgService.checkOrgDataScope(orgId);
        if (!orgService.checkOrgNameUnique(org)) {
            return R.fail("修改部门'" + org.getOrgName() + "'失败，部门名称已存在");
        } else if (org.getParentId().equals(orgId)) {
            return R.fail("修改部门'" + org.getOrgName() + "'失败，上级部门不能是自己");
        } else if (StringUtils.equals(SystemConstants.DISABLE, org.getStatus())) {
            if (orgService.selectNormalChildrenOrgById(orgId) > 0) {
                return R.fail("该部门包含未停用的子部门!");
            } else if (orgService.checkOrgExistUser(orgId)) {
                return R.fail("该部门下存在已分配用户，不能禁用!");
            }
        }
        var auditBefore = auditSnapshot(org.getOrgId(),true);
        int auditResult = orgService.updateOrg(org);
        if (auditResult > 0) BusinessAudit.record("修改组织",org.getOrgId(),"名称",auditBefore,auditSnapshot(org.getOrgId(),false));
        return toAjax(auditResult);
    }

    /**
     * 删除部门
     *
     * @param orgId 部门ID
     * @return 操作结果
     */
    @SaCheckPermission("system:org:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{orgId}")
    public R<Void> remove(@PathVariable Long orgId) {
        if (SystemConstants.DEFAULT_ORG_ID.equals(orgId)) {
            return R.warn("默认部门,不允许删除");
        }
        if (orgService.hasChildByOrgId(orgId)) {
            return R.warn("存在下级部门,不允许删除");
        }
        if (orgService.checkOrgExistUser(orgId)) {
            return R.warn("部门存在用户,不允许删除");
        }
        if (postService.countPostByOrgId(orgId) > 0) {
            return R.warn("部门存在岗位,不允许删除");
        }
        if (orgManageMapper.countManaged(orgId) > 0) {
            return R.warn("该部门被其他部门管辖,请先在管辖部门中移除");
        }
        orgService.checkOrgDataScope(orgId);
        var auditBefore = auditSnapshot(orgId,true);
        int auditResult = orgService.deleteOrgById(orgId);
        if (auditResult > 0) BusinessAudit.record("删除组织",orgId,"名称",auditBefore,auditSnapshot(orgId,false));
        return toAjax(auditResult);
    }

    /**
     * 获取部门选择框列表
     *
     * @param orgIds 部门ID串
     * @return 部门列表
     */
    @SaCheckPermission("system:org:query")
    @GetMapping("/optionselect")
    public R<List<SysOrgVo>> optionselect(@RequestParam(required = false) Long[] orgIds) {
        return R.ok(orgService.selectOrgByIds(orgIds == null ? null : List.of(orgIds)));
    }

    // ==================== 业务管辖关联（管理部门↔被管部门，多对多，与组织树层级无关） ====================

    /**
     * 查询某部门管辖的被管部门ID列表。
     *
     * @param orgId 管辖方部门ID
     * @return 被管部门ID列表
     */
    @SaCheckPermission("system:org:query")
    @GetMapping("/{orgId}/manages")
    public R<List<Long>> listManages(@PathVariable Long orgId) {
        return R.ok(orgService.selectManagedOrgIds(orgId));
    }

    /**
     * 设置某部门管辖的被管部门（全量覆盖）。
     *
     * @param orgId  管辖方部门ID
     * @param orgIds 被管部门ID列表（空=清空）
     * @return 操作结果
     */
    @SaCheckPermission("system:org:edit")
    @Log(title = "部门管辖", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/{orgId}/manages")
    public R<Void> updateManages(@PathVariable Long orgId, @RequestBody List<Long> orgIds) {
        orgService.checkOrgDataScope(orgId);
        var before = auditSnapshot(orgId,true);
        orgService.updateManagedOrgs(orgId, orgIds == null ? List.of() : orgIds);
        BusinessAudit.record("调整组织管辖",orgId,"名称",before,auditSnapshot(orgId,false));
        return R.ok();
    }

    /**
     * 查询全部部门的被管数量（orgId -> 管辖的部门数），供列表展示管辖范围。
     *
     * @return 管辖方部门ID -> 被管部门数
     */
    @SaCheckPermission("system:org:query")
    @GetMapping("/manage-counts")
    public R<java.util.Map<Long, java.util.List<String>>> manageCounts() {
        return R.ok(orgService.selectManagedNames());
    }

    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(auditJdbc,"SELECT org_name AS \"名称\", parent_id::text AS \"上级组织\", org_category AS \"组织类别\", building_id::text AS \"建筑\", location AS \"位置\", order_num AS \"排序\", leader::text AS \"负责人\", phone AS \"电话\", email AS \"邮箱\", status AS \"状态\", del_flag AS \"删除标记\", ancestors AS \"上级路径\" FROM sys_org WHERE org_id=?"+(lock ? " FOR UPDATE" : ""),id);
        if (!row.isEmpty()) row.put("管辖组织 ID",auditJdbc.queryForList(
            "SELECT managed_org_id::text FROM sys_org_manage WHERE manage_org_id=? ORDER BY managed_org_id",String.class,id));
        return row;
    }
}
