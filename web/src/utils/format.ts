const INDEX_STATUS_MAP: Record<
  string,
  {
    label: string
    type: 'success' | 'warning' | 'danger' | 'info'
    icon: 'Clock' | 'Loading' | 'CircleCheck' | 'CircleClose'
    spinning?: boolean
  }
> = {
  INDEXED: { label: '已索引', type: 'success', icon: 'CircleCheck' },
  INDEXING: { label: '索引中', type: 'warning', icon: 'Loading', spinning: true },
  PENDING: { label: '待处理', type: 'info', icon: 'Clock' },
  FAILED: { label: '失败', type: 'danger', icon: 'CircleClose' }
}

export function indexStatusMeta(status: string) {
  return (
    INDEX_STATUS_MAP[status] || {
      label: status,
      type: 'info' as const,
      icon: 'Clock' as const
    }
  )
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
