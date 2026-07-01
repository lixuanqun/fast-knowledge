const INDEX_STATUS_MAP: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  INDEXED: { label: '已索引', type: 'success' },
  INDEXING: { label: '索引中', type: 'warning' },
  PENDING: { label: '待索引', type: 'info' },
  FAILED: { label: '失败', type: 'danger' }
}

export function indexStatusMeta(status: string) {
  return INDEX_STATUS_MAP[status] || { label: status, type: 'info' as const }
}

export function formatFileSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export function formatDateTime(value?: string) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { hour12: false })
}

export function visibilityLabel(v: string) {
  return v === 'PUBLIC' ? '公开' : '私有'
}
