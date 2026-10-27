package org.dromara.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.system.domain.SysOrg;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysRoleOrg;
import org.dromara.system.domain.vo.SysOrgVo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.dromara.common.core.constant.SystemConstants.NORMAL;

/**
 * 部门管理 数据层
 *
 * @author Lion Li
 */
public interface SysOrgMapper extends BaseMapperPlus<SysOrg, SysOrgVo>, MPJBaseMapper<SysOrg> {

    /**
     * 查询部门管理数据
     *
     * @param queryWrapper 查询条件
     * @return 部门信息集合
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id")
    })
    default List<SysOrgVo> selectOrgList(Wrapper<SysOrg> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 分页查询部门管理数据
     *
     * @param page         分页信息
     * @param queryWrapper 查询条件
     * @return 部门信息集合
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
    })
    default Page<SysOrgVo> selectPageOrgList(Page<SysOrg> page, Wrapper<SysOrg> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 统计指定部门ID的部门数量
     *
     * @param orgId 部门ID
     * @return 该部门ID的部门数量
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id")
    })
    default long countOrgById(Long orgId) {
        return this.lambda().eq(SysOrg::getOrgId, orgId).count();
    }

    /**
     * 根据父部门ID查询其所有子部门的列表
     *
     * @param parentId 父部门ID
     * @return 包含子部门的列表
     */
    default List<SysOrg> selectListByParentId(Long parentId) {
        return this.lambda()
            .select(SysOrg::getOrgId)
            .findInSet(parentId, SysOrg::getAncestors)
            .list();
    }

    /**
     * 查询某个部门及其所有子部门ID（含自身）
     *
     * @param parentId 父部门ID
     * @return 部门ID集合
     */
    default List<Long> selectOrgAndChildById(Long parentId) {
        List<SysOrg> orgList = this.selectListByParentId(parentId);
        List<Long> orgIds = StreamUtils.toList(orgList, SysOrg::getOrgId);
        orgIds.add(parentId);
        return orgIds;
    }

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId            角色ID
     * @param orgCheckStrictly 部门树选择项是否关联显示
     * @return 选中部门列表
     */
    default List<Long> selectOrgListByRoleId(Long roleId, boolean orgCheckStrictly) {
        List<SysOrg> orgs = this.selectJoinList(SysOrg.class, QueryBuilder.lambdaJoin("d", SysOrg.class)
            .distinct()
            .select(SysOrg::getOrgId, SysOrg::getParentId, SysOrg::getOrderNum)
            .leftJoin(SysRoleOrg.class, "srd", SysRoleOrg::getOrgId, SysOrg::getOrgId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleOrg::getRoleId)
            .eq("srd", SysRoleOrg::getRoleId, roleId)
            .eq("sr", SysRole::getStatus, NORMAL)
            .orderByAsc("d", SysOrg::getParentId)
            .orderByAsc("d", SysOrg::getOrderNum)
            .build());
        Set<Long> parentIds = orgCheckStrictly ? new HashSet<>(StreamUtils.toList(orgs, SysOrg::getParentId)) : Collections.emptySet();
        return orgs.stream()
            .map(SysOrg::getOrgId)
            .filter(orgId -> !parentIds.contains(orgId))
            .toList();
    }

}
