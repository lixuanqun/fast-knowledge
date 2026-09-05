import { ref } from 'vue'
import type { FormInstance } from 'element-plus'

/**
 * 表单弹窗 composable — 统一 el-dialog + el-form 的 validate/reset 样板。
 *
 * @example
 * const { visible, formRef, form, open, close, validateForm } = useFormDialog({ name: '', description: '' })
 * // open() 打开弹窗并重置为默认值；validateForm() 校验通过返回 true
 */
export function useFormDialog<T extends Record<string, unknown>>(defaults: T) {
  const visible = ref(false)
  const formRef = ref<FormInstance>()
  const form = ref<T>({ ...defaults })

  function open(overrides?: Partial<T>) {
    Object.assign(form.value, defaults, overrides)
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  async function validateForm(): Promise<boolean> {
    return (await formRef.value?.validate().catch(() => false)) ?? false
  }

  return { visible, formRef, form, open, close, validateForm }
}