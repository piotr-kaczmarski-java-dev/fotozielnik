import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.tsx'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows backend info', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Response.json({ name: 'app', version: '1.0' })),
    )
    render(<App />)
    expect(await screen.findByTestId('backend-info')).toHaveProperty('textContent', 'Backend app 1.0 is up.')
  })

  it('shows the problem detail when the backend fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json({ status: 503, title: 'Service Unavailable' }, { status: 503 }),
      ),
    )
    render(<App />)
    expect((await screen.findByRole('alert')).textContent).toContain('Service Unavailable')
  })
})
