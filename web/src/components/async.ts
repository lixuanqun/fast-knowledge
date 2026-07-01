import { defineAsyncComponent } from 'vue'

/** 含 marked + DOMPurify，按需异步加载 */
export const MarkdownBody = defineAsyncComponent({
  loader: () => import('@/components/MarkdownBody.vue'),
  delay: 80
})

/** 文档预览抽屉，仅详情页使用 */
export const DocumentPreviewDrawer = defineAsyncComponent({
  loader: () => import('@/components/DocumentPreviewDrawer.vue'),
  delay: 0
})

/** 改密弹窗，点击时再加载即可 */
export const ChangePasswordDialog = defineAsyncComponent({
  loader: () => import('@/components/ChangePasswordDialog.vue'),
  delay: 0
})
