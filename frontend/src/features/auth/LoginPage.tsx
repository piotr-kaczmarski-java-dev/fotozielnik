import { useMutation } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { apiSend } from '../../api/client.ts'
import { useMe, useSetMe } from '../../api/queries.ts'
import type { LoginRequest, Me } from '../../api/types.ts'
import { ErrorMessage } from '../../ui/ErrorMessage.tsx'

export function LoginPage() {
  const { t } = useTranslation()
  const me = useMe()
  const setMe = useSetMe()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const login = useMutation({
    mutationFn: (body: LoginRequest) => apiSend<Me>('POST', '/api/auth/login', body),
    onSuccess: (user) => {
      setMe(user)
      void navigate(from, { replace: true })
    },
  })

  if (me.data) return <Navigate to={from} replace />

  function submit(e: FormEvent) {
    e.preventDefault()
    login.mutate({ email, password })
  }

  return (
    <main className="narrow">
      <h1 className="brand-title">{t('app.name')}</h1>
      <form className="card form" onSubmit={submit} aria-labelledby="login-title">
        <h2 id="login-title">{t('login.title')}</h2>
        <label>
          {t('login.email')}
          <input
            type="email"
            name="email"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          {t('login.password')}
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {login.isError && <ErrorMessage error={login.error} />}
        <button type="submit" className="button primary" disabled={login.isPending}>
          {login.isPending ? t('login.submitting') : t('login.submit')}
        </button>
        <p className="hint">{t('login.hint')}</p>
      </form>
    </main>
  )
}
