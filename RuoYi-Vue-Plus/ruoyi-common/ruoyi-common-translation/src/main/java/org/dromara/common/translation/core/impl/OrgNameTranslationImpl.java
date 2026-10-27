package org.dromara.common.translation.core.impl;

import cn.hutool.core.convert.Convert;
import lombok.AllArgsConstructor;
import org.dromara.common.translation.annotation.TranslationType;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.common.translation.core.TranslationInterface;
import org.dromara.system.api.OrgService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 部门翻译实现
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.ORG_ID_TO_NAME)
public class OrgNameTranslationImpl implements TranslationInterface<String> {

    private final OrgService orgService;

    /**
     * 将部门 ID 或 ID 集合翻译为部门名称。
     *
     * @param key   部门 ID 或逗号分隔的 ID 字符串
     * @param other 额外参数
     * @return 部门名称
     */
    @Override
    public String translation(Object key, String other) {
        if (key instanceof String ids) {
            return orgService.selectOrgNameByIds(ids);
        } else if (key instanceof Long id) {
            return orgService.selectOrgNameByIds(id.toString());
        }
        return null;
    }

    /**
     * 批量将部门 ID 翻译为部门名称。
     *
     * @param keys  部门 ID 集合
     * @param other 额外参数
     * @return 部门 ID 与部门名称映射
     */
    @Override
    public Map<Object, String> translationBatch(Set<Object> keys, String other) {
        Set<Long> orgIds = collectLongIds(keys);
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> orgNames = orgService.selectOrgNamesByIds(orgIds);
        Map<Object, String> result = new LinkedHashMap<>(keys.size());
        for (Object key : keys) {
            result.put(key, buildValue(key, orgNames));
        }
        return result;
    }

    /**
     * 根据原始键构建部门名称翻译值。
     *
     * @param source    原始键
     * @param orgNames 部门 ID 与部门名称映射
     * @return 部门名称
     */
    private String buildValue(Object source, Map<Long, String> orgNames) {
        if (source instanceof String ids) {
            return joinMappedValues(ids, orgNames::get);
        }
        return source == null ? null : orgNames.get(Convert.toLong(source));
    }
}
