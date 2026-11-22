import { useMutation } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { apiSend } from '../../api/client.ts'
import { useMe, useSetMe } from '../../api/queries.ts'
import type { ChangePasswordRequest } from '../../api/types.ts'
import { applyTheme, storedTheme, type Theme } from '../../theme.ts'
import { ErrorMessage } from '../../ui/ErrorMessage.tsx'

/** Must match UserService.MIN_PASSWORD_LENGTH in the backend. */
export const MIN_PASSWORD_LENGTH = 8

export function AccountPage() {
  const { t } = useTranslation()
  const me = useMe()
  const setMe = useSetMe()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const forced = me.data?.mustChangePassword ?? false

  const change = useMutation({
    mutationFn: (body: ChangePasswordRequest) => apiSend('PUT', '/api/me/password', body),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setRepeat('')
      if (me.data) setMe({ ...me.data, mustChangePassword: false })
      if (forced) void navigate('/', { replace: true })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setLocalError(null)
    if (next.length < MIN_PASSWORD_LENGTH) {
      setLocalError(t('password.tooShort', { min: MIN_PASSWORD_LENGTH }))
      return
    }
    if (next !== repeat) {
      setLocalError(t('password.mismatch'))
      return
    }
    change.mutate({ currentPassword: current, newPassword: next })
  }

  return (
    <div className="narrow">
      <h1>{forced ? t('password.title') : t('account.title')}</h1>
      {me.data && !forced && (
        <p>{t('account.signedInAs', { name: me.data.displayName, email: me.data.email })}</p>
      )}
      {forced && (
        <p className="alert alert-info" role="status">
          {t('password.forcedInfo')}
        </p>
      )}
      {!forced && <ThemeChooser />}
      <form className="card form" onSubmit={submit}>
        {!forced && <h2>{t('password.title')}</h2>}
        <label>
          {t('password.current')}
          <input
            type="password"
            autoComplete="current-password"
            required
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </label>
        <label>
          {t('password.new')}
          <input
            type="password"
            autoComplete="new-password"
            required
            aria-describedby="password-rules"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
        </label>
        <p id="password-rules" className="hint">
          {t('password.rules', { min: MIN_PASSWORD_LENGTH })}
        </p>
        <label>
          {t('password.repeat')}
          <input
            type="password"
            autoComplete="new-password"
            required
            value={repeat}
            onChange={(e) => setRepeat(e.target.value)}
          />
        </label>
        {localError && (
          <p role="alert" className="alert alert-error">
            {localError}
          </p>
        )}
        {change.isError && <ErrorMessage error={change.error} />}
        {change.isSuccess && !forced && (
          <p role="status" className="alert alert-success">
            {t('password.changed')}
          </p>
        )}
        <button type="submit" className="button primary" disabled={change.isPending}>
          {t('password.submit')}
        </button>
      </form>
    </div>
  )
}

function ThemeChooser() {
  const { t } = useTranslation()
  const [theme, setTheme] = useState<Theme>(storedTheme)
  const choose = (value: Theme) => {
    setTheme(value)
    applyTheme(value)
  }
  return (
    <fieldset className="card choice" aria-describedby="theme-hint">
      <legend>{t('theme.title')}</legend>
      {(['dark', 'light', 'system'] as const).map((value) => (
        <label key={value} className="radio">
          <input type="radio" name="theme" value={value} checked={theme === value} onChange={() => choose(value)} />
          {t(`theme.${value}`)}
        </label>
      ))}
      <p id="theme-hint" className="hint">
        {t('theme.hint')}
      </p>
    </fieldset>
  )
}
