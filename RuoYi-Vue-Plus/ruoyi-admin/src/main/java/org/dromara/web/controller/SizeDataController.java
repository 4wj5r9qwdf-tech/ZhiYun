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
   row.put("tagOptions",jdbc.queryForList("SELECT t.tag_id::text AS value,t.tag_name AS label,(t.status<>'0') AS disabled FROM sys_tag_relation r JOIN sys_tag t ON t.tag_id=r.tag_id WHERE r.module_key=? AND r.record_id=?",kind.equals("size")?"size":"size_group",id));
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
 @PostMapping
 @Transactional
 @Log(title="尺码基础资料",businessType=BusinessType.INSERT)
 public R<Long> add(@PathVariable String kind,@RequestBody Map<String,Object> body) { permission(kind,"add"); return save(kind,null,body); }
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
  if(id==null) id=jdbc.queryForObject("INSERT INTO "+t+"(name,name_en,sort_order,remark,create_by) VALUES(?,?,?,?,?) RETURNING id",Long.class,name,en,sort,remark,user);
  else jdbc.update("UPDATE "+t+" SET name=?,name_en=?,sort_order=?,remark=?,update_by=?,update_time=now() WHERE id=?",name,en,sort,remark,user,id);
  if(kind.equals("size-group")) {
   jdbc.update("DELETE FROM sys_size_group_item WHERE group_id=?",id);
   for(int i=0;i<sizes.size();i++) jdbc.update("INSERT INTO sys_size_group_item(group_id,size_id,sort_order) VALUES(?,?,?)",id,sizes.get(i),i);
  }
  jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?",module,id);
  for(Long tag:tags) jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by) VALUES(?,?,?,?)",module,id,tag,user);
  audit(kind,id,before);
  return R.ok(id);
 }
 private Map<String,Object> snapshot(String kind, Long id, boolean lock) {
  List<Map<String,Object>> rows = jdbc.queryForList(
      "SELECT name,name_en,sort_order,remark,status FROM "+table(kind)+" WHERE id=?"+(lock?" FOR UPDATE":""),id);
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
