import { useEffect, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { listPublicInterviews } from './api'
import type { PublicInterview } from './api'

const stages: Record<string, string> = { assessment: '测评', interview_1: '一面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }

export default function PublicInterviewSidebar({ onOpen, onMine, onExpired }: { onOpen: (id: string) => void; onMine?: () => void; onExpired: () => void }) {
  const [items, setItems] = useState<PublicInterview[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    listPublicInterviews(new URLSearchParams({ sort: 'hot', limit: '3' }))
      .then(page => { if (active) setItems(page.items.slice(0, 3)) })
      .catch(error => {
        if (!active) return
        setFailed(true)
        if (error instanceof ApiError && error.status === 401) onExpired()
      })
    return () => { active = false }
  }, [onExpired])

  return <aside className="public-feed-sidebar" aria-label="面经广场辅助信息"><div className="public-feed-panel">
    {onMine && <button className="public-mine-link" onClick={onMine}><small>个人空间</small>{' '}<strong>我的面经 →</strong></button>}
    <section className="public-popular" aria-label="热门面经"><header><h2>热门面经</h2><span>大家都在看</span></header>
      {failed ? <p className="public-popular-note">热门面经暂时无法加载</p> : items === null ? <p className="public-popular-note" role="status">正在加载热门面经…</p> : items.length === 0 ? <p className="public-popular-note">还没有公开面经，期待你的分享。</p> : <ol>{items.map((item, index) => <li key={item.id}><button type="button" aria-label={`阅读热门面经：${item.companyName} · ${item.positionName}`} onClick={() => onOpen(item.id)}><span className="public-popular-rank" aria-hidden="true">{index + 1}</span><span className="public-popular-entry"><strong>{item.companyName} · {item.positionName}</strong><small>{[item.city, stages[item.stage] ?? item.stage].filter(Boolean).join(' · ')}</small></span></button></li>)}</ol>}
    </section>
    <p className="public-sharing-note">完成复盘后可公开，默认匿名，可随时转为私有。</p>
  </div></aside>
}
