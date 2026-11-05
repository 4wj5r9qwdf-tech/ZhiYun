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
