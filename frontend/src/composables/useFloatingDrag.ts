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
