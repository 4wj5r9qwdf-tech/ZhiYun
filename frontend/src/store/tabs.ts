import { defineStore } from 'pinia'

/**
 * 多标签页（Tab）状态：像浏览器 tab，在同一页面顶部开多个功能，点标签快速切换。
 * - 只有"功能页"开 tab（人员信息/建筑档案…），模块首页（PersonIndex/SystemIndex/Workbench）不开。
 * - 持久化到 localStorage（刷新保留），activeName 记当前激活 tab。
 * - 路由驱动：打开 tab = router.push 到该路由；关闭 tab = 回到最近的其他 tab 或模块首页。
 */
export interface TabItem {
  name: string      // 路由 name（唯一键）
  title: string     // 显示名（来自路由 meta.title）
  path: string      // 完整路径（点击切换用）
}

const STORAGE_KEY = 'zy_open_tabs'

function load(): { tabs: TabItem[]; activeName: string } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const d = JSON.parse(raw)
      if (Array.isArray(d?.tabs)) return { tabs: d.tabs, activeName: d.activeName || '' }
    }
  } catch { /* ignore */ }
  return { tabs: [], activeName: '' }
}

export const useTabsStore = defineStore('tabs', {
  state: () => {
    const s = load()
    return { tabs: s.tabs, activeName: s.activeName }
  },
  actions: {
    persist() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ tabs: this.tabs, activeName: this.activeName }))
    },
    /** 打开（或激活）一个功能 tab；重复打开只激活不新增。 */
    openTab(tab: TabItem) {
      const exist = this.tabs.find(t => t.name === tab.name)
      if (exist) {
        this.activeName = tab.name
      } else {
        this.tabs.push(tab)
        this.activeName = tab.name
      }
      this.persist()
    },
    /** 设置当前激活 tab（路由切换时同步）。 */
    setActive(name: string) {
      if (this.activeName !== name) {
        this.activeName = name
        this.persist()
      }
    },
    /** 关闭一个 tab；返回下一个应跳转的路由名（无则 null=回模块首页/工作台由调用方处理）。 */
    closeTab(name: string): string | null {
      const i = this.tabs.findIndex(t => t.name === name)
      if (i === -1) return null
      this.tabs.splice(i, 1)
      let next: string | null = null
      if (this.activeName === name) {
        // 关的是当前激活的 → 切到右边一个，否则左边一个
        const n = this.tabs[i] || this.tabs[i - 1] || null
        next = n ? n.name : null
        this.activeName = next || ''
      }
      this.persist()
      return next
    },
    /** 关闭其他（保留 exceptName）。返回保留的那个路由名，无则 null。 */
    closeOthers(exceptName: string): string | null {
      const keep = this.tabs.find(t => t.name === exceptName) || null
      this.tabs = keep ? [keep] : []
      this.activeName = keep ? keep.name : ''
      this.persist()
      return keep ? keep.name : null
    },
    /** 关闭全部；返回 null（调用方回模块首页/工作台）。 */
    closeAll(): null {
      this.tabs = []
      this.activeName = ''
      this.persist()
      return null
    },
  },
})
