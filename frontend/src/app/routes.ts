export const guestRoutes = new Set(['/login', '/register', '/forgot-password', '/reset-password'])

export const navigation = [
  { path: '/applications', label: '投递记录' },
  { path: '/reminders', label: '提醒' },
  { path: '/analytics', label: '分析' },
  { path: '/interviews', label: '面经' },
  { path: '/import', label: '导入与导出' },
  { path: '/profile', label: '个人资料' },
] as const

export function safePath(value: string | null | undefined, fallback = '/applications'): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return fallback
  try {
    const url = new URL(value, window.location.origin)
    return url.origin === window.location.origin ? url.pathname + url.search + url.hash : fallback
  } catch {
    return fallback
  }
}

export function currentPath() {
  return window.location.pathname + window.location.search + window.location.hash
}
