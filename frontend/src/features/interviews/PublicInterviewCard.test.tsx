import { fireEvent, render, screen, cleanup } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import PublicInterviewCard from './PublicInterviewCard'
import type { PublicInterview } from './api'

afterEach(cleanup)
const item: PublicInterview = { id: 'p1', companyName: '公司', positionName: '工程师', city: '上海', stage: 'interview_1', interviewedOn: '2026-10-08', publishedAt: '2026-10-09', questionCount: 2, author: { username: '作者', image: '/avatar.png' }, engagement: { likeCount: 2, commentCount: 1, viewCount: 8, likedByViewer: false }, highlights: '经验总结', gaps: null, questions: [{ category: 'other', question: '# 面试记录', originalAnswer: '原回答', improvedAnswer: '改进回答', followUpNotes: '追问' }], recentComments: [] }
it('shows a document excerpt and opens the full article from title or footer', () => {
  const onOpen = vi.fn()
  render(<PublicInterviewCard item={item} onOpen={onOpen} />)
  expect(screen.getByText('面试记录 改进回答')).toBeInTheDocument()
  expect(screen.queryByText(/个问题/)).not.toBeInTheDocument()
  expect(screen.getByLabelText('互动数据')).toHaveTextContent('赞 2评论 1浏览 8')
  fireEvent.click(screen.getByRole('button', { name: '公司 · 工程师' }))
  fireEvent.click(screen.getByRole('button', { name: '查看完整面经' }))
  expect(onOpen).toHaveBeenCalledTimes(2)
})
it('preserves original answers and follow-up excerpts for legacy records', () => {
  const { rerender } = render(<PublicInterviewCard item={{ ...item, author: { username: '作者', image: null }, questions: [{ ...item.questions[0], improvedAnswer: null }] }} onOpen={vi.fn()} />)
  expect(screen.getByText('面试记录 原回答')).toBeInTheDocument()
  rerender(<PublicInterviewCard item={{ ...item, author: null, city: null, stage: 'custom', questions: [{ ...item.questions[0], improvedAnswer: null, originalAnswer: null }] }} onOpen={vi.fn()} />)
  expect(screen.getByText('面试记录 追问')).toBeInTheDocument()
  expect(screen.getByText('匿名用户')).toBeInTheDocument()
  expect(screen.getByText('custom')).toBeInTheDocument()
})
it('uses highlights when a record has no body and supports a completely empty excerpt', () => {
  const { rerender } = render(<PublicInterviewCard item={{ ...item, questions: [] }} onOpen={vi.fn()} />)
  expect(screen.getByText('经验总结')).toBeInTheDocument()
  rerender(<PublicInterviewCard item={{ ...item, questions: [], highlights: null }} onOpen={vi.fn()} />)
  expect(screen.queryByText('经验总结')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '查看完整面经' })).toBeInTheDocument()
})
