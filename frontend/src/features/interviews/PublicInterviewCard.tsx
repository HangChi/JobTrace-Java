import type { PublicInterview } from './api'

const stages: Record<string, string> = { assessment: '测评/AI测评', interview_1: '一面/AI面', interview_2: '二面', interview_3: '三面', hr_interview: 'HR 面', final_interview: '终面' }
const categories: Record<string, string> = { technical: '技术基础', project: '项目经历', behavioral: '行为面试', system_design: '系统设计', other: '其他' }

export default function PublicInterviewCard({ item, onOpen }: { item: PublicInterview; onOpen: () => void }) {
  const question = item.questions[0]
  const answer = question?.improvedAnswer || question?.originalAnswer || question?.followUpNotes
  const answerLabel = question?.improvedAnswer ? '改进回答' : question?.originalAnswer ? '当时回答' : '追问记录'
  const author = item.author

  return <article className="public-feed-card">
    <header className="public-feed-card-header"><div className="public-feed-author">
      {author?.image ? <img className="public-feed-avatar" src={author.image} alt="" /> : <span className="public-feed-avatar public-feed-anonymous" aria-hidden="true">{author ? author.username.slice(0, 1) : '匿'}</span>}
      <div><strong>{author ? `@${author.username}` : '匿名用户'}</strong><span>{author ? '署名分享' : '匿名分享'}</span></div>
    </div><time dateTime={item.publishedAt}>{item.publishedAt.slice(0, 10)}</time></header>
    <div className="public-feed-card-body"><div className="public-feed-company"><button type="button" className="public-feed-title-link" onClick={onOpen}><h2>{item.companyName} · {item.positionName}</h2></button><span>{item.city ? `${item.city} · ` : ''}面试于 {item.interviewedOn}</span></div>
      {question ? <section className="public-feed-preview" aria-label="面经预览"><div className="public-feed-preview-heading"><span className="public-feed-status-dot" aria-hidden="true" /><strong>{stages[item.stage] ?? item.stage}</strong><span>{categories[question.category] ?? question.category}</span><small>{item.questionCount} 个问题</small></div><p className="public-feed-preview-question">{question.question}</p>{answer && <div className="public-feed-preview-answer"><strong>{answerLabel}</strong><p>{answer}</p></div>}</section> : item.highlights && <p className="public-feed-highlights">{item.highlights}</p>}
    </div>
    <footer className="public-feed-card-footer"><button type="button" onClick={onOpen}>{item.questionCount > 1 ? `查看全部 ${item.questionCount} 个问题` : '查看完整面经'} <span aria-hidden="true">›</span></button></footer>
    <div className="public-feed-engagement" aria-label="互动数据"><span>♡ {item.engagement.likeCount}</span><span>评论 {item.engagement.commentCount}</span><span>浏览 {item.engagement.viewCount}</span></div>
  </article>
}
