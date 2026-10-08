import { apiRequest, apiUpload } from '../../shared/api/client'

export type ImportRow = {
  rowNumber: number
  data: Record<string, unknown> | null
  errors: { field: string; code: string; message: string }[]
  duplicateApplicationIds: string[]
}
export type ImportPreview = {
  id: string
  expiresAt: string
  totalRows: number
  validRows: number
  invalidRows: number
  duplicateRows: number
  columns: Record<string, string>
  rows: ImportRow[]
}
export type ImportResult = {
  created: number
  skipped: number
  failed: number
  rows: { rowNumber: number; result: string; applicationId: string | null; error: { message: string } | null }[]
}

export function previewImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiUpload<ImportPreview>('/api/imports/preview', form)
}

export function confirmImport(previewId: string, decisions: { rowNumber: number; action: 'import' | 'skip' }[]) {
  return apiRequest<ImportResult>(`/api/imports/${encodeURIComponent(previewId)}/confirm`, { method: 'POST', body: { decisions } })
}
