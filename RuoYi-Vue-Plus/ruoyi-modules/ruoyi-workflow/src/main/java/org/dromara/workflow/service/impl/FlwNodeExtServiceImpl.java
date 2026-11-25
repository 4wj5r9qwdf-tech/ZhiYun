/*
 * 文件用途：承载织云 ERP 对上游框架“FlwNodeExtServiceImpl”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlwNodeExtServiceImpl”能力的项目扩展或适配。
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

package org.dromara.workflow.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.dto.DictTypeDTO;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.ui.service.NodeExtService;
import org.dromara.warm.flow.ui.vo.NodeExt;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.enums.ButtonPermissionEnum;
import org.dromara.workflow.common.enums.NodeExtEnum;
import org.dromara.workflow.common.enums.VariablesEnum;
import org.dromara.workflow.domain.vo.ButtonPermissionVo;
import org.dromara.workflow.domain.vo.NodeExtVo;
import org.dromara.workflow.service.IFlwNodeExtService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程设计器-节点扩展属性
 *
 * @author AprilWind
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwNodeExtServiceImpl implements NodeExtService, IFlwNodeExtService {

    /**
     * 存储不同 dictType 对应的配置信息
     */
    private static final Map<String, Map<String, Object>> CHILD_NODE_MAP;

    static {
        CHILD_NODE_MAP = Map.of(
            VariablesEnum.class.getSimpleName(),
            Map.of(
                "label", "自定义参数",
                "type", 2,
                "must", false,
                "multiple", false,
                "desc", "节点执行时可设置自定义参数，多个参数以逗号分隔，如：key1=value1,key2=value2"
            ),
            ButtonPermissionEnum.class.getSimpleName(),
            Map.of(
                "label", "权限按钮",
                "type", 4,
                "must", false,
                "multiple", true,
                "desc", "控制该节点的按钮权限"
            )
        );
    }

    private final DictService dictService;

    /**
     * 获取节点扩展属性
     *
     * @return 节点扩展属性列表
     */
    @Override
    public List<NodeExt> getNodeExt() {
        List<NodeExt> nodeExtList = new ArrayList<>();
        // 构建基础设置页面
        nodeExtList.add(buildNodeExt("wf_basic_tab", "基础设置", 1,
            List.of(VariablesEnum.class)));
        // 构建按钮权限页面
        nodeExtList.add(buildNodeExt("wf_button_tab", "权限", 2,
            List.of(ButtonPermissionEnum.class)));
        // 自定义构建 规则参考 NodeExt 与 warm-flow文档说明
        // nodeExtList.add(buildNodeExt("xxx_xxx", "xxx", 1, List);
        return nodeExtList;
    }

    /**
     * 构建一个 `NodeExt` 对象
     *
     * @param code    唯一编码
     * @param name    名称（新页签时，作为页签名称）
     * @param type    节点类型（1: 基础设置，2: 新页签）
     * @param sources 数据来源（枚举类或字典类型）
     * @return 构建的 `NodeExt` 对象
     */
    @SuppressWarnings("unchecked")
    private NodeExt buildNodeExt(String code, String name, int type, List<Object> sources) {
        NodeExt nodeExt = new NodeExt();
        nodeExt.setCode(code);
        nodeExt.setType(type);
        nodeExt.setName(name);
        nodeExt.setChilds(sources.stream()
            .map(source -> {
                if (source instanceof Class<?> clazz && NodeExtEnum.class.isAssignableFrom(clazz)) {
                    return buildChildNode((Class<? extends NodeExtEnum>) clazz);
                } else if (source instanceof String dictType) {
                    return buildChildNode(dictType);
                }
                return null;
            })
            .filter(ObjectUtil::isNotNull)
            .toList()
        );
        return nodeExt;
    }

    /**
     * 根据枚举类型构建一个 `ChildNode` 对象
     *
     * @param enumClass 枚举类，必须实现 `NodeExtEnum` 接口
     * @return 构建的 `ChildNode` 对象
     */
    private NodeExt.ChildNode buildChildNode(Class<? extends NodeExtEnum> enumClass) {
        if (!enumClass.isEnum()) {
            return null;
        }
        String simpleName = enumClass.getSimpleName();
        NodeExt.ChildNode childNode = new NodeExt.ChildNode();
        Map<String, Object> map = CHILD_NODE_MAP.get(simpleName);
        // 编码，此json中唯
        childNode.setCode(simpleName);
        // label名称
        childNode.setLabel(Convert.toStr(map.get("label")));
        // 1：输入框 2：文本域 3：下拉框 4：选择框 5：用户选择器
        childNode.setType(Convert.toInt(map.get("type"), 1));
        // 是否必填
        childNode.setMust(Convert.toBool(map.get("must"), false));
        // 是否多选
        childNode.setMultiple(Convert.toBool(map.get("multiple"), true));
        // 描述
        childNode.setDesc(Convert.toStr(map.get("desc"), null));
        // 字典，下拉框和复选框时用到
        childNode.setDict(Arrays.stream(enumClass.getEnumConstants())
            .map(NodeExtEnum.class::cast)
            .map(x ->
                new NodeExt.DictItem(x.getLabel(), x.getValue(), x.isSelected())
            ).toList());
        return childNode;
    }

    /**
     * 根据字典类型构建 `ChildNode` 对象
     *
     * @param dictType 字典类型
     * @return 构建的 `ChildNode` 对象
     */
    private NodeExt.ChildNode buildChildNode(String dictType) {
        DictTypeDTO dictTypeDTO = dictService.getDictType(dictType);
        if (ObjectUtil.isNull(dictTypeDTO)) {
            return null;
        }
        NodeExt.ChildNode childNode = new NodeExt.ChildNode();
        // 编码，此json中唯一
        childNode.setCode(dictType);
        // label名称
        childNode.setLabel(dictTypeDTO.getDictName());
        // 1：输入框 2：文本域 3：下拉框 4：选择框 5：用户选择器
        childNode.setType(3);
        // 是否必填
        childNode.setMust(false);
        // 是否多选
        childNode.setMultiple(true);
        // 描述 (可根据描述参数解析更多配置，如type，must，multiple等)
        childNode.setDesc(dictTypeDTO.getRemark());
        // 字典，下拉框和复选框时用到
        childNode.setDict(dictService.getDictData(dictType)
            .stream().map(x ->
                new NodeExt.DictItem(x.getDictLabel(), x.getDictValue(), Convert.toBool(x.getIsDefault(), false))
            ).toList());
        return childNode;
    }

    /**
     * 解析扩展属性 JSON 并构建 Node 扩展属性对象
     * <p>
     * 根据传入的 JSON 字符串，将扩展属性分为两类：
     * 1. ButtonPermissionEnum：解析为按钮权限列表，标记每个按钮是否勾选
     * 2. VariablesEnum：解析为自定义参数 Map
     *
     * <p>示例 JSON：
     * [
     * {"code": "ButtonPermissionEnum", "value": "back,termination"},
     * {"code": "VariablesEnum", "value": "key1=value1,key2=value2"}
     * ]
     *
     * @param ext      扩展属性 JSON 字符串
     * @param variable 流程变量
     * @return NodeExtVo 对象，封装按钮权限列表和自定义参数 Map
     */
    @Override
    public NodeExtVo parseNodeExt(String ext, Map<String, Object> variable) {
        NodeExtVo nodeExtVo = new NodeExtVo();

        // 解析 JSON 为 Dict 列表
        List<Dict> nodeExtMap = JsonUtils.parseArrayMap(ext);
        if (ObjectUtil.isEmpty(nodeExtMap)) {
            return nodeExtVo;
        }

        for (Dict nodeExt : nodeExtMap) {
            String code = nodeExt.getStr("code");
            String value = nodeExt.getStr("value");

            if (ButtonPermissionEnum.class.getSimpleName().equals(code)) {
                // 解析按钮权限
                // 将 value 拆分为 Set<String>，便于精确匹配
                Set<String> buttonSet = StringUtils.str2Set(value, StringUtils.SEPARATOR);

                // 获取按钮字典配置
                NodeExt.ChildNode childNode = buildChildNode(ButtonPermissionEnum.class);

                // 构建 ButtonPermissionVo 列表
                List<ButtonPermissionVo> buttonList = Optional.ofNullable(childNode)
                    .map(NodeExt.ChildNode::getDict)
                    .orElse(List.of())
                    .stream()
                    .map(dict -> new ButtonPermissionVo(dict.getValue(), buttonSet.contains(dict.getValue())))
                    .toList();

                nodeExtVo.setButtonPermissions(buttonList);

            } else if ("CopySettingEnum".equals(code)) {
                // 兼容升级前存入流程 ext 的抄送配置；新建流程改用 Warm-Flow 2.1 原生 node_config.copy。
                List<String> permissions = spelSmartSplit(value).stream()
                    .map(expression -> {
                        List<String> resolved = ExpressionUtil.evalVariable(expression, variable);
                        return CollUtil.isNotEmpty(resolved) ? resolved : Collections.singletonList(expression);
                    })
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .distinct()
                    .toList();
                if (FlowEngine.permissionHandler() != null) {
                    nodeExtVo.setCopySettings(new HashSet<>(FlowEngine.permissionHandler().convertPermissions(permissions)));
                }

            } else if (VariablesEnum.class.getSimpleName().equals(code)) {
                // 解析自定义参数
                // 将 key=value 字符串拆分为 Map
                Map<String, String> variables = StringUtils.str2List(value, StringUtils.SEPARATOR, true, true).stream()
                    .map(s -> StringUtils.split(s, "="))
                    .filter(arr -> arr.length == 2)
                    .collect(Collectors.toMap(arr -> arr[0], arr -> arr[1], (l, r) -> l));
                nodeExtVo.setVariables(variables);
            } else {
                // 未知扩展类型，记录日志
                log.warn("未知扩展类型：code={}, value={}", code, value);
            }
        }
        return nodeExtVo;
    }


    /**
     * 按逗号分割字符串，但保留 #{...} 表达式和字符串常量中的逗号
     *
     * @param str 原始配置字符串
     * @return 智能分割后的片段列表
     */
    private static List<String> spelSmartSplit(String str) {
        List<String> result = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return result;
        }

        StringBuilder token = new StringBuilder();
        // #{...} 的嵌套深度
        int orgh = 0;
        // 是否在字符串常量中（" 或 '）
        boolean inString = false;
        // 当前字符串引号类型
        char stringQuote = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            // 检测进入 SpEL 表达式 #{...}
            if (!inString && c == '#' && orgh == 0 && checkNext(str, i, '{')) {
                orgh++;
                token.append("#{");
                // 跳过 {
                i++;
                continue;
            }

            // 在表达式中遇到 { 或 } 改变嵌套深度
            if (!inString && orgh > 0) {
                if (c == '{') {
                    orgh++;
                } else if (c == '}') {
                    orgh--;
                }
                token.append(c);
                continue;
            }

            // 检测字符串开始/结束
            if (orgh > 0 && (c == '"' || c == '\'')) {
                if (!inString) {
                    inString = true;
                    stringQuote = c;
                } else if (stringQuote == c) {
                    inString = false;
                }
                token.append(c);
                continue;
            }

            // 外层逗号才分割
            if (c == ',' && orgh == 0 && !inString) {
                String part = token.toString().trim();
                if (!part.isEmpty()) {
                    result.add(part);
                }
                token.setLength(0);
                continue;
            }

            token.append(c);
        }

        // 添加最后一个
        String part = token.toString().trim();
        if (!part.isEmpty()) {
            result.add(part);
        }

        return result;
    }

    /**
     * 判断指定位置的下一个字符是否匹配预期字符。
     *
     * @param str      原始字符串
     * @param index    当前索引
     * @param expected 期望匹配的字符
     * @return 匹配成功返回 {@code true}
     */
    private static boolean checkNext(String str, int index, char expected) {
        return index + 1 < str.length() && str.charAt(index + 1) == expected;
    }

}
