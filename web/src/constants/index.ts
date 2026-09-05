/** 文档类型枚举（与后端 kb_document.doc_type 对齐） */
export const DOC_TYPES = [
  { label: '制度', value: 'POLICY' },
  { label: '工艺', value: 'PROCESS' },
  { label: '设备', value: 'EQUIPMENT' },
  { label: '质量', value: 'QUALITY' },
  { label: '安全', value: 'SAFETY' },
  { label: 'FAQ', value: 'FAQ' },
  { label: '其他', value: 'GENERAL' }
] as const

/** 成员权限枚举 */
export const PERMISSIONS = [
  { label: '只读', value: 'READ' },
  { label: '写入', value: 'WRITE' },
  { label: '管理', value: 'ADMIN' }
] as const

/** 角色标签映射 */
export const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  USER: '普通用户'
}

/** 写文档风格预设 */
export const WRITER_STYLES = [
  { label: '正式、专业', value: '正式、专业' },
  { label: '轻松、易懂', value: '轻松、易懂' },
  { label: '技术、严谨', value: '技术、严谨' }
] as const

export const PASSWORD_MIN_LENGTH = 6
