import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ImportPage from './ImportPage'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })
const preview = { id: 'batch-1', expiresAt: '2026-10-09T00:00:00Z', totalRows: 2, validRows: 2, invalidRows: 0, duplicateRows: 1, columns: {}, rows: [{ rowNumber: 2, data: { companyName: '新公司', positionName: '开发', appliedDate: '2026-10-08' }, errors: [], duplicateApplicationIds: [] }, { rowNumber: 3, data: { companyName: '旧公司', positionName: '开发', appliedDate: '2026-10-08' }, errors: [], duplicateApplicationIds: ['a1'] }] }

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('previews without importing, then confirms only the selected row', async () => {
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/security/csrf') return json({ token: 'csrf' })
    if (path === '/api/imports/preview') return json(preview, 201)
    return json({ created: 1, skipped: 1, failed: 0, rows: [] })
  })
  vi.stubGlobal('fetch', fetcher)
  render(<ImportPage onExpired={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('选择 CSV 或 XLSX 文件'), { target: { files: [new File(['公司,岗位\n新公司,开发'], 'import.csv', { type: 'text/csv' })] } })
  fireEvent.submit(screen.getByRole('button', { name: '预览文件' }).closest('form')!)
  expect(await screen.findByRole('heading', { name: '导入预览' })).toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalledWith('/api/imports/batch-1/confirm', expect.anything())
  fireEvent.click(screen.getByRole('button', { name: '选择全部有效且非重复行' }))
  expect(screen.getByRole('checkbox', { name: '导入第 2 行' })).toBeChecked()
  expect(screen.getByRole('checkbox', { name: '导入第 3 行' })).not.toBeChecked()
  fireEvent.click(screen.getByRole('button', { name: '确认导入 1 行' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/imports/batch-1/confirm', expect.objectContaining({ method: 'POST', body: JSON.stringify({ decisions: [{ rowNumber: 2, action: 'import' }] }) })))
  expect(await screen.findByRole('heading', { name: '导入完成' })).toBeInTheDocument()
})
