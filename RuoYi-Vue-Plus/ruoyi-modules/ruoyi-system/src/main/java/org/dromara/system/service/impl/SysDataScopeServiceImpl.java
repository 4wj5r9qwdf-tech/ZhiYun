/*
 * 文件用途：承载织云 ERP 对上游框架“SysDataScopeServiceImpl”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysDataScopeServiceImpl”能力的项目扩展或适配。
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
