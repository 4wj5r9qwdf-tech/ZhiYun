/** 连续新增共用规则：只记成功保存的数据，读写时剔除主键，按调用方的用户/业务键隔离。 */
type FormStorage = Pick<Storage, 'getItem' | 'setItem'>
export function createSavedFormDefaults<T extends object>(storage: FormStorage, key: string, empty: () => T, identityKeys: (keyof T)[]) {
  const sanitize = (source: unknown): T => {
    const result = empty()
    if (source && typeof source === 'object' && !Array.isArray(source)) {
      for (const field of Object.keys(result) as (keyof T)[]) {
        if (!identityKeys.includes(field) && Object.hasOwn(source, field)) result[field] = (source as T)[field]
      }
    }
    return result
  }
  return {
    load(): T {
      try { return sanitize(JSON.parse(storage.getItem(key) || 'null')) } catch { return empty() }
    },
    save(value: T) {
      try { storage.setItem(key, JSON.stringify(sanitize(value))) } catch { /* 存储不可用时不影响已成功的业务保存 */ }
    },
  }
}
