// 文件用途：集中实现前端useFloatingDrag请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端useFloatingDrag请求或共享处理能力。
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
import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** 拖动悬浮组件并限制在当前视口。 / Drag a floating element within the viewport. */
export function useFloatingDrag(element: Ref<HTMLElement | undefined>) {
  const position = ref<{ x: number; y: number }>()
  const dragged = ref(false)
  let observer: ResizeObserver | undefined
  let active: { pointerId: number; startX: number; startY: number; x: number; y: number } | undefined

  function clamp(x: number, y: number) {
    const rect = element.value?.getBoundingClientRect()
    if (!rect) return { x, y }
    return {
      x: Math.max(0, Math.min(x, Math.max(0, window.innerWidth - rect.width))),
      y: Math.max(0, Math.min(y, Math.max(0, window.innerHeight - rect.height)))
    }
  }

  const style = computed(() => position.value
    ? { left: `${position.value.x}px`, top: `${position.value.y}px`, right: 'auto', bottom: 'auto' }
    : undefined)

  function start(event: PointerEvent) {
    if (event.button !== 0 || !element.value) return
    const target = event.target as HTMLElement
    if (target.closest('[data-no-drag]')) return
    const rect = element.value.getBoundingClientRect()
    active = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, x: rect.left, y: rect.top }
    dragged.value = false
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', end)
    window.addEventListener('pointercancel', end)
  }

  function move(event: PointerEvent) {
    if (!active || event.pointerId !== active.pointerId) return
    const dx = event.clientX - active.startX
    const dy = event.clientY - active.startY
    if (Math.hypot(dx, dy) < 5 && !dragged.value) return
    dragged.value = true
    position.value = clamp(active.x + dx, active.y + dy)
    event.preventDefault()
  }

  function end(event: PointerEvent) {
    if (!active || event.pointerId !== active.pointerId) return
    active = undefined
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', end)
    window.removeEventListener('pointercancel', end)
  }

  function resize() {
    if (position.value) {
      const next = clamp(position.value.x, position.value.y)
      if (next.x !== position.value.x || next.y !== position.value.y) position.value = next
    }
  }

  onMounted(() => {
    window.addEventListener('resize', resize)
    if (element.value && typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(resize)
      observer.observe(element.value)
    }
  })
  onBeforeUnmount(() => {
    observer?.disconnect()
    window.removeEventListener('resize', resize)
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', end)
    window.removeEventListener('pointercancel', end)
  })
  return { style, start, dragged }
}
