package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 公共单位和分类业务服务；管理端和后续业务统一复用校验与换算，不通过业务代码隐式建表。
 * Shared foundation service for validated units, conversions and isolated category hierarchies.
 */
@Service
@RequiredArgsConstructor
public class FoundationDataService {
    private final JdbcTemplate jdbc;
    private static final String UNIT_SELECT = "SELECT unit_id::text AS id,unit_code AS code,unit_name AS name,symbol,dimension,decimal_places AS \"decimalPlaces\",base_unit_id::text AS \"baseUnitId\",factor::text AS factor,sort_order AS \"sortOrder\",status,remark FROM base_unit";
    private static final String SCHEME_SELECT = "SELECT scheme_id::text AS id,scheme_code AS code,scheme_name AS name,max_depth AS \"maxDepth\",sort_order AS \"sortOrder\",status,remark FROM base_category_scheme";
    private static final String CATEGORY_SELECT = "SELECT category_id::text AS id,scheme_id::text AS \"schemeId\",parent_id::text AS \"parentId\",category_code AS code,category_name AS name,sort_order AS \"sortOrder\",status,remark FROM base_category";
    private static final Set<String> DIMENSIONS = Set.of("length","mass","area","volume","time","count","other");

    /** 查询全部目录供管理端完整筛选分页，避免仅筛当前页。 / Load complete small directories for accurate client filtering. */
    public List<Map<String,Object>> units() { return jdbc.queryForList(UNIT_SELECT + " WHERE del_flag='0' ORDER BY sort_order,unit_id"); }

    /** 读取单位并锁定，串行化换算关系修改与引用校验。 / Lock a live unit before changing its semantics. */
    private Map<String,Object> unit(Long id) { return one(UNIT_SELECT + " WHERE unit_id=? AND del_flag='0' FOR UPDATE",id); }

    /** 查询单位选项并标记参照单位停用造成的不可选状态。 / Preserve disabled labels while marking effective availability. */
    public List<Map<String,Object>> unitOptions() {
        var rows = units();
        Set<String> enabled = new HashSet<>();
        rows.stream().filter(r -> "0".equals(r.get("status"))).forEach(r -> enabled.add(r.get("id").toString()));
        rows.forEach(r -> r.put("selectable", "0".equals(r.get("status")) && (r.get("baseUnitId")==null || enabled.contains(r.get("baseUnitId").toString()))));
        return rows;
    }

    /** 保存单位；换算语义被引用后不可变，不能跨维度或链式换算。 / Persist a unit with immutable referenced conversion semantics. */
    @Transactional
    public String saveUnit(Long id, Map<String,Object> body) {
        String code=code(body), name=text(body,"name",80,true), symbol=text(body,"symbol",20,false);
        String dimension=text(body,"dimension",20,true), status=status(body);
        if(!DIMENSIONS.contains(dimension)) fail("请选择有效计量维度");
        int decimals=integer(body,"decimalPlaces",3,0,8), sort=integer(body,"sortOrder",0,0,999999);
        String remark=text(body,"remark",500,false);
        Long base=id(body.get("baseUnitId"));
        BigDecimal factor=decimal(body.getOrDefault("factor","1"));
        if(factor.signum()<=0 || factor.scale()>12 || factor.precision()-factor.scale()>18) fail("换算系数必须大于0，最多18位整数和12位小数");
        if(base==null && factor.compareTo(BigDecimal.ONE)!=0) fail("独立参照单位的换算系数必须为1");
        // 全局事务锁固定顺序，防止单位之间互相修改参照造成死锁或环路。
        // Serialize the small unit dictionary to avoid reciprocal-reference races.
        jdbc.execute("SELECT pg_advisory_xact_lock(20261105,1)");
        Map<String,Object> before=id==null?Map.of():unit(id);
        if(base!=null) {
            if(base.equals(id)) fail("不能以自身为换算参照");
            var root=unit(base);
            if(root.get("baseUnitId")!=null) fail("请选择独立参照单位，不支持多层换算链");
            if(!dimension.equals(root.get("dimension"))) fail("参照单位必须属于相同计量维度");
            boolean retained=Objects.equals(before.get("baseUnitId"),base.toString());
            if(!"0".equals(root.get("status")) && (!retained || "0".equals(status))) fail("参照单位已停用，不能新增引用或启用");
        }
        if(id!=null) {
            boolean dependents=count("SELECT count(*) FROM base_unit WHERE base_unit_id=? AND del_flag='0'",id)>0;
            boolean changed=!dimension.equals(before.get("dimension")) || !Objects.equals(base==null?null:base.toString(),before.get("baseUnitId")) || factor.compareTo(decimal(before.get("factor")))!=0 || decimals!=((Number)before.get("decimalPlaces")).intValue();
            if(changed && (dependents || referenced("base_unit",id))) fail("单位已被引用，不能修改计量维度、精度或换算关系，请新增单位");
            if("1".equals(status) && count("SELECT count(*) FROM base_unit WHERE base_unit_id=? AND status='0' AND del_flag='0'",id)>0) fail("请先停用关联换算单位，再停用参照单位");
        }
        if(count("SELECT count(*) FROM base_unit WHERE (unit_code=? OR (lower(unit_name)=lower(?) AND del_flag='0')) AND unit_id<>?",code,name,id==null?0:id)>0) fail("单位编码或名称已存在（历史编码不可复用）");
        if(id==null) id=jdbc.queryForObject("INSERT INTO base_unit(unit_code,unit_name,symbol,dimension,decimal_places,base_unit_id,factor,sort_order,status,remark,create_by) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING unit_id",Long.class,code,name,symbol,dimension,decimals,base,factor,sort,status,remark,LoginHelper.getUserId());
        else jdbc.update("UPDATE base_unit SET unit_code=?,unit_name=?,symbol=?,dimension=?,decimal_places=?,base_unit_id=?,factor=?,sort_order=?,status=?,remark=?,update_by=?,update_time=now() WHERE unit_id=?",code,name,symbol,dimension,decimals,base,factor,sort,status,remark,LoginHelper.getUserId(),id);
        audit(before.isEmpty()?"新增计量单位":"修改计量单位",id,before,unit(id));
        return id.toString();
    }

    /** 换算仅限同一参照根；数量×源系数÷目标系数，按目标精度四舍五入。 / Convert within one root using target precision and HALF_UP rounding. */
    @Transactional
    public Map<String,Object> convert(String fromId,String toId,String amount) {
        jdbc.execute("SELECT pg_advisory_xact_lock(20261105,1)");
        var from=unit(requiredId(fromId)); var to=unit(requiredId(toId));
        if(!"0".equals(from.get("status")) || !"0".equals(to.get("status"))) fail("停用单位不能用于新换算");
        Object rootFrom=from.get("baseUnitId")==null?from.get("id"):from.get("baseUnitId");
        Object rootTo=to.get("baseUnitId")==null?to.get("id"):to.get("baseUnitId");
        if(!rootFrom.equals(rootTo) || !from.get("dimension").equals(to.get("dimension"))) fail("单位没有共同固定换算参照，不能换算；包装或物料专属换算应由业务维护");
        if(!"0".equals(unit(requiredId(rootFrom)).get("status"))) fail("参照单位已停用");
        BigDecimal quantity=decimal(amount);
        if(quantity.signum()<0 || quantity.precision()>30 || quantity.scale()>12) fail("换算数量须非负，最多30位有效数字和12位小数");
        BigDecimal result=quantity.multiply(decimal(from.get("factor"))).divide(decimal(to.get("factor")),((Number)to.get("decimalPlaces")).intValue(),RoundingMode.HALF_UP);
        return Map.of("quantity",result.toPlainString(),"fromId",fromId,"toId",toId,"rounding","HALF_UP");
    }

    /** 查询分类体系；业务以稳定编码选定体系。 / List independently reusable taxonomy schemes. */
    public List<Map<String,Object>> schemes() { return jdbc.queryForList(SCHEME_SELECT+" WHERE del_flag='0' ORDER BY sort_order,scheme_id"); }

    /** 锁定体系，所有节点写入共享此锁以阻止并发成环。 / Lock the scheme as the hierarchy mutation boundary. */
    private Map<String,Object> scheme(Long id) { return one(SCHEME_SELECT+" WHERE scheme_id=? AND del_flag='0' FOR UPDATE",id); }

    /** 保存体系及深度限制；已有结构不因降低深度而失效。 / Save a scheme without invalidating existing tree depths. */
    @Transactional
    public String saveScheme(Long id,Map<String,Object> body) {
        jdbc.execute("SELECT pg_advisory_xact_lock(20261105,2)");
        String code=code(body),name=text(body,"name",80,true),status=status(body),remark=text(body,"remark",500,false);
        int depth=integer(body,"maxDepth",5,1,20),sort=integer(body,"sortOrder",0,0,999999);
        var before=id==null?Map.<String,Object>of():scheme(id);
        if(id!=null && !code.equals(before.get("code"))) fail("体系编码创建后不可修改");
        if(id!=null && count("WITH RECURSIVE tree AS (SELECT category_id,1 AS depth FROM base_category WHERE scheme_id=? AND parent_id IS NULL AND del_flag='0' UNION ALL SELECT c.category_id,t.depth+1 FROM base_category c JOIN tree t ON c.parent_id=t.category_id WHERE c.del_flag='0') SELECT count(*) FROM tree WHERE depth>?",id,depth)>0) fail("现有分类超过目标最大层级，请先调整分类结构");
        if(count("SELECT count(*) FROM base_category_scheme WHERE (scheme_code=? OR (lower(scheme_name)=lower(?) AND del_flag='0')) AND scheme_id<>?",code,name,id==null?0:id)>0) fail("分类体系编码或名称已存在");
        if(id==null) id=jdbc.queryForObject("INSERT INTO base_category_scheme(scheme_code,scheme_name,max_depth,sort_order,status,remark,create_by) VALUES (?,?,?,?,?,?,?) RETURNING scheme_id",Long.class,code,name,depth,sort,status,remark,LoginHelper.getUserId());
        else jdbc.update("UPDATE base_category_scheme SET scheme_name=?,max_depth=?,sort_order=?,status=?,remark=?,update_by=?,update_time=now() WHERE scheme_id=?",name,depth,sort,status,remark,LoginHelper.getUserId(),id);
        audit(before.isEmpty()?"新增分类体系":"修改分类体系",id,before,scheme(id)); return id.toString();
    }

    /** 返回单体系平铺节点，父级ID构树并保留停用历史显示。 / Load a complete scheme tree as stable flat nodes. */
    public List<Map<String,Object>> categories(Long schemeId) { return jdbc.queryForList(CATEGORY_SELECT+" WHERE scheme_id=? AND del_flag='0' ORDER BY sort_order,category_id",schemeId); }

    /** 查询分类选项并按整条祖先链和体系状态计算可选性。 / Evaluate effective availability through all ancestors. */
    public List<Map<String,Object>> categoryOptions(Long schemeId) {
        var rows=categories(schemeId);
        var ss=jdbc.queryForList("SELECT status FROM base_category_scheme WHERE scheme_id=? AND del_flag='0'",schemeId);
        boolean active=!ss.isEmpty() && "0".equals(ss.getFirst().get("status"));
        Map<String,Map<String,Object>> index=new HashMap<>(); rows.forEach(r->index.put(r.get("id").toString(),r));
        for(var row:rows) {
            boolean enabled=active; var current=row; Set<String> seen=new HashSet<>();
            while(current!=null) {
                if(!seen.add(current.get("id").toString()) || !"0".equals(current.get("status"))) {enabled=false;break;}
                Object parent=current.get("parentId"); if(parent==null) break;
                current=index.get(parent.toString()); if(current==null) enabled=false;
            }
            row.put("selectable",enabled);
        }
        return rows;
    }

    /** 锁定单个分类以获取真实修改前值。 / Fetch the current category snapshot under lock. */
    private Map<String,Object> category(Long id) { return one(CATEGORY_SELECT+" WHERE category_id=? AND del_flag='0' FOR UPDATE",id); }

    /** 保存分类；同体系父链、循环、深度和停用约束均在后端事务内检查。 / Validate hierarchy invariants within a serialized scheme transaction. */
    @Transactional
    public String saveCategory(Long id,Map<String,Object> body) {
        Long schemeId=requiredId(body.get("schemeId")); var owner=scheme(schemeId);
        var before=id==null?Map.<String,Object>of():category(id);
        if(id!=null && !schemeId.toString().equals(before.get("schemeId"))) fail("分类不能迁移到其他体系");
        if(!"0".equals(owner.get("status"))) fail("分类体系已停用，请先启用体系再维护节点");
        Long parent=id(body.get("parentId")); String code=code(body),name=text(body,"name",80,true),status=status(body),remark=text(body,"remark",500,false);
        int sort=integer(body,"sortOrder",0,0,999999),parentDepth=0;
        Set<Long> seen=new HashSet<>(); Long cursor=parent;
        while(cursor!=null) {
            if(cursor.equals(id) || !seen.add(cursor)) fail("上级分类不能是自身或下级，禁止循环");
            var row=category(cursor);
            if(!schemeId.toString().equals(row.get("schemeId"))) fail("不能选择其他体系的分类作为上级");
            if(!"0".equals(row.get("status")) && ("0".equals(status) || !Objects.equals(before.get("parentId"),parent==null?null:parent.toString()))) fail("上级分类已停用，不能新增引用或启用下级");
            parentDepth++; cursor=id(row.get("parentId"));
        }
        int subtree=id==null?1:jdbc.queryForObject("WITH RECURSIVE tree AS (SELECT category_id,1 AS depth FROM base_category WHERE category_id=? UNION ALL SELECT c.category_id,t.depth+1 FROM base_category c JOIN tree t ON c.parent_id=t.category_id WHERE c.del_flag='0') SELECT max(depth) FROM tree",Integer.class,id);
        if(parentDepth+subtree>((Number)owner.get("maxDepth")).intValue()) fail("移动后的分类树超过体系最大层级");
        if(id!=null && "1".equals(status) && count("SELECT count(*) FROM base_category WHERE parent_id=? AND status='0' AND del_flag='0'",id)>0) fail("请先停用下级分类");
        if(count("SELECT count(*) FROM base_category WHERE scheme_id=? AND category_id<>? AND (category_code=? OR (del_flag='0' AND coalesce(parent_id,0)=? AND lower(category_name)=lower(?)))",schemeId,id==null?0:id,code,parent==null?0:parent,name)>0) fail("分类编码已存在或同一上级下名称重复");
        if(id==null) id=jdbc.queryForObject("INSERT INTO base_category(scheme_id,parent_id,category_code,category_name,sort_order,status,remark,create_by) VALUES (?,?,?,?,?,?,?,?) RETURNING category_id",Long.class,schemeId,parent,code,name,sort,status,remark,LoginHelper.getUserId());
        else jdbc.update("UPDATE base_category SET parent_id=?,category_code=?,category_name=?,sort_order=?,status=?,remark=?,update_by=?,update_time=now() WHERE category_id=?",parent,code,name,sort,status,remark,LoginHelper.getUserId(),id);
        audit(before.isEmpty()?"新增分类":"修改分类",id,before,category(id)); return id.toString();
    }

    /** 删除独立单位；显式外键引用和任何未删除换算单位均阻止删除。 / Soft-delete an unreferenced unit under the shared dictionary lock. */
    @Transactional
    public void deleteUnit(Long id) {
        jdbc.execute("SELECT pg_advisory_xact_lock(20261105,1)"); var before=unit(id);
        if(count("SELECT count(*) FROM base_unit WHERE base_unit_id=? AND del_flag='0'",id)>0 || referenced("base_unit",id)) fail("单位已被引用，不能删除，可改为停用");
        jdbc.update("UPDATE base_unit SET del_flag='1',update_by=?,update_time=now() WHERE unit_id=?",LoginHelper.getUserId(),id);
        audit("删除计量单位",id,before,Map.of());
    }

    /** 删除空体系或无引用叶节点，避免孤儿和历史关联丢失。 / Delete only empty schemes or unreferenced leaves. */
    @Transactional
    public void deleteCategory(Long id,boolean isScheme) {
        Map<String,Object> before;
        if(isScheme) {
            before=scheme(id);
            if(count("SELECT count(*) FROM base_category WHERE scheme_id=? AND del_flag='0'",id)>0 || referenced("base_category_scheme",id)) fail("体系存在分类或业务引用，不能删除");
        } else {
            var raw=one(CATEGORY_SELECT+" WHERE category_id=? AND del_flag='0'",id); scheme(requiredId(raw.get("schemeId"))); before=category(id);
            if(count("SELECT count(*) FROM base_category WHERE parent_id=? AND del_flag='0'",id)>0 || referenced("base_category",id)) fail("分类存在下级或业务引用，不能删除，可改为停用");
        }
        String table=isScheme?"base_category_scheme":"base_category",column=isScheme?"scheme_id":"category_id";
        jdbc.update("UPDATE "+table+" SET del_flag='1',update_by=?,update_time=now() WHERE "+column+"=?",LoginHelper.getUserId(),id);
        audit(isScheme?"删除分类体系":"删除分类",id,before,Map.of());
    }

    /** 扫描数据库声明的单列外键，历史引用同样保护；表名仅来自可信系统目录。 / Guard incoming foreign keys using quoted catalog identifiers. */
    private boolean referenced(String table,Long id) {
        var refs=jdbc.queryForList("SELECT n.nspname AS s,t.relname AS t,a.attname AS c,cardinality(k.conkey) AS size FROM pg_constraint k JOIN pg_class t ON t.oid=k.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.conkey[1] WHERE k.contype='f' AND k.confrelid=to_regclass(?) AND k.conrelid<>k.confrelid", "public."+table);
        for(var ref:refs) {
            if("base_category_scheme".equals(table) && "base_category".equals(ref.get("t"))) continue;
            if(((Number)ref.get("size")).intValue()!=1) return true;
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+quote(ref.get("s"))+"."+quote(ref.get("t"))+" WHERE "+quote(ref.get("c"))+"=?)",Boolean.class,id))) return true;
        }
        return false;
    }

    /** 对数据库标识符转义，不接受业务请求提供的表名。 / Quote trusted catalog identifiers only. */
    private String quote(Object v) { return "\""+v.toString().replace("\"","\"\"")+"\""; }
    /** 读取必须存在的目录项。 / Fail explicitly when a live dictionary item is absent. */
    private Map<String,Object> one(String sql,Object...args) { var rows=jdbc.queryForList(sql,args); if(rows.isEmpty()) throw new ServiceException("档案不存在或已删除"); return rows.getFirst(); }
    /** 读取校验计数。 / Evaluate a parameterized invariant count. */
    private long count(String sql,Object...args) { Long value=jdbc.queryForObject(sql,Long.class,args); return value==null?0:value; }
    /** 读取非空稳定主键，拒绝0及负数。 / Parse a positive ID without JavaScript-number coercion. */
    private Long requiredId(Object raw) { Long value=id(raw); if(value==null) throw new ServiceException("档案ID不能为空"); return value; }
    /** 解析可选主键，格式错误明确返回业务错误。 / Parse an optional decimal ID. */
    private Long id(Object raw) { if(raw==null || raw.toString().isBlank()) return null; try {long v=Long.parseLong(raw.toString()); if(v<=0) fail("档案ID必须为正整数"); return v;} catch(NumberFormatException e) {throw new ServiceException("档案ID格式不正确");} }
    /** 统一校验编码，不因自动建议而限制用户填写。 / Validate editable business codes independently of IDs. */
    private String code(Map<String,Object> body) {String value=text(body,"code",40,true); if(!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,39}")) fail("编码只能包含字母、数字、下划线、点和短横线"); return value;}
    /** 读取有长度边界的文本。 / Normalize bounded business strings. */
    private String text(Map<String,Object> b,String key,int max,boolean required) {String value=Objects.toString(b.get(key),"").trim(); if(required && value.isBlank()) fail("名称、编码及必填字段不能为空"); if(value.length()>max) fail("字段"+key+"超过最大长度"+max); return value;}
    /** 校验启停枚举，不静默接收未知状态。 / Accept only supported lifecycle values. */
    private String status(Map<String,Object> b) {String value=Objects.toString(b.getOrDefault("status","0")); if(!Set.of("0","1").contains(value)) fail("状态必须为0启用或1停用"); return value;}
    /** 校验整数边界，不截断小数。 / Parse bounded integers without truncation. */
    private int integer(Map<String,Object> b,String key,int fallback,int min,int max) {try {int value=Integer.parseInt(Objects.toString(b.getOrDefault(key,fallback))); if(value<min || value>max) fail(key+"超出允许范围"); return value;} catch(NumberFormatException e) {throw new ServiceException(key+"必须为整数");}}
    /** 使用十进制精确数，不通过浮点转换。 / Parse exact decimal input. */
    private BigDecimal decimal(Object value) {
        String raw=Objects.toString(value,"");
        if(raw.length()>48 || !raw.matches("[+-]?(?:[0-9]{1,30}(?:\\.[0-9]{1,12})?|\\.[0-9]{1,12})")) fail("数量或系数须为普通十进制数，最多30位整数和12位小数，不支持科学计数法");
        try{return new BigDecimal(raw);}catch(NumberFormatException e){throw new ServiceException("数量或换算系数格式不正确");}
    }
    /** 统一抛出可读业务异常，触发事务回滚。 / Abort invalid mutations with a readable domain error. */
    private void fail(String message) {throw new ServiceException(message);}
    /** 以真实落库字段构建中文业务审计，不记录无关请求正文。 / Record actual persisted changes with Chinese field labels. */
    private void audit(String action,Long id,Map<String,Object> before,Map<String,Object> after) {
        BusinessAudit.record(action,id,"名称",auditFields(before),auditFields(after));
    }
    /** 将公共字段映射成可读日志，保留精确换算值和关联ID。 / Map snapshots to explicit business audit labels. */
    private Map<String,Object> auditFields(Map<String,Object> source) {
        Map<String,Object> out=new LinkedHashMap<>();
        String[][] labels={{"id","档案ID"},{"code","编码"},{"name","名称"},{"symbol","符号"},{"dimension","计量维度"},{"decimalPlaces","小数位数"},{"baseUnitId","参照单位ID"},{"factor","换算系数"},{"schemeId","分类体系ID"},{"parentId","上级分类ID"},{"maxDepth","最大层级"},{"sortOrder","排序"},{"remark","备注"}};
        for(var field:labels) if(source.containsKey(field[0])) out.put(field[1],source.get(field[0]));
        if(source.containsKey("status")) out.put("状态","0".equals(source.get("status"))?"启用":"停用"); return out;
    }
}
