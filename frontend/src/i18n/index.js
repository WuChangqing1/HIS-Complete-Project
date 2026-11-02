import Vue from 'vue'
import elementLocale from 'element-ui/lib/locale'
import en from 'element-ui/lib/locale/lang/en'
import zh from 'element-ui/lib/locale/lang/zh-CN'
import messages from './en.json'

function savedLanguage() {
  try { return localStorage.getItem('his_language') === 'en' ? 'en' : 'zh' } catch { return 'zh' }
}
export const languageState = Vue.observable({ locale: savedLanguage() })
const escapeRegex = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const phrases = new RegExp(Object.keys(messages).sort((a, b) => b.length - a.length).map(escapeRegex).join('|'), 'g')

// Translation applies to presentation only. Submitted values remain unchanged.
export function t(value, params = {}) {
  if (typeof value !== 'string') return value
  let text = value
  if (languageState.locale === 'en') {
    text = Object.prototype.hasOwnProperty.call(messages, value) ? messages[value] : value.replace(phrases, match => messages[match])
  }
  return text.replace(/\{(\w+)\}/g, (placeholder, key) => Object.prototype.hasOwnProperty.call(params, key) ? String(params[key]) : placeholder)
}
function applyLanguage() {
  elementLocale.use(languageState.locale === 'en' ? en : zh)
  document.documentElement.lang = languageState.locale === 'en' ? 'en' : 'zh-CN'
  document.title = languageState.locale === 'en' ? 'Neusoft Cloud HIS · Outpatient Management' : '东软云 HIS · 门诊管理'
}
export function setLanguage(locale) {
  if (locale !== 'zh' && locale !== 'en') return
  languageState.locale = locale
  try { localStorage.setItem('his_language', languageState.locale) } catch { /* Storage may be unavailable. */ }
  applyLanguage()
}
export function installLanguage() {
  applyLanguage()
  Vue.mixin({ methods: { $t: t, localizedCell(row, column, value) { return t(value) } } })
  const originalMessage = Vue.prototype.$message
  const translateOptions = options => {
    if (typeof options === 'string') return t(options)
    if (!options || typeof options !== 'object') return options
    const result = { ...options }
    for (const key of ['message', 'title', 'confirmButtonText', 'cancelButtonText', 'inputErrorMessage']) {
      if (typeof result[key] === 'string') result[key] = t(result[key])
    }
    return result
  }
  const message = options => originalMessage(translateOptions(options))
  for (const kind of ['success', 'warning', 'info', 'error']) message[kind] = options => originalMessage[kind](translateOptions(options))
  message.closeAll = originalMessage.closeAll
  Vue.prototype.$message = message
  for (const kind of ['$confirm', '$prompt', '$alert']) {
    const original = Vue.prototype[kind]
    Vue.prototype[kind] = function (message, title, options) {
      return original.call(this, t(message), translateOptions(title), translateOptions(options))
    }
  }
}
