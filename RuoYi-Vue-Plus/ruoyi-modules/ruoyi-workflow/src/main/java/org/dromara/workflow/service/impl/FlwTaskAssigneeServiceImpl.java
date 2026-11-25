/*
 * 文件用途：承载织云 ERP 对上游框架“FlwTaskAssigneeServiceImpl”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlwTaskAssigneeServiceImpl”能力的项目扩展或适配。
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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.map.MapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.*;
import org.dromara.system.api.domain.OrgDTO;
import org.dromara.system.api.domain.TaskAssigneeDTO;
import org.dromara.system.api.model.TaskAssigneeBody;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.ui.dto.HandlerFunDto;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.dromara.warm.flow.ui.dto.TreeFunDto;
import org.dromara.warm.flow.ui.service.HandlerSelectService;
import org.dromara.warm.flow.ui.vo.HandlerFeedBackVo;
import org.dromara.warm.flow.ui.vo.HandlerSelectVo;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.enums.TaskAssigneeEnum;
import org.dromara.workflow.service.IFlwTaskAssigneeService;
import org.dromara.workflow.service.IFlwSpelService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程设计器-获取办理人权限设置列表
 *
 * @author AprilWind
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwTaskAssigneeServiceImpl implements PermissionHandler, HandlerSelectService, IFlwTaskAssigneeService {

    private static final String DEFAULT_GROUP_NAME = "默认分组";

    private final TaskAssigneeService taskAssigneeService;
    private final UserService userService;
    private final OrgService orgService;
    private final RoleService roleService;
    private final PostService postService;
    private final IFlwSpelService spelService;

    /**
     * 办理人权限标识，比如用户，角色，组织等，用于校验是否有权限办理任务
     * 后续在流程上下文中获取。
     * 返回当前用户权限集合
     *
     * @return 当前用户权限集合
     */
    @Override
    public List<String> permissions() {
        return Collections.singletonList(LoginHelper.getUserIdStr());
    }

    /**
     * 获取当前办理人
     *
     * @return 当前办理人
     */
    @Override
    public String getHandler() {
        return LoginHelper.getUserIdStr();
    }

    /**
     * 将预设办理人转换为实际用户 ID 列表。
     *
     * @param permissions 预设权限标识列表
     * @return 用户 ID 列表
     */
    @Override
    public List<String> convertPermissions(List<String> permissions) {
        if (CollUtil.isEmpty(permissions)) {
            return permissions;
        }

        Map<TaskAssigneeEnum, List<String>> typeIdMap = new EnumMap<>(TaskAssigneeEnum.class);
        for (String storageId : permissions) {
            Pair<TaskAssigneeEnum, String> parsed = this.parseStorageId(storageId);
            if (parsed != null) {
                typeIdMap.computeIfAbsent(parsed.getKey(), k -> new ArrayList<>()).add(parsed.getValue());
            }
        }

        return typeIdMap.entrySet()
            .stream()
            .map(entry -> {
                TaskAssigneeEnum type = entry.getKey();
                List<Long> longIds = StreamUtils.toList(entry.getValue(), Convert::toLong);
                return switch (type) {
                    case USER -> longIds;
                    case ROLE -> userService.selectUserIdsByRoleIds(longIds);
                    case ORG -> userService.selectUserIdsByOrgIds(longIds);
                    case POST -> userService.selectUserIdsByPostIds(longIds);
                    default -> Collections.<Long>emptyList();
                };
            })
            .filter(CollUtil::isNotEmpty)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .distinct()
            .map(Convert::toStr)
            .toList();
    }

    /**
     * 获取办理人权限设置列表tabs页签
     *
     * @return tabs页签
     */
    @Override
    public List<String> getHandlerType() {
        return TaskAssigneeEnum.getAssigneeTypeList();
    }

    /**
     * 获取办理列表, 同时构建左侧组织树状结构
     *
     * @param query 查询条件
     * @return HandlerSelectVo
     */
    @Override
    public HandlerSelectVo getHandlerSelect(HandlerQuery query) {
        // 获取任务办理类型
        TaskAssigneeEnum type = TaskAssigneeEnum.fromDesc(query.getHandlerType());
        // 转换查询条件为 TaskAssigneeBody
        TaskAssigneeBody taskQuery = BeanUtil.toBean(query, TaskAssigneeBody.class);

        // 统一查询并构建业务数据
        TaskAssigneeDTO dto = fetchTaskAssigneeData(type, taskQuery);
        List<OrgDTO> orgs = fetchOrgData(type);

        return getHandlerSelectVo(buildHandlerData(dto, type), buildOrgTree(orgs));
    }

    /**
     * 办理人权限名称回显
     *
     * @param storageIds 入库主键集合
     * @return 结果
     */
    @Override
    public List<HandlerFeedBackVo> handlerFeedback(List<String> storageIds) {
        if (CollUtil.isEmpty(storageIds)) {
            return Collections.emptyList();
        }
        // 解析并归类 ID，同时记录原始顺序和对应解析结果
        Map<TaskAssigneeEnum, List<String>> typeIdMap = new EnumMap<>(TaskAssigneeEnum.class);
        Map<String, Pair<TaskAssigneeEnum, String>> parsedMap = new LinkedHashMap<>();
        for (String storageId : storageIds) {
            Pair<TaskAssigneeEnum, String> parsed = this.parseStorageId(storageId);
            parsedMap.put(storageId, parsed);
            if (parsed != null) {
                typeIdMap.computeIfAbsent(parsed.getKey(), k -> new ArrayList<>()).add(parsed.getValue());
            }
        }

        // 查询所有类型对应的 ID 名称映射
        Map<TaskAssigneeEnum, Map<String, String>> nameMap = this.getNamesByTypes(typeIdMap);
        // 组装返回结果，保持原始顺序
        return parsedMap.entrySet().stream()
            .map(entry -> {
                String storageId = entry.getKey();
                Pair<TaskAssigneeEnum, String> parsed = entry.getValue();
                String handlerName = (parsed == null) ? null
                    : nameMap.getOrDefault(parsed.getKey(), Collections.emptyMap())
                    .get(parsed.getValue());
                return new HandlerFeedBackVo(storageId, handlerName);
            }).toList();
    }

    /**
     * 根据办理人类型查询右侧候选数据。
     *
     * @param type      办理人类型
     * @param taskQuery 查询条件
     * @return 办理人数据
     */
    private TaskAssigneeDTO fetchTaskAssigneeData(TaskAssigneeEnum type, TaskAssigneeBody taskQuery) {
        return switch (type) {
            case USER -> taskAssigneeService.selectUsersByTaskAssigneeList(taskQuery);
            case ROLE -> taskAssigneeService.selectRolesByTaskAssigneeList(taskQuery);
            case ORG -> taskAssigneeService.selectOrgsByTaskAssigneeList(taskQuery);
            case POST -> taskAssigneeService.selectPostsByTaskAssigneeList(taskQuery);
            case SPEL -> spelService.selectSpelByTaskAssigneeList(taskQuery);
        };
    }

    /**
     * 根据办理人类型获取组织树数据源。
     *
     * @param type 办理人类型
     * @return 组织列表
     */
    private List<OrgDTO> fetchOrgData(TaskAssigneeEnum type) {
        if (type.needsOrgService()) {
            return orgService.selectOrgsByList();
        }
        return new ArrayList<>();
    }

    /**
     * 获取权限分组名称
     *
     * @param type      任务分配人枚举
     * @param groupName 权限分组
     * @return 权限分组名称
     */
    private String getGroupName(TaskAssigneeEnum type, String groupName) {
        if (StringUtils.isEmpty(groupName)) {
            return DEFAULT_GROUP_NAME;
        }
        if (type.needsOrgService()) {
            return orgService.selectOrgNameByIds(groupName);
        }
        return DEFAULT_GROUP_NAME;
    }

    /**
     * 构建设计器左侧组织树。
     *
     * @param orgs 组织列表
     * @return 组织树构建器
     */
    private TreeFunDto<OrgDTO> buildOrgTree(List<OrgDTO> orgs) {
        return new TreeFunDto<>(orgs)
            .setId(org -> Convert.toStr(org.getOrgId()))
            .setName(OrgDTO::getOrgName)
            .setParentId(org -> Convert.toStr(org.getParentId()));
    }

    /**
     * 构建设计器右侧办理人列表数据。
     *
     * @param dto  办理人数据
     * @param type 办理人类型
     * @return 办理人列表构建器
     */
    private HandlerFunDto<TaskAssigneeDTO.TaskHandler> buildHandlerData(TaskAssigneeDTO dto, TaskAssigneeEnum type) {
        return new HandlerFunDto<>(dto.getList(), dto.getTotal())
            .setStorageId(assignee -> type.getCode() + assignee.getStorageId())
            .setHandlerCode(assignee -> StringUtils.blankToDefault(assignee.getHandlerCode(), "无"))
            .setHandlerName(assignee -> StringUtils.blankToDefault(assignee.getHandlerName(), "无"))
            .setGroupName(assignee -> this.getGroupName(type, assignee.getGroupName()))
            .setCreateTime(assignee -> DateUtils.formatLocalDateTime(assignee.getCreateTime()));
    }

    /**
     * 按任务分配类型批量查询名称映射。
     *
     * @param typeIdMap 任务分配类型与 id 列表映射
     * @return 任务分配类型与名称映射
     */
    private Map<TaskAssigneeEnum, Map<String, String>> getNamesByTypes(Map<TaskAssigneeEnum, List<String>> typeIdMap) {
        Map<TaskAssigneeEnum, Map<String, String>> nameMap = new EnumMap<>(TaskAssigneeEnum.class);
        typeIdMap.forEach((type, ids) -> nameMap.put(type, this.getNamesByType(type, ids)));
        return nameMap;
    }

    /**
     * 根据任务分配类型和对应 ID 列表，批量查询名称映射关系
     *
     * @param type 分配类型（用户、角色、组织、岗位）
     * @param ids  ID 列表（如用户ID、角色ID等）
     * @return 返回 Map，其中 key 为 ID，value 为对应的名称
     */
    private Map<String, String> getNamesByType(TaskAssigneeEnum type, List<String> ids) {
        if (type == TaskAssigneeEnum.SPEL) {
            return spelService.selectRemarksBySpels(ids);
        }

        List<Long> longIds = StreamUtils.toList(ids, Convert::toLong);
        Map<Long, String> rawMap = switch (type) {
            case USER -> userService.selectUserNicksByIds(longIds);
            case ROLE -> roleService.selectRoleNamesByIds(longIds);
            case ORG -> orgService.selectOrgNamesByIds(longIds);
            case POST -> postService.selectPostNamesByIds(longIds);
            default -> Collections.emptyMap();
        };
        if (MapUtil.isEmpty(rawMap)) {
            return Collections.emptyMap();
        }
        return rawMap.entrySet()
            .stream()
            .collect(Collectors.toMap(
                e -> Convert.toStr(e.getKey()),
                Map.Entry::getValue
            ));
    }

    /**
     * 按 ERP 的存储标识展开人员、角色、组织和岗位，供审批配置保存前校验实际收件人。
     * SpEL 规则由流程运行时解析；配置阶段不执行动态表达式。
     *
     * @param storageIds 逗号分隔的人员 ID 或 role:/org:/post: 标识
     * @return 去重后的正常人员候选列表
     */
    @Override
    public List<org.dromara.system.api.domain.UserDTO> fetchUsersByStorageIds(String storageIds) {
        if (StringUtils.isBlank(storageIds)) {
            return Collections.emptyList();
        }
        Map<TaskAssigneeEnum, List<String>> typeIdMap = new EnumMap<>(TaskAssigneeEnum.class);
        for (String storageId : StringUtils.str2List(storageIds, StringUtils.SEPARATOR, true, true)) {
            Pair<TaskAssigneeEnum, String> parsed = this.parseStorageId(storageId);
            if (parsed != null && parsed.getKey() != TaskAssigneeEnum.SPEL) {
                typeIdMap.computeIfAbsent(parsed.getKey(), key -> new ArrayList<>()).add(parsed.getValue());
            }
        }
        List<org.dromara.system.api.domain.UserDTO> resolved = typeIdMap.entrySet().stream()
            .map(entry -> {
                List<Long> ids = StreamUtils.toList(entry.getValue(), Convert::toLong);
                return switch (entry.getKey()) {
                    case USER -> userService.selectListByIds(ids);
                    case ROLE -> userService.selectUsersByRoleIds(ids);
                    case ORG -> userService.selectUsersByOrgIds(ids);
                    case POST -> userService.selectUsersByPostIds(ids);
                    default -> Collections.<org.dromara.system.api.domain.UserDTO>emptyList();
                };
            })
            .filter(CollUtil::isNotEmpty)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .toList();
        return resolved.stream()
            .filter(user -> user.getUserId() != null)
            .collect(Collectors.toMap(
                org.dromara.system.api.domain.UserDTO::getUserId,
                user -> user,
                (first, ignored) -> first,
                LinkedHashMap::new))
            .values().stream().toList();
    }

    /**
     * 解析 storageId 字符串，返回类型和ID的组合
     *
     * @param storageId 例如 "user:123" 或 "456"
     * @return Pair(TaskAssigneeEnum, Long)，如果格式非法返回 null
     */
    private Pair<TaskAssigneeEnum, String> parseStorageId(String storageId) {
        if (StringUtils.isBlank(storageId)) {
            return null;
        }
        if (TaskAssigneeEnum.isSpelExpression(storageId)) {
            return Pair.of(TaskAssigneeEnum.SPEL, storageId);
        }
        try {
            String[] parts = storageId.split(StringUtils.COLON, 2);
            if (parts.length < 2) {
                return Pair.of(TaskAssigneeEnum.USER, parts[0]);
            } else {
                TaskAssigneeEnum type = TaskAssigneeEnum.fromCode(parts[0] + StringUtils.COLON);
                return Pair.of(type, parts[1]);
            }
        } catch (Exception e) {
            log.warn("解析 storageId 失败，格式非法：{}，错误信息：{}", storageId, e.getMessage());
            return null;
        }
    }

}
