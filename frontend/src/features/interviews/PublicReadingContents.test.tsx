import { createRef } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import PublicReadingContents from './PublicReadingContents'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('builds a directory from rendered headings and keeps duplicate names at unique anchors', async () => {
  const articleRef = createRef<HTMLElement>()
  const { unmount } = render(<><article ref={articleRef}><section className="public-reading-body"><h2>项目<strong>交流</strong></h2><h3>项目交流</h3><pre># 代码中的标题</pre></section></article><PublicReadingContents articleRef={articleRef} documentId="p1" /></>)
  const links = await screen.findAllByRole('link', { name: '项目交流' })
  expect(links[0]).toHaveAttribute('href', '#reading-section-0')
  expect(links[1]).toHaveAttribute('href', '#reading-section-1')
  expect(links[1]).toHaveClass('nested')
  expect(screen.queryByRole('link', { name: '代码中的标题' })).not.toBeInTheDocument()
  const heading = screen.getByRole('heading', { level: 2 })
  expect(heading).toHaveAttribute('id', 'reading-section-0')
  expect(heading).toHaveAttribute('tabindex', '-1')
  const details = screen.getByText('面经目录', { selector: 'summary' }).parentElement as HTMLDetailsElement
  expect(details.open).toBe(true)
  details.open = false
  fireEvent(details, new Event('toggle'))
  expect(details.open).toBe(false)
  unmount()
  expect(heading).not.toHaveAttribute('id')
})

it('starts collapsed on mobile and shows body and comments without headings', () => {
  vi.stubGlobal('innerWidth', 375)
  const articleRef = createRef<HTMLElement>()
  const { rerender } = render(<PublicReadingContents articleRef={articleRef} documentId="p1" />)
  const details = screen.getByText('面经目录', { selector: 'summary' }).parentElement as HTMLDetailsElement
  expect(details.open).toBe(false)
  details.open = true
  fireEvent(details, new Event('toggle'))
  expect(screen.getByRole('link', { name: '面经正文' })).toHaveAttribute('href', '#reading-body')
  expect(screen.getByRole('link', { name: '评论' })).toHaveAttribute('href', '#reading-comments')
  rerender(<PublicReadingContents articleRef={articleRef} documentId="p2" />)
  expect(screen.getAllByRole('link')).toHaveLength(2)
})
