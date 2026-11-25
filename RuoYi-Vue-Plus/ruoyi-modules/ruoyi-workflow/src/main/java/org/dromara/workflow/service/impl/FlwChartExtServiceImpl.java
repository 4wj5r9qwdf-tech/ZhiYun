/*
 * 文件用途：承载织云 ERP 对上游框架“FlwChartExtServiceImpl”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlwChartExtServiceImpl”能力的项目扩展或适配。
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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.api.OrgService;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.PromptContent;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowUserMapper;
import org.dromara.warm.flow.ui.service.ChartExtService;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.constant.FlowConstant;
import org.dromara.workflow.common.enums.TaskAssigneeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程图提示信息
 *
 * @author AprilWind
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwChartExtServiceImpl implements ChartExtService {

    private final UserService userService;
    private final OrgService orgService;
    private final FlowHisTaskMapper flowHisTaskMapper;
    private final FlowTaskMapper flowTaskMapper;
    private final FlowUserMapper flowUserMapper;
    private final DictService dictService;
    @Value("${warm-flow.node-tooltip:true}")
    private boolean nodeTooltip;

    /**
     * 设置流程图提示信息
     *
     * @param defJson 流程定义json对象
     */
    @Override
    public void execute(DefJson defJson) {
        // 配置关闭，直接返回，不渲染悬浮窗
        if (!nodeTooltip) {
            return;
        }

        Long instanceId = defJson.getInstance().getId();
        List<FlowHisTask> flowHisTasks = this.getHisTaskGroupedByNode(instanceId);
        List<FlowTask> activeTasks = flowTaskMapper.selectList(Wrappers.<FlowTask>lambdaQuery()
            .eq(FlowTask::getInstanceId, instanceId)
            .eq(FlowTask::getNodeType, NodeType.BETWEEN.getKey()));
        if (CollUtil.isEmpty(flowHisTasks) && CollUtil.isEmpty(activeTasks)) {
            return;
        }

        Map<String, List<FlowHisTask>> groupedByNode = CollUtil.isEmpty(flowHisTasks)
            ? Collections.emptyMap() : StreamUtils.groupByKey(flowHisTasks, FlowHisTask::getNodeCode);
        Map<Long, String> taskNodeCodes = activeTasks.stream()
            .collect(Collectors.toMap(FlowTask::getId, FlowTask::getNodeCode));
        List<FlowUser> activeUsers = taskNodeCodes.isEmpty() ? Collections.emptyList()
            : flowUserMapper.selectList(Wrappers.<FlowUser>lambdaQuery()
                .in(FlowUser::getAssociated, taskNodeCodes.keySet())
                .in(FlowUser::getType, List.of(TaskAssigneeType.APPROVER.getCode(),
                    TaskAssigneeType.TRANSFER.getCode(), TaskAssigneeType.DELEGATE.getCode())));
        Map<String, Set<Long>> pendingUsersByNode = new HashMap<>();
        for (FlowUser activeUser : activeUsers) {
            Long userId = Convert.toLong(activeUser.getProcessedBy(), null);
            String nodeCode = taskNodeCodes.get(activeUser.getAssociated());
            if (userId != null && nodeCode != null) {
                pendingUsersByNode.computeIfAbsent(nodeCode, key -> new LinkedHashSet<>()).add(userId);
            }
        }

        Set<Long> userIds = new HashSet<>();
        flowHisTasks.forEach(task -> {
            Long approverId = Convert.toLong(task.getApprover(), null);
            if (approverId != null) {
                userIds.add(approverId);
            }
        });
        pendingUsersByNode.values().forEach(userIds::addAll);
        if (userIds.isEmpty()) {
            return;
        }
        List<UserDTO> userDTOList = userService.selectListByIds(userIds);

        // 将查询到的用户列表转换为以用户ID为key的映射
        Map<Long, UserDTO> userMap = StreamUtils.toIdentityMap(userDTOList, UserDTO::getUserId);
        Set<Long> orgIds = StreamUtils.toSet(userDTOList, UserDTO::getOrgId);
        orgIds.remove(null);
        Map<Long, String> orgNameMap = orgIds.isEmpty() ? Collections.emptyMap()
            : orgService.selectOrgNamesByIds(orgIds);

        Map<String, String> dictType = flowHisTasks.isEmpty() ? Collections.emptyMap()
            : dictService.getAllDictByDictType(FlowConstant.WF_TASK_STATUS);

        for (NodeJson nodeJson : defJson.getNodeList()) {
            List<FlowHisTask> taskList = groupedByNode.get(nodeJson.getNodeCode());
            if (CollUtil.isNotEmpty(taskList)) {
                List<FlowHisTask> latestPerApprover = taskList.stream()
                    .filter(task -> StringUtils.isNotBlank(task.getApprover()))
                    .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                            FlowHisTask::getApprover,
                            Function.identity(),
                            (oldTask, newTask) -> newTask.getUpdateTime().after(oldTask.getUpdateTime()) ? newTask : oldTask,
                            LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                    ));
                this.processNodeExtInfo(nodeJson, latestPerApprover, userMap, orgNameMap, dictType);
            }
            for (Long userId : pendingUsersByNode.getOrDefault(nodeJson.getNodeCode(), Collections.emptySet())) {
                UserDTO user = userMap.get(userId);
                if (user != null) {
                    addUserInfo(nodeJson.getPromptContent().getInfo(), user,
                        orgNameMap.getOrDefault(user.getOrgId(), StringUtils.EMPTY));
                    nodeJson.getPromptContent().getInfo().add(buildInfoItem("审批状态", "待审批"));
                }
            }
        }
    }

    /**
     * 初始化流程图提示信息
     *
     * @param defJson 流程定义json对象
     */
    @Override
    public void initPromptContent(DefJson defJson) {
        // 配置关闭，直接返回，不渲染悬浮窗
        if (!nodeTooltip) {
            return;
        }

        defJson.setTopText("流程名称: " + defJson.getFlowName());
        defJson.getNodeList().forEach(nodeJson -> {
            nodeJson.setPromptContent(
                new PromptContent()
                    // 提示信息
                    .setInfo(
                        CollUtil.newArrayList(
                            new PromptContent.InfoItem()
                                .setPrefix("任务名称: ")
                                .setContent(nodeJson.getNodeName())
                                .setContentStyle(Map.of(
                                    "border", "1px solid #d1e9ff",
                                    "backgroundColor", "#e8f4ff",
                                    "padding", "4px 8px",
                                    "borderRadius", "4px"
                                ))
                                .setRowStyle(Map.of(
                                    "fontWeight", "bold",
                                    "margin", "0 0 6px 0",
                                    "padding", "0 0 8px 0",
                                    "borderBottom", "1px solid #ccc"
                                ))
                        )
                    )
                    // 弹窗样式
                    .setDialogStyle(MapUtil.mergeAll(
                        "position", "absolute",
                        "backgroundColor", "#fff",
                        "border", "1px solid #ccc",
                        "borderRadius", "4px",
                        "boxShadow", "0 2px 8px rgba(0, 0, 0, 0.15)",
                        "padding", "8px 12px",
                        "fontSize", "14px",
                        "zIndex", "1000",
                        "maxWidth", "500px",
                        "maxHeight", "300px",
                        "overflowY", "auto",
                        "overflowX", "hidden",
                        "color", "#333",
                        "pointerEvents", "auto",
                        "scrollbarWidth", "thin"
                    ))
            );
        });
    }

    /**
     * 处理节点的扩展信息，构建用于流程图悬浮提示的内容
     *
     * @param nodeJson    当前流程节点对象，包含节点基础信息和提示内容容器
     * @param taskList    当前节点关联的历史审批任务列表，用于生成提示信息
     * @param userMap     用户信息映射表，key 为用户ID，value 为用户DTO对象，用于获取审批人信息
     * @param orgNameMap 部门名称映射表，key 为部门ID，value 为部门名称
     * @param dictType    数据字典映射表，key 为字典项编码，value 为对应显示值，用于翻译审批状态等
     */
    private void processNodeExtInfo(NodeJson nodeJson, List<FlowHisTask> taskList, Map<Long, UserDTO> userMap,
                                    Map<Long, String> orgNameMap, Map<String, String> dictType) {

        // 获取节点提示内容对象中的 info 列表，用于追加提示项
        List<PromptContent.InfoItem> info = nodeJson.getPromptContent().getInfo();

        // 遍历所有任务记录，构建提示内容
        for (FlowHisTask task : taskList) {
            UserDTO userDTO = userMap.get(Convert.toLong(task.getApprover()));
            if (ObjectUtil.isEmpty(userDTO)) {
                continue;
            }

            addUserInfo(info, userDTO, orgNameMap.getOrDefault(userDTO.getOrgId(), StringUtils.EMPTY));

            info.add(buildInfoItem("审批状态", dictType.get(task.getFlowStatus())));
            info.add(buildInfoItem("审批耗时", DateUtils.formatBetweenBySecond(task.getUpdateTime(), task.getCreateTime())));
            info.add(buildInfoItem("办理时间", DateUtils.formatDateTime(task.getUpdateTime())));
        }
    }

    private void addUserInfo(List<PromptContent.InfoItem> info, UserDTO userDTO, String orgName) {
        info.add(new PromptContent.InfoItem()
                .setPrefix(StringUtils.format("👥 {}（{}）", userDTO.getNickName(), orgName))
                .setPrefixStyle(Map.of(
                    "fontWeight", "bold",
                    "fontSize", "15px",
                    "color", "#333"
                ))
                .setRowStyle(Map.of(
                    "margin", "8px 0",
                    "borderBottom", "1px dashed #ccc"
                ))
            );

        info.add(buildInfoItem("用户账号", userDTO.getUserName()));
    }

    /**
     * 构建单条提示内容对象 InfoItem，用于悬浮窗显示（key: value）
     *
     * @param key   字段名（作为前缀）
     * @param value 字段值
     * @return 提示项对象
     */
    private PromptContent.InfoItem buildInfoItem(String key, String value) {
        return new PromptContent.InfoItem()
            // 前缀
            .setPrefix(key + ": ")
            // 前缀样式
            .setPrefixStyle(Map.of(
                "textAlign", "right",
                "color", "#444",
                "userSelect", "none",
                "display", "inline-block",
                "width", "100px",
                "paddingRight", "8px",
                "fontWeight", "500",
                "fontSize", "14px",
                "lineHeight", "24px",
                "verticalAlign", "middle"
            ))
            // 内容
            .setContent(value)
            // 内容样式
            .setContentStyle(Map.of(
                "backgroundColor", "#f7faff",
                "color", "#005cbf",
                "padding", "4px 8px",
                "fontSize", "14px",
                "borderRadius", "4px",
                "whiteSpace", "normal",
                "border", "1px solid #d0e5ff",
                "userSelect", "text",
                "lineHeight", "20px"
            ))
            // 行样式
            .setRowStyle(Map.of(
                "color", "#222",
                "alignItems", "center",
                "display", "flex",
                "marginBottom", "6px",
                "fontWeight", "400",
                "fontSize", "14px"
            ));
    }

    /**
     * 根据流程实例ID获取历史任务列表
     *
     * @param instanceId 流程实例ID
     * @return 按更新时间倒序排列的审批历史任务列表
     */
    public List<FlowHisTask> getHisTaskGroupedByNode(Long instanceId) {
        LambdaQueryWrapper<FlowHisTask> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FlowHisTask::getInstanceId, instanceId)
            .eq(FlowHisTask::getNodeType, NodeType.BETWEEN.getKey())
            .orderByDesc(FlowHisTask::getUpdateTime);
        return flowHisTaskMapper.selectList(wrapper);
    }

}
