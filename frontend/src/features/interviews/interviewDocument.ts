import type { InterviewQuestion } from './api'

/** Keep legacy answer text in the document and private metadata out of its public body. */
export function consolidateInterview(questions: InterviewQuestion[]) {
  const body = questions.map(item => [
    item.question,
    item.originalAnswer && `### 原始回答\n\n${item.originalAnswer}`,
    item.followUpNotes && `### 追问记录\n\n${item.followUpNotes}`,
    item.improvedAnswer && `### 改进回答\n\n${item.improvedAnswer}`,
  ].filter(Boolean).join('\n\n')).join('\n\n---\n\n')
  const metadata = questions.slice(1).map(item => `记录：${item.question}\n类别：${item.category}\n自评分：${item.selfRating ?? '未评分'}`).join('\n\n')
  return {
    question: { ...questions[0], category: questions[0]?.category ?? 'other', question: body, originalAnswer: null, followUpNotes: null, improvedAnswer: null, selfRating: questions[0]?.selfRating ?? null } satisfies InterviewQuestion,
    privateMetadata: metadata ? `历史复盘字段（仅自己可见）\n\n${metadata}` : '',
  }
}

