import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
    return new Date(dateString).toLocaleString(i18n.language)
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">{t('auditLog.title')}</h1>
        <p className="text-muted-foreground text-lg">{t('auditLog.subtitle')}</p>
      </div>

      <Card className="p-5">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Select value={actionFilter} onChange={(e) => {
            setActionFilter(e.target.value)
            setPage(0)
          }} className="w-[200px]">
            <option value="">{t('auditLog.filterAll')}</option>
            <option value="CLI_PUBLISH">{t('auditLog.filterCliPublish')}</option>
            <option value="COMPAT_PUBLISH">{t('auditLog.filterCompatPublish')}</option>
            <option value="REVIEW_APPROVE">{t('auditLog.filterReviewApprove')}</option>
            <option value="REVIEW_REJECT">{t('auditLog.filterReviewReject')}</option>
            <option value="PROMOTION_APPROVE">{t('auditLog.filterPromotionApprove')}</option>
            <option value="YANK_SKILL_VERSION">{t('auditLog.filterYankVersion')}</option>
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
