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
