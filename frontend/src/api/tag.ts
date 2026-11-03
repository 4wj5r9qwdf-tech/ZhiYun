import api from './index'

/** 查询已经通过业务标签动态登记的归属模块；系统不返回预置空模块。 */
export function getTagModules() {
  return api.get('/system/tag/modules')
}

/** 查询指定模块当前可用于业务表单选择的启用标签。 */
export function getTagOptions(moduleKey: string) {
  return api.get('/system/tag/options', { params: { moduleKey } })
}

/** 保存业务表单中新录入的标签目录项；服务端按模块与名称幂等去重。 */
export function ensureTag(moduleKey: string, moduleName: string, tagName: string) {
  return api.post('/system/tag/ensure', { moduleKey, moduleName, tagName })
}

/** 查询管理页中一个模块下的标签目录，可按名称和状态筛选。 */
export function getTagList(moduleKey: string, keyword = '', status = '') {
  return api.get('/system/tag/list', { params: { moduleKey, keyword, status } })
}

/** 管理员手动新增标签，所属模块由表单明确传入。 */
export function addTag(body: { moduleKey: string; moduleName: string; tagName: string; isCommon: string }) {
  return api.post('/system/tag', body)
}

/** 更新标签目录信息；moduleKey 用来限定目标记录，不支持跨模块迁移。 */
export function editTag(body: { tagId: string; moduleKey: string; tagName: string; isCommon: string; status: string }) {
  return api.put('/system/tag', body)
}

/** 软停用标签，历史业务关联继续保留。 */
export function disableTag(tagId: string) {
  return api.put('/system/tag/' + tagId + '/disable')
}
