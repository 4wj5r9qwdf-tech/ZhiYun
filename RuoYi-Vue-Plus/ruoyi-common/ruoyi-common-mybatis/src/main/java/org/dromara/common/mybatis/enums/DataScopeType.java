/*
 * 文件用途：承载织云 ERP 对上游框架“DataScopeType”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“DataScopeType”能力的项目扩展或适配。
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
package org.dromara.common.mybatis.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.system.api.model.LoginUser;

/**
 * 数据权限类型枚举
 * <p>
 * 支持使用 SpEL 模板表达式定义 SQL 查询条件
 * 内置数据：
 * - {@code user}: 当前登录用户信息，参考 {@link LoginUser}
 * 内置服务：
 * - {@code sdss}: 系统数据权限服务，参考 ISysDataScopeService
 * 如需扩展数据，可以通过 {@link DataPermissionHelper} 进行操作
 * 如需扩展服务，可以通过 ISysDataScopeService 自行编写
 * </p>
 *
 * @author Lion Li
 * @version 3.5.0
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {

    /**
     * 全部数据权限
     */
    ALL("1", "", ""),

    /**
     * 自定数据权限
     */
    CUSTOM("2", " #{#orgName} IN ( #{@sdss.getRoleCustom( #roleId )} ) ", " 1 = 0 "),

    /**
     * 部门数据权限
     */
    ORG("3", " #{#orgName} = #{#user.orgId} ", " 1 = 0 "),

    /**
     * 部门及以下数据权限
     */
    ORG_AND_CHILD("4", " #{#orgName} IN ( #{@sdss.getOrgAndChild( #user.orgId )} )", " 1 = 0 "),

    /**
     * 仅本人数据权限
     */
    SELF("5", " #{#userName} = #{#user.userId} ", " 1 = 0 "),

    /**
     * 部门及以下或本人数据权限
     */
    ORG_AND_CHILD_OR_SELF("6", " #{#orgName} IN ( #{@sdss.getOrgAndChild( #user.orgId )} ) OR #{#userName} = #{#user.userId} ", " 1 = 0 ");

    /**
     * 数据权限类型编码。
     */
    private final String code;

    /**
     * SpEL 模板表达式，用于构建 SQL 查询条件
     */
    private final String sqlTemplate;

    /**
     * 如果不满足 {@code sqlTemplate} 的条件，则使用此默认 SQL 表达式
     */
    private final String elseSql;

    /**
     * 根据枚举代码查找对应的枚举值
     *
     * @param code 枚举代码
     * @return 对应的枚举值，如果未找到则返回 null
     */
    public static DataScopeType findCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (DataScopeType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
