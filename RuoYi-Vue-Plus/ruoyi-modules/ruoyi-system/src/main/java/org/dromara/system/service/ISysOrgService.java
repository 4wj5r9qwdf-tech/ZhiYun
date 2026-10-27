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
