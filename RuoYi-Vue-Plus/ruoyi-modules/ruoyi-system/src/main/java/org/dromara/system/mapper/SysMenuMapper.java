/*
 * 文件用途：承载织云 ERP 对上游框架“SysMenuMapper”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysMenuMapper”能力的项目扩展或适配。
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
package org.dromara.system.mapper;

import cn.hutool.core.collection.CollUtil;
import com.github.yulichang.base.MPJBaseMapper;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.system.domain.SysMenu;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysRoleMenu;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.bo.SysMenuBo;
import org.dromara.system.domain.vo.SysMenuVo;
import org.dromara.system.domain.vo.SysRoleMenuPermVo;

import java.util.*;

/**
 * 菜单表 数据层
 *
 * @author Lion Li
 */
public interface SysMenuMapper extends BaseMapperPlus<SysMenu, SysMenuVo>, MPJBaseMapper<SysMenu> {

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    default Set<String> selectMenuPermsByUserId(Long userId) {
        List<SysMenu> list = this.selectJoinList(SysMenu.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .select(SysMenu::getPerms)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRoleMenu::getRoleId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            // 停用菜单不再产生接口权限，避免仅隐藏权限树而保留实际授权。
            .eq("m", SysMenu::getStatus, SystemConstants.NORMAL)
            .isNotNull("m", SysMenu::getPerms)
            .build());
        return new HashSet<>(StreamUtils.filter(StreamUtils.toList(list, SysMenu::getPerms), StringUtils::isNotBlank));
    }

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    default Set<String> selectMenuPermsByRoleId(Long roleId) {
        List<SysMenu> list = this.selectJoinList(SysMenu.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .select(SysMenu::getPerms)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("srm", SysRoleMenu::getRoleId, roleId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            // 停用菜单不再产生接口权限，避免仅隐藏权限树而保留实际授权。
            .eq("m", SysMenu::getStatus, SystemConstants.NORMAL)
            .isNotNull("m", SysMenu::getPerms)
            .build());
        return new HashSet<>(StreamUtils.filter(StreamUtils.toList(list, SysMenu::getPerms), StringUtils::isNotBlank));
    }

    /**
     * 根据角色ID列表批量查询权限
     *
     * @param roleIds 角色ID列表
     * @return 角色权限映射
     */
    default Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return Map.of();
        }
        List<SysRoleMenuPermVo> list = this.selectJoinList(SysRoleMenuPermVo.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAs("srm", SysRoleMenu::getRoleId, SysRoleMenuPermVo::getRoleId)
            .selectAs(SysMenu::getPerms, SysRoleMenuPermVo::getPerms)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .in("srm", SysRoleMenu::getRoleId, roleIds)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            // 停用菜单不再产生接口权限，避免仅隐藏权限树而保留实际授权。
            .eq("m", SysMenu::getStatus, SystemConstants.NORMAL)
            .isNotNull("m", SysMenu::getPerms)
            .build());
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (SysRoleMenuPermVo item : list) {
            if (StringUtils.isBlank(item.getPerms())) {
                continue;
            }
            result.computeIfAbsent(item.getRoleId(), key -> new LinkedHashSet<>()).add(item.getPerms());
        }
        return result;
    }

    /**
     * 查询全部正常状态的目录和菜单
     *
     * @return 菜单列表
     */
    default List<SysMenu> selectMenuTreeAll() {
        return this.lambda()
            .in(SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
            .eq(SysMenu::getStatus, SystemConstants.NORMAL)
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum)
            .list();
    }

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId            角色ID
     * @param menuCheckStrictly 菜单树选择项是否关联显示
     * @return 选中菜单列表
     */
    default List<Long> selectMenuListByRoleId(Long roleId, boolean menuCheckStrictly) {
        List<SysMenu> menus = this.selectJoinList(SysMenu.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .select(SysMenu::getMenuId, SysMenu::getParentId, SysMenu::getOrderNum)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("srm", SysRoleMenu::getRoleId, roleId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
        Set<Long> parentIds = menuCheckStrictly ? new HashSet<>(StreamUtils.toList(menus, SysMenu::getParentId)) : Collections.emptySet();
        return menus.stream()
            .map(SysMenu::getMenuId)
            .filter(menuId -> !parentIds.contains(menuId))
            .toList();
    }

    /**
     * 根据用户ID和查询条件查询菜单列表
     *
     * @param menu   菜单查询条件
     * @param userId 用户ID
     * @return 菜单列表
     */
    default List<SysMenuVo> selectMenuListByUserId(SysMenuBo menu, Long userId) {
        return this.selectJoinList(SysMenuVo.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAll(SysMenu.class)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRoleMenu::getRoleId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            .likeIfText("m", SysMenu::getMenuName, menu.getMenuName())
            .eqIfText("m", SysMenu::getVisible, menu.getVisible())
            .eqIfText("m", SysMenu::getStatus, menu.getStatus())
            .eqIfText("m", SysMenu::getMenuType, menu.getMenuType())
            .eqIfPresent("m", SysMenu::getParentId, menu.getParentId())
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
    }

    /**
     * 根据用户ID查询正常状态的目录和菜单
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    default List<SysMenu> selectMenuTreeByUserId(Long userId) {
        return this.selectJoinList(SysMenu.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAll(SysMenu.class)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRoleMenu::getRoleId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            .in("m", SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
            .eq("m", SysMenu::getStatus, SystemConstants.NORMAL)
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
    }

}
