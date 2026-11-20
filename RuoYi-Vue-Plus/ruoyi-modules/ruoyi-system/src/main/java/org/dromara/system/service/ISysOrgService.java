/*
 * 文件用途：承载织云 ERP 对上游框架“ISysOrgService”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“ISysOrgService”能力的项目扩展或适配。
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
package org.dromara.system.service;

import cn.hutool.core.lang.tree.Tree;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.vo.SysOrgVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 部门管理 服务层
 *
 * @author Lion Li
 */
public interface ISysOrgService {

    /**
     * 分页查询部门管理数据
     *
     * @param org      部门信息
     * @param pageQuery 分页对象
     * @return 部门信息集合
     */
    PageResult<SysOrgVo> selectPageOrgList(SysOrgBo org, PageQuery pageQuery);

    /**
     * 查询部门管理数据
     *
     * @param org 部门信息
     * @return 部门信息集合
     */
    List<SysOrgVo> selectOrgList(SysOrgBo org);

    /**
     * 查询部门树结构信息
     *
     * @param org 部门信息
     * @return 部门树信息集合
     */
    List<Tree<Long>> selectOrgTreeList(SysOrgBo org);

    /**
     * 构建前端所需要下拉树结构
     *
     * @param orgs 部门列表
     * @return 下拉树结构列表
     */
    List<Tree<Long>> buildOrgTreeSelect(List<SysOrgVo> orgs);

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    List<Long> selectOrgListByRoleId(Long roleId);

    /**
     * 根据部门ID查询信息
     *
     * @param orgId 部门ID
     * @return 部门信息
     */
    SysOrgVo selectOrgById(Long orgId);

    /**
     * 通过部门ID串查询部门
     *
     * @param orgIds 部门id串
     * @return 部门列表信息
     */
    List<SysOrgVo> selectOrgByIds(Collection<Long> orgIds);

    /**
     * 查询某部门管辖的被管部门ID列表
     *
     * @param orgId 管辖方部门ID
     * @return 被管部门ID列表
     */
    List<Long> selectManagedOrgIds(Long orgId);

    /**
     * 设置某部门管辖的被管部门（全量覆盖）
     *
     * @param orgId  管辖方部门ID
     * @param orgIds 被管部门ID列表（空=清空）
     */
    void updateManagedOrgs(Long orgId, List<Long> orgIds);

    /**
     * 查询全部部门的被管数量（orgId -> 被管部门数）
     *
     * @return 管辖方部门ID -> 被管部门数
     */
    Map<Long, Long> selectManagedCounts();

    /** 每个管辖方管了哪些被管组织（名称列表） */
    Map<Long, java.util.List<String>> selectManagedNames();

    /**
     * 根据ID查询所有子部门数（正常状态）
     *
     * @param orgId 部门ID
     * @return 子部门数
     */
    long selectNormalChildrenOrgById(Long orgId);

    /**
     * 是否存在部门子节点
     *
     * @param orgId 部门ID
     * @return 结果
     */
    boolean hasChildByOrgId(Long orgId);

    /**
     * 查询部门是否存在用户
     *
     * @param orgId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    boolean checkOrgExistUser(Long orgId);

    /**
     * 校验部门名称是否唯一
     *
     * @param org 部门信息
     * @return 结果
     */
    boolean checkOrgNameUnique(SysOrgBo org);

    /**
     * 校验部门是否有数据权限
     *
     * @param orgId 部门id
     */
    void checkOrgDataScope(Long orgId);

    /**
     * 新增保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    int insertOrg(SysOrgBo bo);

    /**
     * 修改保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    int updateOrg(SysOrgBo bo);

    /**
     * 删除部门管理信息
     *
     * @param orgId 部门ID
     * @return 结果
     */
    int deleteOrgById(Long orgId);
}
