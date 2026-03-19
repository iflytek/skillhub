import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Select } from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { useAuditLog } from '@/features/admin/use-audit-log'

const ACTION_OPTIONS = [
  { value: '', labelKey: 'auditLog.filterAll' },
  { value: 'CLI_PUBLISH', labelKey: 'auditLog.filterCliPublish' },
  { value: 'COMPAT_PUBLISH', labelKey: 'auditLog.filterCompatPublish' },
  { value: 'REVIEW_SUBMIT', labelKey: 'auditLog.filterReviewSubmit' },
  { value: 'REVIEW_APPROVE', labelKey: 'auditLog.filterReviewApprove' },
  { value: 'REVIEW_REJECT', labelKey: 'auditLog.filterReviewReject' },
  { value: 'PROMOTION_SUBMIT', labelKey: 'auditLog.filterPromotionSubmit' },
  { value: 'PROMOTION_APPROVE', labelKey: 'auditLog.filterPromotionApprove' },
  { value: 'PROMOTION_REJECT', labelKey: 'auditLog.filterPromotionReject' },
  { value: 'REPORT_SKILL', labelKey: 'auditLog.filterReportSkill' },
  { value: 'RESOLVE_SKILL_REPORT', labelKey: 'auditLog.filterResolveSkillReport' },
  { value: 'DISMISS_SKILL_REPORT', labelKey: 'auditLog.filterDismissSkillReport' },
  { value: 'HIDE_SKILL', labelKey: 'auditLog.filterHideSkill' },
  { value: 'ARCHIVE_SKILL', labelKey: 'auditLog.filterArchiveSkill' },
  { value: 'UNHIDE_SKILL', labelKey: 'auditLog.filterUnhideSkill' },
  { value: 'UNARCHIVE_SKILL', labelKey: 'auditLog.filterUnarchiveSkill' },
  { value: 'YANK_SKILL_VERSION', labelKey: 'auditLog.filterYankVersion' },
  { value: 'REBUILD_SEARCH_INDEX', labelKey: 'auditLog.filterRebuildSearchIndex' },
] as const

/**
 * Admin audit log page with server-backed filtering. The route owns filter state
 * because the query model maps almost one-to-one to the backend search API.
 */
export function AuditLogPage() {
  const { t, i18n } = useTranslation()
  const [actionFilter, setActionFilter] = useState<string>('')
  const [userIdFilter, setUserIdFilter] = useState('')
  const [requestIdFilter, setRequestIdFilter] = useState('')
  const [ipFilter, setIpFilter] = useState('')
  const [resourceTypeFilter, setResourceTypeFilter] = useState('')
  const [resourceIdFilter, setResourceIdFilter] = useState('')
  const [startTimeFilter, setStartTimeFilter] = useState('')
  const [endTimeFilter, setEndTimeFilter] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useAuditLog({
    action: actionFilter || undefined,
    userId: userIdFilter || undefined,
    requestId: requestIdFilter || undefined,
    ipAddress: ipFilter || undefined,
    resourceType: resourceTypeFilter || undefined,
    resourceId: resourceIdFilter || undefined,
    startTime: startTimeFilter ? new Date(startTimeFilter).toISOString() : undefined,
    endTime: endTimeFilter ? new Date(endTimeFilter).toISOString() : undefined,
    page,
    size: 20,
  })

  const formatDate = (dateString: string) => {
    return formatLocalDateTime(dateString, i18n.language)
  }

  const applySearchIndexRebuildFilter = () => {
    setActionFilter('REBUILD_SEARCH_INDEX')
    setResourceTypeFilter('SEARCH_INDEX')
    setPage(0)
  }

  const clearFilters = () => {
    setActionFilter('')
    setUserIdFilter('')
    setRequestIdFilter('')
    setIpFilter('')
    setResourceTypeFilter('')
    setResourceIdFilter('')
    setStartTimeFilter('')
    setEndTimeFilter('')
    setPage(0)
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">{t('auditLog.title')}</h1>
        <p className="text-muted-foreground text-lg">{t('auditLog.subtitle')}</p>
      </div>

      <Card className="p-5">
        <div className="mb-4 flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" onClick={applySearchIndexRebuildFilter}>
            {t('auditLog.quickFilterSearchRebuild')}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={clearFilters}>
            {t('auditLog.clearFilters')}
          </Button>
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Select value={actionFilter} onChange={(e) => {
            setActionFilter(e.target.value)
            setPage(0)
          }} className="w-[200px]">
            {ACTION_OPTIONS.map((option) => (
              <option key={option.value || 'all'} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </Select>
          <Input
            placeholder={t('auditLog.userIdPlaceholder')}
            value={userIdFilter}
            onChange={(e) => {
              setUserIdFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            placeholder={t('auditLog.requestIdPlaceholder')}
            value={requestIdFilter}
            onChange={(e) => {
              setRequestIdFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            placeholder={t('auditLog.ipPlaceholder')}
            value={ipFilter}
            onChange={(e) => {
              setIpFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            placeholder={t('auditLog.resourceTypePlaceholder')}
            value={resourceTypeFilter}
            onChange={(e) => {
              setResourceTypeFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            placeholder={t('auditLog.resourceIdPlaceholder')}
            value={resourceIdFilter}
            onChange={(e) => {
              setResourceIdFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            type="datetime-local"
            value={startTimeFilter}
            onChange={(e) => {
              setStartTimeFilter(e.target.value)
              setPage(0)
            }}
          />
          <Input
            type="datetime-local"
            value={endTimeFilter}
            onChange={(e) => {
              setEndTimeFilter(e.target.value)
              setPage(0)
            }}
          />
        </div>
      </Card>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-14 animate-shimmer rounded-lg" />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <Card className="p-12 text-center">
          <p className="text-muted-foreground">{t('auditLog.empty')}</p>
        </Card>
      ) : (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('auditLog.colTime')}</TableHead>
                  <TableHead>{t('auditLog.colAction')}</TableHead>
                  <TableHead>{t('auditLog.colUserId')}</TableHead>
                  <TableHead>{t('auditLog.colUsername')}</TableHead>
                  <TableHead>{t('auditLog.colIp')}</TableHead>
                  <TableHead>{t('auditLog.colDetail')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>{formatDate(log.timestamp)}</TableCell>
                    <TableCell className="font-medium">{log.action}</TableCell>
                    <TableCell>{log.userId || '-'}</TableCell>
                    <TableCell>{log.username || '-'}</TableCell>
                    <TableCell>{log.ipAddress || '-'}</TableCell>
                    <TableCell className="max-w-md truncate">
                      {log.details || `${log.resourceType || '-'}:${log.resourceId || '-'}`}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          <div className="flex justify-between items-center">
            <p className="text-sm text-muted-foreground">
              {t('auditLog.totalRecords', { total: data.total, page: page + 1 })}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                {t('auditLog.prevPage')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={(page + 1) * 20 >= data.total}
                onClick={() => setPage(page + 1)}
              >
                {t('auditLog.nextPage')}
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
