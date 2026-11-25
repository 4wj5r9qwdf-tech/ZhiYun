/*
 * 文件用途：承载织云 ERP 工作流节点扩展配置的解析结果。
 * 文件版本：1.1.0
 * 功能目的：向工作流服务传递节点按钮、自定义变量及升级兼容的旧抄送配置。
 * 设计思路：保留 Warm-Flow 原生字段语义，并将历史 ext 中的自定义抄送收件人作为过渡字段。
 * 实现方法：由 FlwNodeExtServiceImpl 解析 JSON，供任务办理与事件监听器消费。
 * 依赖与引用：FlwNodeExtServiceImpl；WorkflowGlobalListener；Docs/功能任务/ZY-ERP-20261124-RuoYi-Vue-Plus-6.1.0升级.md
 * 当前责任人：JY；本次修改：Codex（智能体），2026-11-24
 * 修改标记：ZY-ERP-20261124-RuoYi6.1.0；迁移期间兼容旧节点抄送配置
 */
package org.dromara.workflow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node 扩展属性解析结果 VO
 * <p>
 * 用于封装从扩展属性 JSON 中解析出的各类信息，包括按钮权限、抄送对象和自定义参数。
 *
 * @author AprilWind
 */
@Data
public class NodeExtVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 按钮权限列表
     * <p>
     * 根据扩展属性中 ButtonPermissionEnum 类型的数据生成，每个元素表示一个按钮及其是否勾选。
     */
    private List<ButtonPermissionVo> buttonPermissions;

    /**
     * 升级前在节点 ext 中配置的抄送人员 ID 集合，仅用于旧流程定义兼容。
     * 新版流程设计器使用 Warm-Flow 2.1 的 node_config.copy 配置。
     */
    private Set<String> copySettings;

    /**
     * 自定义参数 Map
     * <p>
     * 根据扩展属性中 VariablesEnum 类型的数据生成，存储 key=value 格式的自定义参数
     */
    private Map<String, String> variables;

}
