export const guestRoutes = new Set(['/login', '/register', '/forgot-password', '/reset-password'])

export const navigation = [
  { path: '/', label: '招聘广场' },
  { path: '/applications', label: '我的投递' },
  { path: '/interviews', label: '面经广场' },
  { path: '/analytics', label: '求职分析' },
] as const

export function safePath(value: string | null | undefined, fallback = '/'): string {
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
