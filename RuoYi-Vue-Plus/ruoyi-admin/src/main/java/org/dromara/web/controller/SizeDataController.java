/*
 * 文件用途：为织云 ERP 的尺码基础资料接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的尺码基础资料接口提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.dromara.common.json.utils.JsonUtils;
import java.util.*;

/** 尺码与尺码组基础资料：稳定ID、独立权限、事务保存组内顺序，停用不删除历史。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/size-data/{kind}")
public class SizeDataController {
 private final JdbcTemplate jdbc;
 /** 路由只能映射到两个固定表名，禁止用户输入参与动态表名。 */
 private String table(String kind) {
  if (!Set.of("size","size-group").contains(kind)) throw new IllegalArgumentException("未知尺码资料类型");
  return kind.equals("size") ? "sys_size" : "sys_size_group";
 }
 private void permission(String kind,String action) { table(kind); StpUtil.checkPermission("system:"+kind+":"+action); }
 @GetMapping("/list")
 public R<List<Map<String,Object>>> list(@PathVariable String kind) {
  permission(kind,"list");
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM "+table(kind)+" ORDER BY sort_order,id");
  for(Map<String,Object> row:rows) {
   Long id=((Number)row.get("id")).longValue();
   row.put("tagIds",jdbc.queryForList("SELECT tag_id::text FROM sys_tag_relation WHERE module_key=? AND record_id=?",String.class,kind.equals("size")?"size":"size_group",id));
   row.put("tagOptions",jdbc.queryForList("SELECT t.tag_id::text AS value,t.tag_name AS label,g.group_name AS \"groupName\",(t.status<>'0') AS disabled FROM sys_tag_relation r JOIN sys_tag t ON t.tag_id=r.tag_id JOIN sys_tag_group g ON g.group_id=t.tag_group_id WHERE r.module_key=? AND r.record_id=?",kind.equals("size")?"size":"size_group",id));
   if(kind.equals("size-group")) row.put("items",jdbc.queryForList("SELECT s.*,i.sort_order AS group_order FROM sys_size_group_item i JOIN sys_size s ON s.id=i.size_id WHERE i.group_id=? ORDER BY i.sort_order",id));
  }
  return R.ok(rows);
 }
 /** 业务选择接口只要求登录，停用档案仍携带状态用于原引用回显。 */
 @GetMapping("/options")
 public R<List<Map<String,Object>>> options(@PathVariable String kind) {
  table(kind);StpUtil.checkLogin();
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM "+table(kind)+" ORDER BY sort_order,id");
  if(kind.equals("size-group")) for(Map<String,Object> row:rows) row.put("items",jdbc.queryForList("SELECT s.*,i.sort_order AS group_order FROM sys_size_group_item i JOIN sys_size s ON s.id=i.size_id WHERE i.group_id=? ORDER BY i.sort_order",row.get("id")));
  return R.ok(rows);
 }
 /**
  * 创建尺码或尺码组基础资料，并复用统一保存逻辑处理编码、关联标签和组内成员。
  * 路径 kind 只允许 size / size-group，具体表名由白名单映射产生，不接受调用方表名。
  */
 @PostMapping
 @Transactional
 @Log(title="尺码基础资料",businessType=BusinessType.INSERT)
 public R<Long> add(@PathVariable String kind,@RequestBody Map<String,Object> body) { permission(kind,"add"); return save(kind,null,body); }
 /**
  * 原子替换多条尺码记录的标签集合。
  * 每次最多处理 500 条；所有目标尺码与标签都必须有效，失败时事务回滚整批变更。
  */
 @PostMapping("/batch-tags")
 @Transactional
 @Log(title="尺码类型标签批量修改",businessType=BusinessType.UPDATE)
 public R<Void> batchTags(@PathVariable String kind,@RequestBody Map<String,Object> body) {
  permission(kind,"edit");
  // 尺码组没有此业务标签批量编辑契约，不能因为共用路由而对它开放该动作。
  if(!kind.equals("size")) return R.fail("仅尺码支持批量修改类型标签");
  // 对批量大小设上限，避免一次请求构造过大的 SQL 参数列表并锁住过多记录。
  if(!(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty() || rawIds.size()>500) return R.fail("请选择1到500条尺码");
  List<Long> ids=new ArrayList<>();
  // 先把外部 JSON 值规范成主键，再拒绝重复项，确保后续快照和审计都一一对应。
  try { for(Object value:rawIds) ids.add(Long.valueOf(value.toString())); } catch(Exception e) { return R.fail("尺码编号无效"); }
  if(new HashSet<>(ids).size()!=ids.size()) return R.fail("尺码列表包含重复项");
  List<Long> tags=new ArrayList<>();
  if(body.get("tagIds") instanceof List<?> rawTags) {
   try { for(Object value:rawTags) tags.add(Long.valueOf(value.toString())); } catch(Exception e) { return R.fail("标签编号无效"); }
  } else if(body.containsKey("tagIds")) return R.fail("标签列表无效");
  if(new HashSet<>(tags).size()!=tags.size()) return R.fail("标签列表包含重复项");
  // 按主键锁定实际记录，并要求查询结果数量与请求一致；列表过期时整批停止，不忽略缺失项。
  String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  List<Object> args=new ArrayList<>(ids);
  List<Long> found=jdbc.queryForList("SELECT id FROM sys_size WHERE id IN ("+placeholders+") ORDER BY id FOR UPDATE",Long.class,args.toArray());
  if(found.size()!=ids.size()) return R.fail("部分尺码已不存在，请刷新列表后重试");
  Map<Long,Map<String,Object>> before=new LinkedHashMap<>();
  for(Long id:ids) before.put(id,snapshot("size",id,false));
  // 允许继续使用已停用但原本已关联的标签，以免批量保存其它标签时意外丢失历史分类；新关联只能选启用标签。
  for(Long tag:tags) {
   List<Object> tagArgs=new ArrayList<>(); tagArgs.add(tag); tagArgs.addAll(ids);
   String idPlaceholders=String.join(",",Collections.nCopies(ids.size(),"?"));
   String sql="SELECT count(*) FROM sys_tag t WHERE t.tag_id=? AND t.module_key='size' AND (t.status='0' OR EXISTS(SELECT 1 FROM sys_tag_relation r WHERE r.module_key='size' AND r.tag_id=t.tag_id AND r.record_id IN ("+idPlaceholders+")))";
   if(jdbc.queryForObject(sql,Long.class,tagArgs.toArray())==0) return R.fail("标签无效或已停用");
  }
  Long user=LoginHelper.getUserId();
  // 事务内先清理本批目标的旧关联，再写入完整新集合，审计同时记录变更前后标签列表。
  for(Long id:ids) {
   jdbc.update("DELETE FROM sys_tag_relation WHERE module_key='size' AND record_id=?",id);
   for(Long tag:tags) jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by) VALUES('size',?,?,?)",id,tag,user);
  }
  ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
      "action","批量修改尺码类型标签","count",ids.size(),"ids",ids.stream().map(String::valueOf).toList(),
      "changes",Map.of("tagIds",Arrays.asList(before.values().stream().map(row -> row.get("tagIds")).toList(),tags.stream().map(String::valueOf).toList()))));
  return R.ok();
 }
 /**
  * 删除尺码或尺码组，并清理本档案拥有的组内成员与标签关系。
  * 尺码仍被尺码组引用时拒绝删除，防止破坏组内顺序和既有选择关系。
  */
 @DeleteMapping("/{id}")
 @Transactional
 @Log(title="尺码基础资料",businessType=BusinessType.DELETE)
 public R<Void> delete(@PathVariable String kind,@PathVariable Long id) {
  permission(kind,"delete");
  Map<String,Object> before=snapshot(kind,id,true);
  if(before.isEmpty()) return R.fail("记录不存在");
  if(kind.equals("size")) {
   // 尺码被尺码组引用时保留档案，让维护者先在尺码组中解除引用。
   Long used=jdbc.queryForObject("SELECT count(*) FROM sys_size_group_item WHERE size_id=?",Long.class,id);
   if(used!=null && used>0) return R.fail("该尺码仍被"+used+"个尺码组引用，请先从尺码组中移除");
  } else {
   // 尺码组是成员关系的拥有方；删除组时清理子项，但不删除组内尺码档案。
   jdbc.update("DELETE FROM sys_size_group_item WHERE group_id=?",id);
  }
  String module=kind.equals("size")?"size":"size_group";
  jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?",module,id);
  jdbc.update("DELETE FROM "+table(kind)+" WHERE id=?",id);
  ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
      "id",id.toString(),"name",before.get("name"),"status",before.get("status"),
      "deleted",true,"changes",Map.of("_record",Arrays.asList(before,Map.of("deleted",true)))));
  return R.ok();
 }
 /** 更新尺码或尺码组主档，并由统一保存逻辑维护相关字段及组成员。 */
 @PutMapping("/{id}")
 @Transactional
 @Log(title="尺码基础资料",businessType=BusinessType.UPDATE)
 public R<Long> edit(@PathVariable String kind,@PathVariable Long id,@RequestBody Map<String,Object> body) { permission(kind,"edit"); return save(kind,id,body); }
 /** 启停与编辑共用权限，不修改组内引用或已有业务。 */
 @Transactional
 @PutMapping("/{id}/status")
 @Log(title="尺码基础资料状态",businessType=BusinessType.UPDATE)
 public R<Void> status(@PathVariable String kind,@PathVariable Long id,@RequestBody Map<String,String> b) {
  permission(kind,"edit");String status=b.get("status");
  if(status==null || !Set.of("0","1").contains(status)) return R.fail("状态无效");
  Map<String,Object> before = snapshot(kind,id,true);
  if(jdbc.update("UPDATE "+table(kind)+" SET status=?,update_by=?,update_time=now() WHERE id=?",status,LoginHelper.getUserId(),id)==0) return R.fail("记录不存在");
  audit(kind,id,before);
  return R.ok();
 }
 /** 所有明细与标签先验证再写入；运行异常回滚整个主从档案。 */
 private R<Long> save(String kind,Long id,Map<String,Object> b) {
  String name=Objects.toString(b.get("name"),"").trim(), en=Objects.toString(b.get("name_en"),"").trim(),remark=Objects.toString(b.get("remark"),"").trim();
  if(name.isBlank() || name.length()>100 || en.length()>200 || remark.length()>2000) return R.fail("请检查名称、英文名称及备注长度");
  int sort;
  try { sort=Integer.parseInt(Objects.toString(b.get("sort_order"),"0")); } catch(Exception e) { return R.fail("排序必须为整数"); }
  String t=table(kind),module=kind.equals("size")?"size":"size_group";
  Map<String,Object> before = id == null ? Map.of() : snapshot(kind,id,true);
  if(id != null && before.isEmpty()) return R.fail("记录不存在");
  List<Long> sizes=new ArrayList<>();
  if(kind.equals("size-group")) {
   if(!(b.get("sizeIds") instanceof List<?> raw) || raw.isEmpty()) return R.fail("尺码组至少包含一个尺码");
   try { for(Object value:raw) sizes.add(Long.valueOf(value.toString())); } catch(Exception e) { return R.fail("尺码无效"); }
   if(new HashSet<>(sizes).size()!=sizes.size()) return R.fail("组内不能重复添加同一尺码");
   Set<String> names=new HashSet<>();
   for(Long size:sizes) {
    List<Map<String,Object>> found=jdbc.queryForList("SELECT name,status FROM sys_size WHERE id=? FOR SHARE",size);
    if(found.isEmpty()) return R.fail("尺码不存在");
    if(!names.add(found.get(0).get("name").toString().trim().toLowerCase(Locale.ROOT))) return R.fail("组内不能包含同名尺码");
    if(!"0".equals(found.get(0).get("status")) && (id==null || jdbc.queryForObject("SELECT count(*) FROM sys_size_group_item WHERE group_id=? AND size_id=?",Long.class,id,size)==0)) return R.fail("不能新增停用尺码");
   }
  }
  List<Long> tags=new ArrayList<>();
  if(b.get("tagIds") instanceof List<?> values) for(Object value:values) {
   Long tag;try {tag=Long.valueOf(value.toString());}catch(Exception e){return R.fail("标签无效");}
   if(jdbc.queryForObject("SELECT count(*) FROM sys_tag WHERE tag_id=? AND module_key=? AND (status='0' OR EXISTS(SELECT 1 FROM sys_tag_relation WHERE module_key=? AND record_id=? AND tag_id=?))",Long.class,tag,module,module,id==null?0L:id,tag)==0) return R.fail("标签无效或已停用");
   if(!tags.contains(tag)) tags.add(tag);
  }
  Long user=LoginHelper.getUserId();
  if(id==null) {
   // 尺码编码从当前占用集合分配；串行化新增，避免并发请求选中同一个空号。
   jdbc.execute(kind.equals("size") ? "SELECT pg_advisory_xact_lock(2026110404)" : "SELECT pg_advisory_xact_lock(2026110405)");
   String code=Objects.toString(b.get("code"),"").trim();
   SizeCodeRule rule=kind.equals("size")?sizeCodeRule():sizeGroupCodeRule();
   if(code.isEmpty() && !rule.enabled()) return R.fail(kind.equals("size")?"自动编码已关闭，请手动填写尺码编码":"尺码组自动编码已关闭，请手动填写编号");
   if(!code.isEmpty() && code.length()>40) return R.fail("编码不能超过40个字符");
   if(!code.isEmpty() && jdbc.queryForObject("SELECT count(*) FROM "+t+" WHERE lower(code)=lower(?)",Long.class,code)>0) return R.fail("编码已存在");
   id=code.isEmpty()
       ?jdbc.queryForObject("INSERT INTO "+t+"(name,name_en,sort_order,remark,create_by) VALUES(?,?,?,?,?) RETURNING id",Long.class,name,en,sort,remark,user)
       :jdbc.queryForObject("INSERT INTO "+t+"(code,name,name_en,sort_order,remark,create_by) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,code,name,en,sort,remark,user);
   if(code.isEmpty()) {
    String generated=nextAvailableCode(t,rule.prefix(),rule.digits());
    if(generated.length()>40) return R.fail("生成编码超过40个字符，请调整编码规则");
    jdbc.update("UPDATE "+t+" SET code=? WHERE id=?",generated,id);
   }
  } else {
   String code=Objects.toString(b.get("code"),"").trim();
   if(code.isEmpty() || code.length()>40) return R.fail("编码不能为空且不能超过40个字符");
   if(jdbc.queryForObject("SELECT count(*) FROM "+t+" WHERE lower(code)=lower(?) AND id<>?",Long.class,code,id)>0) return R.fail("编码已存在");
   jdbc.update("UPDATE "+t+" SET code=?,name=?,name_en=?,sort_order=?,remark=?,update_by=?,update_time=now() WHERE id=?",code,name,en,sort,remark,user,id);
  }
  if(kind.equals("size-group")) {
   jdbc.update("DELETE FROM sys_size_group_item WHERE group_id=?",id);
   for(int i=0;i<sizes.size();i++) jdbc.update("INSERT INTO sys_size_group_item(group_id,size_id,sort_order) VALUES(?,?,?)",id,sizes.get(i),i);
  }
  jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?",module,id);
  for(Long tag:tags) jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by) VALUES(?,?,?,?)",module,id,tag,user);
  audit(kind,id,before);
  return R.ok(id);
 }
 private String formatCode(String prefix,int digits,long number) {
  String value=Long.toString(number);
  return prefix+"0".repeat(Math.max(0,digits-value.length()))+value;
 }
 /** 按当前有效编码找最小空号；删除记录释放该编码，尺码表清空后从 1 重新分配。 */
 private String nextAvailableSizeCode(String prefix,int digits) {
  return nextAvailableCode("sys_size",prefix,digits);
 }
 private String nextAvailableCode(String table,String prefix,int digits) {
  if(!Set.of("sys_size","sys_size_group").contains(table)) throw new IllegalArgumentException("未知编码表");
  Set<String> used=new HashSet<>(jdbc.queryForList("SELECT lower(code) FROM "+table,String.class));
  for(long number=1;number<Long.MAX_VALUE;number++) {
   String candidate=formatCode(prefix,digits,number);
   if(candidate.length()>40) throw new IllegalStateException("生成编码超过40个字符，请调整编码规则");
   if(!used.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
  }
  throw new IllegalStateException("没有可用的尺码编码序号");
 }
 private record SizeCodeRule(boolean enabled,String prefix,int digits) {}
 private SizeCodeRule sizeCodeRule() {
  String prefix="SZ"; int digits=4; boolean enabled=true;
  try {
   String value=jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?",String.class,"system.code.size");
   if(value!=null) {
    Map<String,Object> rule=JsonUtils.parseMap(value);
    Object rawPrefix=rule.get("prefix"), rawDigits=rule.get("digits"), rawEnabled=rule.get("enabled");
    if(rawPrefix instanceof String p) prefix=p.trim();
    if(rawDigits instanceof Number n) digits=n.intValue();
    if(rawEnabled instanceof Boolean b) enabled=b;
   }
  } catch(org.springframework.dao.EmptyResultDataAccessException ignored) { /* 未配置时兼容当前 SZ + ID 格式 */ }
    catch(Exception e) { throw new IllegalStateException("尺码自动编码规则配置无效，请在系统参数中修复",e); }
  if(prefix.isBlank() || prefix.length()>30 || digits<1 || digits>10) throw new IllegalStateException("尺码自动编码规则配置无效，请检查前缀和数字位数");
  return new SizeCodeRule(enabled,prefix,digits);
 }
 private SizeCodeRule sizeGroupCodeRule() {
  String prefix="SG"; int digits=4; boolean enabled=true;
  try {
   String value=jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?",String.class,"system.code.sizeGroup");
   if(value!=null) {
    Map<String,Object> rule=JsonUtils.parseMap(value);
    Object rawPrefix=rule.get("prefix"), rawDigits=rule.get("digits"), rawEnabled=rule.get("enabled");
    if(rawPrefix instanceof String p) prefix=p.trim();
    if(rawDigits instanceof Number n) digits=n.intValue();
    if(rawEnabled instanceof Boolean b) enabled=b;
   }
  } catch(org.springframework.dao.EmptyResultDataAccessException ignored) { /* 使用默认 SG + 四位序号 */ }
    catch(Exception e) { throw new IllegalStateException("尺码组自动编码规则配置无效，请在系统参数中修复",e); }
  if(prefix.isBlank() || prefix.length()>30 || digits<1 || digits>10) throw new IllegalStateException("尺码组自动编码规则配置无效，请检查前缀和数字位数");
  return new SizeCodeRule(enabled,prefix,digits);
 }
 private Map<String,Object> snapshot(String kind, Long id, boolean lock) {
  List<Map<String,Object>> rows = jdbc.queryForList(
      "SELECT code,name,name_en,sort_order,remark,status FROM "+table(kind)+" WHERE id=?"+(lock?" FOR UPDATE":""),id);
  if(rows.isEmpty()) return Map.of();
  Map<String,Object> row = rows.getFirst();
  if(kind.equals("size-group")) row.put("sizeIds",jdbc.queryForList(
      "SELECT size_id::text FROM sys_size_group_item WHERE group_id=? ORDER BY sort_order,size_id",String.class,id));
  row.put("tagIds",jdbc.queryForList(
      "SELECT tag_id::text FROM sys_tag_relation WHERE module_key=? AND record_id=? ORDER BY tag_id",
      String.class,kind.equals("size")?"size":"size_group",id));
  return row;
 }
 private void audit(String kind, Long id, Map<String,Object> before) {
  Map<String,Object> after = snapshot(kind,id,false);
  Map<String,Object> changes = new LinkedHashMap<>();
  after.forEach((field,value) -> {
   if(!Objects.equals(before.get(field),value)) changes.put(field,Arrays.asList(before.get(field),value));
  });
  ServletUtils.getRequest().setAttribute("zhiyun.audit",Map.of(
      "id",id.toString(),"name",after.get("name"),"status",after.get("status"),"changes",changes));
 }
}
