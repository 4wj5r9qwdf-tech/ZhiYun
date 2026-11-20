/*
 * 文件用途：承载织云 ERP 对上游框架“LoginUser”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“LoginUser”能力的项目扩展或适配。
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
package org.dromara.system.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.dromara.system.api.domain.PostDTO;
import org.dromara.system.api.domain.RoleDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录用户上下文对象，保存当前会话的身份、权限和终端信息。
 *
 * @author Lion Li
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录标识分隔符，用于拼接 userType 与 userId
     * <p>不使用冒号：Sa-Token 1.46.0 起默认禁止 loginId 包含冒号；历史冒号格式仅为兼容旧 token 保留解析
     */
    public static final String LOGIN_ID_SEPARATOR = "-";

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long orgId;

    /**
     * 部门类别编码
     */
    private String orgCategory;

    /**
     * 部门名
     */
    private String orgName;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 菜单权限
     */
    private Set<String> menuPermission;

    /**
     * 角色权限
     */
    private Set<String> rolePermission;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 角色对象
     */
    private List<RoleDTO> roles;

    /**
     * 数据权限角色映射 key 为权限码 value 为可参与数据权限计算的角色ID列表
     */
    private Map<String, List<Long>> dataScopeRoleMap;

    /**
     * 岗位对象
     */
    private List<PostDTO> posts;

    /**
     * 数据权限 当前角色ID
     */
    private Long roleId;

    /**
     * 客户端
     */
    private String clientKey;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 获取 Sa-Token 使用的登录标识。
     *
     * @return 登录标识（userType-userId）
     */
    public String getLoginId() {
        if (userType == null) {
            throw new IllegalArgumentException("用户类型不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userType + LOGIN_ID_SEPARATOR + userId;
    }

}
