import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as taxonomyApi from '@/api/taxonomy'

/**
 * 分类树与标签缓存：全站多处共用，仅首次拉取，之后复用
 */
export const useTaxonomyStore = defineStore('taxonomy', () => {
  const categoryTree = ref([])
  const hotTags = ref([])
  const loaded = ref(false)
  const loading = ref(false)

  /** 扁平化分类列表（书吧已拉平，children 恒为空，保留字段仅为兼容老数据） */
  function flattenCategories() {
    const result = []
    for (const parent of categoryTree.value || []) {
      result.push({
        id: parent.id,
        name: parent.name,
        icon: parent.icon || '',
        description: parent.description || '',
        isParent: true,
        parentId: parent.parentId
      })
      for (const child of parent.children || []) {
        result.push({
          id: child.id,
          name: child.name,
          icon: child.icon || '',
          description: child.description || '',
          isParent: false,
          parentId: parent.id,
          parentName: parent.name
        })
      }
    }
    return result
  }

  /** 根据分类 ID 反查名称 */
  function findCategoryName(id) {
    if (!id) return ''
    const target = flattenCategories().find((item) => String(item.id) === String(id))
    return target?.name || ''
  }

  /** 根据分类 ID 反查整条记录（书吧图标、简介都要用） */
  function findCategory(id) {
    if (!id) return null
    return flattenCategories().find((item) => String(item.id) === String(id)) || null
  }

  // 进行中的加载 Promise：多个组件同时首次调用时复用同一个请求，
  // 避免后者因为「已在加载中」直接返回、拿着空数据继续渲染
  let pending = null

  async function load(force = false) {
    if (loaded.value && !force) return
    if (pending) return pending

    pending = (async () => {
      loading.value = true
      try {
        const [tree, tags] = await Promise.all([
          taxonomyApi.getCategoryTree().catch(() => null),
          taxonomyApi.getHotTags(20).catch(() => null)
        ])
        if (tree) categoryTree.value = tree
        if (tags) hotTags.value = tags
        // 只有分类树真正拿到数据才算加载完成。
        // 未登录时 /category/tree 返回 401，此时若也标记 loaded，
        // 登录之后就再也不会重新拉取，分类会一直显示为空。
        loaded.value = tree != null
      } finally {
        loading.value = false
        pending = null
      }
    })()

    return pending
  }

  return { categoryTree, hotTags, loaded, loading, load, flattenCategories, findCategoryName, findCategory }
})
