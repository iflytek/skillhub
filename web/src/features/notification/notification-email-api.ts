import { WEB_API_PREFIX, fetchJson, getCsrfHeaders } from '@/api/client'
import type { paths } from '@/api/generated/schema'

type GetData<P extends keyof paths> = paths[P] extends { get: { responses: { 200: { content: { '*/*': { data?: infer D } } } } } } ? NonNullable<D> : never
export type EmailContact = GetData<'/api/v1/notification-email-contact'>
export type EmailPreferences = GetData<'/api/v1/notification-email-preferences'>
export type EmailSettings = GetData<'/api/v1/admin/system-config/notification/email'>
export type EmailBatch = GetData<'/api/v1/notification-email-deliveries/{id}'>
export type EmailDeliveryPage = GetData<'/api/v1/admin/notification-email-deliveries'>
export type EmailMode = 'OFF' | 'IMMEDIATE' | 'DIGEST'
export const EMAIL_CATEGORIES = ['PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT'] as const
export type EmailCategory = typeof EMAIL_CATEGORIES[number]

function write<T>(path: string, method: 'POST' | 'PUT', body: unknown) {
  return fetchJson<T>(`${WEB_API_PREFIX}${path}`, { method, headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(body) })
}
export const notificationEmailApi = {
  contact: () => fetchJson<EmailContact>(`${WEB_API_PREFIX}/notification-email-contact`),
  requestCode: (locale: 'zh' | 'en') => write<EmailContact>('/notification-email-contact/verification-code', 'POST', { locale }),
  verify: (code: string) => write<EmailContact>('/notification-email-contact/verify', 'POST', { code }),
  preferences: () => fetchJson<EmailPreferences>(`${WEB_API_PREFIX}/notification-email-preferences`),
  updatePreferences: (preferences: EmailPreferences) => write<EmailPreferences>('/notification-email-preferences', 'PUT', { preferences }),
  settings: () => fetchJson<EmailSettings>(`${WEB_API_PREFIX}/admin/system-config/notification/email`),
  updateSettings: (settings: Pick<EmailSettings, 'enabled' | 'digestWindowMinutes' | 'publicBaseUrl' | 'version'>) => write<EmailSettings>('/admin/system-config/notification/email', 'PUT', settings),
  batch: (id: string, page: number) => fetchJson<EmailBatch>(`${WEB_API_PREFIX}/notification-email-deliveries/${encodeURIComponent(id)}?page=${page}&size=20`),
  deliveries: (page: number, status: string) => fetchJson<EmailDeliveryPage>(`${WEB_API_PREFIX}/admin/notification-email-deliveries?page=${page}&size=20${status ? `&status=${encodeURIComponent(status)}` : ''}`),
  retry: (id: number, confirmDuplicateRisk: boolean) => write<null>(`/admin/notification-email-deliveries/${id}/retry`, 'POST', { confirmDuplicateRisk }),
}
