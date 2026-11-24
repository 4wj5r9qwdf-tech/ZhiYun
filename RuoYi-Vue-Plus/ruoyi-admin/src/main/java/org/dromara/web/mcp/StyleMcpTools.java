/*
 * 文件用途：向受控智能体暴露织云 ERP 的款式档案 MCP 工具能力。
 * 文件版本：1.0.0
 * 功能目的：向受控智能体暴露织云 ERP 的款式档案 MCP 工具能力。
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
 * 修改标记：ZY-ERP-20261121-款式 MCP 输入契约；校验标量白名单、嵌套集合和生产模式适用字段
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（MCP 通用工具契约 9 项、款式输入契约 5 项）
 * 测试范围与记录位置：McpToolContractTest、StyleMcpInputContractTest；Maven Surefire 报告
 */
package org.dromara.web.mcp;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.web.controller.StyleController;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** 款式 MCP 复用控制器的分类授权、事务与业务审计。 / Reuse style authorization, transactions and audit. */
@Component
@RequiredArgsConstructor
public class StyleMcpTools {
    private final StyleController controller;

    /**
     * 在复用款式控制器前校验款式列表权限，避免 MCP 调用绕过页面权限边界。
     * Check style-list permission before delegating to the controller so MCP cannot bypass the UI authorization boundary.
     */
    private void requireList() {
        StpUtil.checkPermission("rd:style:list");
    }
    /** 通过 MCP 查询当前人员可见的款式档案，遵循分类范围限制。 / List styles visible to the caller through MCP while enforcing category scope. */

    @McpTool(name = "style_list", description = "查询当前员工有权查看的款式及规格、套装组成，需要 rd:style:list 权限")
    public R<?> list() {
        requireList();
        return controller.list();
    }
    /** 提供当前人员可访问的款式分类树。 / Provide the caller-scoped style category tree. */

    @McpTool(name = "style_category_options", description = "查询当前员工可使用的款式分类，需要 rd:style:list 权限")
    public R<?> categoryOptions() {
        requireList();
        return controller.categoryOptions();
    }
    /** 提供款式颜色、尺码和套装规格选项。 / Provide selectable style colors, sizes and set specifications. */

    @McpTool(name = "style_spec_options", description = "查询颜色、尺码及尺码组，需要 rd:style:list 权限")
    public R<?> specOptions() {
        requireList();
        return controller.specOptions();
    }
    /** 读取款式主档、套装子款及已配置规格。 / Read style data, set members and configured specifications. */

    @McpTool(name = "style_configuration", description = "查询款式颜色、尺码和套装子款，受人员分类权限约束，需要 rd:style:list 权限")
    public R<?> configuration(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        return controller.configuration(McpInputValidator.requirePositiveId(id, "款式ID"));
    }
    /** 查询款式功能可用的标签及分组。 / List tags and groups available to the style feature. */

    @McpTool(name = "style_tags", description = "查询款式已关联的标签及分组，需要 rd:style:list 权限")
    public R<?> tags(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        return controller.tags(McpInputValidator.requirePositiveId(id, "款式ID"));
    }
    /** 通过 MCP 创建款式并保留服务端分类与字段校验。 / Create a style through MCP while retaining server-side authorization and validation. */

    @McpTool(name = "style_create", description = "新增单款或套装；MCP适配器递归校验ID字符串、对象字段与数组元素；调用前向员工展示本次主档、生产方式、规格、子款和附件/主图及关联影响，并取得本次明确确认；需要 rd:style:list 与 rd:style:add 权限")
    public R<?> create(@McpToolParam(description = "款式字段对象：styleCode、name、categoryId必填；其余字段、嵌套对象和空值语义见款式档案API字段矩阵") Map<String, Object> body) {
        requireList();
        StpUtil.checkPermission("rd:style:add");
        return controller.create(JsonUtils.parseObject(JsonUtils.toJsonString(styleBody(body, true)), StyleController.StyleInput.class));
    }
    /** 通过 MCP 更新款式并保留审计及关联校验。 / Update a style through MCP with auditing and reference validation. */

    @McpTool(name = "style_update", description = "完整替换款式标量字段并按提供的关联集合维护规格；省略关联集合保留现值，空数组清除相应集合；调用前展示款号/名称、所有标量字段变化、关联最终集合及被移除项影响并取得本次明确确认；需要 rd:style:list 与 rd:style:edit 权限")
    public R<?> update(@McpToolParam(description = "款式ID，十进制字符串") String id,
                       @McpToolParam(description = "完整款式资料：必须包含styleCode、name、customerId、categoryId、season、description、coverOssId、remark、productionMode、sameHanger；关联集合可省略表示保留。所有嵌套ID使用十进制字符串，字段表见款式档案API") Map<String, Object> body) {
        requireList();
        StpUtil.checkPermission("rd:style:edit");
        return controller.update(McpInputValidator.requirePositiveId(id, "款式ID"), JsonUtils.parseObject(JsonUtils.toJsonString(styleBody(body, false)), StyleController.StyleInput.class));
    }
    /** 通过 MCP 调整款式状态并保护历史业务引用。 / Change style status through MCP while protecting historical references. */

    @McpTool(name = "style_set_status", description = "启用或停用款式；调用前读取目标款式并展示款号/名称、当前状态、目标状态及状态变化影响，取得本次明确确认；需要 rd:style:list 与 rd:style:edit 权限")
    public R<?> status(@McpToolParam(description = "款式ID，十进制字符串") String id,
                       @McpToolParam(description = "目标状态，0启用、1停用") String status) {
        requireList();
        StpUtil.checkPermission("rd:style:edit");
        return controller.status(McpInputValidator.requirePositiveId(id, "款式ID"),
            Map.of("status", McpInputValidator.requireBinaryFlag(status, "款式状态")));
    }
    /** 通过 MCP 删除未被引用的款式，引用存在时说明拒绝原因。 / Delete an unreferenced style through MCP and explain reference-based rejection. */

    @McpTool(name = "style_delete", description = "删除款式；调用前先读取目标记录，向员工展示款号、名称及删除对象，并说明仅无业务引用的款式可删除、存在引用时服务端会拒绝，取得本次明确确认；需要 rd:style:list 与 rd:style:remove 权限")
    public R<?> delete(@McpToolParam(description = "款式ID，十进制字符串") String id) {
        requireList();
        StpUtil.checkPermission("rd:style:remove");
        return controller.delete(McpInputValidator.requirePositiveId(id, "款式ID"));
    }

    /** 递归校验款式 Map 与全部嵌套 Map/数组，确保每个 bigint 先以字符串验证再解析为 DTO Long。 / Recursively validate style maps and string IDs before DTO conversion. */
    Map<String, Object> styleBody(Map<String, Object> body, boolean creating) {
        if (body == null) throw new org.dromara.common.core.exception.ServiceException("款式字段对象不能为空");
        Set<String> fields = Set.of("styleCode", "name", "customerId", "categoryId", "season", "description", "coverOssId", "remark", "tagIds", "productionMode", "sameHanger", "members", "colorIds", "sizeIds", "excludedPairs", "attachments", "setSpecs");
        for (String key : body.keySet()) if (!fields.contains(key)) throw new org.dromara.common.core.exception.ServiceException("不支持的款式字段：" + key);
        Set<String> required = creating
            ? Set.of("styleCode", "name", "categoryId")
            : Set.of("styleCode", "name", "customerId", "categoryId", "season", "description", "coverOssId", "remark", "productionMode", "sameHanger");
        if (!body.keySet().containsAll(required)) throw new org.dromara.common.core.exception.ServiceException("款式字段对象缺少必填字段");

        validateText(body, "styleCode", true, 60);
        validateText(body, "name", true, 120);
        validateText(body, "season", false, 40);
        validateText(body, "description", false, 1000);
        validateText(body, "remark", false, 500);
        validateId(body, "categoryId", true);
        validateId(body, "customerId", false);
        validateId(body, "coverOssId", false);
        if (body.containsKey("productionMode") && body.get("productionMode") != null
            && (!(body.get("productionMode") instanceof String mode) || !Set.of("single", "combined", "separate").contains(mode))) {
            throw new org.dromara.common.core.exception.ServiceException("productionMode必须是single、combined或separate");
        }
        if (body.containsKey("sameHanger") && body.get("sameHanger") != null && !(body.get("sameHanger") instanceof Boolean)) {
            throw new org.dromara.common.core.exception.ServiceException("sameHanger必须是JSON布尔值或null");
        }
        validateIdList(body, "tagIds", 100);
        validateIdList(body, "colorIds", 100);
        validateIdList(body, "sizeIds", 100);
        validatePairs(body.get("excludedPairs"));
        validateMembers(body.get("members"));
        validateAttachments(body.get("attachments"));
        validateSetSpecs(body.get("setSpecs"));

        boolean anySpec = body.get("colorIds") != null || body.get("sizeIds") != null || body.get("excludedPairs") != null;
        if (anySpec && (body.get("colorIds") == null || body.get("sizeIds") == null || body.get("excludedPairs") == null)) {
            throw new org.dromara.common.core.exception.ServiceException("颜色、尺码和排除组合必须一并提交；省略全部表示保留，空数组表示清空");
        }
        if (anySpec && body.get("productionMode") instanceof String mode && !"single".equals(mode)) {
            throw new org.dromara.common.core.exception.ServiceException("合并或分开生产套装不能提交单款颜色、尺码和排除组合字段");
        }
        return new LinkedHashMap<>(body);
    }

    /** 校验字符串字段，不把数字、布尔或嵌套 JSON 隐式转为文本。 / Validate text values without coercing other JSON types. */
    private void validateText(Map<String, Object> body, String key, boolean required, int max) {
        if (!body.containsKey(key)) {
            if (required) throw new org.dromara.common.core.exception.ServiceException(key + "不能为空");
            return;
        }
        Object raw = body.get(key);
        if (raw == null) {
            if (required) throw new org.dromara.common.core.exception.ServiceException(key + "不能为空");
            return;
        }
        if (!(raw instanceof String text)) throw new org.dromara.common.core.exception.ServiceException(key + "必须是字符串或null");
        if (text.strip().length() > max) throw new org.dromara.common.core.exception.ServiceException(key + "不能超过" + max + "字");
        if (required && text.isBlank()) throw new org.dromara.common.core.exception.ServiceException(key + "不能为空");
    }

    /** 验证单个必填或可空 ID 字段；验证通过后原字符串传入 JSON DTO 转换。 / Validate a scalar ID and preserve its decimal-string representation. */
    private void validateId(Map<String, Object> body, String key, boolean required) {
        if (!body.containsKey(key)) {
            if (required) throw new org.dromara.common.core.exception.ServiceException(key + "必须使用十进制字符串");
            return;
        }
        Object raw = body.get(key);
        if (raw == null && !required) return;
        McpInputValidator.requirePositiveId(raw, key);
    }

    /** 校验可空 ID 数组的类型、长度、字符串格式和唯一性。 / Validate a nullable ID array's type, size, string format and uniqueness. */
    private void validateIdList(Map<String, Object> body, String key, int max) {
        if (!body.containsKey(key) || body.get(key) == null) return;
        Object raw = body.get(key);
        if (!(raw instanceof List<?> values) || values.size() > max) throw new org.dromara.common.core.exception.ServiceException(key + "必须是最多" + max + "个十进制字符串ID的数组");
        Set<Long> ids = new HashSet<>();
        for (Object value : values) if (!ids.add(McpInputValidator.requirePositiveId(value, key + "成员"))) {
            throw new org.dromara.common.core.exception.ServiceException(key + "不能包含重复ID");
        }
    }

    /** 校验排除规格对象及其中的颜色、尺码 ID。 / Validate excluded color-size pairs. */
    private void validatePairs(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof List<?> pairs) || pairs.size() > 10000) throw new org.dromara.common.core.exception.ServiceException("excludedPairs必须是数组");
        Set<String> seen = new HashSet<>();
        for (Object value : pairs) {
            Map<String, Object> pair = object(value, "excludedPairs成员", Set.of("colorId", "sizeId"), Set.of("colorId", "sizeId"));
            String color = McpInputValidator.requirePositiveId(pair.get("colorId"), "colorId").toString();
            String size = McpInputValidator.requirePositiveId(pair.get("sizeId"), "sizeId").toString();
            if (!seen.add(color + ":" + size)) throw new org.dromara.common.core.exception.ServiceException("excludedPairs不能包含重复组合");
        }
    }

    /** 校验套装子款对象；每个子款 ID 唯一，数量必须为正整数。 / Validate unique child styles and positive quantities. */
    private void validateMembers(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof List<?> members) || members.size() > 100) throw new org.dromara.common.core.exception.ServiceException("members必须是最多100项的数组");
        Set<Long> children = new HashSet<>();
        for (Object value : members) {
            Map<String, Object> member = object(value, "members成员", Set.of("childId", "roleName", "quantity"), Set.of("childId", "roleName", "quantity"));
            Long child = McpInputValidator.requirePositiveId(member.get("childId"), "childId");
            if (!children.add(child)) throw new org.dromara.common.core.exception.ServiceException("members不能重复引用同一子款");
            if (!(member.get("roleName") instanceof String role) || role.isBlank() || role.strip().length() > 60) throw new org.dromara.common.core.exception.ServiceException("roleName必须是1至60字的字符串");
            if (!(member.get("quantity") instanceof Integer || member.get("quantity") instanceof Long) || ((Number) member.get("quantity")).longValue() < 1 || ((Number) member.get("quantity")).longValue() > Integer.MAX_VALUE) throw new org.dromara.common.core.exception.ServiceException("quantity必须是正JSON整数");
        }
    }

    /** 校验私有文件附件的文件 ID、类别和重复引用。 / Validate attachment file IDs, categories, and duplicates. */
    private void validateAttachments(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof List<?> attachments) || attachments.size() > 101) throw new org.dromara.common.core.exception.ServiceException("attachments必须是最多101项的数组");
        Set<Long> ids = new HashSet<>();
        Set<String> categories = Set.of("style_image", "main_label", "size_label", "wash_label", "hang_tag", "tag_sticker", "document");
        for (Object value : attachments) {
            Map<String, Object> attachment = object(value, "attachments成员", Set.of("ossId", "category"), Set.of("ossId", "category"));
            Long id = McpInputValidator.requirePositiveId(attachment.get("ossId"), "ossId");
            if (!ids.add(id)) throw new org.dromara.common.core.exception.ServiceException("attachments不能重复引用文件");
            if (!(attachment.get("category") instanceof String category) || !categories.contains(category)) throw new org.dromara.common.core.exception.ServiceException("attachments.category无效");
        }
    }

    /** 校验完整套装规格及每个子款颜色/尺码选择。 / Validate each complete set specification and member pairing. */
    private void validateSetSpecs(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof List<?> specs) || specs.size() > 100) throw new org.dromara.common.core.exception.ServiceException("setSpecs必须是最多100项的数组");
        Set<Long> ids = new HashSet<>();
        for (Object value : specs) {
            Map<String, Object> spec = object(value, "setSpecs成员", Set.of("id", "colorName", "sizeName", "remark", "items"), Set.of("colorName", "sizeName", "items"));
            validateId(spec, "id", false);
            if (spec.get("id") != null && !ids.add(McpInputValidator.requirePositiveId(spec.get("id"), "setSpecs.id"))) throw new org.dromara.common.core.exception.ServiceException("setSpecs不能重复引用同一规格ID");
            for (String key : Set.of("colorName", "sizeName")) {
                if (!(spec.get(key) instanceof String text) || text.isBlank() || text.strip().length() > 120) throw new org.dromara.common.core.exception.ServiceException(key + "必须是1至120字的字符串");
            }
            validateText(spec, "remark", false, 500);
            Object itemsRaw = spec.get("items");
            if (!(itemsRaw instanceof List<?> items) || items.size() > 100) throw new org.dromara.common.core.exception.ServiceException("setSpecs.items必须是最多100项的数组");
            Set<Long> children = new HashSet<>();
            for (Object itemRaw : items) {
                Map<String, Object> item = object(itemRaw, "setSpecs.items成员", Set.of("childId", "colorId", "sizeId"), Set.of("childId", "colorId", "sizeId"));
                Long child = McpInputValidator.requirePositiveId(item.get("childId"), "childId");
                if (!children.add(child)) throw new org.dromara.common.core.exception.ServiceException("setSpecs.items不能重复子款");
                McpInputValidator.requirePositiveId(item.get("colorId"), "colorId");
                McpInputValidator.requirePositiveId(item.get("sizeId"), "sizeId");
            }
        }
    }

    /** 限制嵌套对象键集合并复制为有序字符串键 Map。 / Enforce nested object key allowlists. */
    private Map<String, Object> object(Object raw, String field, Set<String> allowed, Set<String> required) {
        if (!(raw instanceof Map<?, ?> values)) throw new org.dromara.common.core.exception.ServiceException(field + "必须是对象");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !allowed.contains(key)) throw new org.dromara.common.core.exception.ServiceException(field + "包含不支持的字段");
            result.put(key, entry.getValue());
        }
        if (!result.keySet().containsAll(required)) throw new org.dromara.common.core.exception.ServiceException(field + "缺少必填字段");
        return result;
    }
}
