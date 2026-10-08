import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import InterviewQuestionsEditor from './InterviewQuestionsEditor'
import type { InterviewQuestion } from './api'

afterEach(cleanup)

it('adds, edits and removes questions without dropping the others', () => {
  const first: InterviewQuestion = { id: 'q1', category: 'technical', question: '第一题', originalAnswer: null, followUpNotes: null, improvedAnswer: null, selfRating: null }
  function Host() {
    const [questions, setQuestions] = useState([first])
    return <InterviewQuestionsEditor questions={questions} onChange={setQuestions} />
  }
  render(<Host />)
  fireEvent.click(screen.getByRole('button', { name: '添加问题' }))
  const fields = screen.getAllByLabelText('面试问题')
  expect(fields).toHaveLength(2)
  fireEvent.change(fields[1], { target: { value: '第二题' } })
  expect(screen.getAllByLabelText('面试问题')[0]).toHaveValue('第一题')
  expect(screen.getAllByLabelText('面试问题')[1]).toHaveValue('第二题')
  fireEvent.click(screen.getAllByRole('button', { name: '移除' })[0])
  expect(screen.getAllByLabelText('面试问题')).toHaveLength(1)
  expect(screen.getByLabelText('面试问题')).toHaveValue('第二题')
})
