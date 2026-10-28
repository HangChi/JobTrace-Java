import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import Pagination from './Pagination'
afterEach(cleanup)
it('shows page numbers, current state and five-page shortcuts', () => {
  const change = vi.fn()
  render(<Pagination page={10} totalPages={20} label="记录分页" onChange={change} />)
  expect(screen.getByRole('navigation', { name: '记录分页' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '第 10 页' })).toHaveAttribute('aria-current', 'page')
  for (const [name, target] of [['上一页', 9], ['下一页', 11], ['第 1 页', 1], ['第 20 页', 20], ['向前跳 5 页', 5], ['向后跳 5 页', 15]] as const) {
    fireEvent.click(screen.getByRole('button', { name }))
    expect(change).toHaveBeenLastCalledWith(target)
  }
})
it('clamps shortcuts and disables navigation at the ends', () => {
  const change = vi.fn()
  const { rerender } = render(<Pagination page={3} totalPages={20} label="分页" onChange={change} />)
  fireEvent.click(screen.getByRole('button', { name: '向后跳 5 页' }))
  expect(change).toHaveBeenLastCalledWith(8)
  rerender(<Pagination page={18} totalPages={20} label="分页" onChange={change} />)
  fireEvent.click(screen.getByRole('button', { name: '向前跳 5 页' }))
  expect(change).toHaveBeenLastCalledWith(13)
  rerender(<Pagination page={4} totalPages={20} label="分页" onChange={change} />)
  fireEvent.click(screen.getByRole('button', { name: '向前跳 5 页' }))
  expect(change).toHaveBeenLastCalledWith(1)
  rerender(<Pagination page={17} totalPages={20} label="分页" onChange={change} />)
  fireEvent.click(screen.getByRole('button', { name: '向后跳 5 页' }))
  expect(change).toHaveBeenLastCalledWith(20)
  rerender(<Pagination page={1} totalPages={1} label="分页" onChange={change} />)
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
  expect(screen.getAllByRole('button', { name: /^第/ })).toHaveLength(1)
})
it('jumps to valid integers only and resets input after navigation', () => {
  const change = vi.fn()
  const { rerender } = render(<Pagination page={1} totalPages={20} label="分页" onChange={change} />)
  const input = screen.getByRole('spinbutton', { name: '跳转页码' })
  for (const value of ['', '0', '21', '1.5']) {
    fireEvent.change(input, { target: { value } })
    fireEvent.submit(input.closest('form')!)
  }
  expect(change).not.toHaveBeenCalled()
  fireEvent.change(input, { target: { value: '12' } })
  fireEvent.submit(input.closest('form')!)
  expect(change).toHaveBeenLastCalledWith(12)
  rerender(<Pagination page={12} totalPages={20} label="分页" onChange={change} />)
  expect(screen.getByLabelText('跳转页码')).toHaveValue(12)
})
