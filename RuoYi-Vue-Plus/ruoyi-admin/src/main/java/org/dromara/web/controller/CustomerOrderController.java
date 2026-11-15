package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 客户订单录入、交付分配和审核快照。 / Customer orders, delivery allocations and review snapshots. */
@RestController
@RequestMapping("/order/customer-orders")
@SaCheckPermission("order:customer-order:list")
@RequiredArgsConstructor
public class CustomerOrderController {
    private final JdbcTemplate jdbc;
    private final org.dromara.web.service.CustomerOrderApprovalService approval;
    private final StyleController styles;
    private final StyleBomController boms;

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Line(UUID key, Long styleId, Long colorId, Long sizeId, Long setSpecId, Integer quantity, String supply, BigDecimal unitPrice) {}
    public record Delivery(LocalDate date, String destination, Map<UUID,Integer> allocations) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Po(String number, LocalDate date, LocalDate due, LocalDate offlineDate, String destination, String remark, String supply, List<Line> lines, List<Delivery> deliveries) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Input(Long id, Integer revision, String orderNo, String suggestedCode, Long customerId, Long companyId,
                        Long salesId, Long followerId, LocalDate date, String reference, String destination, String remark, String supply, List<Po> pos, Map<String,String> sources) {}
    public record Action(int revision, String action, String reason) {}
    public record DemandRequest(Integer revision, Boolean recalculate) {}

    /** 订单角色可检索基础资料，款式选择继续执行人员分类限制。 */
    @GetMapping("/options")
    public R<Map<String,Object>> options() {
        var styleCategories = new LinkedHashMap<>(boms.styleCategories().getData());
        @SuppressWarnings("unchecked") var categoryRows = (List<Map<String,Object>>) styleCategories.get("rows");
        String scheme = Objects.toString(styleCategories.get("schemeId"), "");
        if (Boolean.TRUE.equals(styleCategories.get("invalid"))) categoryRows = List.of();
        else if (!scheme.isEmpty()) categoryRows = categoryRows.stream().filter(row -> scheme.equals(Objects.toString(row.get("schemeId"),""))).toList();
        styleCategories.put("rows",categoryRows);
        var allowedCategories = new HashSet<String>();
        categoryRows.stream().filter(row -> Boolean.TRUE.equals(row.get("selectable"))).forEach(row -> allowedCategories.add(Objects.toString(row.get("id"),"")));
        return R.ok(Map.of("customers",styles.customerOptions().getData(), "specs",styles.specOptions().getData(),
            "styles",boms.styles().getData().stream().filter(s -> "0".equals(s.get("status")) && (scheme.isEmpty() && !Boolean.TRUE.equals(styleCategories.get("invalid")) || allowedCategories.contains(Objects.toString(s.get("categoryId"),"")))).toList(),
            "categories",styleCategories,
            "companies",jdbc.queryForList("SELECT company_id::text AS value,company_name AS label FROM sys_company WHERE status='0' AND del_flag='0' ORDER BY is_default DESC,company_name"),
            "salesPeople",peopleWithDuty("SALES"), "followers",peopleWithDuty("FOLLOWER"),
            "currentUser",LoginHelper.getUserId().toString(), "offlineLeadDays",offlineLeadDays()));
    }

    /** 默认下线提前天数；参数未设置时按交期当天建议，录单时允许人工修改。 */
    private int offlineLeadDays() {
        var values=jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key='order.customer.offlineLeadDays' LIMIT 1",String.class);
        if (values.isEmpty()) return 0;
        try { int days=Integer.parseInt(values.getFirst()); return days>=0 && days<=3650 ? days : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    /** 用订单权限查询单款与套装有效规格。 */
    @GetMapping("/styles/{id}")
    public R<Map<String,Object>> style(@PathVariable Long id) {
        return boms.context(id);
    }

    /** 建议单号可由用户编辑。 */
    @GetMapping("/next-code")
    @SaCheckPermission({"order:customer-order:list","order:customer-order:edit"})
    public R<String> next() { return R.ok(null,nextCode()); }

    private String nextCode() {
        String prefix = "ORD" + LocalDate.now(ZoneId.of("Asia/Shanghai")).toString().replace("-","");
        var used = new HashSet<>(jdbc.queryForList("SELECT upper(order_no) FROM biz_customer_order WHERE upper(order_no) LIKE ?",String.class,prefix+"%"));
        for (int n=1; n<Integer.MAX_VALUE; n++) {
            String code = prefix + String.format(Locale.ROOT,"%04d",n);
            if (!used.contains(code)) return code;
        }
        throw new ServiceException("无可用的订单号");
    }

    private static final String SELECT = """
        SELECT id::text AS id,order_no AS "orderNo",customer_id::text AS "customerId",company_id::text AS "companyId",
            sales_id::text AS "salesId",follower_id::text AS "followerId",status,revision,created_at AS "createdAt",
            updated_at AS "updatedAt",document::text AS document,demand::text AS demand,
            EXISTS (SELECT 1 FROM sys_approval_business_instance a WHERE a.business_type='customer_order'
                AND a.business_id=biz_customer_order.id AND a.state IN ('pending','approved')) AS "workflowManaged"
        FROM biz_customer_order
        """;

    /** 查询当前员工参与的订单，订单审核权限允许查看全部订单。 */
    @GetMapping("/list")
    public R<List<Map<String,Object>>> list(@RequestParam(defaultValue="") String keyword,
                                             @RequestParam(defaultValue="") String status) {
        Long user = LoginHelper.getUserId();
        var rows=jdbc.queryForList(SELECT + """
            WHERE (created_by=? OR sales_id=? OR follower_id=? OR ?)
            AND (?='' OR status=?) AND (order_no ILIKE ? OR document::text ILIKE ?) ORDER BY updated_at DESC,id DESC
            """,user,user,user,reviewer(),status,status,"%"+keyword+"%","%"+keyword+"%")
            .stream().map(this::decode).toList();
        var newest=new HashMap<String,String>();
        for (var bom : jdbc.queryForList("SELECT DISTINCT ON (style_id) style_id::text AS style, id::text AS id FROM rd_style_bom WHERE status='released' ORDER BY style_id, version DESC")) newest.put(bom.get("style").toString(),bom.get("id").toString());
        for (var row : rows) row.put("demandState",demandState(row,newest));
        return R.ok(rows);
    }

    /** 比较已保存的 BOM 快照和当前可用版本，状态仅作为提示，不改变已算出的数量。 */
    private String demandState(Map<String,Object> row, Map<String,String> newest) {
        if (!(row.get("demand") instanceof List<?> demand) || demand.isEmpty()) return "unplanned";
        if (!(row.get("document") instanceof Map<?,?> doc) || !(doc.get("pos") instanceof List<?> pos)) return "unknown";
        var stylesByLine=new HashMap<String,String>();
        for (Object po : pos) if (po instanceof Map<?,?> p && p.get("lines") instanceof List<?> lines)
            for (Object line : lines) if (line instanceof Map<?,?> l) stylesByLine.put(Objects.toString(l.get("key"),""),Objects.toString(l.get("styleId"),""));
        boolean unknown=false;
        for (Object item : demand) {
            if (!(item instanceof Map<?,?> plan)) { unknown=true; continue; }
            String style=stylesByLine.get(Objects.toString(plan.get("lineKey"),"")), used=Objects.toString(plan.get("bomId"),"");
            if (style==null || style.isEmpty() || used.isEmpty()) { unknown=true; continue; }
            String latest=newest.get(style);
            if (latest==null) return "unavailable";
            if (!latest.equals(used)) return "outdated";
        }
        return unknown ? "unknown" : "ready";
    }

    @GetMapping("/{id}")
    public R<Map<String,Object>> detail(@PathVariable Long id) {
        return load(id,false);
    }

    private boolean reviewer() { return StpUtil.hasPermission("order:customer-order:review"); }

    private R<Map<String,Object>> load(Long id, boolean lock) {
        Long user = LoginHelper.getUserId();
        var rows = jdbc.queryForList(SELECT + " WHERE id=? AND (created_by=? OR sales_id=? OR follower_id=? OR ?)"
            + (lock ? " FOR UPDATE" : ""),id,user,user,user,reviewer());
        if (rows.isEmpty()) throw new ServiceException("订单不存在或超出访问范围");
        return R.ok(decode(rows.getFirst()));
    }

    private Map<String,Object> decode(Map<String,Object> row) {
        row.put("document",JsonUtils.parseMap(row.get("document").toString()));
        row.put("demand",JsonUtils.parseObject(row.get("demand").toString(),List.class));
        return row;
    }

    /** 保存草稿及客户、款式名称快照；并发修改必须重新加载。 */
    @PostMapping
    @Transactional
    @SaCheckPermission({"order:customer-order:list","order:customer-order:edit"})
    @org.dromara.common.log.annotation.Log(title="客户订单",businessType=org.dromara.common.log.enums.BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> save(@RequestBody Input input) {
        jdbc.execute("SELECT pg_advisory_xact_lock(1763100000000000100)");
        Map<String,Object> before = input.id()==null ? Map.of() : load(input.id(),true).getData();
        if (!before.isEmpty() && (!"draft".equals(before.get("status")) || !Objects.equals(input.revision(),before.get("revision"))))
            throw new ServiceException("订单状态或版本已变化，请重新加载");
        if (!before.isEmpty() && !Objects.equals(input.companyId(),Long.valueOf(before.get("companyId").toString())))
            throw new ServiceException("归属公司在首次保存后锁定");
        if (input.date()==null) throw new ServiceException("请填写订单日期");
        var customer = label("SELECT customer_name FROM customer_master WHERE customer_id=? AND status='0'",input.customerId(),"客户");
        var company = label("SELECT company_name FROM sys_company WHERE company_id=? AND status='0' AND del_flag='0'",input.companyId(),"归属公司");
        var sales = person(input.salesId(), "SALES", before.get("salesId"));
        var follower = input.followerId()==null ? "" : person(input.followerId(), "FOLLOWER", before.get("followerId"));
        var document = new LinkedHashMap<String,Object>();
        document.put("date",input.date().toString());
        document.put("customerName",customer);
        document.put("companyName",company);
        document.put("salesName",sales);
        document.put("followerName",follower);
        document.put("reference",clean(input.reference()));
        document.put("destination",clean(input.destination()));
        document.put("remark",clean(input.remark()));
        document.put("supply",supplyMode(input.supply()));
        var pos = input.pos()==null ? List.<Po>of() : input.pos();
        document.put("sources",input.sources()==null?Map.of():input.sources());
        document.put("pos",validate(pos,input.customerId(),input.id(),false));
        String number = clean(input.orderNo());
        if (number.isEmpty() || (input.id()==null && number.equals(input.suggestedCode()))) number=nextCode();
        if (number.length()>60 || Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM biz_customer_order WHERE lower(order_no)=lower(?) AND id<>?)
            """,Boolean.class,number,input.id()==null?0L:input.id()))) throw new ServiceException("订单号重复或超过 60 字符");
        String json = JsonUtils.toJsonString(document);
        Long id = input.id();
        if (id==null) {
            id=jdbc.queryForObject("""
                INSERT INTO biz_customer_order(order_no,customer_id,company_id,sales_id,follower_id,created_by,document)
                VALUES(?,?,?,?,?,?,?::jsonb) RETURNING id
                """,Long.class,number,input.customerId(),input.companyId(),input.salesId(),input.followerId(),LoginHelper.getUserId(),json);
        } else {
            jdbc.update("""
                UPDATE biz_customer_order SET order_no=?,customer_id=?,sales_id=?,follower_id=?,document=?::jsonb,
                revision=revision+1,updated_at=now() WHERE id=?
                """,number,input.customerId(),input.salesId(),input.followerId(),json,id);
        }
        var after = decode(jdbc.queryForMap(SELECT+" WHERE id=?",id));
        BusinessAudit.record(input.id()==null?"新增客户订单":"修改客户订单",id,"orderNo",before,after);
        return R.ok(after);
    }

    /** 单据人员身份决定候选资格，不授予该模块的新增、审核或取消权限。 */
    private List<Map<String,Object>> peopleWithDuty(String duty) {
        return jdbc.queryForList("SELECT u.user_id::text AS value,COALESCE(NULLIF(u.nick_name,''),u.user_name) AS label FROM sys_user u JOIN sys_user_business_duty d ON d.user_id=u.user_id WHERE d.duty_code=? AND u.status='0' AND u.del_flag='0' AND u.user_name<>'admin' ORDER BY label", duty);
    }

    private String person(Long id, String duty, Object previousId) {
        if (id == null) throw new ServiceException("请选择有效人员");
        // 兼容已保存的历史关联：职责后来被撤销时，允许未变更该责任人继续编辑其他订单字段。
        if (previousId != null && id.toString().equals(previousId.toString())) {
            return label("SELECT COALESCE(NULLIF(nick_name,''),user_name) FROM sys_user WHERE user_id=? AND del_flag='0'",id,"人员");
        }
        var names = jdbc.queryForList("SELECT COALESCE(NULLIF(u.nick_name,''),u.user_name) FROM sys_user u JOIN sys_user_business_duty d ON d.user_id=u.user_id AND d.duty_code=? WHERE u.user_id=? AND u.status='0' AND u.del_flag='0'",String.class,duty,id);
        if (names.isEmpty()) throw new ServiceException("请选择具有对应业务职责的在职人员");
        return names.getFirst();
    }

    private String label(String query, Long id, String field) {
        var rows = id==null ? List.<String>of() : jdbc.queryForList(query,String.class,id);
        if (rows.isEmpty()) throw new ServiceException("请选择有效的"+field);
        return rows.getFirst();
    }

    private String clean(String value) { return value==null ? "" : value.trim(); }

    /** 校验 PO 去重、规格有效性及交付分配，按 PO 保留款式和套装配套快照。 */
    private List<Map<String,Object>> validate(List<Po> pos,Long customer,Long order,boolean submit) {
        if (submit && pos.isEmpty()) throw new ServiceException("至少需要一个客户 PO");
        var numbers = new HashSet<String>();
        var result = new ArrayList<Map<String,Object>>();
        for (var po : pos) {
            String no = clean(po.number());
            if (no.isEmpty() || no.length()>100 || !numbers.add(no.toLowerCase(Locale.ROOT)))
                throw new ServiceException("请填写有效且不重复的客户 PO 号");
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM biz_customer_order o CROSS JOIN LATERAL jsonb_array_elements(o.document->'pos') p
                  WHERE o.customer_id=? AND o.id<>? AND o.status<>'cancelled' AND lower(p->>'number')=lower(?))
                """,Boolean.class,customer,order==null?0L:order,no))) throw new ServiceException("客户 PO 已被使用："+no);
            var lines = new ArrayList<Map<String,Object>>();
            var quantities = new HashMap<UUID,Integer>();
            var combinations = new HashSet<List<String>>();
            for (var line : po.lines()==null?List.<Line>of():po.lines()) {
                if (line.key()==null || line.styleId()==null || line.quantity()==null || line.quantity()<1 || line.quantity()>10000000
                    || quantities.putIfAbsent(line.key(),line.quantity())!=null)
                    throw new ServiceException("PO "+no+" 的款式或数量无效");
                if (!combinations.add(List.of(line.styleId().toString(),Objects.toString(line.colorId(),""),Objects.toString(line.sizeId(),""),Objects.toString(line.setSpecId(),""))))
                    throw new ServiceException("PO "+no+" 存在重复款式规格，请合并数量");
                var source = jdbc.queryForMap("SELECT internal_code,style_name,production_mode,status FROM rd_style WHERE style_id=?",line.styleId());
                if (!"0".equals(source.get("status"))) throw new ServiceException("PO "+no+" 选用了停用款式");
                if ("single".equals(source.get("production_mode")) && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM rd_style_member m JOIN rd_style parent ON parent.style_id=m.style_id
                      WHERE m.child_id=? AND parent.production_mode='combined' AND parent.status='0')
                    """,Boolean.class,line.styleId())))
                    throw new ServiceException("PO "+no+" 的合并生产子款须通过套装下单："+source.get("internal_code"));
                var snapshot = JsonUtils.parseMap(JsonUtils.toJsonString(line));
                if (line.unitPrice()!=null && (line.unitPrice().signum()<0 || line.unitPrice().scale()>2 || line.unitPrice().compareTo(new BigDecimal("99999999.99"))>0))
                    throw new ServiceException("PO "+no+" 的加工单价须为 0 至 99999999.99，最多两位小数");
                snapshot.put("processingAmount",line.unitPrice()==null ? null : line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())).setScale(2));
                snapshot.put("styleName",source.get("internal_code")+" · "+source.get("style_name"));
                snapshot.put("productionMode",source.get("production_mode"));
                snapshot.put("configuration",boms.orderSelection(line.styleId(),line.setSpecId(),line.colorId(),line.sizeId()));
                lines.add(snapshot);
            }
            var deliveries = po.deliveries()==null ? List.<Delivery>of() : po.deliveries();
            var remaining = new HashMap<>(quantities);
            if (submit && (po.date()==null || lines.isEmpty() || (deliveries.isEmpty() && po.due()==null)))
                throw new ServiceException("PO "+no+" 需填写日期、款式数量及交期");
            for (var delivery : deliveries) {
                if (submit && delivery.date()==null) throw new ServiceException("PO "+no+" 存在未填写交期的批次");
                if (delivery.allocations()==null) continue;
                delivery.allocations().forEach((key,qty)->{
                    if (!remaining.containsKey(key) || qty==null || qty<0 || qty>remaining.get(key))
                        throw new ServiceException("PO "+no+" 的交付数量超过订购量或引用了已移除的规格");
                    remaining.computeIfPresent(key,(k,left)->left-qty);
                });
            }
            if (submit && !deliveries.isEmpty() && remaining.values().stream().anyMatch(q->q!=0))
                throw new ServiceException("PO "+no+" 还有未分配的交付数量");
            var saved = JsonUtils.parseMap(JsonUtils.toJsonString(po));
            saved.put("number",no);
            saved.put("supply",supplyMode(po.supply()));
            saved.put("lines",lines);
            result.add(saved);
        }
        return result;
    }

    /** 校验订单与 PO 默认值，空值用于继承或读取旧单。 / Validate inherited sourcing defaults. */
    private String supplyMode(String value) {
        if (value==null || value.isBlank()) return null;
        if (!Set.of("customer","purchase","mixed").contains(value)) throw new ServiceException("供料安排无效");
        return value;
    }

    /** 复用已发布 BOM 的规格匹配与库存单位换算，并记录每行计划来源。 */
    private List<Map<String,Object>> plan(Input input,boolean complete) {
        var result = new ArrayList<Map<String,Object>>();
        var purchaseUnits = new HashMap<String,List<Map<String,Object>>>();
        for (var po : input.pos()) for (var line : po.lines()==null?List.<Line>of():po.lines()) {
            var versions = jdbc.queryForList("SELECT id FROM rd_style_bom WHERE style_id=? AND status='released' ORDER BY version DESC LIMIT 1",Long.class,line.styleId());
            if (versions.isEmpty()) {
                if (complete) throw new ServiceException("PO "+po.number()+" 有款式缺少已发布 BOM");
                result.add(Map.of("po",po.number(),"lineKey",line.key(),"issue","缺少已发布 BOM"));
                continue;
            }
            var preview = boms.preview(versions.getFirst(),line.quantity(),line.setSpecId(),line.colorId(),line.sizeId()).getData();
            if (complete && preview.get("gaps") instanceof Collection<?> gaps && !gaps.isEmpty())
                throw new ServiceException("PO "+po.number()+" 的用料不完整："+gaps);
            var item = new LinkedHashMap<String,Object>(preview);
            // 优先采用 PO 设置，再继承订单设置，旧单仍可读取原规格默认值。
            // Prefer the PO override, then the order default, then the legacy line value.
            String defaultSupply = supplyMode(po.supply());
            if (defaultSupply==null) defaultSupply=supplyMode(input.supply());
            if (defaultSupply==null) defaultSupply=Objects.requireNonNullElse(line.supply(),"customer");
            if (preview.get("details") instanceof List<?> details) {
                for (Object detail : details) {
                    @SuppressWarnings("unchecked")
                    var material = (Map<String,Object>) detail;
                    String key = line.key()+":"+versions.getFirst()+":"+material.get("slotId");
                    // 混合供料是允许的入库来源范围，实际收货时再确认，不能提前假定为客供。
                    // Mixed supply permits both receipt sources; determine the actual source at receipt.
                    String supply = "mixed".equals(defaultSupply) ? "mixed" : input.sources()==null ? null : input.sources().get(key);
                    if (supply==null) supply=defaultSupply;
                    if (complete && !"customer".equals(supply) && !"purchase".equals(supply) && !"mixed".equals(supply))
                        throw new ServiceException("PO "+po.number()+" 有物料尚未安排供料方式");
                    material.put("sourceKey",key);
                    material.put("supply",Objects.toString(supply,""));
                    // 采购单位为物料独立配置；变动换算不能根据库存数量推算卷数。
                    // Purchase units are material-specific; variable conversions cannot be inferred.
                    String materialId=Objects.toString(material.get("materialId"),"");
                    var units=purchaseUnits.computeIfAbsent(materialId, ignored -> jdbc.queryForList("""
                        SELECT a.unit_id::text AS "unitId",u.unit_name AS "unitName",a.conversion_mode AS mode,
                               trim_scale(a.inventory_factor)::text AS "inventoryFactor"
                        FROM inv_material_aux_unit a JOIN base_unit u ON u.unit_id=a.unit_id
                        WHERE a.material_id=? AND a.purchase_enabled=true AND u.status='0' AND u.del_flag='0'
                        ORDER BY u.sort_order,u.unit_id
                        """,Long.valueOf(materialId)));
                    var suggestions=new ArrayList<Map<String,Object>>();
                    for (var unit : units) {
                        var suggestion=new LinkedHashMap<>(unit);
                        Object factor=unit.get("inventoryFactor");
                        if ("FIXED".equals(unit.get("mode")) && factor!=null && new BigDecimal(factor.toString()).signum()>0) {
                            var inventoryQty=new BigDecimal(material.get("quantity").toString());
                            var unitFactor=new BigDecimal(factor.toString());
                            // 保留高精度参考值；不是采购单的最终数量，也不提前按整包装取整。
                            var converted=inventoryQty.divide(unitFactor,8,RoundingMode.HALF_UP);
                            suggestion.put("quantity",converted.stripTrailingZeros().toPlainString());
                            suggestion.put("approximate",converted.multiply(unitFactor).compareTo(inventoryQty)!=0);
                        } else suggestion.put("quantity",null);
                        suggestions.add(suggestion);
                    }
                    material.put("purchaseUnits",suggestions);
                }
            }
            item.put("po",po.number());
            item.put("lineKey",line.key());
            item.put("bomId",versions.getFirst().toString());
            result.add(item);
        }
        return result;
    }

    private Input storedInput(Map<String,Object> row) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(row.get("document")),Input.class);
    }

    @GetMapping("/{id}/demand")
    public R<List<Map<String,Object>>> preview(@PathVariable Long id) {
        var row = load(id,false).getData();
        if (!"draft".equals(row.get("status"))) return R.ok(JsonUtils.parseObject(JsonUtils.toJsonString(row.get("demand")),
            new tools.jackson.core.type.TypeReference<List<Map<String,Object>>>() {}));
        return R.ok(plan(storedInput(row),false));
    }

    /** 已审核订单可在 BOM 就绪后算料；不完整结果仅预览，不覆盖既有快照。 */
    @PostMapping("/{id}/demand/calculate")
    @Transactional
    @SaCheckPermission({"order:customer-order:list","order:customer-order:edit"})
    @org.dromara.common.log.annotation.Log(title="订单审核后算料",businessType=org.dromara.common.log.enums.BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> calculateApprovedDemand(@PathVariable Long id,@RequestBody DemandRequest request) {
        var before=load(id,true).getData();
        if (!"approved".equals(before.get("status"))) throw new ServiceException("仅已审核订单可在审核后算料");
        if (!Objects.equals(request.revision(),before.get("revision"))) throw new ServiceException("订单版本已变化，请重新加载");
        boolean hasExisting=before.get("demand") instanceof Collection<?> existing && !existing.isEmpty();
        if (hasExisting && !Boolean.TRUE.equals(request.recalculate())) throw new ServiceException("该订单已有用料快照，请明确选择重新算料");
        // 所有关联计划须处理完毕；不覆盖计划人员的调整或审核记录。
        Boolean tableExists=jdbc.queryForObject("SELECT to_regclass('public.biz_material_demand_plan') IS NOT NULL",Boolean.class);
        if (Boolean.TRUE.equals(tableExists) && !jdbc.queryForList("SELECT id FROM biz_material_demand_plan WHERE order_id=? AND status<>'cancelled' FOR UPDATE",id).isEmpty())
            throw new ServiceException("订单已关联有效物料需求计划，请先处理该计划，再重新算料");
        var result=plan(storedInput(before),false);
        boolean incomplete=result.isEmpty() || result.stream().anyMatch(item -> item.containsKey("issue") || item.get("gaps") instanceof Collection<?> gaps && !gaps.isEmpty());
        if (incomplete) return R.ok(Map.of("saved",false,"plans",result));
        if (hasExisting) jdbc.update("INSERT INTO biz_order_demand_history(order_id,order_revision,demand,replaced_by) VALUES(?,?,?::jsonb,?)",id,before.get("revision"),JsonUtils.toJsonString(before.get("demand")),LoginHelper.getUserId());
        jdbc.update("UPDATE biz_customer_order SET demand=?::jsonb,revision=revision+1,updated_at=now() WHERE id=?",JsonUtils.toJsonString(result),id);
        var after=load(id,false).getData();
        BusinessAudit.record(hasExisting?"客户订单重新算料":"客户订单审核后算料",id,"orderNo",before,after);
        return R.ok(Map.of("saved",true,"plans",result,"order",after));
    }

    /** 流转在行锁内检查权限与版本，退回和取消保留执行原因。 */
    @PostMapping("/{id}/transition")
    @Transactional
    @org.dromara.common.log.annotation.Log(title="客户订单流转",businessType=org.dromara.common.log.enums.BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> transition(@PathVariable Long id,@RequestBody Action input) {
        var before = load(id,true).getData();
        if (!Objects.equals(input.revision(),before.get("revision"))) throw new ServiceException("订单版本已变化，请重新加载");
        String state = before.get("status").toString();
        if (!"submit".equals(input.action()) && !"draft".equals(state)) {
            Boolean managed = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM sys_approval_business_instance
                WHERE business_type='customer_order' AND business_id=? AND state IN ('starting','pending','approved'))
                """, Boolean.class, id);
            if (Boolean.TRUE.equals(managed)) throw new ServiceException("该订单由审批流程管理，请在审批模块处理");
        }

        String target;
        Object demand = before.get("demand");
        var doc = JsonUtils.parseMap(JsonUtils.toJsonString(before.get("document")));
        switch (input.action()) {
            case "submit" -> {
                StpUtil.checkPermission("order:customer-order:edit");
                if (!state.equals("draft")) throw new ServiceException("仅草稿可提交");
                var saved = storedInput(before);
                if (before.get("followerId")==null) throw new ServiceException("请指定跟单员");
                doc.put("pos",validate(saved.pos(),Long.valueOf(before.get("customerId").toString()),id,true));
                // 订单审核不依赖 BOM；已审核后再保存完整用料快照。
                // Order submission is independent of BOM readiness; material planning can follow approval.
                demand=List.of();
                target="submitted";
            }
            case "approve", "return" -> {
                StpUtil.checkPermission("order:customer-order:review");
                if (!state.equals("submitted")) throw new ServiceException("订单已不在待审核状态");
                target=input.action().equals("approve")?"approved":"draft";
                if (target.equals("draft")) demand=List.of();
            }
            case "unsubmit" -> {
                StpUtil.checkPermission("order:customer-order:edit");
                if (!state.equals("submitted")) throw new ServiceException("仅待审核订单可以反提交");
                // 草稿允许修改，旧需求快照不再代表当前订单；再次提交时重新计算。
                // The draft may change, so rebuild its demand snapshot on the next submission.
                target="draft";
                demand=List.of();
            }
            case "unapprove" -> {
                StpUtil.checkPermission("order:customer-order:review");
                if (!state.equals("approved")) throw new ServiceException("仅已审核订单可以反审核");
                // 后续订单下游单据上线时，须在同一事务加引用检查，存在有效执行记录则禁止反审核。
                // Future downstream documents must be checked transactionally before approval can be reversed.
                target="submitted";
            }
            case "cancel" -> {
                StpUtil.checkPermission("order:customer-order:cancel");
                if (!state.equals("draft") && !state.equals("submitted")) throw new ServiceException("已审核订单需通过订单变更处理");
                target="cancelled";
            }
            default -> throw new ServiceException("未知的订单操作");
        }
        String reason = clean(input.reason());
        if ((input.action().equals("return") || input.action().equals("cancel")) && reason.isEmpty())
            throw new ServiceException("请填写操作原因");
        doc.put("reviewNote",reason);
        jdbc.update("UPDATE biz_customer_order SET status=?,document=?::jsonb,demand=?::jsonb,revision=revision+1,updated_at=now() WHERE id=?",
            target,JsonUtils.toJsonString(doc),JsonUtils.toJsonString(demand),id);
        if ("submit".equals(input.action())) approval.submit(id, input.revision()+1, before.get("orderNo").toString());
        var after = load(id,false).getData();
        BusinessAudit.record(switch(input.action()) {
            case "submit" -> "提交客户订单";
            case "approve" -> "审核通过客户订单";
            case "return" -> "退回客户订单";
            case "unsubmit" -> "反提交客户订单";
            case "unapprove" -> "反审核客户订单";
            default -> "取消客户订单";
        },id,"orderNo",before,after);
        return R.ok(after);
    }
}
