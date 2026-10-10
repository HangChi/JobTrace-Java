import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import ApplicationOverview from './ApplicationOverview'

it('shows the legacy stage distribution and localized follow-up reason', () => {
  const onOpen = vi.fn()
  render(<ApplicationOverview summary={{ total: 2, submitted: 2, refused: 0, offers: 0, addedThisWeek: 1, stageDistribution: { screening: 1, interview_1: 2 }, followUps: [{ id: 'a1', companyName: '星河科技', positionName: '前端工程师', followUpDays: 7, followUpReason: 'timeline' }], progressReminders: [] }} onOpen={onOpen} />)
  expect(screen.getByLabelText('共 3 次阶段记录')).toBeInTheDocument()
  expect(screen.getByText('时间线 7 天未更新')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '查看 星河科技 前端工程师 详情' }))
  expect(onOpen).toHaveBeenCalledWith('a1')
})
