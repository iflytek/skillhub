import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BatchMemberResponse } from '@/api/types'
import { useBatchAddNamespaceMembers } from '@/shared/hooks/use-namespace-queries'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'

interface BatchImportMembersDialogProps {
  slug: string
  children: React.ReactNode
}

interface ParsedRow {
  userId: string
  role: string
  validation: 'valid' | 'missing_user_id' | 'invalid_role' | 'duplicate'
}

type Step = 'upload' | 'preview' | 'results'

const VALID_ROLES = ['MEMBER', 'ADMIN']

function generateCsvTemplate(): string {
  return 'userId,role\nuser-example-1,MEMBER\nuser-example-2,ADMIN\n'
}

function downloadCsvTemplate() {
  const blob = new Blob([generateCsvTemplate()], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'member-import-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

export function parseCsv(text: string): Array<{ userId: string; role: string }> {
  const lines = text.split(/\r?\n/).filter((line) => line.trim().length > 0)
  if (lines.length === 0) return []

  const firstLine = lines[0].toLowerCase().trim()
  const startIndex = firstLine.includes('userid') || firstLine.includes('user_id') ? 1 : 0

  return lines.slice(startIndex).map((line) => {
    const parts = line.split(',').map((part) => part.trim())
    return { userId: parts[0] || '', role: (parts[1] || '').toUpperCase() }
  })
}

export function validateRows(rows: Array<{ userId: string; role: string }>): ParsedRow[] {
  const seen = new Set<string>()
  return rows.map((row) => {
    if (!row.userId) {
      return { ...row, validation: 'missing_user_id' as const }
    }
    if (!VALID_ROLES.includes(row.role)) {
      return { ...row, validation: 'invalid_role' as const }
    }
    if (seen.has(row.userId)) {
      return { ...row, validation: 'duplicate' as const }
    }
    seen.add(row.userId)
    return { ...row, validation: 'valid' as const }
  })
}

function mapResultError(error: string | undefined, t: (key: string) => string): string {
  if (!error) return t('members.batchResultSuccess')
  switch (error) {
    case 'ALREADY_MEMBER': return t('members.batchResultAlreadyMember')
    case 'USER_NOT_FOUND': return t('members.batchResultUserNotFound')
    case 'INVALID_ROLE': return t('members.batchResultInvalidRole')
    default: return t('members.batchResultUnknownError')
  }
}

export function BatchImportMembersDialog({ slug, children }: BatchImportMembersDialogProps) {
  const { t } = useTranslation()
  const batchMutation = useBatchAddNamespaceMembers()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<Step>('upload')
  const [parsedRows, setParsedRows] = useState<ParsedRow[]>([])
  const [results, setResults] = useState<BatchMemberResponse | null>(null)
  const [dragOver, setDragOver] = useState(false)

  const resetDialog = () => {
    setStep('upload')
    setParsedRows([])
    setResults(null)
    setDragOver(false)
    batchMutation.reset()
  }

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (!nextOpen) resetDialog()
  }

  const processFile = useCallback((file: File) => {
    if (!file.name.endsWith('.csv')) {
      toast.error(t('members.batchParseError'), t('members.batchFormatHint'))
      return
    }
    const reader = new FileReader()
    reader.onload = (event) => {
      const text = event.target?.result as string
      const raw = parseCsv(text)
      if (raw.length === 0) {
        toast.error(t('members.batchEmptyFile'))
        return
      }
      const validated = validateRows(raw)
      setParsedRows(validated)
      setStep('preview')
    }
    reader.readAsText(file)
  }, [t])

  const handleDrop = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    setDragOver(false)
    const file = event.dataTransfer.files[0]
    if (file) processFile(file)
  }, [processFile])

  const handleFileSelect = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) processFile(file)
    event.target.value = ''
  }, [processFile])

  const validRows = parsedRows.filter((row) => row.validation === 'valid')
  const invalidRows = parsedRows.filter((row) => row.validation !== 'valid')

  const handleSubmit = async () => {
    const members = validRows.map((row) => ({ userId: row.userId, role: row.role }))
    try {
      const response = await batchMutation.mutateAsync({ slug, members })
      setResults(response)
      setStep('results')
    } catch (error) {
      toast.error(t('members.addErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const validationLabel = (row: ParsedRow) => {
    switch (row.validation) {
      case 'valid': return t('members.batchValidationValid')
      case 'missing_user_id': return t('members.batchValidationMissingUserId')
      case 'invalid_role': return t('members.batchValidationInvalidRole')
      case 'duplicate': return t('members.batchValidationDuplicate')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle className="text-center">{t('members.batchDialogTitle')}</DialogTitle>
          <DialogDescription className="text-center">
            {t('members.batchDialogDescription')}
          </DialogDescription>
        </DialogHeader>

        {step === 'upload' && (
          <div className="space-y-4 py-2">
            <Button type="button" variant="outline" size="sm" onClick={downloadCsvTemplate}>
              {t('members.batchDownloadTemplate')}
            </Button>
            <div
              role="button"
              tabIndex={0}
              aria-label={t('members.batchDropHint')}
              className={`flex min-h-[120px] cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-6 transition-colors ${
                dragOver ? 'border-primary bg-primary/5' : 'border-border/50 hover:border-primary/50'
              }`}
              onClick={() => fileInputRef.current?.click()}
              onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') fileInputRef.current?.click() }}
              onDragOver={(event) => { event.preventDefault(); setDragOver(true) }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
            >
              <p className="text-sm text-muted-foreground">{t('members.batchDropHint')}</p>
              <p className="text-xs text-muted-foreground mt-1">{t('members.batchFormatHint')}</p>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              className="hidden"
              onChange={handleFileSelect}
              aria-hidden="true"
            />
          </div>
        )}

        {step === 'preview' && (
          <div className="space-y-4 py-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">
                {t('members.batchPreviewTitle', { count: parsedRows.length })}
              </span>
              <span className="text-xs text-muted-foreground">
                {t('members.batchValidRows', { valid: validRows.length, invalid: invalidRows.length })}
              </span>
            </div>
            <div className="max-h-[300px] overflow-auto rounded-lg border border-border/50">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/40 bg-secondary/30">
                    <th className="p-2 text-left font-medium">{t('members.batchColUserId')}</th>
                    <th className="p-2 text-left font-medium">{t('members.batchColRole')}</th>
                    <th className="p-2 text-left font-medium">{t('members.batchColStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {parsedRows.map((row, index) => (
                    <tr key={index} className="border-b border-border/40 last:border-b-0">
                      <td className="p-2 font-mono text-xs">{row.userId || '-'}</td>
                      <td className="p-2">{row.role || '-'}</td>
                      <td className={`p-2 text-xs ${row.validation === 'valid' ? 'text-green-600' : 'text-red-600'}`}>
                        {validationLabel(row)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {step === 'results' && results && (
          <div className="space-y-4 py-2">
            <div className="text-sm font-medium">{t('members.batchResultTitle')}</div>
            <p className="text-sm text-muted-foreground">
              {t('members.batchResultSummary', {
                total: results.totalCount,
                success: results.successCount,
                failure: results.failureCount,
              })}
            </p>
            <div className="max-h-[300px] overflow-auto rounded-lg border border-border/50">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/40 bg-secondary/30">
                    <th className="p-2 text-left font-medium">{t('members.batchColUserId')}</th>
                    <th className="p-2 text-left font-medium">{t('members.batchColRole')}</th>
                    <th className="p-2 text-left font-medium">{t('members.batchColStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {results.results.map((result, index) => (
                    <tr key={index} className="border-b border-border/40 last:border-b-0">
                      <td className="p-2 font-mono text-xs">{result.userId}</td>
                      <td className="p-2">{result.role}</td>
                      <td className={`p-2 text-xs ${result.success ? 'text-green-600' : 'text-red-600'}`}>
                        {result.success ? t('members.batchResultSuccess') : mapResultError(result.error, t)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <DialogFooter className="sm:justify-center sm:space-x-3">
          {step === 'upload' && (
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
          )}
          {step === 'preview' && (
            <>
              <Button type="button" variant="outline" onClick={() => { setParsedRows([]); setStep('upload') }}>
                {t('members.batchBack')}
              </Button>
              <Button
                type="button"
                onClick={handleSubmit}
                disabled={validRows.length === 0 || batchMutation.isPending}
              >
                {batchMutation.isPending
                  ? t('members.batchSubmitting')
                  : t('members.batchSubmit', { count: validRows.length })}
              </Button>
            </>
          )}
          {step === 'results' && (
            <Button type="button" onClick={() => handleOpenChange(false)}>
              {t('members.batchDone')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
