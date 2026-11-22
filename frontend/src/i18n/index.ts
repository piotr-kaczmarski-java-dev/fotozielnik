import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import pl from './pl.json'

// Polish only for now (NFR-009); every UI text lives in pl.json. Sync init: resources are bundled.
void i18n.use(initReactI18next).init({
  resources: { pl: { translation: pl } },
  lng: 'pl',
  fallbackLng: 'pl',
  initAsync: false,
  interpolation: { escapeValue: false }, // React escapes already
})

export default i18n
