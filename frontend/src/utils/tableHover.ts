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
