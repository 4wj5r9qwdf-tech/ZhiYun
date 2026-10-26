/**
 * 当后端 perms 可见性工具：登录/路由守卫时拉取当前用户权限码，
 * 功能卡片/模块/路由据此显隐。admin 通配 "*"；无 perm 标注的功能人人可见。
 */
import { reactive } from 'vue'
import api from '../api'

export const perms = reactive({ loaded: false, set: new Set<string>() })

/** 确保权限已加载（幂等；路由守卫 await 它）。失败也置 loaded 防死循环。 */
export async function ensurePerms(): Promise<void> {
  if (perms.loaded) return
  try {
    const res: any = await api.get('/system/profile/permissions')
    if (res.code === 200) perms.set = new Set<string>((res.data as string[]) || [])
    else perms.set = new Set<string>()
  } catch {
    perms.set = new Set<string>()
  } finally {
    perms.loaded = true
  }
}

/** 是否具备某权限；perm 未定义 = 人人可见。 */
export function can(perm?: string): boolean {
  if (!perm) return true
  if (perms.set.has('*')) return true
  return perms.set.has(perm)
}
