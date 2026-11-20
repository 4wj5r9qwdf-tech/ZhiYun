/*
 * 文件用途：实现织云 ERP 的UserLoginSuccessEvent配置或基础能力。
 * 文件版本：1.0.0
 * 功能目的：实现织云 ERP 的UserLoginSuccessEvent配置或基础能力。
 * 设计思路：沿用项目现有分层与框架扩展点，保持和相关业务模块的契约一致。
 * 实现方法：由所属框架加载后与对应业务模块协作；细节以本文件和关联接口为准。
 * 关键约束：仅对本项目实际变更负责，并保留上游版权/许可。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
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
package org.dromara.web.event;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;

/**
 * 用户登录成功事件。
 *
 * @param loginId        登录标识
 * @param tokenValue     token 值
 * @param loginParameter 登录参数
 */
public record UserLoginSuccessEvent(Object loginId, String tokenValue, SaLoginParameter loginParameter) {
}
