import type { PublicInterview } from './api'
import { interviewExcerpt } from './interviewExcerpt'

const stages: Record<string, string> = { assessment: '测评/AI测评', interview_1: '一面/AI面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }

export default function PublicInterviewCard({ item, onOpen }: { item: PublicInterview; onOpen: () => void }) {
  const question = item.questions[0]
  const answer = question?.improvedAnswer || question?.originalAnswer || question?.followUpNotes
  const excerpt = interviewExcerpt([question?.question, answer].filter(Boolean).join(' '))
  const author = item.author

  return <article className="public-feed-card">
    <header className="public-feed-card-header"><div className="public-feed-author">
      {author?.image ? <img className="public-feed-avatar" src={author.image} alt="" /> : <span className="public-feed-avatar public-feed-anonymous" aria-hidden="true">{author ? author.username.slice(0, 1) : '匿'}</span>}
      <div><strong>{author ? `@${author.username}` : '匿名用户'}</strong><span>{author ? '署名分享' : '匿名分享'}</span></div>
    </div><time dateTime={item.publishedAt}>{item.publishedAt.slice(0, 10)}</time></header>
    <div className="public-feed-card-body"><div className="public-feed-company"><button type="button" className="public-feed-title-link" onClick={onOpen}><h2>{item.companyName} · {item.positionName}</h2></button><span>{item.city ? `${item.city} · ` : ''}面试于 {item.interviewedOn}</span></div>
      <div className="public-card-tags"><span>{stages[item.stage] ?? item.stage}</span>{item.city && <span>{item.city}</span>}</div>
      {excerpt ? <p className="public-feed-excerpt">{excerpt}</p> : item.highlights && <p className="public-feed-excerpt">{interviewExcerpt(item.highlights)}</p>}
    </div>
    <footer className="public-feed-card-footer"><div className="public-feed-engagement" aria-label="互动数据"><span>赞 {item.engagement.likeCount}</span><span>评论 {item.engagement.commentCount}</span><span>浏览 {item.engagement.viewCount}</span></div><button type="button" onClick={onOpen}>查看完整面经 <span aria-hidden="true">›</span></button></footer>
  </article>
}
