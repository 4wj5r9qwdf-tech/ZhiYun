// 文件用途：集中实现前端tableHover请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端tableHover请求或共享处理能力。
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
/** 统一表格行列高亮，兼容固定表头与表体分开的列表。 / Highlight rows and columns across split tables. */
export function installTableHover(root: HTMLElement) {
  let highlighted: HTMLElement[] = []
  let previousCell: HTMLElement | undefined

  const clear = () => {
    for (const cell of highlighted) {
      cell.classList.remove('zy-hover-column', 'zy-hover-intersection')
    }
    highlighted = []
    previousCell = undefined
  }

  root.addEventListener('pointerover', event => {
    const gridCell = event.target instanceof Element
      ? event.target.closest<HTMLElement>('[data-hover-column]')
      : null
    const grid = gridCell?.closest('[data-hover-grid]')
    if (gridCell && grid) {
      if (gridCell === previousCell) return
      clear()
      previousCell = gridCell
      for (const item of grid.querySelectorAll<HTMLElement>('[data-hover-column]')) {
        if (item.dataset.hoverColumn !== gridCell.dataset.hoverColumn) continue
        item.classList.add('zy-hover-column')
        highlighted.push(item)
      }
      gridCell.classList.add('zy-hover-intersection')
      return
    }

    const cell = event.target instanceof Element
      ? event.target.closest<HTMLTableCellElement>('td, th')
      : null
    if (cell === previousCell) return
    clear()
    if (!cell || cell.colSpan !== 1 || cell.closest('.ant-table-measure-row, .ant-table-placeholder, .picker-row-disabled')) return
    const table = cell.closest('table')
    if (!table) return
    const scope = table.closest('.ant-table-wrapper') || table
    if (!root.contains(scope)) return
    previousCell = cell
    const row = cell.parentElement as HTMLTableRowElement
    const index = Array.from(row.cells).slice(0, cell.cellIndex).reduce((sum, item) => sum + item.colSpan, 0)
    const tables = scope === table ? [table] : scope.querySelectorAll('table')
    for (const candidate of tables) {
      if (candidate !== table && candidate.closest('.ant-table-wrapper') !== scope) continue
      for (const candidateRow of candidate.rows) {
        if (candidateRow.matches('.ant-table-measure-row, .ant-table-placeholder, .picker-row-disabled')) continue
        let offset = 0
        for (const item of candidateRow.cells) {
          if (offset === index && item.colSpan === 1 && !item.classList.contains('ant-table-cell-scrollbar')) {
            item.classList.add('zy-hover-column')
            highlighted.push(item)
            break
          }
          offset += item.colSpan
          if (offset > index) break
        }
      }
    }
    if (row.parentElement?.tagName === 'TBODY') cell.classList.add('zy-hover-intersection')
  })
  root.addEventListener('pointerleave', clear)
}
