/*
 * 文件用途：承载织云 ERP 对上游框架“SysPostController”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“SysPostController”能力的项目扩展或适配。
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
package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.web.bind.annotation.RequestParam;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.bo.SysPostBo;
import org.dromara.system.domain.vo.SysPostVo;
import org.dromara.system.service.ISysOrgService;
import org.dromara.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 岗位信息操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/post")
public class SysPostController extends BaseController {

    private final org.springframework.jdbc.core.JdbcTemplate auditJdbc;
    private final ISysPostService postService;
    private final ISysOrgService orgService;

    /**
     * 分页查询岗位列表。
     *
     * @param post      查询条件
     * @param pageQuery 分页参数
     * @return 岗位分页结果
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/list")
    public R<PageResult<SysPostVo>> list(SysPostBo post, PageQuery pageQuery,
                                        @RequestParam(required = false) String keyword) {
        // keyword：岗位名称 OR 编码 模糊搜索（前端搜索框直传，避免只搜单字段搜不到）
        if (StringUtils.isNotBlank(keyword)) {
            post.getParams().put("keyword", keyword.trim());
        }
        return R.ok(postService.selectPagePostList(post, pageQuery));
    }

    /**
     * 导出岗位列表。
     *
     * @param post     查询条件
     * @param response HTTP 响应
     */
    @Log(title = "岗位管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:post:export")
    @PostMapping("/export")
    public void export(SysPostBo post, HttpServletResponse response) {
        List<SysPostVo> list = postService.selectPostList(post);
        ExcelBuilder.of(list, SysPostVo.class).sheetName("岗位数据").toResponse(response);
    }

    /**
     * 根据岗位编号获取详细信息
     *
     * @param postId 岗位ID
     * @return 岗位详情
     */
    @SaCheckPermission("system:post:query")
    @GetMapping(value = "/{postId}")
    public R<SysPostVo> getInfo(@PathVariable Long postId) {
        return R.ok(postService.selectPostById(postId));
    }

    /**
     * 新增岗位。
     *
     * @param post 岗位参数
     * @return 操作结果
     */
    @SaCheckPermission("system:post:add")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return R.fail("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return R.fail("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        int auditResult = postService.insertPost(post);
        if (auditResult > 0) BusinessAudit.record("新增岗位",post.getPostId(),"名称",java.util.Map.of(),auditSnapshot(post.getPostId(),false));
        return toAjax(auditResult);
    }

    /**
     * 修改岗位。
     *
     * @param post 岗位参数
     * @return 操作结果
     */
    @SaCheckPermission("system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return R.fail("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return R.fail("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        } else if (SystemConstants.DISABLE.equals(post.getStatus())
            && postService.countUserPostById(post.getPostId()) > 0) {
            return R.fail("该岗位下存在已分配用户，不能禁用!");
        }
        var auditBefore = auditSnapshot(post.getPostId(),true);
        int auditResult = postService.updatePost(post);
        if (auditResult > 0) BusinessAudit.record("修改岗位",post.getPostId(),"名称",auditBefore,auditSnapshot(post.getPostId(),false));
        return toAjax(auditResult);
    }

    @SaCheckPermission("system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{postId}/status")
    public R<Void> changeStatus(@PathVariable Long postId, @RequestBody java.util.Map<String,String> body) {
        String status = body.get("status");
        if (!SystemConstants.NORMAL.equals(status) && !SystemConstants.DISABLE.equals(status)) {
            return R.fail("岗位状态无效");
        }
        var before = auditSnapshot(postId,true);
        if (before.isEmpty() || "1".equals(before.get("删除标记"))) return R.fail("岗位不存在");
        if (status.equals(before.get("状态"))) return R.ok();
        if (SystemConstants.DISABLE.equals(status) && postService.countUserPostById(postId) > 0) {
            return R.fail("该岗位下存在已分配用户，不能停用");
        }
        var update = new SysPostBo();
        update.setPostId(postId);
        update.setStatus(status);
        int affected = postService.updatePost(update);
        if (affected > 0) {
            BusinessAudit.record(SystemConstants.NORMAL.equals(status) ? "启用岗位" : "停用岗位",
                postId,"名称",before,auditSnapshot(postId,false));
        }
        return toAjax(affected);
    }

    /**
     * 删除岗位
     *
     * @param postIds 岗位ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:post:remove")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{postIds}")
    public R<Void> remove(@PathVariable Long[] postIds) {
        var before = new java.util.LinkedHashMap<String,Object>();
        for (Long id : java.util.Arrays.stream(postIds).distinct().sorted().toList()) {
            var row = auditSnapshot(id,true);
            if (!row.isEmpty()) before.put(id.toString(),row);
        }
        int affected = postService.deletePostByIds(Arrays.asList(postIds));
        if (affected > 0) {
            var after = new java.util.LinkedHashMap<String,Object>();
            for (Long id : postIds) {
                var row = auditSnapshot(id,false);
                if (!row.isEmpty()) after.put(id.toString(),row);
            }
            BusinessAudit.record("删除岗位",before.keySet(),"名称",before,after);
        }
        return toAjax(affected);
    }

    /**
     * 获取岗位选择框列表
     *
     * @param postIds 岗位ID串
     * @param orgId  部门id
     * @return 岗位列表
     */
    @SaCheckPermission("system:post:query")
    @GetMapping("/optionselect")
    public R<List<SysPostVo>> optionselect(@RequestParam(required = false) Long[] postIds, @RequestParam(required = false) Long orgId) {
        List<SysPostVo> list = new ArrayList<>();
        if (ObjectUtil.isNotNull(orgId)) {
            SysPostBo post = new SysPostBo();
            post.setOrgId(orgId);
            list = postService.selectPostList(post);
        } else if (postIds != null) {
            list = postService.selectPostByIds(List.of(postIds));
        }
        return R.ok(list);
    }

    /**
     * 获取岗位筛选用的部门树。
     *
     * @param org 部门查询条件
     * @return 部门树列表
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/orgTree")
    public R<List<Tree<Long>>> orgTree(SysOrgBo org) {
        return R.ok(orgService.selectOrgTreeList(org));
    }


    private java.util.Map<String,Object> auditSnapshot(Object id,boolean lock) {
        var row = BusinessAudit.snapshot(auditJdbc,"SELECT post_name AS \"名称\", org_id::text AS \"归属组织\", post_code AS \"岗位编码\", post_category AS \"岗位类别\", post_sort AS \"排序\", status AS \"状态\", del_flag AS \"删除标记\", remark AS \"备注\" FROM sys_post WHERE post_id=?"+(lock ? " FOR UPDATE" : ""),id);
        return row;
    }
}
