import { describe, expect, it } from 'vitest'
import { interviewExcerpt } from './interviewExcerpt'

describe('interviewExcerpt', () => {
  it('turns a whole Markdown document into a plain text summary', () => {
    expect(interviewExcerpt('# 项目经历\n\n## 技术选择\n- **缓存**\n1. _幂等_\n> 复盘\n\n[资料](https://example.com) ![架构图](diagram.png)\n```ts\nconst ready = true\n```\n---\n')).toBe('项目经历 技术选择 缓存 幂等 复盘 资料 架构图 const ready = true')
  })
  it('handles blank documents and undecorated Chinese text', () => {
    expect(interviewExcerpt('  \n\n ')).toBe('')
    expect(interviewExcerpt('记录面试体验，整理准备方向。')).toBe('记录面试体验，整理准备方向。')
  })
})
