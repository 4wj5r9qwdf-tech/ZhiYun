/*
 * 文件用途：向受控智能体暴露织云 ERP 的尺码 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的尺码 MCP 工具能力。
 * 设计思路：工具契约以显式权限、输入白名单、当前用户身份和数据范围为边界，复用既有业务实现。
 * 实现方法：解析参数、校验身份权限和格式、调用业务层并按最小必要字段返回结构化结果。
 * 关键约束：不得扩大员工数据范围或返回非必要敏感信息。
 * 依赖与引用：Docs/API/API文档.md；Docs/AI/织云AI集成总体规划.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP写工具确认契约；补齐尺码和尺码组写入的调用前展示
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（MCP 工具契约测试覆盖全部命名写工具；只读分类回归覆盖批量标签替换）
 * 测试范围与记录位置：McpToolContractTest、SnailAiMcpToolPolicyTest；Maven Surefire 报告
 */
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.system.controller.system.SysConfigController;
import org.dromara.web.controller.SizeDataController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 尺码与尺码组 MCP 工具，复用现有 REST 控制器的校验、事务和审计逻辑。
 * Size and size-group MCP tools reuse the existing REST validation, transactions, and audit behavior.
 */
@Component
@RequiredArgsConstructor
public class SizeMcpTools {

    private final SizeDataController sizeController;
    private final SysConfigController configController;

    // 尺码管理 size

    /** 按当前员工权限查询尺码及标签。 / List sizes and tags under the current employee permission. */
    @McpTool(name = "size_list", description = "查询尺码档案及类型标签，需要 system:size:list 权限")
    public R<?> sizeList() {
        StpUtil.checkPermission("system:size:list");
        return sizeController.list("size");
    }

    /** 查询业务选择尺码，停用项仅用于历史回显。 / Read size options; disabled items are for historical display only. */
    @McpTool(name = "size_options", description = "查询尺码业务选项；停用尺码仅用于历史回显，需要员工登录")
    public R<?> sizeOptions() {
        StpUtil.checkLogin();
        return sizeController.options("size");
    }

    /** 新增尺码前必须展示完整字段并取得员工确认。 / Create a size only after presenting the full payload and receiving confirmation. */
    @McpTool(name = "size_create", description = "新增尺码档案；执行前展示编码、名称、标签等完整字段并取得员工确认，需要 system:size:add 权限")
    public R<?> sizeCreate(@McpToolParam(description = "尺码字段对象：code、name、name_en、sort_order、remark、tagIds；编码可按规则留空生成") Map<String, Object> body) {
        StpUtil.checkPermission("system:size:add");
        return sizeController.add("size", body);
    }

    /** 修改尺码完整字段和标签集合。 / Update the full size fields and final tag set. */
    @McpTool(name = "size_update", description = "修改尺码及其完整类型标签集合；调用前先读取当前记录，向员工展示尺码字段和完整标签集合的前后差异，并取得本次明确确认，需要 system:size:edit 权限")
    public R<?> sizeUpdate(@McpToolParam(description = "尺码ID十进制字符串") String id,
                           @McpToolParam(description = "完整尺码字段对象：code、name、name_en、sort_order、remark、tagIds") Map<String, Object> body) {
        StpUtil.checkPermission("system:size:edit");
        return sizeController.edit("size", McpInputValidator.requirePositiveId(id, "尺码ID"), body);
    }

    /** 启停尺码但保留已有尺码组引用。 / Change size availability while preserving existing group references. */
    @McpTool(name = "size_set_status", description = "启用或停用尺码；执行前读取并展示尺码编码、名称、当前及目标状态，说明停用不清除尺码组历史引用，并取得员工本次明确确认，需要 system:size:edit 权限")
    public R<?> sizeSetStatus(@McpToolParam(description = "尺码ID十进制字符串") String id,
                              @McpToolParam(description = "目标状态：0启用、1停用") String status) {
        StpUtil.checkPermission("system:size:edit");
        return sizeController.status("size", McpInputValidator.requirePositiveId(id, "尺码ID"),
            Map.of("status", McpInputValidator.requireBinaryFlag(status, "尺码状态")));
    }

    /** 删除未被尺码组引用的尺码。 / Delete a size only when no size group references it. */
    @McpTool(name = "size_delete", description = "删除尺码；被尺码组引用时后端会拒绝，执行前展示编码名称并取得本次确认，需要 system:size:delete 权限")
    public R<?> sizeDelete(@McpToolParam(description = "尺码ID十进制字符串") String id) {
        StpUtil.checkPermission("system:size:delete");
        return sizeController.delete("size", McpInputValidator.requirePositiveId(id, "尺码ID"));
    }

    /** 批量替换选中尺码的最终类型标签集合。 / Replace the final type-tag set for selected sizes. */
    @McpTool(name = "size_batch_set_tags", description = "批量替换尺码类型标签；空标签列表会清除所选尺码全部类型标签，最多500条，执行前展示影响并取得确认，需要 system:size:edit 权限")
    public R<?> sizeBatchSetTags(@McpToolParam(description = "批量标签字段：ids为尺码ID数组，tagIds为最终标签ID数组；ID均用十进制字符串") Map<String, Object> body) {
        StpUtil.checkPermission("system:size:edit");
        return sizeController.batchTags("size", body);
    }

    /** 只读取固定尺码编码规则参数，不开放通用系统参数读取。 / Read only the fixed size-code rule key. */
    @McpTool(name = "size_get_code_rule", description = "读取尺码自动编码规则，仅读取固定参数键 system.code.size，需要员工登录")
    public R<?> sizeGetCodeRule() {
        StpUtil.checkLogin();
        return configController.getConfigKey("system.code.size");
    }

    /** 更新尺码编码规则前必须展示完整新旧规则并取得确认。 / Update the size-code rule after confirming old and new values. */
    @McpTool(name = "size_set_code_rule", description = "修改尺码自动编码开关、前缀和位数；执行前展示旧新规则并取得确认，需要 system:config:edit 权限")
    public R<?> sizeSetCodeRule(@McpToolParam(description = "规则对象：enabled布尔值、prefix字符串、digits整数1到10") Map<String, Object> body) {
        StpUtil.checkPermission("system:config:edit");
        return configController.updateSizeCodeRule(body);
    }

    // 尺码组管理 size_group

    /** 按权限查询尺码组、组内尺码顺序及标签。 / List size groups with ordered members and tags. */
    @McpTool(name = "size_group_list", description = "查询尺码组、组内尺码顺序和类型标签，需要 system:size-group:list 权限")
    public R<?> sizeGroupList() {
        StpUtil.checkPermission("system:size-group:list");
        return sizeController.list("size-group");
    }

    /** 查询业务选择尺码组，停用项仅用于历史回显。 / Read size-group options; disabled items are for historical display only. */
    @McpTool(name = "size_group_options", description = "查询尺码组业务选项及组内尺码；停用组仅用于历史回显，需要员工登录")
    public R<?> sizeGroupOptions() {
        StpUtil.checkLogin();
        return sizeController.options("size-group");
    }

    /** 新增尺码组前展示成员顺序并取得员工确认。 / Create a size group after confirming its ordered members. */
    @McpTool(name = "size_group_create", description = "新增尺码组；调用前向员工展示编码、名称、标签和有序尺码成员并取得本次明确确认，需要 system:size-group:add 权限")
    public R<?> sizeGroupCreate(@McpToolParam(description = "尺码组对象：code、name、name_en、sort_order、remark、tagIds、sizeIds；至少包含一个尺码，顺序即组内顺序") Map<String, Object> body) {
        StpUtil.checkPermission("system:size-group:add");
        return sizeController.add("size-group", body);
    }

    /** 修改尺码组档案、成员顺序和完整标签集合。 / Update a size group, ordered members, and final tags. */
    @McpTool(name = "size_group_update", description = "修改尺码组及组内尺码顺序；调用前先读取当前组，向员工展示完整前后差异及被移除的尺码/标签影响，并取得本次明确确认，需要 system:size-group:edit 权限")
    public R<?> sizeGroupUpdate(@McpToolParam(description = "尺码组ID十进制字符串") String id,
                                @McpToolParam(description = "完整尺码组对象：code、name、name_en、sort_order、remark、tagIds、sizeIds") Map<String, Object> body) {
        StpUtil.checkPermission("system:size-group:edit");
        return sizeController.edit("size-group", McpInputValidator.requirePositiveId(id, "尺码组ID"), body);
    }

    /** 启停尺码组并保留历史引用。 / Change size-group availability and preserve historical references. */
    @McpTool(name = "size_group_set_status", description = "启用或停用尺码组；执行前读取并展示编码、名称、当前及目标状态，并取得员工本次明确确认，需要 system:size-group:edit 权限")
    public R<?> sizeGroupSetStatus(@McpToolParam(description = "尺码组ID十进制字符串") String id,
                                   @McpToolParam(description = "目标状态：0启用、1停用") String status) {
        StpUtil.checkPermission("system:size-group:edit");
        return sizeController.status("size-group", McpInputValidator.requirePositiveId(id, "尺码组ID"),
            Map.of("status", McpInputValidator.requireBinaryFlag(status, "尺码组状态")));
    }

    /** 删除尺码组以及控制器事务内清理的成员和标签关联。 / Delete a group and its transactional member/tag links. */
    @McpTool(name = "size_group_delete", description = "删除尺码组并清理组内尺码关系；不可恢复，执行前展示编码名称并取得本次确认，需要 system:size-group:delete 权限")
    public R<?> sizeGroupDelete(@McpToolParam(description = "尺码组ID十进制字符串") String id) {
        StpUtil.checkPermission("system:size-group:delete");
        return sizeController.delete("size-group", McpInputValidator.requirePositiveId(id, "尺码组ID"));
    }

    /** 只读取固定尺码组编码规则参数。 / Read only the fixed size-group code rule key. */
    @McpTool(name = "size_group_get_code_rule", description = "读取尺码组自动编码规则，仅读取固定参数键 system.code.sizeGroup，需要员工登录")
    public R<?> sizeGroupGetCodeRule() {
        StpUtil.checkLogin();
        return configController.getConfigKey("system.code.sizeGroup");
    }

    /** 更新尺码组编码规则前展示完整新旧规则并取得员工确认。 / Update size-group code rules after confirming old and new values. */
    @McpTool(name = "size_group_set_code_rule", description = "修改尺码组自动编码开关、前缀和位数；执行前展示旧新规则并取得确认，需要 system:config:edit 权限")
    public R<?> sizeGroupSetCodeRule(@McpToolParam(description = "规则对象：enabled布尔值、prefix字符串、digits整数1到10") Map<String, Object> body) {
        StpUtil.checkPermission("system:config:edit");
        return configController.updateSizeGroupCodeRule(body);
    }
}
