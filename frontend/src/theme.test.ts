import { afterEach, describe, expect, it } from 'vitest'
import { applyTheme, storedTheme } from './theme.ts'

describe('theme', () => {
  afterEach(() => localStorage.clear())

  it('is dark by default', () => {
    expect(storedTheme()).toBe('dark')
  })

  it('remembers the choice and applies it to <html>', () => {
    applyTheme('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(storedTheme()).toBe('light')
  })

  it('ignores garbage in storage', () => {
    localStorage.setItem('fz-theme', 'neon')
    expect(storedTheme()).toBe('dark')
  })
})
