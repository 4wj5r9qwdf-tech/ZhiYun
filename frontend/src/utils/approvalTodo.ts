import api from '../api'
/** 与“我的待办”使用同一接口，统计当前账号的审批任务。 */
export async function approvalTodoCount(): Promise<number> {
  const result: any = await api.get('/workflow/task/pageByTaskWait', { params: { pageNum: 1, pageSize: 1 } })
  if (result.code !== 200) throw new Error(result.msg || '审批待办加载失败')
  return Math.max(0, Number(result.data?.total || 0))
}
