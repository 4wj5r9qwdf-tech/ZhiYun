/*
 * 文件用途：为织云 ERP 的计量单位接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的计量单位接口提供服务端 HTTP 接口。
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

/** 计量单位公共接口，权限与业务服务分层。 / Expose authorized shared measurement-unit operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/unit")
public class UnitController {
    private final FoundationDataService service;
    /** 完整管理目录，由页面做全量筛选分页。 / Return the complete authorized management directory. */
    @GetMapping("/list") @SaCheckPermission("system:unit:list")
    public R<List<Map<String,Object>>> list() { return R.ok(service.units()); }
    /** 登录业务可读公共选项，保留停用项供历史回显。 / Provide logged-in business selectors with availability flags. */
    @GetMapping("/options") @SaCheckLogin
    public R<List<Map<String,Object>>> options() { return R.ok(service.unitOptions()); }
    /** 新增单位，换算定义由公共服务校验。 / Create a validated measurement unit. */
    @PostMapping @SaCheckPermission("system:unit:add") @Log(title="计量单位",businessType=BusinessType.INSERT)
    public R<String> add(@RequestBody Map<String,Object> body) { return R.data(service.saveUnit(null,body)); }
    /** 编辑单位及状态，被引用换算语义不可修改。 / Update editable fields without corrupting references. */
    @PutMapping("/{id}") @SaCheckPermission("system:unit:edit") @Log(title="计量单位",businessType=BusinessType.UPDATE)
    public R<String> edit(@PathVariable Long id,@RequestBody Map<String,Object> body) {return R.data(service.saveUnit(id,body));}
    /** 删除无引用单位，保留编码历史。 / Soft-delete an unreferenced unit. */
    @DeleteMapping("/{id}") @SaCheckPermission("system:unit:remove") @Log(title="计量单位",businessType=BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {service.deleteUnit(id);return R.ok();}
    /** 精确固定换算，仅同一参照单位可用。 / Convert exact quantities within one shared reference unit. */
    @GetMapping("/convert") @SaCheckLogin
    public R<Map<String,Object>> convert(@RequestParam String fromId,@RequestParam String toId,@RequestParam String quantity) {return R.ok(service.convert(fromId,toId,quantity));}
}
