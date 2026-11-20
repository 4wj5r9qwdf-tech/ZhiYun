/*
 * 文件用途：承载织云 ERP 对上游框架“TaskAssigneeEnum”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“TaskAssigneeEnum”能力的项目扩展或适配。
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
package org.dromara.workflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务分配人枚举
 *
 * @author AprilWind
 */
@Getter
@AllArgsConstructor
public enum TaskAssigneeEnum {

    /**
     * 用户
     */
    USER("用户", ""),

    /**
     * 角色
     */
    ROLE("角色", "role:"),

    /**
     * 部门
     */
    ORG("部门", "org:"),

    /**
     * 岗位
     */
    POST("岗位", "post:"),

    /**
     * SPEL表达式
     */
    SPEL("SpEL表达式", "");

    /**
     * 类型描述。
     */
    private final String desc;

    /**
     * 类型编码前缀。
     */
    private final String code;

    /**
     * 根据描述获取对应的枚举类型
     * <p>
     * 通过传入描述，查找并返回匹配的枚举项。如果未找到匹配项，会抛出 {@link ServiceException}。
     * </p>
     *
     * @param desc 描述，用于匹配对应的枚举项
     * @return TaskAssigneeEnum 返回对应的枚举类型
     * @throws ServiceException 如果未找到匹配的枚举项
     */
    public static TaskAssigneeEnum fromDesc(String desc) {
        for (TaskAssigneeEnum type : values()) {
            if (type.getDesc().equals(desc)) {
                return type;
            }
        }
        throw new ServiceException("未知的办理人类型: " + desc);
    }

    /**
     * 根据代码获取对应的枚举类型
     * <p>
     * 通过传入代码，查找并返回匹配的枚举项。如果未找到匹配项，会抛出 {@link ServiceException}。
     * </p>
     *
     * @param code 代码，用于匹配对应的枚举项
     * @return TaskAssigneeEnum 返回对应的枚举类型
     * @throws IllegalArgumentException 如果未找到匹配的枚举项
     */
    public static TaskAssigneeEnum fromCode(String code) {
        for (TaskAssigneeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new ServiceException("未知的办理人类型代码: " + code);
    }

    /**
     * 获取所有办理人类型的描述列表，通常用于前端展示选择项。
     *
     * @return List<String> 返回所有办理人类型的描述列表
     */
    public static List<String> getAssigneeTypeList() {
        return Arrays.stream(values())
            .map(TaskAssigneeEnum::getDesc)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有办理人类型的代码列表，通常用于程序内部逻辑判断。
     *
     * @return List<String> 返回所有办理人类型的代码列表
     */
    public static List<String> getAssigneeCodeList() {
        return Arrays.stream(values())
            .map(TaskAssigneeEnum::getCode)
            .collect(Collectors.toList());
    }

    /**
     * 判断当前办理人类型是否需要调用部门服务。
     *
     * @return 如果类型是 USER、ORG 或 POST，则返回 true；否则返回 false
     */
    public boolean needsOrgService() {
        return this == USER || this == ORG || this == POST;
    }

    /**
     * 判断给定字符串是否符合 SPEL 表达式格式（以 $ 或 # 开头）
     *
     * @param value 待判断字符串
     * @return 是否为 SPEL 表达式
     */
    public static boolean isSpelExpression(String value) {
        if (value == null) {
            return false;
        }
        // $前缀表示默认办理人变量策略
        // #前缀表示spel办理人变量策略
        return StringUtils.startsWith(value, "$") || StringUtils.startsWith(value, "#");
    }

}

