import type { TFunction } from 'i18next'
import { ApiError } from '../api/client.ts'

/**
 * A user-facing Polish message for any error: the translation of the backend's ProblemDetail `code`
 * (errors.auth.bad-credentials ...), else the server's detail, else a generic text.
 */
export function errorMessage(error: unknown, t: TFunction): string {
  if (error instanceof ApiError) {
    const code = error.code
    if (code) {
      const key = `errors.${code}`
      const translated = t(key, { defaultValue: '' })
      if (translated) return translated
    }
    if (error.status === 400) return t('errors.validation')
    if (error.status === 404) return t('errors.not-found')
    return t('errors.generic')
  }
  if (error instanceof TypeError) return t('errors.network') // fetch() network failure
  return t('errors.generic')
}
