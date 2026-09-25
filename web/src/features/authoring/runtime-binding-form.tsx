import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authoringApi } from '@/api/client'
import type { RuntimeBindingInfo } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { toast } from '@/shared/lib/toast'

const AGENT_TYPES = ['local-script', 'openai-compatible'] as const

function prettyJson(value: unknown): string {
  return value == null || (Array.isArray(value) && value.length === 0)
    ? ''
    : JSON.stringify(value, null, 2)
}

/**
 * Runtime binding editor: which agent executes behavior tasks, with which
 * configuration, which tools are allowed, and which MCP servers are attached.
 */
export function RuntimeBindingForm({
  draftId,
  binding,
  onSaved,
}: {
  draftId: number
  binding: RuntimeBindingInfo | undefined
  onSaved: (binding: RuntimeBindingInfo) => void
}) {
  const { t } = useTranslation()
  const [agentType, setAgentType] = useState<string>(binding?.agentType ?? 'local-script')
  const [interpreter, setInterpreter] = useState(
    typeof binding?.config?.interpreter === 'string' ? binding.config.interpreter : 'sh',
  )
  const [baseUrl, setBaseUrl] = useState(
    typeof binding?.config?.endpoint === 'string' ? binding.config.endpoint : '',
  )
  const [model, setModel] = useState(
    typeof binding?.config?.model === 'string' ? binding.config.model : '',
  )
  const [toolAllowlist, setToolAllowlist] = useState((binding?.toolAllowlist ?? []).join(', '))
  const [mcpServersJson, setMcpServersJson] = useState(prettyJson(binding?.mcpServers))
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!binding) {
      return
    }
    // the API answers "no binding saved yet" with agentType: null — keep the
    // same local-script default the initializer uses instead of wiping it
    setAgentType(binding.agentType ?? 'local-script')
    setInterpreter(typeof binding.config?.interpreter === 'string' ? binding.config.interpreter : 'sh')
    setBaseUrl(typeof binding.config?.endpoint === 'string' ? binding.config.endpoint : '')
    setModel(typeof binding.config?.model === 'string' ? binding.config.model : '')
    setToolAllowlist((binding.toolAllowlist ?? []).join(', '))
    setMcpServersJson(prettyJson(binding.mcpServers))
  }, [binding])

  const save = async () => {
    let config: Record<string, unknown>
    if (agentType === 'local-script') {
      config = { interpreter: interpreter.trim() || 'sh' }
    } else {
      // the backend validator and adapter read "endpoint" — never send baseUrl
      config = { endpoint: baseUrl.trim(), model: model.trim() }
    }

    let mcpServers: Record<string, unknown>[] = []
    const json = mcpServersJson.trim()
    if (json) {
      try {
        const parsed = JSON.parse(json)
        if (!Array.isArray(parsed)) {
          throw new Error('not an array')
        }
        mcpServers = parsed
      } catch {
        toast.error(t('authoring.binding.invalidMcpJson'))
        return
      }
    }

    setSaving(true)
    try {
      const saved = await authoringApi.saveRuntimeBinding(draftId, {
        agentType,
        config,
        toolAllowlist: toolAllowlist
          .split(',')
          .map((tool) => tool.trim())
          .filter(Boolean),
        mcpServers,
      })
      onSaved(saved)
      toast.success(t('authoring.binding.saved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-2">
        <Label htmlFor="agent-type">{t('authoring.binding.agentType')}</Label>
        <Select value={agentType} onValueChange={setAgentType}>
          <SelectTrigger id="agent-type" data-testid="agent-type">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {AGENT_TYPES.map((type) => (
              <SelectItem key={type} value={type}>
                {t(`authoring.binding.type.${type}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {agentType === 'local-script' ? (
        <div className="grid gap-2">
          <Label htmlFor="interpreter">{t('authoring.binding.interpreter')}</Label>
          <Input
            id="interpreter"
            value={interpreter}
            onChange={(event) => setInterpreter(event.target.value)}
            placeholder="sh"
          />
          <p className="text-xs text-muted-foreground">{t('authoring.binding.interpreterHint')}</p>
        </div>
      ) : (
        <>
          <div className="grid gap-2">
            <Label htmlFor="base-url">{t('authoring.binding.baseUrl')}</Label>
            <Input
              id="base-url"
              value={baseUrl}
              onChange={(event) => setBaseUrl(event.target.value)}
              placeholder="https://api.example.com/v1"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="model">{t('authoring.binding.model')}</Label>
            <Input
              id="model"
              value={model}
              onChange={(event) => setModel(event.target.value)}
              placeholder="gpt-4o-mini"
            />
          </div>
        </>
      )}

      <div className="grid gap-2">
        <Label htmlFor="tool-allowlist">{t('authoring.binding.toolAllowlist')}</Label>
        <Input
          id="tool-allowlist"
          value={toolAllowlist}
          onChange={(event) => setToolAllowlist(event.target.value)}
          placeholder={t('authoring.binding.toolAllowlistPlaceholder')}
        />
        <p className="text-xs text-muted-foreground">{t('authoring.binding.toolAllowlistHint')}</p>
      </div>

      <div className="grid gap-2">
        <Label htmlFor="mcp-servers">{t('authoring.binding.mcpServers')}</Label>
        <Textarea
          id="mcp-servers"
          rows={5}
          className="font-mono text-xs"
          value={mcpServersJson}
          onChange={(event) => setMcpServersJson(event.target.value)}
          placeholder={'[{"name": "docs", "url": "https://mcp.example.com/sse"}]'}
        />
        <p className="text-xs text-muted-foreground">{t('authoring.binding.mcpServersHint')}</p>
      </div>

      <Button onClick={save} disabled={saving} data-testid="save-binding">
        {saving ? t('authoring.binding.saving') : t('authoring.binding.save')}
      </Button>
    </div>
  )
}
