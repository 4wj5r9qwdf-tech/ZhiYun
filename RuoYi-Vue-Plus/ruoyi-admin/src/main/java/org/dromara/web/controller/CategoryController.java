package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.web.service.FoundationDataService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 分类体系与节点管理入口，不复用审批流程分类的数据。 / Manage isolated business taxonomies independently of workflow categories. */
@RestController @RequiredArgsConstructor @RequestMapping("/system/category")
public class CategoryController {
    private final FoundationDataService service;
    /** 查询管理用分类体系。 / Load all management schemes. */
    @GetMapping("/schemes") @SaCheckPermission("system:category:list")
    public R<List<Map<String,Object>>> schemes() {return R.ok(service.schemes());}
    /** 提供业务体系选项，登录后可读非敏感基础资料。 / Expose shared scheme labels to authenticated business selectors. */
    @GetMapping("/scheme-options") @SaCheckLogin
    public R<List<Map<String,Object>>> schemeOptions() {return R.ok(service.schemes());}
    /** 新增分类体系，编码作为稳定集成标识。 / Create a stable business taxonomy namespace. */
    @PostMapping("/schemes") @SaCheckPermission("system:category:add") @Log(title="分类体系",businessType=BusinessType.INSERT)
    public R<String> addScheme(@RequestBody Map<String,Object> body) {return R.data(service.saveScheme(null,body));}
    /** 修改体系、深度及启停，不改稳定编码。 / Update scheme policy without renaming its integration key. */
    @PutMapping("/schemes/{id}") @SaCheckPermission("system:category:edit") @Log(title="分类体系",businessType=BusinessType.UPDATE)
    public R<String> editScheme(@PathVariable Long id,@RequestBody Map<String,Object> body) {return R.data(service.saveScheme(id,body));}
    /** 删除空且无引用的体系。 / Soft-delete an empty unreferenced scheme. */
    @DeleteMapping("/schemes/{id}") @SaCheckPermission("system:category:remove") @Log(title="分类体系",businessType=BusinessType.DELETE)
    public R<Void> removeScheme(@PathVariable Long id) {service.deleteCategory(id,true);return R.ok();}
    /** 单体系完整节点列表，支持前端构树和全量筛选。 / Load all nodes belonging to one scheme. */
    @GetMapping("/list") @SaCheckPermission("system:category:list")
    public R<List<Map<String,Object>>> list(@RequestParam Long schemeId) {return R.ok(service.categories(schemeId));}
    /** 历史标签保留，新增选择须同时满足体系与祖先启用。 / Return node options with effective ancestor availability. */
    @GetMapping("/options") @SaCheckLogin
    public R<List<Map<String,Object>>> options(@RequestParam Long schemeId) {return R.ok(service.categoryOptions(schemeId));}
    /** 新增分类，公共服务校验父级和深度。 / Create a validated node within its namespace. */
    @PostMapping @SaCheckPermission("system:category:add") @Log(title="分类管理",businessType=BusinessType.INSERT)
    public R<String> add(@RequestBody Map<String,Object> body) {return R.data(service.saveCategory(null,body));}
    /** 更新或移动分类，事务内防止跨体系、成环和超深。 / Update nodes while preserving tree invariants. */
    @PutMapping("/{id}") @SaCheckPermission("system:category:edit") @Log(title="分类管理",businessType=BusinessType.UPDATE)
    public R<String> edit(@PathVariable Long id,@RequestBody Map<String,Object> body) {return R.data(service.saveCategory(id,body));}
    /** 删除无下级、无业务引用的分类。 / Soft-delete an unreferenced leaf. */
    @DeleteMapping("/{id}") @SaCheckPermission("system:category:remove") @Log(title="分类管理",businessType=BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {service.deleteCategory(id,false);return R.ok();}
}
