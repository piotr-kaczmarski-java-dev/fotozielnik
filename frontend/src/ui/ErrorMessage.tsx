import { useTranslation } from 'react-i18next'
import { errorMessage } from './errors.ts'

/** An error shown to the user; `role="alert"` makes screen readers announce it. */
export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation()
  return (
    <div role="alert" className="alert alert-error">
      <p>{errorMessage(error, t)}</p>
      {onRetry && (
        <button type="button" className="button secondary" onClick={onRetry}>
          {t('common.retry')}
        </button>
      )}
    </div>
  )
}

export function Loading() {
  const { t } = useTranslation()
  return (
    <p className="loading" role="status">
      {t('common.loading')}
    </p>
  )
}
