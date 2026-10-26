import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { MarkdownPreview } from './MarkdownPreview'

afterEach(cleanup)

it('renders review headings, emphasis, lists, quotes and fenced code', () => {
  render(<MarkdownPreview value={'# 本轮面试\n\n**重点**与*复盘*、`code`\n\n- 问题一\n- 问题二\n\n1. 继续练习\n2. 提交总结\n\n> 反馈\n\n---\n\n```\n<script>bad</script>\n```'} headingOffset={1} />)
  expect(screen.getByRole('heading', { name: '本轮面试', level: 2 })).toBeInTheDocument()
  expect(screen.getByText('重点', { selector: 'strong' })).toBeInTheDocument()
  expect(screen.getByText('复盘', { selector: 'em' })).toBeInTheDocument()
  expect(screen.getByText('问题二')).toBeInTheDocument()
  expect(screen.getByText('提交总结')).toBeInTheDocument()
  expect(screen.getByText('反馈')).toBeInTheDocument()
  expect(screen.getByText('<script>bad</script>')).toBeInTheDocument()
  expect(document.querySelector('script')).toBeNull()
})

it('keeps safe HTTP links and renders unsafe destinations as plain text', () => {
  render(<MarkdownPreview value={'[官网](https://example.com) [危险](javascript:alert(1))'} />)
  expect(screen.getByRole('link', { name: '官网' })).toHaveAttribute('href', 'https://example.com')
  expect(screen.queryByRole('link', { name: '危险' })).not.toBeInTheDocument()
  expect(screen.getByText('危险')).toBeInTheDocument()
})

it('shows an empty hint when a review has no content', () => {
  render(<MarkdownPreview value={'\n  \n'} />)
  expect(screen.getByText(/还没有内容/)).toBeInTheDocument()
})
