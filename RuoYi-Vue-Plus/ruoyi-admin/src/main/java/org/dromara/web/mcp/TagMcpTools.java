/*
 * 文件用途：向受控智能体暴露织云 ERP 的业务标签 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的业务标签 MCP 工具能力。
 * 设计思路：工具契约以显式权限、输入白名单、当前用户身份和数据范围为边界，复用既有业务实现。
 * 实现方法：解析参数、校验身份权限和格式、调用业务层并按最小必要字段返回结构化结果。
 * 关键约束：不得扩大员工数据范围或返回非必要敏感信息。
 * 依赖与引用：Docs/API/API文档.md；Docs/AI/织云AI集成总体规划.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
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
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.web.controller.TagController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 通用标签管理 MCP 工具；复用标签 REST 控制器中的命名空间校验、事务和业务审计。
 * Generic tag-management MCP tools reuse the REST controller's namespace validation, transactions, and audit behavior.
 */
@Component
@RequiredArgsConstructor
public class TagMcpTools {

    private final TagController tagController;

    /** 查询已登记的业务功能标签命名空间。 / List registered business-feature tag namespaces. */
    @McpTool(name = "tag_module_list", description = "查询已登记的标签功能命名空间；需要 system:tag:list 权限")
    public R<?> moduleList() {
        StpUtil.checkPermission("system:tag:list");
        return tagController.modules();
    }

    /** 查询一个功能下的分组及标签数量，包含默认分组。 / List groups and tag counts for one feature, including its default group. */
    @McpTool(name = "tag_group_list", description = "查询指定标签功能的分组和标签数量；moduleKey 必须先从 tag_module_list 获取，需要员工登录")
    public R<?> groupList(
        @McpToolParam(description = "功能命名空间 moduleKey；先从 tag_module_list 读取，不要使用导航模块名") String moduleKey) {
        StpUtil.checkLogin();
        return tagController.groups(moduleKey);
    }

    /** 按功能及可选条件查询标签。 / List tags within a feature and optional filters. */
    @McpTool(name = "tag_list", description = "按功能查询标签及所属分组；需要 system:tag:list 权限")
    public R<?> tagList(
        @McpToolParam(description = "功能命名空间 moduleKey；先从 tag_module_list 获取") String moduleKey,
        @McpToolParam(description = "可选，按标签名称模糊筛选；省略或空白表示不过滤", required = false) String keyword,
        @McpToolParam(description = "可选，0启用、1停用；省略表示全部状态", required = false) String status,
        @McpToolParam(description = "可选，分组ID十进制字符串；省略表示该功能下全部分组", required = false) String groupId) {
        StpUtil.checkPermission("system:tag:list");
        String statusFilter = McpInputValidator.optionalBinaryFlag(status, "标签状态");
        Long groupFilter = McpInputValidator.optionalPositiveId(groupId, "标签分组ID");
        return tagController.list(moduleKey, keyword, statusFilter, groupFilter == null ? null : groupFilter.toString());
    }

    /** 查询业务表单可新选的启用标签。 / List active tag options available to business forms. */
    @McpTool(name = "tag_options", description = "查询指定功能可供新业务选择的启用标签及分组；停用标签不返回，需要员工登录")
    public R<?> tagOptions(
        @McpToolParam(description = "功能命名空间 moduleKey；先从 tag_module_list 获取") String moduleKey) {
        StpUtil.checkLogin();
        return tagController.options(moduleKey);
    }

    /** 新增标签前由员工确认功能、分组和标签名称。 / Create a tag only after the employee confirms its feature, group, and name. */
    @McpTool(name = "tag_create", description = "新增功能内标签；执行前展示功能、分组、标签名和常用标记并取得员工确认，需要 system:tag:add 权限")
    public R<?> tagCreate(
        @McpToolParam(description = "标签字段对象：必填moduleKey、moduleName、tagName；groupId为可选正十进制字符串，省略时归入默认分组；isCommon可选，传字符串0或1，省略默认为0") Map<String, Object> body) {
        StpUtil.checkPermission("system:tag:add");
        Map<String, Object> input = requireFields(body,
            Set.of("moduleKey", "moduleName", "tagName", "groupId", "isCommon"),
            Set.of("moduleKey", "moduleName", "tagName"));
        requireTextFields(input, "moduleKey", "moduleName", "tagName");
        normalizeOptionalId(input, "groupId", "标签分组ID");
        input.putIfAbsent("isCommon", "0");
        validateFlag(input.get("isCommon"), "isCommon");
        return tagController.add(input);
    }

    /** 更新标签全量可编辑字段和状态。 / Update all editable tag fields and its status. */
    @McpTool(name = "tag_update", description = "修改标签名称、功能内分组、常用标记或状态；执行前展示前后差异并取得员工确认，需要 system:tag:edit 权限")
    public R<?> tagUpdate(
        @McpToolParam(description = "完整标签字段对象：tagId、moduleKey、groupId、tagName、isCommon、status；所有ID用十进制字符串，isCommon/status为0或1") Map<String, Object> body) {
        StpUtil.checkPermission("system:tag:edit");
        Map<String, Object> input = requireFields(body,
            Set.of("tagId", "moduleKey", "groupId", "tagName", "isCommon", "status"),
            Set.of("tagId", "moduleKey", "groupId", "tagName", "isCommon", "status"));
        requireTextFields(input, "moduleKey", "tagName");
        normalizeRequiredId(input, "tagId", "标签ID");
        normalizeRequiredId(input, "groupId", "标签分组ID");
        validateFlag(input.get("isCommon"), "isCommon");
        validateFlag(input.get("status"), "status");
        return tagController.edit(input);
    }

    /** 软停用标签并保留历史业务关联。 / Disable a tag while preserving historical business relations. */
    @McpTool(name = "tag_disable", description = "停用标签；保留历史关系并阻止业务新选，执行前取得员工确认，需要 system:tag:remove 权限")
    public R<?> tagDisable(
        @McpToolParam(description = "标签ID十进制字符串") String tagId) {
        StpUtil.checkPermission("system:tag:remove");
        return tagController.disable(McpInputValidator.requirePositiveId(tagId, "标签ID"));
    }

    /** 仅删除没有业务记录关联的标签。 / Delete a tag only when no business record references it. */
    @McpTool(name = "tag_delete", description = "删除未被业务记录引用的标签；删除不可恢复，执行前展示标签并取得员工确认，需要 system:tag:remove 权限")
    public R<?> tagDelete(
        @McpToolParam(description = "标签ID十进制字符串") String tagId) {
        StpUtil.checkPermission("system:tag:remove");
        return tagController.delete(McpInputValidator.requirePositiveId(tagId, "标签ID"));
    }

    /** 新增当前功能内的自定义分组。 / Create a custom group within an existing feature namespace. */
    @McpTool(name = "tag_group_create", description = "新增标签功能内自定义分组；执行前展示功能和分组名并取得员工确认，需要 system:tag:add 权限")
    public R<?> groupCreate(
        @McpToolParam(description = "分组字段对象：moduleKey、groupName；moduleKey先从tag_module_list获取") Map<String, Object> body) {
        StpUtil.checkPermission("system:tag:add");
        Map<String, Object> input = requireFields(body,
            Set.of("moduleKey", "groupName"), Set.of("moduleKey", "groupName"));
        requireTextFields(input, "moduleKey", "groupName");
        return tagController.addGroup(input);
    }

    /** 重命名自定义分组；默认分组由服务端拒绝修改。 / Rename a custom group; the server protects the default group. */
    @McpTool(name = "tag_group_update", description = "重命名功能内自定义分组；默认分组不可重命名，执行前取得员工确认，需要 system:tag:edit 权限")
    public R<?> groupUpdate(
        @McpToolParam(description = "分组字段对象：groupId、moduleKey、groupName；ID使用十进制字符串") Map<String, Object> body) {
        StpUtil.checkPermission("system:tag:edit");
        Map<String, Object> input = requireFields(body,
            Set.of("groupId", "moduleKey", "groupName"), Set.of("groupId", "moduleKey", "groupName"));
        requireTextFields(input, "moduleKey", "groupName");
        normalizeRequiredId(input, "groupId", "标签分组ID");
        return tagController.editGroup(input);
    }

    /** 删除空的自定义分组；默认分组及非空分组由服务端拒绝。 / Delete an empty custom group; the server protects default and non-empty groups. */
    @McpTool(name = "tag_group_delete", description = "删除空的自定义分组；默认分组不可删除且非空分组需先迁出标签，执行前确认，需要 system:tag:remove 权限")
    public R<?> groupDelete(
        @McpToolParam(description = "分组ID十进制字符串") String groupId) {
        StpUtil.checkPermission("system:tag:remove");
        return tagController.deleteGroup(McpInputValidator.requirePositiveId(groupId, "分组ID"));
    }

    /** 拒绝缺失、未知字段，避免 MCP 静默忽略输入后执行偏离员工意图的维护操作。 / Reject missing or unknown fields instead of silently changing the requested operation. */
    private Map<String, Object> requireFields(Map<String, Object> body, Set<String> allowed, Set<String> required) {
        if (body == null) throw new ServiceException("请求对象不能为空");
        for (String key : body.keySet()) {
            if (!allowed.contains(key)) throw new ServiceException("不支持的标签字段：" + key);
        }
        for (String field : required) {
            if (!body.containsKey(field) || body.get(field) == null) {
                throw new ServiceException("缺少必填字段：" + field);
            }
        }
        return body;
    }

    /** 要求业务名称字段是非空字符串，不把数字或对象静默转换成标签名称。 / Require nonblank text fields instead of coercing numbers or objects into names. */
    private void requireTextFields(Map<String, Object> body, String... fields) {
        for (String field : fields) {
            if (!(body.get(field) instanceof String value) || value.isBlank()) {
                throw new ServiceException(field + "必须是非空字符串");
            }
        }
    }

    /** 限定标志字段只能传字符串0或1，避免错误值被控制器默认解释为0。 / Accept only documented string flags. */
    private void validateFlag(Object value, String field) {
        McpInputValidator.requireBinaryFlag(value, field);
    }

    /** 将通过校验的可选 ID 写回规范十进制字符串；空值保持未提供语义。 / Normalize an optional validated ID before passing it to legacy controller parsing. */
    private void normalizeOptionalId(Map<String, Object> body, String key, String field) {
        Long id = McpInputValidator.optionalPositiveId(body.get(key), field);
        if (id == null) {
            body.remove(key);
        } else {
            body.put(key, id.toString());
        }
    }

    /** 将必填主键规范化为十进制字符串，避免下游宽松解析器接收原始空格或 JSON 数字。 / Normalize a required ID before invoking the downstream controller. */
    private void normalizeRequiredId(Map<String, Object> body, String key, String field) {
        body.put(key, McpInputValidator.requirePositiveId(body.get(key), field).toString());
    }
}
