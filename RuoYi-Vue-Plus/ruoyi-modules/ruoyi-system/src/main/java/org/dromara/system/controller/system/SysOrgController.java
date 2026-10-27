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
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysOrgBo org) {
        if (!orgService.checkOrgNameUnique(org)) {
            return R.fail("新增部门'" + org.getOrgName() + "'失败，部门名称已存在");
        }
        return toAjax(orgService.insertOrg(org));
    }

    /**
     * 修改部门。
     *
     * @param org 部门参数
     * @return 操作结果
     */
    @SaCheckPermission("system:org:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
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
        return toAjax(orgService.updateOrg(org));
    }

    /**
     * 删除部门
     *
     * @param orgId 部门ID
     * @return 操作结果
     */
    @SaCheckPermission("system:org:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
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
        return toAjax(orgService.deleteOrgById(orgId));
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
    @RepeatSubmit()
    @PutMapping("/{orgId}/manages")
    public R<Void> updateManages(@PathVariable Long orgId, @RequestBody List<Long> orgIds) {
        orgService.checkOrgDataScope(orgId);
        orgService.updateManagedOrgs(orgId, orgIds == null ? List.of() : orgIds);
        return R.ok();
    }

    /**
     * 查询全部部门的被管数量（orgId -> 管辖的部门数），供列表展示管辖范围。
     *
     * @return 管辖方部门ID -> 被管部门数
     */
    @SaCheckPermission("system:org:query")
    @GetMapping("/manage-counts")
    public R<java.util.Map<Long, Long>> manageCounts() {
        return R.ok(orgService.selectManagedCounts());
    }

}
