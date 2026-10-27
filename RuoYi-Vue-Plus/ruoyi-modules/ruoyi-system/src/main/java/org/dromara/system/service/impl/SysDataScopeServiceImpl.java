package org.dromara.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.system.domain.SysRoleOrg;
import org.dromara.system.mapper.SysOrgMapper;
import org.dromara.system.mapper.SysRoleOrgMapper;
import org.dromara.system.service.ISysDataScopeService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据权限 实现
 * <p>
 * 注意: 此Service内不允许调用标注`数据权限`注解的方法
 * 例如: orgMapper.selectList 此 selectList 方法标注了`数据权限`注解 会出现循环解析的问题
 * 当前实现仅负责返回角色自定义部门范围以及部门树展开后的 id 串，供数据权限插件拼装 SQL 时使用。
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service("sdss")
public class SysDataScopeServiceImpl implements ISysDataScopeService {

    private final SysRoleOrgMapper roleOrgMapper;
    private final SysOrgMapper orgMapper;

    /**
     * 获取角色自定义权限
     *
     * @param roleId 角色Id
     * @return 逗号分隔的部门 id 字符串，未配置时返回 `-1`
     */
    @Cacheable(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#roleId", condition = "#roleId != null")
    @Override
    public String getRoleCustom(Long roleId) {
        if (ObjectUtil.isNull(roleId)) {
            return "-1";
        }
        List<SysRoleOrg> list = roleOrgMapper.lambda()
            .select(SysRoleOrg::getOrgId)
            .eq(SysRoleOrg::getRoleId, roleId)
            .list();
        if (CollUtil.isNotEmpty(list)) {
            return StreamUtils.join(list, rd -> Convert.toStr(rd.getOrgId()));
        }
        return "-1";
    }

    /**
     * 获取部门及以下权限
     *
     * @param orgId 部门Id
     * @return 当前部门及其子部门的逗号分隔 id 字符串，未查询到时返回 `-1`
     */
    @Cacheable(cacheNames = CacheNames.SYS_ORG_AND_CHILD, key = "#orgId", condition = "#orgId != null")
    @Override
    public String getOrgAndChild(Long orgId) {
        if (ObjectUtil.isNull(orgId)) {
            return "-1";
        }
        List<Long> orgIds = orgMapper.selectOrgAndChildById(orgId);
        return CollUtil.isNotEmpty(orgIds) ? StreamUtils.join(orgIds, Convert::toStr) : "-1";
    }

}
