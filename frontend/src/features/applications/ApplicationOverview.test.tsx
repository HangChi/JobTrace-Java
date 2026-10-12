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

it('reserves metric and panel space while summary data loads', () => {
  const { container, rerender } = render(<ApplicationOverview summary={null} onOpen={vi.fn()} />)
  expect(container.querySelectorAll('.application-summary-card')).toHaveLength(5)
  expect(container.querySelectorAll('.application-overview-panel')).toHaveLength(2)
  expect(screen.getByText('正在加载阶段分布…')).toBeInTheDocument()
  rerender(<ApplicationOverview summary={{ total: 0, submitted: 0, refused: 0, offers: 0, addedThisWeek: 0, stageDistribution: {}, followUps: [], progressReminders: [] }} onOpen={vi.fn()} />)
  expect(container.querySelectorAll('.application-summary-card')).toHaveLength(5)
  expect(container.querySelectorAll('.application-overview-panel')).toHaveLength(2)
  expect(screen.getByText('目前都跟进得很好。时间线或投递记录长期未更新会出现在这里。')).toBeInTheDocument()
})
