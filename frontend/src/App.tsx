import { useEffect, useState } from 'react'
import { apiGet } from './api/client.ts'
import type { AppInfo } from './api/types.ts'
import './App.css'

function App() {
  const [info, setInfo] = useState<AppInfo | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    apiGet<AppInfo>('/api/public/info')
      .then(setInfo)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  return (
    <main>
      <h1>{'fotozielnik.pl'}</h1>
      {error && <p role="alert">Backend unreachable: {error}</p>}
      {!error && !info && <p>Loading...</p>}
      {info && (
        <p data-testid="backend-info">
          Backend <strong>{info.name}</strong> {info.version} is up.
        </p>
      )}
    </main>
  )
}

export default App
