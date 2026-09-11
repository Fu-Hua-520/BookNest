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

  /** 扁平化分类列表（含一级与二级），供下拉选择使用 */
  function flattenCategories() {
    const result = []
    for (const parent of categoryTree.value || []) {
      result.push({ id: parent.id, name: parent.name, isParent: true, parentId: parent.parentId })
      for (const child of parent.children || []) {
        result.push({
          id: child.id,
          name: child.name,
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

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      const [tree, tags] = await Promise.all([
        taxonomyApi.getCategoryTree().catch(() => []),
        taxonomyApi.getHotTags(20).catch(() => [])
      ])
      categoryTree.value = tree || []
      hotTags.value = tags || []
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  return { categoryTree, hotTags, loaded, loading, load, flattenCategories, findCategoryName }
})
