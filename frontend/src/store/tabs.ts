// 文件用途：支撑织云 ERP 前端的tabs启动、路由、布局或样式。
// 文件版本：1.0.0
// 功能目的：支撑织云 ERP 前端的tabs启动、路由、布局或样式。
// 设计思路：集中管理全局基础行为；业务页面遵循统一模板，权限由服务端复核。
// 实现方法：由 Vite/Vue 入口加载并向业务页面提供公共依赖或行为。
// 关键约束：不得与实际 API、权限和页面布局契约冲突。
// 依赖与引用：Docs/规范/前端规范.md；frontend/src/App.vue；frontend/src/router/index.ts
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

const storageKey = () => {
  const userId = sessionStorage.getItem('userId')
  return userId ? 'zy_open_tabs:' + userId : ''
}

function load(): { tabs: TabItem[]; activeName: string } {
  try {
    const key = storageKey()
    const raw = key ? localStorage.getItem(key) : null
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
    reload() {
      const saved = load()
      this.tabs = saved.tabs
      this.activeName = saved.activeName
    },
    clearSession() {
      this.tabs = []
      this.activeName = ''
    },
    persist() {
      const key = storageKey()
      if (key) localStorage.setItem(key, JSON.stringify({ tabs: this.tabs, activeName: this.activeName }))
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
    /** 关闭左侧（保留 targetName 及其右边）。若当前激活被关，返回应跳转的路由名 targetName。 */
    closeLeft(targetName: string): string | null {
      const i = this.tabs.findIndex(t => t.name === targetName)
      if (i <= 0) return null
      const removedActive = this.tabs.slice(0, i).some(t => t.name === this.activeName)
      this.tabs = this.tabs.slice(i)
      if (removedActive) {
        this.activeName = targetName
        this.persist()
        return targetName
      }
      this.persist()
      return null
    },
    /** 关闭右侧（保留 targetName 及其左边）。若当前激活被关，返回应跳转的路由名 targetName。 */
    closeRight(targetName: string): string | null {
      const i = this.tabs.findIndex(t => t.name === targetName)
      if (i === -1 || i === this.tabs.length - 1) return null
      const removedActive = this.tabs.slice(i + 1).some(t => t.name === this.activeName)
      this.tabs = this.tabs.slice(0, i + 1)
      if (removedActive) {
        this.activeName = targetName
        this.persist()
        return targetName
      }
      this.persist()
      return null
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
