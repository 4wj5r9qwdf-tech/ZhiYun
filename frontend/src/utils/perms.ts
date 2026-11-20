// 文件用途：集中实现前端perms请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端perms请求或共享处理能力。
// 设计思路：抽离跨页面复用逻辑，以明确输入输出减少重复；不替代服务端校验。
// 实现方法：提供 API 封装、格式化工具或组合状态供业务页面调用。
// 关键约束：不得伪造业务成功或改变服务端数据口径。
// 依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md
// 初始开发人员：JY（织云项目自研内容）
// 初始开发时间：待追溯（不以补录时间代替原始开发时间）
// 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
// 最近修改人员：JY
// AI辅助工具：Codex（智能体）
// 最近修改时间：2026-11-20
// 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
// 最近测试时间：本次整理未执行功能测试
// 测试人员：本次整理未执行
// 测试状态：待功能回归；历史测试记录待追溯
// 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
/**
 * 当前用户权限码工具。功能卡片、模块、路由和按钮均以权限接口返回的权限码为准；
 * 超级管理员由后端返回通配 "*"。菜单路由树只包含目录/页面，不包含按钮权限。
 */
import { reactive } from 'vue'
import api from '../api'

export const perms = reactive({ loaded: false, set: new Set<string>() })

/** 功能未配置权限码时人人可见；有权限码时按当前用户授权集合判断。 */
export function canEnter(perm?: string): boolean {
  return !perm || can(perm)
}

/** 确保权限已加载（幂等；失败也置 loaded 防死循环）。 */
export async function ensurePerms(): Promise<void> {
  if (perms.loaded) return
  try {
    const res: any = await api.get('/system/profile/permissions')
    if (res.code !== 200 || !Array.isArray(res.data)) throw new Error()
    perms.set = new Set<string>(res.data)
  } catch {
    perms.set = new Set<string>()
  } finally {
    perms.loaded = true
  }
}

/** 退出登录/换账号前重置权限状态，避免不同账号之间残留权限。 */
export function resetPerms() {
  perms.loaded = false
  perms.set = new Set<string>()
}

/** 是否具备指定权限；perm 未定义时允许访问。 */
export function can(perm?: string): boolean {
  if (!perm) return true
  if (perms.set.has('*')) return true
  return perms.set.has(perm)
}
