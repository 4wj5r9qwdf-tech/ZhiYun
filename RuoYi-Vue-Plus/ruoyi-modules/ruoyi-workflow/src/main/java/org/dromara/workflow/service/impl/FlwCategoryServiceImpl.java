/*
 * 文件用途：承载织云 ERP 对上游框架“FlwCategoryServiceImpl”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlwCategoryServiceImpl”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
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
package org.dromara.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.*;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.ui.service.CategoryService;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.dromara.workflow.common.constant.FlowConstant;
import org.dromara.workflow.domain.FlowCategory;
import org.dromara.workflow.domain.bo.FlowCategoryBo;
import org.dromara.workflow.domain.vo.FlowCategoryVo;
import org.dromara.workflow.mapper.FlwCategoryMapper;
import org.dromara.workflow.service.IFlwCategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程分类Service业务层处理
 *
 * @author may
 */
@ConditionalOnEnable
@RequiredArgsConstructor
@Service
public class FlwCategoryServiceImpl implements IFlwCategoryService, CategoryService {

    private final DefService defService;
    private final FlwCategoryMapper categoryMapper;

    /**
     * 查询流程分类
     *
     * @param categoryId 主键
     * @return 流程分类
     */
    @Override
    public FlowCategoryVo queryById(Long categoryId) {
        return categoryMapper.selectVoById(categoryId);
    }

    /**
     * 根据流程分类ID查询流程分类名称
     *
     * @param categoryId 流程分类ID
     * @return 流程分类名称
     */
    @Cacheable(cacheNames = FlowConstant.FLOW_CATEGORY_NAME, key = "#categoryId")
    @Override
    public String selectCategoryNameById(Long categoryId) {
        if (ObjectUtil.isNull(categoryId)) {
            return null;
        }
        FlowCategory category = categoryMapper.lambda()
            .select(FlowCategory::getCategoryName)
            .eq(FlowCategory::getCategoryId, categoryId)
            .one();
        return ObjectUtils.notNullGetter(category, FlowCategory::getCategoryName);
    }

    /**
     * 根据流程分类ID查询流程分类名称
     *
     * @param categoryIds 流程分类ID
     * @return 流程分类名称
     */
    @Override
    public Map<Long, String> selectCategoryNameByIds(Set<Long> categoryIds) {
        if (CollUtil.isEmpty(categoryIds)) {
            return Collections.emptyMap();
        }
        List<FlowCategory> list = categoryMapper.lambda()
            .select(FlowCategory::getCategoryId, FlowCategory::getCategoryName)
            .in(FlowCategory::getCategoryId, categoryIds)
            .list();
        return StreamUtils.toMap(list, FlowCategory::getCategoryId, FlowCategory::getCategoryName);
    }

    /**
     * 查询符合条件的流程分类列表
     *
     * @param bo 查询条件
     * @return 流程分类列表
     */
    @Override
    public List<FlowCategoryVo> queryList(FlowCategoryBo bo) {
        LambdaQueryWrapper<FlowCategory> lqw = buildQueryWrapper(bo);
        return categoryMapper.selectVoList(lqw);
    }

    /**
     * 查询流程分类树结构信息
     *
     * @param category 流程分类信息
     * @return 流程分类树信息集合
     */
    @Override
    public List<Tree<String>> selectCategoryTreeList(FlowCategoryBo category) {
        List<FlowCategoryVo> categoryList = this.queryList(category);
        if (CollUtil.isEmpty(categoryList)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.buildMultiRoot(
            categoryList,
            node -> Convert.toStr(node.getCategoryId()),
            node -> Convert.toStr(node.getParentId()),
            (node, treeNode) -> treeNode
                .setId(Convert.toStr(node.getCategoryId()))
                .setParentId(Convert.toStr(node.getParentId()))
                .setName(node.getCategoryName())
                .setWeight(node.getOrderNum())
        );
    }

    /**
     * 工作流查询分类
     *
     * @return 分类树结构列表
     */
    @Override
    public List<org.dromara.warm.flow.core.dto.Tree> queryCategory() {
        List<FlowCategoryVo> list = this.queryList(new FlowCategoryBo());
        return StreamUtils.toList(list, category -> new org.dromara.warm.flow.core.dto.Tree()
            .setId(Convert.toStr(category.getCategoryId()))
            .setName(category.getCategoryName())
            .setParentId(Convert.toStr(category.getParentId()))
        );
    }

    /**
     * 校验流程分类名称是否唯一
     *
     * @param category 流程分类信息
     * @return 结果
     */
    @Override
    public boolean checkCategoryNameUnique(FlowCategoryBo category) {
        boolean exist = categoryMapper.lambda()
            .eq(FlowCategory::getCategoryName, category.getCategoryName())
            .eq(FlowCategory::getParentId, category.getParentId())
            .neIfPresent(FlowCategory::getCategoryId, category.getCategoryId())
            .exists();
        return !exist;
    }

    /**
     * 查询流程分类是否存在流程定义
     *
     * @param categoryId 流程分类ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkCategoryExistDefinition(Long categoryId) {
        FlowDefinition definition = new FlowDefinition();
        definition.setCategory(categoryId.toString());
        return defService.exists(definition);
    }

    /**
     * 是否存在流程分类子节点
     *
     * @param categoryId 流程分类ID
     * @return 结果
     */
    @Override
    public boolean hasChildByCategoryId(Long categoryId) {
        return categoryMapper.lambda().eq(FlowCategory::getParentId, categoryId).exists();
    }

    /**
     * 构建流程分类查询条件。
     *
     * @param bo 查询条件
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<FlowCategory> buildQueryWrapper(FlowCategoryBo bo) {
        return QueryBuilder.lambda(FlowCategory.class)
            .eqIfPresent(FlowCategory::getCategoryId, bo.getCategoryId())
            .eqIfPresent(FlowCategory::getParentId, bo.getParentId())
            .likeIfText(FlowCategory::getCategoryName, bo.getCategoryName())
            .orderByAsc(FlowCategory::getAncestors, FlowCategory::getParentId, FlowCategory::getOrderNum, FlowCategory::getCategoryId)
            .build();
    }

    /**
     * 新增流程分类
     *
     * @param bo 流程分类
     * @return 是否新增成功
     */
    @Override
    public int insertByBo(FlowCategoryBo bo) {
        // 顶级分类以 0 为父级，不要求存在虚拟父节点。 / Root categories use parent 0 without a synthetic database row.
        if (Long.valueOf(0L).equals(bo.getParentId())) {
            FlowCategory category = MapstructUtils.convert(bo, FlowCategory.class);
            category.setAncestors("0");
            return categoryMapper.insert(category);
        }
        FlowCategory info = categoryMapper.selectById(bo.getParentId());
        if (ObjectUtil.isNull(info)) {
            throw new ServiceException("父级流程分类不存在!");
        }
        FlowCategory category = MapstructUtils.convert(bo, FlowCategory.class);
        category.setAncestors(info.getAncestors() + StringUtils.SEPARATOR + category.getParentId());
        return categoryMapper.insert(category);
    }

    /**
     * 修改流程分类
     *
     * @param bo 流程分类
     * @return 是否修改成功
     */
    @CacheEvict(cacheNames = FlowConstant.FLOW_CATEGORY_NAME, key = "#bo.categoryId")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(FlowCategoryBo bo) {
        FlowCategory category = MapstructUtils.convert(bo, FlowCategory.class);
        FlowCategory oldCategory = categoryMapper.selectById(category.getCategoryId());
        if (ObjectUtil.isNull(oldCategory)) {
            throw new ServiceException("流程分类不存在，无法修改");
        }
        if (oldCategory.getParentId() == 0L && category.getParentId() != 0L) {
            throw new ServiceException("不允许修改顶级分类的父级节点");
        }
        if (!oldCategory.getParentId().equals(category.getParentId())) {
            FlowCategory newParentCategory = categoryMapper.selectById(category.getParentId());
            if (ObjectUtil.isNotNull(newParentCategory)) {
                String newAncestors = newParentCategory.getAncestors() + StringUtils.SEPARATOR + newParentCategory.getCategoryId();
                String oldAncestors = oldCategory.getAncestors();
                category.setAncestors(newAncestors);
                updateCategoryChildren(category.getCategoryId(), newAncestors, oldAncestors);
            } else {
                throw new ServiceException("父级流程分类不存在!");
            }
        } else {
            category.setAncestors(oldCategory.getAncestors());
        }
        return categoryMapper.updateById(category);
    }

    /**
     * 修改子元素关系
     *
     * @param categoryId   被修改的流程分类ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    private void updateCategoryChildren(Long categoryId, String newAncestors, String oldAncestors) {
        List<FlowCategory> children = categoryMapper.lambda()
            .findInSet(categoryId, FlowCategory::getAncestors)
            .list();
        List<FlowCategory> list = new ArrayList<>();
        for (FlowCategory child : children) {
            FlowCategory category = new FlowCategory();
            category.setCategoryId(child.getCategoryId());
            category.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
            list.add(category);
        }
        if (CollUtil.isNotEmpty(list)) {
            categoryMapper.updateBatchById(list);
        }
    }

    /**
     * 删除流程分类信息
     *
     * @param categoryId 主键
     * @return 是否删除成功
     */
    @CacheEvict(cacheNames = FlowConstant.FLOW_CATEGORY_NAME, key = "#categoryId")
    @Override
    public int deleteWithValidById(Long categoryId) {
        return categoryMapper.deleteById(categoryId);
    }
}
