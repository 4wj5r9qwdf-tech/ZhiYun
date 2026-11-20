/*
 * 文件用途：承载织云 ERP 对上游框架“SysUserMapper”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysUserMapper”能力的项目扩展或适配。
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
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.system.domain.SysOrg;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysUserExportVo;
import org.dromara.system.domain.vo.SysUserVo;

import java.util.List;

/**
 * 用户表 数据层
 *
 * @author Lion Li
 */
public interface SysUserMapper extends BaseMapperPlus<SysUser, SysUserVo>, MPJBaseMapper<SysUser> {

    /**
     * 分页查询用户列表，并进行数据权限控制
     *
     * @param page         分页参数
     * @param queryWrapper 查询条件
     * @return 分页的用户信息
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysUserVo> selectPageUserList(Page<SysUser> page, Wrapper<SysUser> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 查询用户列表，并进行数据权限控制
     *
     * @param queryWrapper 查询条件
     * @return 用户信息集合
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysUserVo> selectUserList(Wrapper<SysUser> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 根据条件分页查询用户列表
     *
     * @param user    查询条件
     * @param orgIds 部门ID集合
     * @return 用户信息集合信息
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "d.org_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default List<SysUserExportVo> selectUserExportList(SysUserBo user, List<Long> orgIds) {
        MPJLambdaWrapper<SysUser> wrapper = QueryBuilder.lambdaJoin("u", SysUser.class)
            .selectAll(SysUser.class)
            .selectAs("u1", SysUser::getUserName, SysUserExportVo::getLeaderName)
            .leftJoin(SysOrg.class, "d", SysOrg::getOrgId, SysUser::getOrgId)
            .leftJoin(SysUser.class, "u1", SysUser::getUserId, SysOrg::getLeader)
            .likeIfText("u", SysUser::getUserName, user.getUserName())
            .likeIfText("u", SysUser::getNickName, user.getNickName())
            .eqIfText("u", SysUser::getStatus, user.getStatus())
            .likeIfText("u", SysUser::getPhoneNumber, user.getPhoneNumber())
            .betweenParams("u", SysUser::getCreateTime, user.getParams(), "beginTime", "endTime")
            .inIfNotEmpty("u", SysUser::getOrgId, orgIds)
            .orderByAsc("u", SysUser::getUserId)
            .build();
        return this.selectJoinList(SysUserExportVo.class, wrapper);
    }

    /**
     * 根据条件分页查询已配用户角色列表
     *
     * @param page 分页信息
     * @param user 查询条件
     * @return 用户信息集合信息
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "d.org_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default Page<SysUserVo> selectAllocatedList(Page<SysUserVo> page, SysUserBo user) {
        MPJLambdaWrapper<SysUser> wrapper = this.buildUserRoleJoinWrapper(user)
            .eq(user.getRoleId() != null, "r", SysRole::getRoleId, user.getRoleId())
            .orderByAsc("u", SysUser::getUserId);
        return this.selectJoinPage(page, SysUserVo.class, wrapper);
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param page    分页信息
     * @param user    查询条件
     * @param userIds 未分配用户角色的用户ID列表
     * @return 用户信息集合信息
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "d.org_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default Page<SysUserVo> selectUnallocatedList(Page<SysUserVo> page, SysUserBo user, List<Long> userIds) {
        MPJLambdaWrapper<SysUser> wrapper = this.buildUserRoleJoinWrapper(user)
            .notIn(userIds != null && !userIds.isEmpty(), "u", SysUser::getUserId, userIds)
            .orderByAsc("u", SysUser::getUserId);
        return this.selectJoinPage(page, SysUserVo.class, wrapper);
    }

    /**
     * 根据用户ID统计用户数量
     *
     * @param userId 用户ID
     * @return 用户数量
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countUserById(Long userId) {
        return lambda().eq(SysUser::getUserId, userId).count();
    }

    /**
     * 根据条件更新用户数据
     *
     * @param user          要更新的用户实体
     * @param updateWrapper 更新条件封装器
     * @return 更新操作影响的行数
     */
    @Override
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int update(@Param(Constants.ENTITY) SysUser user, @Param(Constants.WRAPPER) Wrapper<SysUser> updateWrapper);

    /**
     * 根据用户ID更新用户数据
     *
     * @param user 要更新的用户实体
     * @return 更新操作影响的行数
     */
    @Override
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int updateById(@Param(Constants.ENTITY) SysUser user);

    /**
     * 构建用户与角色关联查询条件
     *
     * @param user 查询条件
     * @return 用户角色关联查询包装器
     */
    default MPJLambdaWrapper<SysUser> buildUserRoleJoinWrapper(SysUserBo user) {
        return QueryBuilder.lambdaJoin("u", SysUser.class)
            .distinct()
            .selectAll(SysUser.class)
            .leftJoin(SysOrg.class, "d", SysOrg::getOrgId, SysUser::getOrgId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getUserId, SysUser::getUserId)
            .leftJoin(SysRole.class, "r", SysRole::getRoleId, SysUserRole::getRoleId)
            .likeIfText("u", SysUser::getUserName, user.getUserName())
            .eqIfText("u", SysUser::getStatus, user.getStatus())
            .likeIfText("u", SysUser::getPhoneNumber, user.getPhoneNumber())
            .build();
    }

}
