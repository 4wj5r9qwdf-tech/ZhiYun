package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.bo.SysRoleBo;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.event.OnlineUserCleanEvent;
import org.dromara.system.service.ISysOrgService;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * 角色信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/role")
public class SysRoleController extends BaseController {

    private final org.springframework.jdbc.core.JdbcTemplate auditJdbc;
    private final ISysRoleService roleService;
    private final ISysUserService userService;
    private final ISysOrgService orgService;

    /**
     * 分页查询角色列表。
     *
     * @param role      查询条件
     * @param pageQuery 分页参数
     * @return 角色分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public R<PageResult<SysRoleVo>> list(SysRoleBo role, PageQuery pageQuery) {
        return R.ok(roleService.selectPageRoleList(role, pageQuery));
    }

    /**
     * 导出角色信息列表。
     *
     * @param role     查询条件
     * @param response HTTP 响应
     */
    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:role:export")
    @PostMapping("/export")
    public void export(SysRoleBo role, HttpServletResponse response) {
        List<SysRoleVo> list = roleService.selectRoleList(role);
        ExcelBuilder.of(list, SysRoleVo.class).sheetName("角色数据").toResponse(response);
    }

    /**
     * 根据角色编号获取详细信息
     *
     * @param roleId 角色ID
     * @return 角色详情
     */
    @SaCheckPermission("system:role:query")
    @GetMapping(value = "/{roleId}")
    public R<SysRoleVo> getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return R.ok(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        int auditResult = roleService.insertRole(role);
        if (auditResult > 0) BusinessAudit.record("新增角色",role.getRoleId(),"名称",java.util.Map.of(),auditSnapshot(role.getRoleId(),false));
        return toAjax(auditResult);

    }

    /**
     * 修改角色基础信息（不包含菜单权限、数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }

        var auditBefore = auditSnapshot(role.getRoleId(),true);
        if (roleService.updateRoleBaseInfo(role) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
            BusinessAudit.record("修改角色",role.getRoleId(),"名称",auditBefore,auditSnapshot(role.getRoleId(),false));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改角色权限信息（菜单权限 + 数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/permission")
    public R<Void> editPermission(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        var auditBefore = auditSnapshot(role.getRoleId(),true);
        if (roleService.updateRolePermission(role) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
            BusinessAudit.record("调整角色权限",role.getRoleId(),"名称",auditBefore,auditSnapshot(role.getRoleId(),false));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'权限失败，请联系管理员");
    }

    /**
     * 修改角色状态。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        var auditBefore = auditSnapshot(role.getRoleId(),true);
        if (roleService.updateRoleStatus(role.getRoleId(), role.getStatus()) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
            BusinessAudit.record("变更角色状态",role.getRoleId(),"名称",auditBefore,auditSnapshot(role.getRoleId(),false));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'状态失败，请联系管理员");
    }

    /**
     * 删除角色
     *
     * @param roleIds 角色ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{roleIds}")
    public R<Void> remove(@PathVariable Long[] roleIds) {
        var before = new java.util.LinkedHashMap<String,Object>();
        for (Long id : java.util.Arrays.stream(roleIds).distinct().sorted().toList()) {
            var row = auditSnapshot(id,true);
            if (!row.isEmpty()) before.put(id.toString(),row);
        }
        int affected = roleService.deleteRoleByIds(List.of(roleIds));
        if (affected > 0) {
            var after = new java.util.LinkedHashMap<String,Object>();
            for (Long id : roleIds) {
                var row = auditSnapshot(id,false);
                if (!row.isEmpty()) after.put(id.toString(),row);
            }
            BusinessAudit.record("删除角色",before.keySet(),"名称",before,after);
        }
        return toAjax(affected);
    }

    /**
     * 获取角色选择框列表
     *
     * @param roleIds 角色ID串
     * @return 角色列表
     */
    @SaCheckPermission("system:role:query")
    @GetMapping("/optionselect")
    public R<List<SysRoleVo>> optionselect(@RequestParam(required = false) Long[] roleIds) {
        return R.ok(roleService.selectRoleByIds(roleIds == null ? null : List.of(roleIds)));
    }

    /**
     * 查询已分配用户角色列表。
     *
     * @param user      查询条件
     * @param pageQuery 分页参数
     * @return 用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/allocatedList")
    public R<PageResult<SysUserVo>> allocatedList(SysUserBo user, PageQuery pageQuery) {
        return R.ok(userService.selectAllocatedList(user, pageQuery));
    }

    /**
     * 查询未分配用户角色列表。
     *
     * @param user      查询条件
     * @param pageQuery 分页参数
     * @return 用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/unallocatedList")
    public R<PageResult<SysUserVo>> unallocatedList(SysUserBo user, PageQuery pageQuery) {
        return R.ok(userService.selectUnallocatedList(user, pageQuery));
    }

    /**
     * 取消授权用户。
     *
     * @param userRole 用户角色关系
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/authUser/cancel")
    public R<Void> cancelAuthUser(@RequestBody SysUserRole userRole) {
        var before = auditSnapshot(userRole.getRoleId(),true);
        int affected = roleService.deleteAuthUser(userRole);
        if (affected > 0) BusinessAudit.record("取消人员授权",userRole.getRoleId(),"名称",before,auditSnapshot(userRole.getRoleId(),false));
        return toAjax(affected);
    }

    /**
     * 批量取消授权用户
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/authUser/cancelAll")
    public R<Void> cancelAuthUserAll(Long roleId, Long[] userIds) {
        var before = auditSnapshot(roleId,true);
        int affected = roleService.deleteAuthUsers(roleId, List.of(userIds));
        if (affected > 0) BusinessAudit.record("批量取消人员授权",roleId,"名称",before,auditSnapshot(roleId,false));
        return toAjax(affected);
    }

    /**
     * 批量选择用户授权
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping("/authUser/selectAll")
    public R<Void> selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        var before = auditSnapshot(roleId,true);
        int affected = roleService.insertAuthUsers(roleId, List.of(userIds));
        if (affected > 0) BusinessAudit.record("批量授予人员角色",roleId,"名称",before,auditSnapshot(roleId,false));
        return toAjax(affected);
    }

    /**
     * 获取对应角色部门树列表
     *
     * @param roleId 角色ID
     * @return 角色部门树信息
     */
    @SaCheckPermission("system:role:list")
    @GetMapping(value = "/orgTree/{roleId}")
    public R<OrgTreeSelectVo> roleOrgTreeselect(@PathVariable("roleId") Long roleId) {
        OrgTreeSelectVo selectVo = new OrgTreeSelectVo(
            orgService.selectOrgListByRoleId(roleId),
            orgService.selectOrgTreeList(new SysOrgBo()));
        return R.ok(selectVo);
    }

    /**
     * 角色部门列表树信息
     *
     * @param checkedKeys 选中部门列表
     * @param orgs       下拉树结构列表
     */
    public record OrgTreeSelectVo(Collection<Long> checkedKeys, List<Tree<Long>> orgs) {
    }

    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(auditJdbc,"SELECT role_name AS \"名称\", role_key AS \"角色编码\", role_sort AS \"排序\", data_scope AS \"数据范围\", menu_check_strictly AS \"菜单联动\", org_check_strictly AS \"组织联动\", status AS \"状态\", del_flag AS \"删除标记\", remark AS \"备注\" FROM sys_role WHERE role_id=?"+(lock ? " FOR UPDATE" : ""),id);
        if (!row.isEmpty()) row.put("菜单权限 ID",auditJdbc.queryForList("SELECT menu_id::text FROM sys_role_menu WHERE role_id=? ORDER BY menu_id",String.class,id));
        if (!row.isEmpty()) row.put("数据范围组织 ID",auditJdbc.queryForList("SELECT org_id::text FROM sys_role_org WHERE role_id=? ORDER BY org_id",String.class,id));
        if (!row.isEmpty()) row.put("授权人员 ID",auditJdbc.queryForList("SELECT user_id::text FROM sys_user_role WHERE role_id=? ORDER BY user_id",String.class,id));
        return row;
    }
}
