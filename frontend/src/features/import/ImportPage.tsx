import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { confirmImport, previewImport } from './api'
import type { ImportPreview, ImportResult } from './api'

const message = (error: unknown) => error instanceof ApiError ? error.message : '导入未完成，请重试。'
const rowText = (value: unknown) => typeof value === 'string' ? value : ''

export default function ImportPage({ onExpired }: { onExpired: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [visibleRows, setVisibleRows] = useState(100)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function showPreview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || busy) return
    setBusy(true); setError(''); setPreview(null); setResult(null)
    try {
      const value = await previewImport(file)
      setPreview(value)
      setSelected(new Set())
      setVisibleRows(100)
    } catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  async function confirm() {
    if (!preview || selected.size === 0 || busy) return
    setBusy(true); setError('')
    try {
      const decisions = preview.rows.filter(row => selected.has(row.rowNumber)).map(row => ({ rowNumber: row.rowNumber, action: 'import' as const }))
      setResult(await confirmImport(preview.id, decisions))
      setPreview(null)
    } catch (caught) { if (caught instanceof ApiError && caught.status === 401) onExpired(); else setError(message(caught)) }
    finally { setBusy(false) }
  }

  function toggle(rowNumber: number) {
    setSelected(current => { const next = new Set(current); if (next.has(rowNumber)) next.delete(rowNumber); else next.add(rowNumber); return next })
  }

  return <section className="import-content">
    <ol className="transfer-steps" aria-label="导入步骤"><li className={file ? 'is-complete' : 'is-current'}><span>1</span><strong>选择文件</strong></li><li className={preview ? 'is-current' : result ? 'is-complete' : ''}><span>2</span><strong>检查数据</strong></li><li className={result ? 'is-current' : ''}><span>3</span><strong>确认导入</strong></li></ol>
    <div className="content-card import-intro"><h2>上传投递记录</h2><p>选择 CSV 或 XLSX 文件，预检后逐行确认导入。预检不会创建投递记录。</p><form onSubmit={event => void showPreview(event)}><label className="import-file-label">选择 CSV 或 XLSX 文件<input type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={event => setFile(event.target.files?.[0] ?? null)} required /></label><button disabled={!file || busy} type="submit" aria-label="预览文件">{busy && !preview ? '正在预检…' : '上传并预检'}</button></form></div>
    {error && <p className="error" role="alert">{error}</p>}
    {preview && <section className="content-card import-preview"><h2>导入预览</h2><p>共 {preview.totalRows} 行，{preview.validRows} 行有效，{preview.invalidRows} 行无效，{preview.duplicateRows} 行可能重复。已选择 {selected.size} 行。</p><p className="muted-note">预览有效期至 {new Date(preview.expiresAt).toLocaleString('zh-CN')}；默认不选中任何行，请逐项核对。</p><button className="quiet-button" onClick={() => setSelected(new Set(preview.rows.filter(row => row.errors.length === 0 && row.duplicateApplicationIds.length === 0).map(row => row.rowNumber)))}>选择全部有效且非重复行</button><div className="import-table-wrap"><table><thead><tr><th>导入</th><th>行号</th><th>公司</th><th>岗位</th><th>日期</th><th>检查结果</th></tr></thead><tbody>{preview.rows.slice(0, visibleRows).map(row => <tr key={row.rowNumber}><td><input type="checkbox" aria-label={`导入第 ${row.rowNumber} 行`} checked={selected.has(row.rowNumber)} disabled={row.errors.length > 0} onChange={() => toggle(row.rowNumber)} /></td><td>{row.rowNumber}</td><td>{rowText(row.data?.companyName)}</td><td>{rowText(row.data?.positionName)}</td><td>{rowText(row.data?.appliedDate)}</td><td>{row.errors.length > 0 ? row.errors.map(item => item.message).join('；') : row.duplicateApplicationIds.length > 0 ? '可能重复' : '可导入'}</td></tr>)}</tbody></table></div>{preview.rows.length > visibleRows && <button type="button" className="quiet-button" onClick={() => setVisibleRows(count => count + 100)}>显示更多行（已显示 {Math.min(visibleRows, preview.rows.length)} / {preview.rows.length}）</button>}<div className="form-actions"><button disabled={busy || selected.size === 0} onClick={() => void confirm()}>{busy ? '正在导入…' : `确认导入 ${selected.size} 行`}</button><button className="quiet-button" onClick={() => setPreview(null)}>取消预览</button></div></section>}
    {result && <section className="content-card import-result" role="status"><h2>导入完成</h2><p>创建 {result.created} 行，跳过 {result.skipped} 行，失败 {result.failed} 行。</p>{result.rows.filter(row => row.error).map(row => <p key={row.rowNumber} className="reminder-warning">第 {row.rowNumber} 行：{row.error?.message}</p>)}</section>}
  </section>
}
