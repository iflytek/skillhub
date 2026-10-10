/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ComplianceSnapshotPanel } from './compliance-snapshot-panel'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, number>) =>
        key === 'compliance.mappingCount'
          ? `${values?.count} mappings`
          : key === 'common.expand'
            ? '展开详情'
            : key === 'common.collapse'
              ? '收起详情'
              : key === 'compliance.title'
                ? '合规声明'
                : key,
    }),
  }
})

describe('ComplianceSnapshotPanel', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders nothing when there are no compliance mappings', () => {
    const html = renderToStaticMarkup(<ComplianceSnapshotPanel snapshot={{ schemaVersion: '1.0', items: [], digest: 'sha256:empty' }} />)

    expect(html).toBe('')
  })

  it('renders a compact summary by default and expands on demand', () => {
    render(
      <ComplianceSnapshotPanel
        snapshot={{
          schemaVersion: '1.0',
          digest: 'sha256:12345678901234567890',
          items: [
            {
              standard: 'mitre-attack',
              version: 'v19.1',
              controlId: 'T1059',
              title: 'Command and Scripting Interpreter',
              evidence: [{ type: 'packaged-file', path: 'references/standards.md', sha256: 'abc' }],
            },
            {
              standard: 'nist-csf',
              version: '2.0',
              controlId: 'PR.DS-01',
              title: 'Data-at-rest protection',
              evidence: [],
            },
            {
              standard: 'soc2',
              version: '2023',
              controlId: 'CC6.1',
              title: 'Logical Access Security',
              evidence: [],
            },
          ],
        }}
      />,
    )

    const toggle = screen.getByRole('button', { name: '展开详情' })
    expect(toggle).toBeTruthy()
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(screen.getByText('mitre-attack · T1059')).toBeTruthy()
    expect(screen.getByText('+1')).toBeTruthy()
    expect(screen.queryByText('references/standards.md')).toBeNull()

    fireEvent.click(toggle)

    const expandedToggle = screen.getByRole('button', { name: '收起详情' })
    expect(expandedToggle.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByText('references/standards.md')).toBeTruthy()
    expect(screen.getByText('soc2')).toBeTruthy()
  })
})
