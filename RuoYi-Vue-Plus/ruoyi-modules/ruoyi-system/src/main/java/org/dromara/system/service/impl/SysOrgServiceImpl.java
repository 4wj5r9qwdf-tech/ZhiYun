package org.dromara.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.*;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.LambdaQueryBuilder;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.OrgService;
import org.dromara.system.api.domain.OrgDTO;
import org.dromara.system.domain.SysOrg;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.vo.SysOrgVo;
import org.dromara.system.mapper.SysOrgMapper;
import org.dromara.system.mapper.SysOrgManageMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysOrgService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 部门管理 服务实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysOrgServiceImpl implements ISysOrgService, OrgService {

    private final SysOrgMapper orgMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysOrgManageMapper orgManageMapper;

    /**
     * 分页查询部门管理数据
     *
     * @param org      部门信息
     * @param pageQuery 分页对象
     * @return 部门信息集合
     */
    @Override
    public PageResult<SysOrgVo> selectPageOrgList(SysOrgBo org, PageQuery pageQuery) {
        Page<SysOrgVo> page = orgMapper.selectPageOrgList(pageQuery.build(), buildQueryWrapper(org));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 查询部门管理数据
     *
     * @param org 部门信息
     * @return 部门信息集合
     */
    @Override
    public List<SysOrgVo> selectOrgList(SysOrgBo org) {
        LambdaQueryWrapper<SysOrg> lqw = buildQueryWrapper(org);
        return orgMapper.selectOrgList(lqw);
    }

    /**
     * 查询部门树结构信息
     *
     * @param bo 部门信息
     * @return 部门树信息集合
     */
    @Override
    public List<Tree<Long>> selectOrgTreeList(SysOrgBo bo) {
        LambdaQueryWrapper<SysOrg> lqw = buildQueryWrapper(bo);
        List<SysOrgVo> orgs = orgMapper.selectOrgList(lqw);
        return buildOrgTreeSelect(orgs);
    }

    /**
     * 构造部门列表查询条件。
     *
     * @param bo 部门筛选条件
     * @return 包含树级过滤、状态、分类和时间区间的查询包装器
     */
    private LambdaQueryWrapper<SysOrg> buildQueryWrapper(SysOrgBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryBuilder<SysOrg> builder = QueryBuilder.lambda(SysOrg.class)
            .eqIfPresent(SysOrg::getOrgId, bo.getOrgId())
            .eqIfPresent(SysOrg::getParentId, bo.getParentId())
            .likeIfText(SysOrg::getOrgName, bo.getOrgName())
            .likeIfText(SysOrg::getOrgCategory, bo.getOrgCategory())
            .eqIfText(SysOrg::getStatus, bo.getStatus())
            .betweenParams(SysOrg::getCreateTime, params, "beginTime", "endTime")
            .orderByAsc(SysOrg::getAncestors, SysOrg::getParentId, SysOrg::getOrderNum, SysOrg::getOrgId);
        if (ObjectUtil.isNotNull(bo.getBelongOrgId())) {
            //部门树搜索
            builder.and(x -> {
                List<Long> orgIds = orgMapper.selectOrgAndChildById(bo.getBelongOrgId());
                x.in(SysOrg::getOrgId, orgIds);
            });
        }
        return builder.build();
    }

    /**
     * 构建前端所需要下拉树结构
     *
     * @param orgs 部门列表
     * @return 下拉树结构列表
     */
    @Override
    public List<Tree<Long>> buildOrgTreeSelect(List<SysOrgVo> orgs) {
        if (CollUtil.isEmpty(orgs)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.buildMultiRoot(
            orgs,
            SysOrgVo::getOrgId,
            SysOrgVo::getParentId,
            (node, treeNode) -> treeNode
                .setId(node.getOrgId())
                .setParentId(node.getParentId())
                .setName(node.getOrgName())
                .setWeight(node.getOrderNum())
                .putExtra("disabled", SystemConstants.DISABLE.equals(node.getStatus()))
        );
    }

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    @Override
    public List<Long> selectOrgListByRoleId(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return orgMapper.selectOrgListByRoleId(roleId, role.getOrgCheckStrictly());
    }

    /**
     * 根据部门ID查询信息
     *
     * @param orgId 部门ID
     * @return 部门信息
     */
    @Cacheable(cacheNames = CacheNames.SYS_ORG, key = "#orgId")
    @Override
    public SysOrgVo selectOrgById(Long orgId) {
        SysOrgVo org = orgMapper.selectVoById(orgId);
        if (ObjectUtil.isNull(org)) {
            return null;
        }
        SysOrgVo parentOrg = orgMapper.lambda()
            .select(SysOrg::getOrgName)
            .eq(SysOrg::getOrgId, org.getParentId())
            .voOne();
        org.setParentName(ObjectUtils.notNullGetter(parentOrg, SysOrgVo::getOrgName));
        return org;
    }

    /**
     * 按部门主键集合查询部门基础信息。
     *
     * @param orgIds 部门主键集合
     * @return 部门基础信息列表
     */
    @Override
    public List<SysOrgVo> selectOrgByIds(Collection<Long> orgIds) {
        return orgMapper.selectOrgList(orgMapper.lambda()
            .select(SysOrg::getOrgId, SysOrg::getOrgName, SysOrg::getLeader)
            .eq(SysOrg::getStatus, SystemConstants.NORMAL)
            .in(CollUtil.isNotEmpty(orgIds), SysOrg::getOrgId, orgIds)
            .build());
    }

    /**
     * 通过部门ID查询部门名称
     *
     * @param orgIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    @Override
    public String selectOrgNameByIds(String orgIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(orgIds, Convert::toLong)) {
            SysOrgVo vo = SpringUtils.getAopProxy(this).selectOrgById(id);
            if (ObjectUtil.isNotNull(vo)) {
                list.add(vo.getOrgName());
            }
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 根据部门ID查询部门负责人
     *
     * @param orgId 部门ID，用于指定需要查询的部门
     * @return 返回该部门的负责人ID
     */
    @Override
    public Long selectOrgLeaderById(Long orgId) {
        SysOrgVo vo = SpringUtils.getAopProxy(this).selectOrgById(orgId);
        return ObjectUtil.isNull(vo) ? null : vo.getLeader();
    }

    /**
     * 查询部门
     *
     * @return 部门列表
     */
    @Override
    public List<OrgDTO> selectOrgsByList() {
        List<SysOrgVo> list = orgMapper.selectOrgList(orgMapper.lambda()
            .select(SysOrg::getOrgId, SysOrg::getOrgName, SysOrg::getParentId)
            .eq(SysOrg::getStatus, SystemConstants.NORMAL)
            .build());
        return BeanUtil.copyToList(list, OrgDTO.class);
    }

    /**
     * 根据ID查询所有子部门数（正常状态）
     *
     * @param orgId 部门ID
     * @return 子部门数
     */
    @Override
    public long selectNormalChildrenOrgById(Long orgId) {
        return orgMapper.lambda()
            .eq(SysOrg::getStatus, SystemConstants.NORMAL)
            .findInSet(orgId, SysOrg::getAncestors)
            .count();
    }

    /**
     * 是否存在子节点
     *
     * @param orgId 部门ID
     * @return 结果
     */
    @Override
    public boolean hasChildByOrgId(Long orgId) {
        return orgMapper.lambda().eq(SysOrg::getParentId, orgId).exists();
    }

    /**
     * 查询部门是否存在用户
     *
     * @param orgId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkOrgExistUser(Long orgId) {
        return userMapper.lambda().eq(SysUser::getOrgId, orgId).exists();
    }

    /**
     * 校验部门名称是否唯一
     *
     * @param org 部门信息
     * @return 结果
     */
    @Override
    public boolean checkOrgNameUnique(SysOrgBo org) {
        boolean exist = orgMapper.lambda()
            .eq(SysOrg::getOrgName, org.getOrgName())
            .eq(SysOrg::getParentId, org.getParentId())
            .neIfPresent(SysOrg::getOrgId, org.getOrgId())
            .exists();
        return !exist;
    }

    /**
     * 校验部门是否有数据权限
     *
     * @param orgId 部门id
     */
    @Override
    public void checkOrgDataScope(Long orgId) {
        if (ObjectUtil.isNull(orgId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        if (orgMapper.countOrgById(orgId) == 0) {
            throw new ServiceException("没有权限访问部门数据！");
        }
    }

    /**
     * 新增保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ORG_AND_CHILD, allEntries = true)
    @Override
    public int insertOrg(SysOrgBo bo) {
        SysOrg org = MapstructUtils.convert(bo, SysOrg.class);
        Long parentId = bo.getParentId();
        // 顶层部门：parentId 为 null 或 0 → 无需父部门，ancestors="0"
        if (parentId == null || parentId == 0L) {
            org.setParentId(0L);
            org.setAncestors("0");
            return orgMapper.insert(org);
        }
        SysOrg info = orgMapper.selectById(parentId);
        // 如果父节点不存在或不为正常状态,则不允许新增子节点
        if (ObjectUtil.isNull(info)) {
            throw new ServiceException("父部门不存在");
        }
        if (!SystemConstants.NORMAL.equals(info.getStatus())) {
            throw new ServiceException("部门停用，不允许新增");
        }
        org.setAncestors(info.getAncestors() + StringUtils.SEPARATOR + org.getParentId());
        return orgMapper.insert(org);
    }

    /**
     * 修改保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_ORG, key = "#bo.orgId"),
        @CacheEvict(cacheNames = CacheNames.SYS_ORG_AND_CHILD, allEntries = true)
    })
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrg(SysOrgBo bo) {
        SysOrg org = MapstructUtils.convert(bo, SysOrg.class);
        SysOrg oldOrg = orgMapper.selectById(org.getOrgId());
        if (ObjectUtil.isNull(oldOrg)) {
            throw new ServiceException("部门不存在，无法修改");
        }
        if (!oldOrg.getParentId().equals(org.getParentId())) {
            Long newParentId = org.getParentId();
            if (newParentId == null || newParentId == 0L) {
                // 移到顶层：ancestors 重置为 "0"
                String oldAncestors = oldOrg.getAncestors();
                org.setParentId(0L);
                org.setAncestors("0");
                updateOrgChildren(org.getOrgId(), "0", oldAncestors);
            } else {
                // 如果是新父部门 则校验是否具有新父部门权限 避免越权
                this.checkOrgDataScope(newParentId);
                SysOrg newParentOrg = orgMapper.selectById(newParentId);
                if (ObjectUtil.isNotNull(newParentOrg)) {
                    String newAncestors = newParentOrg.getAncestors() + StringUtils.SEPARATOR + newParentOrg.getOrgId();
                    String oldAncestors = oldOrg.getAncestors();
                    org.setAncestors(newAncestors);
                    updateOrgChildren(org.getOrgId(), newAncestors, oldAncestors);
                }
            }
        } else {
            org.setAncestors(oldOrg.getAncestors());
        }
        int result = orgMapper.updateById(org);
        // 如果部门状态为启用，且部门祖级列表不为空，且部门祖级列表不等于根部门祖级列表（如果部门祖级列表不等于根部门祖级列表，则说明存在上级部门）
        if (SystemConstants.NORMAL.equals(org.getStatus())
            && StringUtils.isNotEmpty(org.getAncestors())
            && !StringUtils.equals(SystemConstants.ROOT_ORG_ANCESTORS, org.getAncestors())) {
            // 如果该部门是启用状态，则启用该部门的所有上级部门
            updateParentOrgStatusNormal(org);
        }
        return result;
    }

    /**
     * 修改该部门的父级部门状态
     *
     * @param org 当前部门
     */
    private void updateParentOrgStatusNormal(SysOrg org) {
        String ancestors = org.getAncestors();
        Long[] orgIds = Convert.toLongArray(ancestors);
        orgMapper.lambda()
            .set(SysOrg::getStatus, SystemConstants.NORMAL)
            .in(SysOrg::getOrgId, Arrays.asList(orgIds))
            .update();
    }

    /**
     * 修改子元素关系
     *
     * @param orgId       被修改的部门ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    private void updateOrgChildren(Long orgId, String newAncestors, String oldAncestors) {
        List<SysOrg> children = orgMapper.lambda().findInSet(orgId, SysOrg::getAncestors).list();
        List<SysOrg> list = new ArrayList<>();
        for (SysOrg child : children) {
            SysOrg org = new SysOrg();
            org.setOrgId(child.getOrgId());
            org.setAncestors(StringUtils.replaceOnce(child.getAncestors(), oldAncestors, newAncestors));
            list.add(org);
        }
        if (CollUtil.isNotEmpty(list)) {
            if (orgMapper.updateBatchById(list)) {
                list.forEach(org -> CacheUtils.evict(CacheNames.SYS_ORG, org.getOrgId()));
            }
        }
    }

    /**
     * 删除部门管理信息
     *
     * @param orgId 部门ID
     * @return 结果
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_ORG, key = "#orgId"),
        @CacheEvict(cacheNames = CacheNames.SYS_ORG_AND_CHILD, allEntries = true)
    })
    @Override
    public int deleteOrgById(Long orgId) {
        return orgMapper.deleteById(orgId);
    }


    /**
     * 根据部门 ID 列表查询部门名称映射关系
     *
     * @param orgIds 部门 ID 列表
     * @return Map，其中 key 为部门 ID，value 为对应的部门名称
     */
    @Override
    public Map<Long, String> selectOrgNamesByIds(Collection<Long> orgIds) {
        if (CollUtil.isEmpty(orgIds)) {
            return Collections.emptyMap();
        }
        List<SysOrg> list = orgMapper.lambda()
            .select(SysOrg::getOrgId, SysOrg::getOrgName)
            .in(SysOrg::getOrgId, orgIds)
            .list();
        return StreamUtils.toMap(list, SysOrg::getOrgId, SysOrg::getOrgName);
    }

    // ==================== 业务管辖关联（管理部门↔被管部门） ====================

    @Override
    public List<Long> selectManagedOrgIds(Long orgId) {
        return orgManageMapper.selectManagedIds(orgId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateManagedOrgs(Long orgId, List<Long> orgIds) {
        orgManageMapper.deleteByManageOrgId(orgId);
        if (orgIds != null) {
            for (Long id : orgIds) {
                if (id != null && !id.equals(orgId)) {  // 不能管自己
                    orgManageMapper.insertManage(orgId, id);
                }
            }
        }
    }

    @Override
    public Map<Long, Long> selectManagedCounts() {
        List<Map<String, Object>> rows = orgManageMapper.selectManagedCounts();
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            map.put(Convert.toLong(r.get("manageOrgId")), Convert.toLong(r.get("cnt")));
        }
        return map;
    }

}
