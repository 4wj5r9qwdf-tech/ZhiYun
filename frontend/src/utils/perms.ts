/**
 * 当后端 perms 可见性工具：登录/路由守卫时拉取当前用户权限码，
 * 功能卡片/模块/路由据此显隐。admin 通配 "*"；无 perm 标注的功能人人可见。
 */
import { reactive } from 'vue'
import api from '../api'

export const menuPerms = reactive(new Set<string>())
export function canEnter(perm?: string): boolean {
  return !perm || (menuPerms.has(perm) && can(perm))
}
export const perms = reactive({ loaded: false, set: new Set<string>() })

/** 确保权限已加载（幂等；路由守卫 await 它）。失败也置 loaded 防死循环。 */
export async function ensurePerms(): Promise<void> {
  if (perms.loaded) return
  try {
    const [res, menus]: any[] = await Promise.all([
      api.get('/system/profile/permissions'), api.get('/system/menu/getRouters'),
    ])
    if (res.code !== 200 || menus.code !== 200 || !Array.isArray(menus.data)) throw new Error()
    menuPerms.clear()
    const visit = (nodes: any[], hidden = false) => {
      for (const node of nodes) {
        const invisible = hidden || node.hidden === true
        if (!invisible && node.perms) menuPerms.add(node.perms)
        visit(node.children || [], invisible)
      }
    }
    visit(menus.data)
    if (res.code === 200) perms.set = new Set<string>((res.data as string[]) || [])
    else perms.set = new Set<string>()
  } catch {
    menuPerms.clear()
    perms.set = new Set<string>()
  } finally {
    perms.loaded = true
  }
}

/** 退出登录/换账号前重置（内存权限状态随会话走，不清会残留上一账号的可见性）。 */
export function resetPerms() {
  menuPerms.clear()
  perms.loaded = false
  perms.set = new Set<string>()
}

/** 是否具备某权限；perm 未定义 = 人人可见。 */
export function can(perm?: string): boolean {
  if (!perm) return true
  if (perms.set.has('*')) return true
  return perms.set.has(perm)
}
