package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 规格模板目录与保存校验。模板按稳定编码选择，字段按稳定键保留旧值。
 * Validate template selections and preserve historical values by stable field key.
 */
@Service
@RequiredArgsConstructor
public class MaterialSpecService {
    private static final Set<String> LEGACY_FIELDS = Set.of("fabricWeightGsm", "fabricWidthCm", "composition");
    private static final String SELECT = """
        SELECT template_code AS code, template_name AS name, active, fields::text AS fields
        FROM inv_material_spec_template
        """;
    private final JdbcTemplate jdbc;

    public record Field(String key, String label, String type, String unit, Boolean required, List<String> options,
                        String placeholder) {
        /** 归一化可选标记和选项集合，保证 API 返回稳定的非空结构。 / Normalize optional flags and choices so the API always exposes a stable non-null shape. */
        public Field {
            required = Boolean.TRUE.equals(required);
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
    /** 一个可用于物料建档的规格字段模板及其字段定义。 / A material-specification template and its ordered field definitions. */
    public record Template(String code, String name, boolean active, List<Field> fields) {}
    /** 经校验的模板选择、规格值及需同步旧列的兼容字段。 / A validated template selection, its values and compatibility-column updates. */
    public record Selection(String code, Map<String, Object> values, Map<String, Object> legacyUpdates) {}

    /** 停用模板仍供编辑旧物料回显，由保存校验阻止新的引用。 / Include retired templates for existing records. */
    public List<Template> catalog() {
        return jdbc.query(SELECT + " ORDER BY sort_order,template_code", (rs, row) ->
            new Template(rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                JsonUtils.parseArray(rs.getString("fields"), Field.class)));
    }

    /** 将历史规格 JSON 解析为可修改的有序映射，空 JSON 返回空映射。 / Parse stored specification JSON into a mutable ordered map, using an empty map for blank data. */
    public static Map<String, Object> readValues(String json) {
        return new LinkedHashMap<>(JsonUtils.parseMap(json));
    }

    /**
     * 更新时仅接受当前模板定义的字段。切换模板后保留旧字段，后续切回时可恢复。
     * Accept only active template fields and retain previous values for future re-selection.
     */
    public Selection resolve(Long id, Map<String, Object> body) {
        Map<String, Object> previous = id == null ? Map.of() : jdbc.queryForMap(
            "SELECT spec_template_code AS code, spec_values::text AS values FROM inv_material WHERE material_id=?", id);
        String oldCode = Objects.toString(previous.get("code"), "");
        String code = body.containsKey("specTemplateCode")
            ? Objects.toString(body.get("specTemplateCode"), "").trim() : oldCode;
        Map<String, Object> values = previous.isEmpty() ? new LinkedHashMap<>() : readValues((String) previous.get("values"));
        Map<String, Object> legacyUpdates = new LinkedHashMap<>();
        Template template = code.isEmpty() ? null : catalog().stream().filter(item -> item.code().equals(code))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("规格模板不存在，请重新选择"));
        if (template != null && !template.active() && !code.equals(oldCode)) {
            throw new IllegalArgumentException("规格模板已停用，请重新选择");
        }
        if (!body.containsKey("specValues") && code.equals(oldCode)) {
            return new Selection(template == null ? null : code, values, legacyUpdates);
        }
        Object raw = body.getOrDefault("specValues", Map.of());
        if (!(raw instanceof Map<?, ?> submitted)) throw new IllegalArgumentException("规格值必须为字段对象");
        List<Field> fields = template == null ? List.of() : template.fields();
        Set<String> keys = fields.stream().map(Field::key).collect(java.util.stream.Collectors.toSet());
        if (!keys.containsAll(submitted.keySet())) throw new IllegalArgumentException("规格值包含当前模板未定义的字段");
        Set<String> scoped = variantFields(template, body.get("variantFields"));
        for (Field field : fields) {
            if (scoped.contains(field.key())) {
                if (LEGACY_FIELDS.contains(field.key())) legacyUpdates.put(field.key(), null);
                values.remove(field.key());
                continue;
            }
            Object input = submitted.get(field.key());
            if (!submitted.containsKey(field.key())) {
                if (field.required()) throw new IllegalArgumentException("请填写" + field.label());
                continue;
            }
            Object value = validate(field, input);
            if (LEGACY_FIELDS.contains(field.key())) {
                legacyUpdates.put(field.key(), value);
            } else if (value == null) {
                values.remove(field.key());
            } else {
                values.put(field.key(), value);
            }
        }
        return new Selection(template == null ? null : code, values, legacyUpdates);
    }

    /** 仅允许当前模板字段下放到规格。 / Restrict variant fields to the current template. */
    public Set<String> variantFields(Template template, Object raw) {
        if (raw == null) return Set.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("按规格填写字段格式无效");
        Set<String> keys = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String key) || template == null || template.fields().stream().noneMatch(field -> field.key().equals(key))) {
                throw new IllegalArgumentException("按规格填写字段不属于当前属性模板");
            }
            keys.add(key);
        }
        return keys;
    }

    /** 按共用属性相同规则校验规格值，并定位缺项。 / Validate variant values with the same template rules. */
    public Map<String,Object> variantValues(Template template, Set<String> keys, Object raw, String name, boolean enabled) {
        if (keys.isEmpty()) return Map.of();
        if (!(raw instanceof Map<?,?> input)) throw new IllegalArgumentException("请填写规格「" + name + "」的属性");
        Map<String,Object> result = new LinkedHashMap<>();
        for (Field field : template.fields()) {
            if (!keys.contains(field.key())) continue;
            try {
                Object value = input.get(field.key());
                if (enabled && (value == null || value instanceof String text && text.isBlank())) {
                    throw new IllegalArgumentException("请填写" + field.label());
                }
                if (value != null && !(value instanceof String text && text.isBlank())) result.put(field.key(), validate(field, value));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("规格「" + name + "」：" + e.getMessage());
            }
        }
        return result;
    }

    /** 数值保持十进制文本，避免浮点转换或数据库静默舍入。 / Keep exact decimal strings. */
    private Object validate(Field field, Object input) {
        if (input == null || input instanceof String text && text.isBlank()) {
            if (field.required()) throw new IllegalArgumentException("请填写" + field.label());
            return null;
        }
        if (input instanceof Boolean && "boolean".equals(field.type())) return input;
        if (!(input instanceof String text)) throw new IllegalArgumentException(field.label() + "格式无效");
        String value = text.trim();
        boolean valid = switch (field.type()) {
            case "text" -> value.length() <= 300;
            case "integer" -> value.matches("[0-9]{1,8}");
            case "decimal" -> value.matches("[0-9]{1,8}(\\.[0-9]{1,4})?") && new BigDecimal(value).signum() > 0;
            // 缩率允许负数表示尺寸增长，范围限制在 -100% 到 100%。
            case "signed_decimal" -> value.matches("-?[0-9]{1,3}(\\.[0-9]{1,4})?")
                && new BigDecimal(value).abs().compareTo(BigDecimal.valueOf(100)) <= 0;
            case "select" -> field.options() != null && field.options().contains(value);
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException(field.label() + "格式无效，请核对单位、精度或选项");
        return value;
    }

    /** 以字段名称记录规格变化，旧模板中的隐含历史值不参与当前表单差异。 / Audit visible specifications by label. */
    public Map<String, Object> auditValues(String code, String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (code == null) return result;
        Map<String, Object> values = readValues(json);
        for (Template template : catalog()) {
            if (!template.code().equals(code)) continue;
            result.put("规格模板", template.name());
            for (Field field : template.fields()) {
                if (values.containsKey(field.key())) {
                    String unit = field.unit() == null ? "" : "（" + field.unit() + "）";
                    Object value = values.get(field.key());
                    result.put("规格 · " + field.label() + unit, value instanceof Boolean b ? (b ? "是" : "否") : value);
                }
            }
            break;
        }
        return result;
    }
}
