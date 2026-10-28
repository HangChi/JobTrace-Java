import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import InterviewDocumentEditor from './InterviewDocumentEditor'
import { consolidateInterview } from './interviewDocument'

afterEach(cleanup)

it('edits a single document and switches preview without question controls', () => {
  function Host() {
    const [value, setValue] = useState('')
    return <InterviewDocumentEditor value={value} onChange={setValue} />
  }
  render(<Host />)
  expect(screen.getAllByRole('textbox')).toHaveLength(1)
  expect(screen.queryByText('添加问题')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '预览 Markdown' }))
  expect(screen.getByText('还没有内容，切回编辑开始记录。')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '编辑' }))
  fireEvent.change(screen.getByLabelText('面经正文'), { target: { value: '# 整场面试\n\n过程、回答和复盘' } })
  fireEvent.click(screen.getByRole('button', { name: '预览 Markdown' }))
  expect(screen.getByRole('heading', { name: '整场面试' })).toBeVisible()
  expect(screen.getByText('过程、回答和复盘')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '编辑' }))
  expect(screen.getByLabelText('面经正文')).toHaveValue('# 整场面试\n\n过程、回答和复盘')
})

it('consolidates legacy content without exposing private scores in the document', () => {
  const question = { id: 'q1', category: 'technical', question: '# 首轮面试', originalAnswer: '最初回答', followUpNotes: '追问内容', improvedAnswer: '复盘回答', selfRating: 4 }
  const second = { id: 'q2', category: 'project', question: '项目讨论', originalAnswer: null, followUpNotes: null, improvedAnswer: null, selfRating: 2 }
  const original = JSON.stringify([question, second])
  const document = consolidateInterview([question, second])
  for (const text of ['# 首轮面试', '最初回答', '追问内容', '复盘回答', '项目讨论']) expect(document.question.question).toContain(text)
  expect(document.question.question).not.toContain('自评分')
  expect(document.privateMetadata).toContain('类别：project')
  expect(document.privateMetadata).toContain('自评分：2')
  expect(document.question).toMatchObject({ id: 'q1', selfRating: 4, originalAnswer: null, followUpNotes: null, improvedAnswer: null })
  expect(JSON.stringify([question, second])).toBe(original)
  expect(consolidateInterview([document.question])).toEqual({ question: document.question, privateMetadata: '' })
})

it('handles empty drafts and unscored legacy content', () => {
  expect(consolidateInterview([]).question).toMatchObject({ question: '', category: 'other', selfRating: null })
  const item = { category: 'other', question: '记录', originalAnswer: '', followUpNotes: '', improvedAnswer: '', selfRating: null }
  expect(consolidateInterview([item, item]).privateMetadata).toContain('未评分')
  expect(consolidateInterview([item]).privateMetadata).toBe('')
})
