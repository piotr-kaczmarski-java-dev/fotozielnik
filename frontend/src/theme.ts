// Colour theme (NFR-008). The choice lives in the browser (localStorage); index.html applies it before the first
// paint with the same logic, so keep the two in sync.
export type Theme = 'dark' | 'light' | 'system'

const KEY = 'fz-theme'
export const DEFAULT_THEME: Theme = 'dark'

export function storedTheme(): Theme {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'dark' || v === 'light' || v === 'system' ? v : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME // storage blocked (private mode etc.)
  }
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // not persisted; still applied for this page
  }
}
