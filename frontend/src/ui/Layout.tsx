import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { apiSend } from '../api/client.ts'
import { useMe } from '../api/queries.ts'

/** Few, large navigation entries (03-product-design "Screens and flows"). */
export function Layout() {
  const { t } = useTranslation()
  const me = useMe()
  const client = useQueryClient()
  const navigate = useNavigate()

  async function logout() {
    try {
      await apiSend('POST', '/api/auth/logout')
    } finally {
      client.clear()
      client.setQueryData(['me'], null)
      void navigate('/login', { replace: true })
    }
  }

  const forcedPasswordChange = me.data?.mustChangePassword ?? false
  return (
    <>
      <header className="topbar">
        <span className="brand">{t('app.name')}</span>
        {!forcedPasswordChange && (
          <nav aria-label={t('nav.main')}>
            <NavLink to="/" end>
              {t('nav.library')}
            </NavLink>
            <NavLink to="/import">{t('nav.import')}</NavLink>
            {me.data?.role === 'ADMIN' && <NavLink to="/admin/users">{t('nav.admin')}</NavLink>}
            <NavLink to="/account">{t('nav.account')}</NavLink>
          </nav>
        )}
        <button type="button" className="button link" onClick={() => void logout()}>
          {t('nav.logout')}
        </button>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </>
  )
}
