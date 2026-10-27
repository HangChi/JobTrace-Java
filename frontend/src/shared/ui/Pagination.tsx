type Props = { page: number; totalPages: number; onChange: (page: number) => void; label: string }

export default function Pagination({ page, totalPages, onChange, label }: Props) {
  const pages = [...new Set([1, page - 1, page, page + 1, totalPages])].filter(value => value >= 1 && value <= totalPages).sort((a, b) => a - b)
  return <nav className="page-navigation" aria-label={label}>
    <span className="page-navigation-count">第 {page} / {totalPages} 页</span>
    <div className="page-navigation-buttons">
      <button type="button" aria-label="上一页" disabled={page <= 1} onClick={() => onChange(page - 1)}>‹</button>
      {pages.map((value, index) => <span className="page-navigation-item" key={value}>
        {index > 0 && value - pages[index - 1] > 1 && <button type="button" className="page-navigation-skip" aria-label={value <= page ? '向前跳 5 页' : '向后跳 5 页'} title={value <= page ? '向前跳 5 页' : '向后跳 5 页'} onClick={() => onChange(value <= page ? Math.max(1, page - 5) : Math.min(totalPages, page + 5))}>…</button>}
        <button type="button" className={value !== page && value !== 1 && value !== totalPages ? 'page-navigation-neighbor' : undefined} aria-label={`第 ${value} 页`} aria-current={value === page ? 'page' : undefined} onClick={() => onChange(value)}>{value}</button>
      </span>)}
      <button type="button" aria-label="下一页" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>›</button>
    </div>
    <form className="page-navigation-jump" onSubmit={event => {
      event.preventDefault()
      const value = Number(new FormData(event.currentTarget).get('page'))
      if (Number.isInteger(value) && value >= 1 && value <= totalPages) onChange(value)
    }}><label>跳至<input key={page} name="page" aria-label="跳转页码" type="number" min={1} max={totalPages} step={1} required defaultValue={page} inputMode="numeric" />页</label><button type="submit">跳转</button></form>
  </nav>
}
