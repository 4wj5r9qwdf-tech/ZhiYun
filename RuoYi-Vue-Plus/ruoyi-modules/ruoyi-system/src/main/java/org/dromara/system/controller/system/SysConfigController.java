package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.domain.bo.SysConfigBo;
import org.dromara.system.domain.vo.SysConfigVo;
import org.dromara.system.domain.SysConfig;
import org.dromara.system.mapper.SysConfigMapper;
import org.dromara.system.service.ISysConfigService;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 参数配置 信息操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/config")
public class SysConfigController extends BaseController {

    private final ISysConfigService configService;
    private final SysConfigMapper configMapper;

    /**
     * 分页查询参数配置列表。
     *
     * @param config    查询条件
     * @param pageQuery 分页参数
     * @return 参数配置分页结果
     */
    @SaCheckPermission("system:config:list")
    @GetMapping("/list")
    public R<PageResult<SysConfigVo>> list(SysConfigBo config, PageQuery pageQuery) {
        return R.ok(configService.selectPageConfigList(config, pageQuery));
    }

    /**
     * 导出参数配置列表。
     *
     * @param config   查询条件
     * @param response HTTP 响应
     */
    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:config:export")
    @PostMapping("/export")
    public void export(SysConfigBo config, HttpServletResponse response) {
        List<SysConfigVo> list = configService.selectConfigList(config);
        ExcelBuilder.of(list, SysConfigVo.class).sheetName("参数数据").toResponse(response);
    }

    /**
     * 根据参数编号获取详细信息
     *
     * @param configId 参数ID
     * @return 参数配置详情
     */
    @SaCheckPermission("system:config:query")
    @GetMapping(value = "/{configId}")
    public R<SysConfigVo> getInfo(@PathVariable Long configId) {
        return R.ok(configService.selectConfigById(configId));
    }

    /**
     * 根据参数键名查询参数值
     *
     * @param configKey 参数Key
     * @return 参数值
     */
    @GetMapping(value = "/configKey/{configKey}")
    public R<String> getConfigKey(@PathVariable String configKey) {
        return R.data(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置。
     *
     * @param config 参数配置
     * @return 操作结果
     */
    @SaCheckPermission("system:config:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            return R.fail("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.insertConfig(config);
        return R.ok();
    }

    /**
     * 修改参数配置。
     *
     * @param config 参数配置
     * @return 操作结果
     */
    @SaCheckPermission("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            return R.fail("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.updateConfig(config);
        return R.ok();
    }

    /**
     * 根据参数键名修改参数配置。
     *
     * @param config 参数配置
     * @return 操作结果
     */
    @SaCheckPermission("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/updateByKey")
    public R<Void> updateByKey(@RequestBody SysConfigBo config) {
        configService.updateConfig(config);
        return R.ok();
    }

    /** 只更新尺码自动编码规则值，避免通用参数编辑接口的整行校验及更新逻辑。 */
    @SaCheckPermission("system:config:edit")
    @Log(title = "尺码自动编码规则", businessType = BusinessType.UPDATE)
    @PostMapping("/size-code-rule")
    public R<Void> updateSizeCodeRule(@RequestBody Map<String, Object> rule) {
        Object enabled = rule.get("enabled");
        Object prefix = rule.get("prefix");
        Object digits = rule.get("digits");
        if (!(enabled instanceof Boolean) || !(prefix instanceof String prefixValue)
            || prefixValue.isBlank() || prefixValue.trim().length() > 30
            || !(digits instanceof Number number) || number.intValue() < 1 || number.intValue() > 10
            || number.doubleValue() != number.intValue()) {
            return R.fail("尺码自动编码规则无效，请检查开关、前缀和数字位数");
        }
        try {
            String value = JsonUtils.toJsonString(Map.of(
                "enabled", enabled, "prefix", prefixValue.trim(), "digits", number.intValue()));
            boolean updated = configMapper.lambdaUpdate()
                .set(SysConfig::getConfigValue, value)
                .eq(SysConfig::getConfigKey, "system.code.size")
                .update();
            if (!updated) return R.fail("未找到尺码自动编码规则参数");
            CacheUtils.evict(CacheNames.SYS_CONFIG, "system.code.size");
            return R.ok();
        } catch (Exception e) {
            return R.fail("保存尺码自动编码规则失败");
        }
    }

    /** 只更新尺码组自动编码规则值。 */
    @SaCheckPermission("system:config:edit")
    @Log(title = "尺码组自动编码规则", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/size-group-code-rule")
    public R<Void> updateSizeGroupCodeRule(@RequestBody Map<String, Object> rule) {
        Object enabled = rule.get("enabled");
        Object prefix = rule.get("prefix");
        Object digits = rule.get("digits");
        if (!(enabled instanceof Boolean) || !(prefix instanceof String prefixValue)
            || prefixValue.isBlank() || prefixValue.trim().length() > 30
            || !(digits instanceof Number number) || number.intValue() < 1 || number.intValue() > 10
            || number.doubleValue() != number.intValue()) {
            return R.fail("尺码组自动编码规则无效，请检查开关、前缀和数字位数");
        }
        try {
            String value = JsonUtils.toJsonString(Map.of(
                "enabled", enabled, "prefix", prefixValue.trim(), "digits", number.intValue()));
            boolean updated = configMapper.lambdaUpdate()
                .set(SysConfig::getConfigValue, value)
                .eq(SysConfig::getConfigKey, "system.code.sizeGroup")
                .update();
            if (!updated) {
                SysConfig config = new SysConfig();
                config.setConfigName("尺码组自动编码规则");
                config.setConfigKey("system.code.sizeGroup");
                config.setConfigValue(value);
                config.setConfigType("Y");
                config.setRemark("尺码组自动编码开关、编号前缀及数字位数");
                configMapper.insert(config);
            }
            CacheUtils.evict(CacheNames.SYS_CONFIG, "system.code.sizeGroup");
            return R.ok();
        } catch (Exception e) {
            return R.fail("保存尺码组自动编码规则失败");
        }
    }

    /** 保存仓库布局层级编码规则。 */
    @SaCheckPermission("system:config:edit")
    @Log(title = "仓库布局编码规则", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/warehouse-layout-code-rule")
    public R<Void> updateWarehouseLayoutCodeRule(@RequestBody Map<String, Object> rule) {
        Object enabled = rule.get("enabled");
        if (!(enabled instanceof Boolean)) return R.fail("仓库布局编码规则开关无效");
        Map<String, Object> saved = new java.util.LinkedHashMap<>();
        saved.put("enabled", enabled);
        for (String level : List.of("zone", "rack", "bin")) {
            Object prefix = rule.get(level + "Prefix"), digits = rule.get(level + "Digits");
            if (!(prefix instanceof String text) || !text.matches("[A-Za-z0-9]{1,8}")
                || !(digits instanceof Number number) || number.intValue() < 1 || number.intValue() > 6
                || number.doubleValue() != number.intValue()) {
                return R.fail("库区、货架和货位前缀须为1到8位字母或数字，序号位数须为1到6位");
            }
            saved.put(level + "Prefix", text.toUpperCase(java.util.Locale.ROOT));
            saved.put(level + "Digits", number.intValue());
        }
        try {
            String configKey = "inventory.warehouseLayout.codeRule";
            SysConfig previous = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, configKey));
            String value = JsonUtils.toJsonString(saved);
            boolean updated = configMapper.lambdaUpdate().set(SysConfig::getConfigValue, value)
                .eq(SysConfig::getConfigKey, configKey).update();
            if (!updated) {
                SysConfig config = new SysConfig();
                config.setConfigName("仓库布局自动编码规则");
                config.setConfigKey(configKey);
                config.setConfigValue(value);
                config.setConfigType("Y");
                config.setRemark("仓库布局编码开关、库区/货架/货位前缀及序号位数");
                configMapper.insert(config);
            }
            SysConfig current = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, configKey));
            Map<String, Object> before = previous == null ? Map.of() : warehouseLayoutRuleAudit(previous.getConfigValue());
            BusinessAudit.record(previous == null ? "新增仓库布局编码规则" : "修改仓库布局编码规则",
                current == null ? null : current.getConfigId(), "参数名称", before, warehouseLayoutRuleAudit(value));
            CacheUtils.evict(CacheNames.SYS_CONFIG, configKey);
            return R.ok();
        } catch (Exception e) {
            return R.fail("保存仓库布局编码规则失败");
        }
    }

    private Map<String, Object> warehouseLayoutRuleAudit(String value) {
        Map<String, Object> rule = JsonUtils.parseMap(value);
        Map<String, Object> audit = new java.util.LinkedHashMap<>();
        audit.put("参数名称", "仓库布局自动编码规则");
        audit.put("自动生成", Boolean.TRUE.equals(rule.get("enabled")) ? "开启" : "关闭");
        audit.put("库区前缀", rule.get("zonePrefix")); audit.put("库区序号位数", rule.get("zoneDigits"));
        audit.put("货架前缀", rule.get("rackPrefix")); audit.put("货架序号位数", rule.get("rackDigits"));
        audit.put("货位前缀", rule.get("binPrefix")); audit.put("货位序号位数", rule.get("binDigits"));
        return audit;
    }

    /** 保存仓库编码自动生成规则。 */
    @SaCheckPermission("system:config:edit")
    @Log(title = "仓库编码规则", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/warehouse-code-rule")
    public R<Void> updateWarehouseCodeRule(@RequestBody Map<String, Object> rule) {
        Object enabled = rule.get("enabled"), prefix = rule.get("prefix"), digits = rule.get("digits");
        if (!(enabled instanceof Boolean) || !(prefix instanceof String text) || !text.matches("[A-Za-z0-9]{1,12}")
            || !(digits instanceof Number number) || number.intValue() < 1 || number.intValue() > 10 || number.doubleValue() != number.intValue()) {
            return R.fail("仓库编码规则无效：前缀须为1到12位字母或数字，序号位数须为1到10位");
        }
        String key = "inventory.warehouse.codeRule";
        try {
            SysConfig previous = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
            String value = JsonUtils.toJsonString(Map.of("enabled", enabled, "prefix", text.toUpperCase(java.util.Locale.ROOT), "digits", number.intValue()));
            boolean updated = configMapper.lambdaUpdate().set(SysConfig::getConfigValue, value).eq(SysConfig::getConfigKey, key).update();
            if (!updated) {
                SysConfig config = new SysConfig();
                config.setConfigName("仓库自动编码规则"); config.setConfigKey(key); config.setConfigValue(value);
                config.setConfigType("Y"); config.setRemark("仓库编码自动生成开关、前缀及序号位数");
                configMapper.insert(config);
            }
            SysConfig current = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
            BusinessAudit.record(previous == null ? "新增仓库编码规则" : "修改仓库编码规则",
                current == null ? null : current.getConfigId(), "参数名称",
                previous == null ? Map.of() : warehouseCodeRuleAudit(previous.getConfigValue()), warehouseCodeRuleAudit(value));
            CacheUtils.evict(CacheNames.SYS_CONFIG, key);
            return R.ok();
        } catch (Exception e) { return R.fail("保存仓库自动编码规则失败"); }
    }

    private Map<String, Object> warehouseCodeRuleAudit(String value) {
        Map<String, Object> rule = JsonUtils.parseMap(value);
        Map<String, Object> audit = new java.util.LinkedHashMap<>();
        audit.put("参数名称", "仓库自动编码规则");
        audit.put("自动生成", Boolean.TRUE.equals(rule.get("enabled")) ? "开启" : "关闭");
        audit.put("仓库编码前缀", rule.get("prefix")); audit.put("序号位数", rule.get("digits"));
        return audit;
    }

    /**
     * 删除参数配置
     *
     * @param configIds 参数ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public R<Void> remove(@PathVariable Long[] configIds) {
        configService.deleteConfigByIds(Arrays.asList(configIds));
        return R.ok();
    }

    /**
     * 刷新参数缓存。
     *
     * @return 操作结果
     */
    @SaCheckPermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public R<Void> refreshCache() {
        configService.resetConfigCache();
        return R.ok();
    }
}
