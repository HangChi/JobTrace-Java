import { useEffect, useId, useRef, useState } from 'react'
import type { ReactNode } from 'react'

export default function QueryFilterPanel({ count, onReset, instant = false, children }: { count: number; onReset: () => void; instant?: boolean; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const id = useId()

  useEffect(() => {
    if (!open) return
    const pointer = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false)
    }
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
    }
    document.addEventListener('pointerdown', pointer)
    document.addEventListener('keydown', keyboard)
    return () => { document.removeEventListener('pointerdown', pointer); document.removeEventListener('keydown', keyboard) }
  }, [open])

  return <div className="query-filter" ref={root}>
    <button ref={trigger} type="button" className={`query-filter-trigger${count ? ' has-filters' : ''}`} aria-label="筛选条件" aria-expanded={open} aria-controls={id} onClick={() => setOpen(value => !value)}>
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M3 5h14M6 10h8M8.5 15h3" strokeLinecap="round" /><circle cx="7" cy="5" r="1.5" fill="currentColor" stroke="none" /><circle cx="12" cy="10" r="1.5" fill="currentColor" stroke="none" /></svg>
      <span>筛选条件</span>{count > 0 && <span className="query-filter-count" aria-label={`已选 ${count} 项`}>{count}</span>}
    </button>
    <section id={id} className="query-filter-panel" aria-label="筛选面板" hidden={!open}>
      <header><div><h2>筛选条件</h2><p>{instant ? '选择后自动更新结果' : '设置条件后应用筛选'}</p></div><button type="button" className="query-panel-close" aria-label="关闭筛选面板" onClick={() => { setOpen(false); trigger.current?.focus() }}><svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg></button></header>
      <div className="query-panel-fields">{children}</div>
      <footer><button type="button" className="query-reset" onClick={() => { onReset(); setOpen(false) }}>清除全部条件</button><button type={instant ? 'button' : 'submit'} className="query-submit" onClick={() => setOpen(false)}>{instant ? '完成' : '应用筛选'}</button></footer>
    </section>
  </div>
}
