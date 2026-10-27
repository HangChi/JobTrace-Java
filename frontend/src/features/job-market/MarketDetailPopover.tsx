import { useId } from 'react'

export default function MarketDetailPopover({ company, label, summary, items, note }: { company: string; label: '岗位' | '地点'; summary: string; items: string[]; note?: string }) {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, '')
  const anchor = `--market-detail-${id}`
  return <div className="market-detail">
    <button type="button" className="market-detail-trigger" popoverTarget={id} aria-haspopup="dialog" style={{ anchorName: anchor }}>{summary}<span aria-hidden="true">↗</span></button>
    <div id={id} popover="auto" role="dialog" aria-label={`${company}的${label}列表`} className="market-detail-popover" style={{ positionAnchor: anchor }}>
      <div className="market-detail-heading"><div><small>{company}</small><h3>{label}列表 <span>{items.length}</span></h3></div><button type="button" className="market-detail-close" popoverTarget={id} popoverTargetAction="hide" aria-label={`关闭${label}列表`}>×</button></div>
      <div className="market-detail-scroll" tabIndex={0} role="region" aria-label={`浏览全部${label}`}><ul className={label === '地点' ? 'market-location-tags' : 'market-position-list'}>{items.map(item => <li key={item}>{item}</li>)}</ul></div>
      {note && <p className="market-detail-note">{note}</p>}
    </div>
  </div>
}
