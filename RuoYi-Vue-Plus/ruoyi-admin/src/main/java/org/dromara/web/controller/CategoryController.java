/*
 * 文件用途：为织云 ERP 的资料分类管理接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的资料分类管理接口提供服务端 HTTP 接口。
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
    /** 返回当前类型下一个可编辑分类编码建议值；正式写入时由保存事务重新分配。 / Preview the next editable category code for a type. */
    @GetMapping("/next-code") @SaCheckPermission("system:category:add")
    public R<Map<String,Object>> nextCode(@RequestParam Long schemeId,@RequestParam(required=false) Long parentId) {return R.ok(service.nextCategoryCode(schemeId,parentId));}
    /** 新增分类体系，编码作为稳定集成标识。 / Create a stable business taxonomy namespace. */
    @PostMapping("/schemes") @SaCheckPermission("system:category:add") @Log(title="分类体系",businessType=BusinessType.INSERT)
    public R<String> addScheme(@RequestBody Map<String,Object> body) {return R.data(service.saveScheme(null,body));}
    /** 修改体系、深度及启停，不改稳定编码。 / Update scheme policy without renaming its integration key. */
    @PutMapping("/schemes/{id}") @SaCheckPermission("system:category:edit") @Log(title="分类体系",businessType=BusinessType.UPDATE)
    public R<String> editScheme(@PathVariable Long id,@RequestBody Map<String,Object> body) {return R.data(service.saveScheme(id,body));}
    /** 永久删除空且无引用的分类类型；审计快照保留删除前内容。 / Hard-delete an empty unreferenced type while retaining its audit snapshot. */
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
