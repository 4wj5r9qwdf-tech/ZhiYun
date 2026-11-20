/*
 * 文件用途：承载织云 ERP 对上游框架“TestDemoMapper”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“TestDemoMapper”能力的项目扩展或适配。
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
package org.dromara.demo.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.demo.domain.TestDemo;
import org.dromara.demo.domain.vo.TestDemoVo;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 测试单表Mapper接口
 *
 * @author Lion Li
 * @date 2021-07-26
 */
public interface TestDemoMapper extends BaseMapperPlus<TestDemo, TestDemoVo> {

    /**
     * 自定义分页查询演示数据。
     *
     * @param page    分页对象
     * @param wrapper 查询条件
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    Page<TestDemoVo> customPageList(@Param("page") Page<TestDemo> page, @Param("ew") Wrapper<TestDemo> wrapper);

    /**
     * 分页查询演示 VO 列表并应用数据权限。
     *
     * @param page    分页对象
     * @param wrapper 查询条件
     * @return 分页结果
     */
    @Override
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    default <P extends IPage<TestDemoVo>> P selectVoPage(IPage<TestDemo> page, Wrapper<TestDemo> wrapper) {
        return selectVoPage(page, wrapper, this.currentVoClass());
    }

    /**
     * 查询演示 VO 列表并应用数据权限。
     *
     * @param wrapper 查询条件
     * @return 演示 VO 列表
     */
    @Override
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    default List<TestDemoVo> selectVoList(Wrapper<TestDemo> wrapper) {
        return selectVoList(wrapper, this.currentVoClass());
    }

    /**
     * 按主键集合查询演示数据并应用数据权限。
     *
     * @param idList 主键集合
     * @return 演示数据列表
     */
    @Override
    @DataPermission(value = {
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "user_id")
    }, joinStr = "AND")
    List<TestDemo> selectByIds(@Param(Constants.COLL) Collection<? extends Serializable> idList);

    /**
     * 按主键更新演示数据并应用数据权限。
     *
     * @param entity 演示数据实体
     * @return 更新行数
     */
    @Override
    @DataPermission({
        @DataColumn(key = "orgName", value = "org_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    int updateById(@Param(Constants.ENTITY) TestDemo entity);

}
