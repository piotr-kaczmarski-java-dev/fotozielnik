import type { RouteObject } from 'react-router'
import { AccountPage } from './features/account/AccountPage.tsx'
import { AdminUsersPage } from './features/admin/AdminUsersPage.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { RequireAuth } from './features/auth/RequireAuth.tsx'
import { ImportPage } from './features/import/ImportPage.tsx'
import { LibraryPage } from './features/library/LibraryPage.tsx'
import { PhotoPage } from './features/library/PhotoPage.tsx'
import { Layout } from './ui/Layout.tsx'

/** Screens of 03-product-design; everything but /login needs a session. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <LibraryPage /> },
      { path: 'photos/:id', element: <PhotoPage /> },
      { path: 'import', element: <ImportPage /> },
      { path: 'account', element: <AccountPage /> },
      {
        path: 'admin/users',
        element: (
          <RequireAuth role="ADMIN">
            <AdminUsersPage />
          </RequireAuth>
        ),
      },
      { path: '*', element: <LibraryPage /> },
    ],
  },
]
