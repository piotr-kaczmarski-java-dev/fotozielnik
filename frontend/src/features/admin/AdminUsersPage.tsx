import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { apiGet, apiSend } from '../../api/client.ts'
import { keys, useMe } from '../../api/queries.ts'
import type { CreateUserRequest, Role, User, UserPage } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { MIN_PASSWORD_LENGTH } from '../account/AccountPage.tsx'
import { generatePassword } from './password.ts'

/** UC-05: create users, reset passwords, lock/unlock accounts (FR-002). */
export function AdminUsersPage() {
  const { t } = useTranslation()
  const me = useMe()
  const users = useQuery({ queryKey: keys.users, queryFn: () => apiGet<UserPage>('/api/admin/users?size=200') })
  const [notice, setNotice] = useState<string | null>(null)

  return (
    <div>
      <h1>{t('admin.title')}</h1>
      {notice && (
        <p role="status" className="alert alert-success">
          {notice}
        </p>
      )}
      <CreateUser onCreated={(u, password) => setNotice(`${t('admin.created', { email: u.email })} ${t('admin.temporaryPassword')}: ${password}`)} />
      {users.isPending && <Loading />}
      {users.isError && <ErrorMessage error={users.error} onRetry={() => void users.refetch()} />}
      {users.data && users.data.items.length === 0 && <p>{t('admin.empty')}</p>}
      {users.data && users.data.items.length > 0 && (
        <table className="users">
          <thead>
            <tr>
              <th scope="col">{t('admin.displayName')}</th>
              <th scope="col">{t('admin.email')}</th>
              <th scope="col">{t('admin.role')}</th>
              <th scope="col">{t('admin.status')}</th>
              <th scope="col">{t('admin.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {users.data.items.map((u) => (
              <UserRow key={u.id} user={u} isMe={u.id === me.data?.id} onNotice={setNotice} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function CreateUser({ onCreated }: { onCreated: (u: User, password: string) => void }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [form, setForm] = useState<CreateUserRequest>(() => ({
    email: '',
    displayName: '',
    role: 'USER',
    temporaryPassword: generatePassword(),
  }))
  const create = useMutation({
    mutationFn: (body: CreateUserRequest) => apiSend<User>('POST', '/api/admin/users', body),
    onSuccess: (u, body) => {
      onCreated(u, body.temporaryPassword)
      setForm({ email: '', displayName: '', role: 'USER', temporaryPassword: generatePassword() })
      void client.invalidateQueries({ queryKey: keys.users })
    },
  })
  const set = (patch: Partial<CreateUserRequest>) => setForm((f) => ({ ...f, ...patch }))

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate(form)
  }

  return (
    <details className="card">
      <summary className="button primary">{t('admin.create')}</summary>
      <form className="form" onSubmit={submit}>
        <label>
          {t('admin.displayName')}
          <input required maxLength={100} value={form.displayName} onChange={(e) => set({ displayName: e.target.value })} />
        </label>
        <label>
          {t('admin.email')}
          <input type="email" required maxLength={254} value={form.email} onChange={(e) => set({ email: e.target.value })} />
        </label>
        <label>
          {t('admin.role')}
          <select value={form.role} onChange={(e) => set({ role: e.target.value as Role })}>
            <option value="USER">{t('admin.roles.USER')}</option>
            <option value="ADMIN">{t('admin.roles.ADMIN')}</option>
          </select>
        </label>
        <label>
          {t('admin.temporaryPassword')}
          <span className="inline">
            <input
              required
              minLength={MIN_PASSWORD_LENGTH}
              aria-describedby="temp-hint"
              value={form.temporaryPassword}
              onChange={(e) => set({ temporaryPassword: e.target.value })}
            />
            <button type="button" className="button secondary" onClick={() => set({ temporaryPassword: generatePassword() })}>
              {t('admin.generate')}
            </button>
          </span>
        </label>
        <p id="temp-hint" className="hint">
          {t('admin.temporaryHint')}
        </p>
        {create.isError && <ErrorMessage error={create.error} />}
        <button type="submit" className="button primary" disabled={create.isPending}>
          {t('admin.create')}
        </button>
      </form>
    </details>
  )
}

function UserRow({ user, isMe, onNotice }: { user: User; isMe: boolean; onNotice: (s: string) => void }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const refresh = () => void client.invalidateQueries({ queryKey: keys.users })

  const reset = useMutation({
    mutationFn: (temporaryPassword: string) =>
      apiSend<User>('POST', `/api/admin/users/${user.id}/password-reset`, { temporaryPassword }),
    onSuccess: (_u, password) => {
      onNotice(`${t('admin.resetDone', { email: user.email })} ${t('admin.temporaryPassword')}: ${password}`)
      refresh()
    },
  })
  const toggle = useMutation({
    mutationFn: () => apiSend<User>('POST', `/api/admin/users/${user.id}/${user.active ? 'deactivate' : 'activate'}`),
    onSuccess: refresh,
  })

  return (
    <tr>
      <td>
        {user.displayName} {isMe && <span className="muted">{t('admin.you')}</span>}
      </td>
      <td>{user.email}</td>
      <td>{t(`admin.roles.${user.role}`)}</td>
      <td>
        {user.active ? t('admin.active') : t('admin.inactive')}
        {user.mustChangePassword && <span className="muted"> ({t('admin.mustChange')})</span>}
      </td>
      <td className="row-actions">
        <button type="button" className="button secondary" onClick={() => {
            if (window.confirm(t('admin.confirmReset', { email: user.email }))) reset.mutate(generatePassword())
          }} disabled={reset.isPending}>
          {t('admin.resetPassword')}
        </button>
        {!isMe && (
          <button
            type="button"
            className="button secondary"
            disabled={toggle.isPending}
            onClick={() => {
              if (!user.active || window.confirm(t('admin.confirmDeactivate', { email: user.email }))) toggle.mutate()
            }}
          >
            {user.active ? t('admin.deactivate') : t('admin.activate')}
          </button>
        )}
        {(reset.isError || toggle.isError) && <ErrorMessage error={reset.error ?? toggle.error} />}
      </td>
    </tr>
  )
}
