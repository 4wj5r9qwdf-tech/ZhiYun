package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.web.service.MaterialSpecService;
import org.dromara.web.service.CategoryAccessService;
import org.dromara.web.service.CategoryAccessService.Scope;
import org.dromara.common.core.exception.ServiceException;

/** 物料主档维护；所有分类范围在服务端校验，颜色和单位复用公共基础资料。 / Maintain materials with server-enforced category scope and shared color/unit masters. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/material")
public class MaterialController {
    private static final String CODE_RULE_KEY = "inventory.material.codeRule";
    private static final long CODE_LOCK_KEY = 2026110702L;
    private final JdbcTemplate jdbc;
    private final MaterialSpecService materialSpecs;
    private final CategoryAccessService categoryAccess;

    /** 规格模板供物料录入复用，分类权限仍由物料保存接口检查。 / Load shared templates for material entry. */
    @GetMapping("/spec-templates")
    @SaCheckLogin
    public R<List<MaterialSpecService.Template>> specTemplates() {
        return R.ok(materialSpecs.catalog());
    }

    /** 查询管理目录；先按人员分类权限限制，再做关键字和分类筛选。 / Filter by employee taxonomy scope before returning rows. */
    @GetMapping("/list") @SaCheckPermission("inventory:material:list")
    public R<List<Map<String,Object>>> list(@RequestParam(required=false) String keyword,
                                             @RequestParam(required=false) Long schemeId,
                                             @RequestParam(required=false) Long categoryId,
                                             @RequestParam(required=false) String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT m.material_id::text AS id,m.material_code AS code,m.material_name AS name,
                   m.category_id::text AS "categoryId",s.scheme_id::text AS "schemeId",s.scheme_name AS "schemeName",
                   c.category_name AS "categoryName",m.specification,m.model,m.brand,m.material_desc AS "materialDesc",
                   m.inventory_unit_id::text AS "inventoryUnitId",u.unit_name AS "inventoryUnitName",u.unit_code AS "inventoryUnitCode",
                   m.status,m.sort_order AS "sortOrder",m.remark,
                   trim_scale(m.fabric_weight_gsm)::text AS "fabricWeightGsm",
                   trim_scale(m.fabric_width_cm)::text AS "fabricWidthCm",m.composition,
                   m.spec_template_code AS "specTemplateCode",m.spec_values::text AS "specValues",
                   COALESCE((SELECT json_agg(au.unit_id::text ORDER BY au.unit_id) FROM inv_material_aux_unit au JOIN base_unit child ON child.unit_id=au.unit_id
                             WHERE au.material_id=m.material_id AND au.usage_enabled AND au.conversion_mode='FIXED'
                               AND au.reference_unit_id=m.inventory_unit_id AND child.base_unit_id=m.inventory_unit_id AND au.factor=child.factor),'[]'::json)::text AS "auxiliaryUnitIds",
                   COALESCE((SELECT json_agg(json_build_object('id',co.color_id::text,'code',co.color_code,'name',co.color_name,'value',co.color_value) ORDER BY co.sort_order,co.color_name)
                             FROM inv_material_color mc JOIN base_color co ON co.color_id=mc.color_id WHERE mc.material_id=m.material_id),'[]'::json)::text AS colors
            FROM inv_material m JOIN base_category c ON c.category_id=m.category_id
            JOIN base_category_scheme s ON s.scheme_id=c.scheme_id JOIN base_unit u ON u.unit_id=m.inventory_unit_id
            WHERE m.del_flag='0' """);
        Scope scope = categoryAccess.scope();
        sql.append(" AND ");
        categoryAccess.appendScope(sql,args,"c.category_id",scope);
        if (schemeId != null) { sql.append(" AND s.scheme_id=?"); args.add(schemeId); }
        if (categoryId != null) { sql.append(" AND c.category_id=?"); args.add(categoryId); }
        if (status != null && Set.of("0","1").contains(status)) { sql.append(" AND m.status=?"); args.add(status); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (m.material_code ILIKE ? OR m.material_name ILIKE ? OR m.specification ILIKE ? OR m.model ILIKE ? OR m.brand ILIKE ?)"); String q="%"+keyword.trim()+"%"; for(int i=0;i<5;i++) args.add(q); }
        sql.append(" ORDER BY m.sort_order,m.material_code,m.material_id");
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String,Object> row : rows) {
            row.put("specValues", MaterialSpecService.readValues((String) row.get("specValues")));
            row.put("colors", JsonUtils.parseArrayMap((String) row.get("colors")));
            row.put("auxiliaryUnitIds", JsonUtils.parseArray((String) row.get("auxiliaryUnitIds"), String.class));
            row.put("materialUnits", materialUnits(Long.valueOf(String.valueOf(row.get("id"))), false));
            appendVariants(row, Long.valueOf(row.get("id").toString()), false);
        }
        return R.ok(rows);
    }

    /** 供物料页面安全构建分类树；仅返回授权节点与用于展示路径的祖先名称。 / Return authorized taxonomy nodes and path-only ancestors. */
    @GetMapping("/category-options") @SaCheckLogin
    public R<Map<String,Object>> categoryOptions() {
        return R.ok(categoryAccess.options());
    }

    /** 读取业务默认分类类型；无权访问或已失效时安全回退到全部授权分类。 / Resolve the default taxonomy without exposing inaccessible schemes. */
    @GetMapping("/default-category") @SaCheckLogin
    public R<Map<String,Object>> defaultCategory(){List<String> values=jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key='inventory.material.defaultCategoryScheme'",String.class);String id=values.isEmpty()?"":Objects.toString(values.getFirst(),"");if(id.isBlank())return R.ok(Map.of("schemeId","","invalid",false));Long schemeId=number(id);if(schemeId==null||jdbc.queryForList("SELECT scheme_id FROM base_category_scheme WHERE scheme_id=? AND status='0' AND del_flag='0'",schemeId).isEmpty())return R.ok(Map.of("schemeId","","invalid",true));Scope current=categoryAccess.scope();boolean allowed=current.mode().equals("ALL")||current.schemeIds().contains(id);if(!allowed&&!current.mode().equals("NONE")){for(Long categoryId:jdbc.queryForList("SELECT category_id FROM base_category WHERE scheme_id=? AND del_flag='0'",Long.class,schemeId)){if(categoryAccess.categoryAllowed(categoryId)){allowed=true;break;}}}if(!allowed)return R.ok(Map.of("schemeId","","invalid",true));return R.ok(Map.of("schemeId",id,"invalid",false));}

    /** 提供可编辑编码建议，关闭规则时返回空值。 / Return an editable advisory code. */
    @GetMapping("/next-code") @SaCheckPermission("inventory:material:add")
    public R<Map<String,Object>> nextCode() { CodeRule rule=codeRule(); return R.ok(Map.of("enabled",rule.enabled,"code",rule.enabled?nextCodeValue(rule):"")); }

    /** 新建物料及颜色关系作为一个事务提交。 / Create the material and its color links atomically. */
    @PostMapping @SaCheckPermission("inventory:material:add") @Transactional(rollbackFor=Exception.class) @Log(title="物料档案",businessType=BusinessType.INSERT)
    public R<String> add(@RequestBody Map<String,Object> body) { return save(null,body); }

    /** 更新可维护字段，编码作为稳定标识不允许修改。 / Update mutable fields while keeping the code stable. */
    @PutMapping("/{id}") @SaCheckPermission("inventory:material:edit") @Transactional(rollbackFor=Exception.class) @Log(title="物料档案",businessType=BusinessType.UPDATE)
    public R<String> edit(@PathVariable Long id,@RequestBody Map<String,Object> body) { return save(id,body); }

    /** 停用保留历史引用；启用/停用都记入字段级业务审计。 / Preserve historical references when changing availability. */
    @PutMapping("/{id}/status") @SaCheckPermission("inventory:material:edit") @Transactional(rollbackFor=Exception.class) @Log(title="物料档案状态",businessType=BusinessType.UPDATE)
    public R<Void> status(@PathVariable Long id,@RequestBody Map<String,Object> body) {
        String target=text(body,"status"); if(!Set.of("0","1").contains(target))return R.fail("状态无效");
        Map<String,Object> before=snapshot(id,true); if(before.isEmpty())return R.fail("物料不存在或无权查看");
        if(Objects.equals(before.get("状态"),target))return R.ok();
        jdbc.update("UPDATE inv_material SET status=?,update_by=?,update_time=now() WHERE material_id=?",target,LoginHelper.getUserId(),id);
        BusinessAudit.record(("0".equals(target)?"启用":"停用")+"物料",id,Objects.toString(before.get("物料名称"),"物料"),before,snapshot(id,false)); return R.ok();
    }

    /** 仅删除未被其他资料引用的物料；数据库外键保护后续业务引用。 / Delete only materials without references. */
    @DeleteMapping("/{id}") @SaCheckPermission("inventory:material:remove") @Transactional(rollbackFor=Exception.class) @Log(title="物料档案",businessType=BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {
        Map<String,Object> before=snapshot(id,true);if(before.isEmpty())return R.fail("物料不存在或无权查看");
        if(hasForeignReferences(id))return R.fail("该物料已被业务记录引用，不能删除；如不再使用请停用");
        jdbc.update("DELETE FROM inv_material_color WHERE material_id=?",id);
        jdbc.update("DELETE FROM inv_material WHERE material_id=?",id);
        BusinessAudit.record("删除物料",id,Objects.toString(before.get("物料名称"),"物料"),before,Map.of("已删除",true));return R.ok();
    }

    /** 业务选项只返回授权范围内的启用物料，停用项不用于新引用。 / Return only enabled materials within the caller's category scope. */
    @GetMapping("/options") @SaCheckLogin
    public R<List<Map<String,Object>>> options(@RequestParam(required=false) String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT m.material_id::text AS id,m.material_code AS code,m.material_name AS name,m.specification,m.model,m.brand,
                   m.spec_template_code AS "specTemplateCode",m.spec_values::text AS "specValues",m.variant_fields::text AS "variantFields",
                   (SELECT template_name FROM inv_material_spec_template WHERE template_code=m.spec_template_code) AS "templateName",
                   m.fabric_weight_gsm::text AS "fabricWeightGsm",m.fabric_width_cm::text AS "fabricWidthCm",m.composition,
                   COALESCE((SELECT string_agg(co.color_name, '、' ORDER BY co.sort_order,co.color_name)
                       FROM inv_material_color mc JOIN base_color co ON co.color_id=mc.color_id
                       WHERE mc.material_id=m.material_id AND co.status='0'), '') AS "colorsText",
                   m.category_id::text AS "categoryId",c.category_name AS "categoryName",
                   cs.scheme_name || ' / ' || c.category_name AS "categoryLabel",
                   u.unit_id::text AS "inventoryUnitId",u.unit_name AS "inventoryUnitName",
                   COALESCE((SELECT json_agg(child.unit_id::text ORDER BY child.sort_order,child.unit_id)
                             FROM inv_material_aux_unit au JOIN base_unit child ON child.unit_id=au.unit_id
                             WHERE au.material_id=m.material_id AND au.usage_enabled AND au.conversion_mode='FIXED'
                               AND au.reference_unit_id=m.inventory_unit_id AND child.base_unit_id=m.inventory_unit_id AND au.factor=child.factor
                               AND child.status='0' AND child.del_flag='0' AND u.status='0' AND u.del_flag='0'
                             ),'[]'::json)::text AS "auxiliaryUnitIds"
            FROM inv_material m JOIN base_category c ON c.category_id=m.category_id
            JOIN base_category_scheme cs ON cs.scheme_id=c.scheme_id
            JOIN base_unit u ON u.unit_id=m.inventory_unit_id
            WHERE m.del_flag='0' AND m.status='0' AND
            """);
        List<Long> categoryIds=jdbc.queryForList("SELECT c.category_id FROM base_category c JOIN base_category_scheme s ON s.scheme_id=c.scheme_id WHERE c.status='0' AND c.del_flag='0' AND s.status='0' AND s.del_flag='0' AND NOT EXISTS (WITH RECURSIVE ancestors(category_id,parent_id,status,del_flag) AS (SELECT category_id,parent_id,status,del_flag FROM base_category WHERE category_id=c.category_id UNION ALL SELECT p.category_id,p.parent_id,p.status,p.del_flag FROM base_category p JOIN ancestors a ON p.category_id=a.parent_id) SELECT 1 FROM ancestors WHERE status<>'0' OR del_flag<>'0')",Long.class);
        sql.append("m.category_id IN (").append(categoryIds.isEmpty()?"NULL":String.join(",",categoryIds.stream().map(String::valueOf).toList())).append(") AND ");
        categoryAccess.appendScope(sql,args,"c.category_id",categoryAccess.scope());
        if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (m.material_code ILIKE ? OR m.material_name ILIKE ? OR m.specification ILIKE ? OR m.model ILIKE ?)");String q="%"+keyword.trim()+"%";for(int i=0;i<4;i++)args.add(q);}
        sql.append(" ORDER BY m.material_name,m.material_code");
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String,Object> row : rows) {
            row.put("specValues", MaterialSpecService.readValues((String) row.get("specValues")));
            row.put("variantFields", JsonUtils.parseArray((String) row.get("variantFields"), String.class));
            row.put("auxiliaryUnitIds", JsonUtils.parseArray((String) row.get("auxiliaryUnitIds"), String.class));
            row.put("materialUnits", materialUnits(Long.valueOf(String.valueOf(row.get("id"))), true));
            appendVariants(row, Long.valueOf(row.get("id").toString()), true);
        }
        return R.ok(rows);
    }

    /** 新建及编辑共用校验、权限过滤和主从保存。 / Share validation, authorization, and persistence. */
    private R<String> save(Long id,Map<String,Object> body) {
        Map<String,Object> b = new LinkedHashMap<>(body);
        String code=text(b,"code"),suggestion=text(b,"autoGeneratedCode"),name=text(b,"name"),spec=text(b,"specification"),model=text(b,"model"),brand=text(b,"brand"),desc=text(b,"materialDesc"),remark=text(b,"remark");
        Long categoryId=number(b.get("categoryId")),unitId=number(b.get("inventoryUnitId"));int sort;
        Map<String,Object> before=id==null?Map.of():snapshot(id,true);if(id!=null&&before.isEmpty())return R.fail("物料不存在或无权查看");
        Long oldCategory=id==null?null:jdbc.queryForObject("SELECT category_id FROM inv_material WHERE material_id=?",Long.class,id);
        Long oldUnit=id==null?null:jdbc.queryForObject("SELECT inventory_unit_id FROM inv_material WHERE material_id=?",Long.class,id);
        Set<Long> oldColors=id==null?Set.of():new HashSet<>(jdbc.queryForList("SELECT color_id FROM inv_material_color WHERE material_id=?",Long.class,id));
        try{sort=Integer.parseInt(Objects.toString(b.getOrDefault("sortOrder",0)));}catch(Exception e){return R.fail("排序必须为整数");}
        if(name.isBlank()||name.length()>120||spec.length()>200||model.length()>120||brand.length()>100||desc.length()>1000||remark.length()>500||sort<0||sort>999999)return R.fail("请检查物料名称、规格型号、说明和排序长度");
        if(categoryId==null||unitId==null)return R.fail("请选择分类和库存单位");
        if(!categoryAccess.categoryAllowed(categoryId))return R.fail("当前分类不可用或您没有该分类权限，请联系管理员核对分类权限");
        if(!Objects.equals(categoryId,oldCategory)&&!categoryAccess.categoryIsActive(categoryId))return R.fail("所选分类或其上级已停用或不存在，请重新选择");
        // 与计量单位维护使用同一把事务锁，防止校验后换算关系被并发修改。
        // Share the dictionary lock so a conversion cannot change between validation and save.
        jdbc.execute("SELECT pg_advisory_xact_lock(20261105,1)");
        if(!Objects.equals(unitId,oldUnit)&&jdbc.queryForList("SELECT unit_id FROM base_unit WHERE unit_id=? AND status='0' AND del_flag='0' AND base_unit_id IS NULL",unitId).isEmpty())return R.fail("库存单位须为启用的独立库存基本单位，不能选择辅助单位");
        if (b.containsKey("variantFields") && (!b.containsKey("multiSpec") || !b.containsKey("specValues"))) {
            return R.fail("调整按规格填写的属性时，请提交共用属性及完整规格配置");
        }
        if (id != null && !b.containsKey("variantFields")) {
            var previousFields = variantFieldKeys(id);
            if (!previousFields.isEmpty() && b.containsKey("specTemplateCode")
                && !Objects.equals(b.get("specTemplateCode"), jdbc.queryForObject(
                    "SELECT spec_template_code FROM inv_material WHERE material_id=?", String.class, id))) {
                throw new ServiceException("请在物料档案中调整模板和各规格属性");
            }
            b.put("variantFields", previousFields);
        }
        MaterialSpecService.Selection selection;
        try {
            selection = materialSpecs.resolve(id, b);
            b.putAll(selection.legacyUpdates());
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
        BigDecimal weight;
        BigDecimal width;
        try {
            weight = materialMeasure(b, "fabricWeightGsm", before.get("克重（g/m²）"), "克重");
            width = materialMeasure(b, "fabricWidthCm", before.get("幅宽（cm）"), "幅宽");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
        String composition = b.containsKey("composition") ? text(b, "composition") : Objects.toString(before.get("成分"), "");
        if (composition.length() > 300) return R.fail("成分不能超过300个字符");

        List<UnitConfig> auxiliary;
        try { auxiliary = resolveMaterialUnits(id, unitId, oldUnit, b); }
        catch (IllegalArgumentException e) { return R.fail(e.getMessage()); }
        List<Long> colors=parseIds(b.get("colorIds"));if(colors==null||new HashSet<>(colors).size()!=colors.size())return R.fail("颜色列表无效或包含重复项");
        for(Long color:colors)if(!oldColors.contains(color)&&jdbc.queryForList("SELECT color_id FROM base_color WHERE color_id=? AND status='0'",color).isEmpty())return R.fail("所选颜色已停用或不存在，请重新选择");
        jdbc.execute("SELECT pg_advisory_xact_lock("+CODE_LOCK_KEY+")");CodeRule rule=codeRule();
        if(id==null){if(rule.enabled()&&(code.isBlank()||(!suggestion.isBlank()&&code.equalsIgnoreCase(suggestion))))code=nextCodeValue(rule);}
        if(code.isBlank()||code.length()>50||!code.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))return R.fail("物料编码必填，且只能由字母、数字、点、下划线和短横线组成");
        if(codeExists(code,id))return R.fail("物料编码已存在");
        Long savedId = id;
        Long actor = LoginHelper.getUserId();
        if (id == null) {
            savedId = jdbc.queryForObject("""
                INSERT INTO inv_material(material_code,material_name,category_id,specification,model,brand,material_desc,
                    inventory_unit_id,sort_order,remark,create_by,fabric_weight_gsm,fabric_width_cm,composition)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING material_id
                """, Long.class, code,name,categoryId,spec,model,brand,desc,unitId,sort,remark,actor,weight,width,composition);
        } else {
            jdbc.update("""
                UPDATE inv_material SET material_name=?,category_id=?,specification=?,model=?,brand=?,material_desc=?,
                    inventory_unit_id=?,sort_order=?,remark=?,update_by=?,update_time=now(),
                    fabric_weight_gsm=?,fabric_width_cm=?,composition=? WHERE material_id=?
                """, name,categoryId,spec,model,brand,desc,unitId,sort,remark,actor,weight,width,composition,id);
        }
        jdbc.update("UPDATE inv_material SET spec_template_code=?,spec_values=CAST(? AS jsonb) WHERE material_id=?",
            selection.code(), JsonUtils.toJsonString(selection.values()), savedId);
        jdbc.update("DELETE FROM inv_material_color WHERE material_id=?", savedId);
        for (Long color : colors) {
            jdbc.update("INSERT INTO inv_material_color(material_id,color_id,create_by) VALUES(?,?,?)", savedId,color,actor);
        }
        jdbc.update("DELETE FROM inv_material_aux_unit WHERE material_id=?", savedId);
        for (UnitConfig item : auxiliary) {
            jdbc.update("INSERT INTO inv_material_aux_unit(material_id,unit_id,conversion_mode,reference_unit_id,factor,inventory_factor,purchase_enabled,usage_enabled) VALUES(?,?,?,?,?,?,?,?)",
                savedId,item.id(),item.mode(),item.reference(),item.factor(),item.inventoryFactor(),item.purchase(),item.usage());
        }
        saveVariants(savedId, b, selection.code());
        BusinessAudit.record(id == null ? "新增物料" : "修改物料", savedId, "物料名称", before, snapshot(savedId,false));
        return R.ok("操作成功", String.valueOf(savedId));
    }

    /** 返回稳定规格ID，业务选料仅提供启用项。 / Return stable IDs and hide disabled variants from new selections. */
    private List<Map<String,Object>> variants(Long id, boolean activeOnly) {
        var rows = jdbc.queryForList("SELECT variant_id::text AS id, code, name, status, attributes::text AS attributes FROM inv_material_variant WHERE material_id=?"
            + (activeOnly ? " AND status='0'" : "") + " ORDER BY variant_id", id);
        rows.forEach(row -> row.put("attributes", MaterialSpecService.readValues((String) row.get("attributes"))));
        return rows;
    }

    /** 读取按规格填写的模板字段。 / Read the configured variant field keys. */
    private List<String> variantFieldKeys(Long id) {
        return JsonUtils.parseArray(jdbc.queryForObject("SELECT variant_fields::text FROM inv_material WHERE material_id=?", String.class, id), String.class);
    }

    /** 档案与选料接口使用同一规格结构。 / Share the variant contract across material and picker APIs. */
    private void appendVariants(Map<String,Object> row, Long id, boolean activeOnly) {
        row.put("multiSpec", jdbc.queryForObject("SELECT multi_spec FROM inv_material WHERE material_id=?", Boolean.class, id));
        row.put("variants", variants(id, activeOnly));
        if (!activeOnly) row.put("variantFields", variantFieldKeys(id));
    }

    /** 保存规格时保留已建主键，旧调用未传规格字段时保留原设置。 / Keep existing identities and support legacy callers. */
    private void saveVariants(Long materialId, Map<String,Object> body, String templateCode) {
        if (!body.containsKey("multiSpec")) {
            if (body.containsKey("variants")) throw new ServiceException("请提交多规格开关及完整规格列表");
            return;
        }
        var template = templateCode == null ? null : materialSpecs.catalog().stream()
            .filter(item -> item.code().equals(templateCode)).findFirst().orElse(null);
        var fields = materialSpecs.variantFields(template, body.get("variantFields"));
        if (!(body.get("multiSpec") instanceof Boolean enabled) || !(body.get("variants") instanceof List<?> inputs)
            || inputs.size() > 200 || enabled != !inputs.isEmpty() || !enabled && !fields.isEmpty()) {
            throw new ServiceException("启用多规格时请填写 1 至 200 条规格");
        }
        var existing = variants(materialId, false);
        var remaining = new HashSet<>(existing.stream().map(row -> row.get("id").toString()).toList());
        var codes = new HashSet<String>();
        var names = new HashSet<String>();
        for (Object input : inputs) {
            if (!(input instanceof Map<?,?> item)) throw new ServiceException("物料规格格式无效");
            String id = Objects.toString(item.get("id"), "").trim();
            String code = Objects.toString(item.get("code"), "").trim();
            String name = Objects.toString(item.get("name"), "").trim();
            String status = Objects.toString(item.get("status"), "");
            if (code.isBlank() || code.length()>64 || name.isBlank() || name.length()>120 || !Set.of("0","1").contains(status)
                || !codes.add(code.toLowerCase(Locale.ROOT)) || !names.add(name.toLowerCase(Locale.ROOT))) {
                throw new ServiceException("请检查物料规格编码、名称和状态，编码和名称不能重复");
            }
            if (!id.isEmpty() && !remaining.remove(id)) throw new ServiceException("物料规格不存在或重复提交");
            String attributes;
            try {
                attributes = JsonUtils.toJsonString(materialSpecs.variantValues(template, fields, item.get("attributes"), name, "0".equals(status)));
            } catch (IllegalArgumentException e) {
                throw new ServiceException(e.getMessage());
            }
            try {
                if (id.isEmpty()) {
                    jdbc.update("INSERT INTO inv_material_variant(material_id,code,name,status,attributes) VALUES(?,?,?,?,CAST(? AS jsonb))", materialId,code,name,status,attributes);
                } else {
                    jdbc.update("UPDATE inv_material_variant SET code=?,name=?,status=?,attributes=CAST(? AS jsonb) WHERE material_id=? AND variant_id=?",
                        code,name,status,attributes,materialId,Long.valueOf(id));
                }
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new ServiceException("规格编码已存在，或同一物料的规格名称重复");
            }
        }
        if (!remaining.isEmpty()) throw new ServiceException("已保存的物料规格请停用，系统需保留历史引用");
        jdbc.update("UPDATE inv_material SET multi_spec=?,variant_fields=CAST(? AS jsonb) WHERE material_id=?", enabled,JsonUtils.toJsonString(fields),materialId);
    }

    private record UnitConfig(Long id, String mode, Long reference, BigDecimal factor,
                              BigDecimal inventoryFactor, boolean purchase, boolean usage) {}

    /** 返回物料自己的单位规则，系数以文本传输，避免浮点误差。 / Return material-specific exact conversions. */
    private List<Map<String,Object>> materialUnits(Long id, boolean selectableOnly) {
        return jdbc.queryForList("""
            SELECT a.unit_id::text AS "unitId",u.unit_name AS "unitName",ref.unit_name AS "referenceUnitName",a.conversion_mode AS mode,
                   a.reference_unit_id::text AS "referenceUnitId",trim_scale(a.factor)::text AS factor,
                   trim_scale(a.inventory_factor)::text AS "inventoryFactor",
                   a.purchase_enabled AS purchase,a.usage_enabled AS usage
            FROM inv_material_aux_unit a JOIN base_unit u ON u.unit_id=a.unit_id
            LEFT JOIN base_unit ref ON ref.unit_id=a.reference_unit_id
            WHERE a.material_id=? AND (?=false OR (u.status='0' AND u.del_flag='0')) ORDER BY u.sort_order,u.unit_id
            """, id, selectableOnly);
    }

    /** 检查单位引用和换算链，统一计算到库存单位，拒绝循环和精度损失。 / Validate and resolve conversion chains exactly. */
    private List<UnitConfig> resolveMaterialUnits(Long id, Long inventoryId, Long oldInventoryId, Map<String,Object> body) {
        boolean sameRoot = Objects.equals(inventoryId, oldInventoryId);
        List<Map<String,Object>> old = id != null && sameRoot ? materialUnits(id, false) : List.of();
        Set<Long> oldIds = new HashSet<>();
        for (Map<String,Object> item : old) oldIds.add(number(item.get("unitId")));
        List<Map<String,Object>> input = new ArrayList<>();
        if (body.containsKey("materialUnits")) {
            if (!(body.get("materialUnits") instanceof List<?> items)) throw new IllegalArgumentException("物料单位配置格式无效");
            for (Object value : items) {
                if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("物料单位配置格式无效");
                Map<String,Object> row = new LinkedHashMap<>();
                map.forEach((key,val) -> row.put(String.valueOf(key),val));
                input.add(row);
            }
        } else if (body.containsKey("auxiliaryUnitIds")) {
            List<Long> ids = parseIds(body.get("auxiliaryUnitIds"));
            if (ids == null || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("辅助单位列表无效或包含重复项");
            // 兼容旧接口：已有关系保留其物料系数；旧接口新增只接受公共固定换算。
            for (Long unitId : ids) {
                Map<String,Object> previous = old.stream().filter(row -> Objects.equals(number(row.get("unitId")),unitId)).findFirst().orElse(null);
                if (previous != null) { Map<String,Object> copy = new LinkedHashMap<>(previous); copy.put("usage",true); input.add(copy); continue; }
                List<Map<String,Object>> matches = jdbc.queryForList("SELECT factor::text AS factor FROM base_unit WHERE unit_id=? AND base_unit_id=? AND status='0' AND del_flag='0'",unitId,inventoryId);
                if (matches.isEmpty()) throw new IllegalArgumentException("辅助单位须属于所选库存单位，请使用物料单位配置填写专属系数");
                input.add(new LinkedHashMap<>(Map.of("unitId",unitId.toString(),"mode","FIXED","referenceUnitId",inventoryId.toString(),"factor",matches.getFirst().get("factor"),"purchase",false,"usage",true)));
            }
            for (Map<String,Object> previous : old) if (!ids.contains(number(previous.get("unitId")))) {
                Long previousId = number(previous.get("unitId"));
                boolean legacyCompatible = !jdbc.queryForList("SELECT a.unit_id FROM inv_material_aux_unit a JOIN base_unit u ON u.unit_id=a.unit_id WHERE a.material_id=? AND a.unit_id=? AND a.conversion_mode='FIXED' AND a.reference_unit_id=? AND u.base_unit_id=? AND a.factor=u.factor", id, previousId, inventoryId, inventoryId).isEmpty();
                if (!legacyCompatible) input.add(new LinkedHashMap<>(previous));
                else if (Boolean.TRUE.equals(previous.get("purchase"))) {
                    Map<String,Object> copy = new LinkedHashMap<>(previous); copy.put("usage",false); input.add(copy);
                }
            }
        } else input.addAll(old);
        Map<Long,UnitConfig> configs = new LinkedHashMap<>();
        for (Map<String,Object> row : input) {
            Long unit = number(row.get("unitId"));
            if (unit == null || unit.equals(inventoryId) || configs.containsKey(unit)) throw new IllegalArgumentException("物料单位不能重复或与库存单位相同");
            if (!oldIds.contains(unit) && jdbc.queryForList("SELECT child.unit_id FROM base_unit child LEFT JOIN base_unit parent ON parent.unit_id=child.base_unit_id WHERE child.unit_id=? AND child.status='0' AND child.del_flag='0' AND (child.base_unit_id IS NULL OR (parent.status='0' AND parent.del_flag='0'))",unit).isEmpty()) throw new IllegalArgumentException("所选单位或其公共参照单位已停用或不存在");
            String mode = Objects.toString(row.get("mode"), "FIXED");
            boolean purchase = Boolean.TRUE.equals(row.get("purchase")), usage = Boolean.TRUE.equals(row.get("usage"));
            if (!purchase && !usage) throw new IllegalArgumentException("物料单位至少选择采购或生产领用一种用途");
            Long reference = null; BigDecimal factor = null;
            if ("FIXED".equals(mode)) {
                reference = number(row.get("referenceUnitId"));
                String raw = Objects.toString(row.get("factor"),"").trim();
                if (reference == null || !(row.get("factor") instanceof String) || !raw.matches("[0-9]{1,18}(\\.[0-9]{1,12})?") || (factor = new BigDecimal(raw)).signum() <= 0)
                    throw new IllegalArgumentException("固定换算须选择参照单位并填写大于零的系数（最多12位小数）");
            } else if (!"VARIABLE".equals(mode)) throw new IllegalArgumentException("单位换算方式无效");
            configs.put(unit,new UnitConfig(unit,mode,reference,factor,null,purchase,usage));
        }
        List<UnitConfig> result = new ArrayList<>();
        for (UnitConfig config : configs.values()) {
            BigDecimal total = "FIXED".equals(config.mode()) ? resolveFactor(config.id(),inventoryId,configs,new HashSet<>()) : null;
            result.add(new UnitConfig(config.id(),config.mode(),config.reference(),config.factor(),total,config.purchase(),config.usage()));
        }
        return result;
    }

    private BigDecimal resolveFactor(Long id, Long inventoryId, Map<Long,UnitConfig> configs, Set<Long> visiting) {
        if (id.equals(inventoryId)) return BigDecimal.ONE;
        if (!visiting.add(id)) throw new IllegalArgumentException("单位换算关系不能循环引用");
        UnitConfig item = configs.get(id);
        if (item == null || !"FIXED".equals(item.mode())) throw new IllegalArgumentException("参照单位须为库存单位或已配置的固定换算单位");
        BigDecimal total = item.factor().multiply(resolveFactor(item.reference(),inventoryId,configs,visiting)).stripTrailingZeros();
        visiting.remove(id);
        if (total.scale() > 12 || total.precision() - total.scale() > 18) throw new IllegalArgumentException("换算到库存单位的系数超出精度，请调整换算关系");
        return total;
    }

    /** 只接受正数及明确的小数位数，避免数据库静默舍入。 / Validate exact positive measurements before writing. */
    private BigDecimal materialMeasure(Map<String,Object> body, String key, Object previous, String label) {
        if (!body.containsKey(key)) return (BigDecimal) previous;
        String value = text(body, key);
        if (value.isEmpty()) return null;
        if (value.matches("[0-9]{1,8}(\\.[0-9]{1,4})?")) {
            BigDecimal measure = new BigDecimal(value);
            if (measure.signum() > 0) return measure;
        }
        throw new IllegalArgumentException(label + "须为正数，最多8位整数和4位小数");
    }

    private boolean codeExists(String code,Long id){String q="SELECT count(*) FROM inv_material WHERE lower(material_code)=lower(?) AND del_flag='0'"+(id==null?"":" AND material_id<>?");Long count=id==null?jdbc.queryForObject(q,Long.class,code):jdbc.queryForObject(q,Long.class,code,id);return count!=null&&count>0;}
    private Map<String,Object> snapshot(Long id,boolean lock){List<Long> categories=jdbc.queryForList("SELECT category_id FROM inv_material WHERE material_id=? AND del_flag='0'"+(lock?" FOR UPDATE":""),Long.class,id);if(categories.isEmpty()||!categoryAccess.categoryAllowed(categories.getFirst()))return Map.of();String q="SELECT m.material_code AS \"物料编码\",m.material_name AS \"物料名称\",s.scheme_name AS \"分类类型\",c.category_name AS \"分类\",m.specification AS \"规格尺寸\",m.fabric_weight_gsm AS \"克重（g/m²）\",m.fabric_width_cm AS \"幅宽（cm）\",m.composition AS \"成分\",(SELECT string_agg(au.unit_name || '（' || au.unit_code || '）' || CASE WHEN mu.conversion_mode='VARIABLE' THEN '：按实际数量' ELSE '：1=' || trim_scale(mu.factor)::text || COALESCE(ref.unit_name,'') END || CASE WHEN mu.purchase_enabled THEN ' [采购]' ELSE '' END || CASE WHEN mu.usage_enabled THEN ' [生产领用]' ELSE '' END,'、' ORDER BY au.unit_id) FROM inv_material_aux_unit mu JOIN base_unit au ON au.unit_id=mu.unit_id LEFT JOIN base_unit ref ON ref.unit_id=mu.reference_unit_id WHERE mu.material_id=m.material_id) AS \"辅助单位\",m.model AS \"型号\",m.brand AS \"品牌\",m.material_desc AS \"材质说明\",u.unit_name AS \"库存单位\",m.status AS \"状态\",m.sort_order AS \"排序\",m.remark AS \"备注\",(SELECT string_agg(co.color_name,'、' ORDER BY co.color_name) FROM inv_material_color mc JOIN base_color co ON co.color_id=mc.color_id WHERE mc.material_id=m.material_id) AS \"可用颜色\" FROM inv_material m JOIN base_category c ON c.category_id=m.category_id JOIN base_category_scheme s ON s.scheme_id=c.scheme_id JOIN base_unit u ON u.unit_id=m.inventory_unit_id WHERE m.material_id=? AND m.del_flag='0'";Map<String,Object> result = BusinessAudit.snapshot(jdbc,q,id);
        Map<String,Object> specs = jdbc.queryForMap(
            "SELECT spec_template_code AS code, spec_values::text AS values FROM inv_material WHERE material_id=?", id);
        result.putAll(materialSpecs.auditValues((String) specs.get("code"), (String) specs.get("values")));
        result.put("启用多规格", jdbc.queryForObject("SELECT multi_spec FROM inv_material WHERE material_id=?", Boolean.class, id));
        result.put("物料规格", variants(id, false));
        result.put("按规格填写的属性", variantFieldKeys(id));
        return result;
    }
    private boolean hasForeignReferences(Long id){List<Map<String,Object>>refs=jdbc.queryForList("SELECT c.conrelid::regclass::text AS table_name,a.attname AS column_name FROM pg_constraint c JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ord) ON true JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum WHERE c.contype='f' AND c.confrelid='public.inv_material'::regclass AND c.conrelid NOT IN ('public.inv_material_color'::regclass, 'public.inv_material_aux_unit'::regclass)");for(Map<String,Object>r:refs){String t=Objects.toString(r.get("table_name"),""),c=Objects.toString(r.get("column_name"),"");if(t.matches("[A-Za-z0-9_.\"]+")&&c.matches("[A-Za-z0-9_]+")&&jdbc.queryForObject("SELECT count(*) FROM "+t+" WHERE \""+c+"\"=?",Long.class,id)>0)return true;}return false;}
    private record CodeRule(boolean enabled,String prefix,int digits){}
    private CodeRule codeRule(){String raw=jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?",String.class,CODE_RULE_KEY);Map<String,Object>v=org.dromara.common.json.utils.JsonUtils.parseMap(raw);Object e=v.get("enabled"),p=v.get("prefix"),d=v.get("digits");if(!(e instanceof Boolean)||!(p instanceof String)||((String)p).isBlank()||((String)p).length()>30||!((String)p).matches("[A-Za-z0-9_.-]+")||!(d instanceof Number n)||n.intValue()<1||n.intValue()>10)throw new IllegalStateException("物料编码自动生成规则无效，请在参数设置中修复");return new CodeRule((Boolean)e,((String)p).toUpperCase(Locale.ROOT),n.intValue());}
    private String nextCodeValue(CodeRule r){Set<String>used=new HashSet<>(jdbc.queryForList("SELECT lower(material_code) FROM inv_material WHERE del_flag='0' AND lower(material_code) LIKE lower(?)",String.class,r.prefix+"%"));for(long i=1;i<Long.MAX_VALUE;i++){String s=Long.toString(i),code=r.prefix+"0".repeat(Math.max(0,r.digits-s.length()))+s;if(!used.contains(code.toLowerCase(Locale.ROOT)))return code;}throw new IllegalStateException("没有可用的物料编码序号");}
    private List<Long> parseIds(Object value){if(!(value instanceof List<?> list))return value==null?List.of():null;List<Long>ids=new ArrayList<>();try{for(Object v:list)ids.add(Long.valueOf(v.toString()));return ids;}catch(Exception e){return null;}}
    private Long number(Object v){try{return v==null?null:Long.valueOf(v.toString());}catch(Exception e){return null;}}
    private String text(Map<String,Object>b,String key){return Objects.toString(b.get(key),"").trim();}
}
