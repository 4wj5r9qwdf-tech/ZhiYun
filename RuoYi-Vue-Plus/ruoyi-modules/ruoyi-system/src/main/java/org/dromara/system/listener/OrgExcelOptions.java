package org.dromara.system.listener;

import cn.hutool.core.lang.tree.Tree;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.TreeBuildUtils;
import org.dromara.common.excel.core.ExcelOptionsProvider;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.service.ISysOrgService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Excel 部门下拉选项数据源
 *
 * @author AprilWind
 */
@Component
@RequiredArgsConstructor
public class OrgExcelOptions implements ExcelOptionsProvider {

    /**
     * 获取下拉选项数据
     *
     * @return 下拉选项列表
     */
    @Override
    public Set<String> getOptions() {
        ISysOrgService orgService = SpringUtils.getBean(ISysOrgService.class);
        List<Tree<Long>> trees = orgService.selectOrgTreeList(new SysOrgBo());
        return TreeBuildUtils.buildTreeNodeMap(trees, "/", Tree::getName).keySet();
    }

}
