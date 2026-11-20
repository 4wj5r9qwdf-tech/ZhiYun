/*
 * 文件用途：承载织云 ERP 对上游框架“SysOrgMapper”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysOrgMapper”能力的项目扩展或适配。
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
