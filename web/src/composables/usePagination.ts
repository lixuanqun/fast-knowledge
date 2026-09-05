import { computed, ref, type Ref } from 'vue'

/**
 * 客户端分页 composable — 消除各列表页重复的 page/pageSize/slice 样板。
 *
 * @example
 * const { page, pageSize, paged, total, tableFooterProps } = usePagination(filteredUsers)
 */
export function usePagination<T>(source: Ref<T[]>, defaultPageSize = 10) {
  const page = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = computed(() => source.value.length)

  const paged = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return source.value.slice(start, start + pageSize.value)
  })

  /** 供 el-pagination v-model 绑定 */
  const pageBindings = {
    currentPage: page,
    pageSize
  }

  const paginationProps = {
    'page-sizes': [10, 20, 50],
    layout: 'total, sizes, prev, pager, next' as const
  }

  return { page, pageSize, total, paged, pageBindings, paginationProps }
}