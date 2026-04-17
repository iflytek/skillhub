# Phase 04 — UI Review

**Audited:** 2026-04-16
**Baseline:** abstract standards
**Screenshots:** not captured (no dev server on localhost:3000/5173/8080)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Тексты в UI в основном через i18n-ключи, но E2E местами жестко привязан к английским literal-лейблам. |
| 2. Visuals | 2/4 | По коду иерархия выглядит структурно корректно, но визуальная проверка не выполнена из-за отсутствия dev server. |
| 3. Color | 2/4 | Выраженное смешение token-based стилей и hardcoded цветов (hex/rgb/inline). |
| 4. Typography | 2/4 | В проекте одновременно используется 9 размеров текста и 4 веса шрифта. |
| 5. Spacing | 2/4 | Много arbitrary spacing/size значений (`[...]`) вместо единой scale. |
| 6. Experience Design | 3/4 | Хорошее покрытие loading/error/empty и confirm для destructive actions в коллекциях. |

**Overall: 14/24**

---

## Top 3 Priority Fixes

1. **Свернуть hardcoded palette в design tokens** — сейчас цвета задаются разрозненно и повышают риск визуального дрейфа — вынести inline/hex в `index.css` custom properties и использовать только token classes в компонентах.
2. **Нормализовать typography contract (size/weight)** — текущая сетка перегружена и усложняет визуальную иерархию — оставить целевой набор (например, 4 размера и 2 веса) и привести страницы/компоненты к нему.
3. **Убрать arbitrary spacing/size значения из критичных экранов** — несистемные `w-[...]`, `text-[...]`, `rounded-[...]` ухудшают консистентность — заменить на масштабируемые Tailwind tokens (`p-*`, `gap-*`, `text-*`, `rounded-*`).

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)
- UI-копирайтинг в коллекциях строится через переводные ключи (`t(...)`), что снижает риск hardcoded copy drift: `web/src/pages/dashboard/collection-new.tsx:74`, `web/src/pages/dashboard/collection-edit.tsx:102`, `web/src/pages/dashboard/collection-detail.tsx:69`.
- Для приватного guard-case зафиксирован недвусмысленный not-found UX в E2E: `web/e2e/collections-visibility-guard.spec.ts:66`.
- Наблюдение: часть E2E использует точные английские лейблы (`Create`, `Add contributor`, `Remove`), что делает тесты чувствительными к редактуре копирайта: `web/e2e/collections-flow.spec.ts:69`, `web/e2e/collections-flow.spec.ts:102`, `web/e2e/collections-flow.spec.ts:108`.

### Pillar 2: Visuals (2/4)
- Код страниц коллекций показывает ожидаемую визуальную иерархию (page header → section cards → action groups): `web/src/pages/dashboard/collections-list.tsx:22`, `web/src/pages/dashboard/collection-detail.tsx:94`, `web/src/pages/public/public-collection-page.tsx:86`.
- Для icon-only reorder actions присутствуют `aria-label`, что снижает a11y-риск: `web/src/features/collection/collection-skill-rows.tsx:76`, `web/src/features/collection/collection-skill-rows.tsx:86`.
- Ограничение аудита: скриншоты не сняты (dev server отсутствует), поэтому нельзя подтвердить фактические визуальные пропорции/контраст/адаптивность на viewport-ах.

### Pillar 3: Color (2/4)
- Accent-классы (`text-primary|bg-primary|border-primary`) используются часто: `ACCENT_MATCH_COUNT=51`, `ACCENT_UNIQUE_FILES=30` (code-only scan `web/src`).
- В TSX остаются hardcoded цвета и inline styles: `web/src/app/layout.tsx:135`, `web/src/pages/skill-detail.tsx:751`, `web/src/pages/skill-detail.tsx:756`, `web/src/shared/components/quick-start.tsx:37`.
- В CSS также много явных hex-значений (например, статусы и бренд-градиенты): `web/src/index.css:40`, `web/src/index.css:344`, `web/src/index.css:389`.

### Pillar 4: Typography (2/4)
- Обнаружено 9 distinct text sizes: `text-xs/sm/base/lg/xl/2xl/3xl/4xl/5xl`.
- Обнаружено 4 distinct font weights: `font-normal/medium/semibold/bold`.
- Распределение показывает явный перекос в мелкие размеры, но с широким хвостом редких вариантов (риск фрагментации визуальной системы): `web/src/pages/skill-detail.tsx:761`, `web/src/pages/public/public-collection-page.tsx:80`, `web/src/shared/components/legal-document.tsx:1` (использует `text-5xl` по scan).

### Pillar 5: Spacing (2/4)
- Частые spacing-токены в норме (`space-y-2`, `gap-2`, `space-y-4`), но есть много arbitrary значений.
- Зафиксировано `ARBITRARY_SPACING_AND_SIZE_COUNT=31` в `web/src` (`[...px]`, `[...rem]` и схожие произвольные классы).
- Примеры: `web/src/app/layout.tsx:66`, `web/src/features/skill/code-renderer.tsx:40`, `web/src/features/skill/markdown-renderer.tsx:44`, `web/src/features/skill/version-status-badge.tsx:47`.

### Pillar 6: Experience Design (3/4)
- В коллекциях покрыты loading/error/empty состояния: `web/src/pages/dashboard/collection-detail.tsx:89`, `web/src/pages/dashboard/collection-detail.tsx:90`, `web/src/pages/dashboard/collection-detail.tsx:128`, `web/src/pages/public/public-collection-page.tsx:29`, `web/src/pages/public/public-collection-page.tsx:37`.
- Destructive действие удаления contributor защищено подтверждением: `web/src/pages/dashboard/collection-detail.tsx:198`.
- Верификация anti-leak сценария для приватной коллекции присутствует в E2E: `web/e2e/collections-visibility-guard.spec.ts:67`, `web/e2e/collections-visibility-guard.spec.ts:68`.

---

## Files Audited
- `.planning/phases/04-verification-docs/04-01-SUMMARY.md`
- `.planning/phases/04-verification-docs/04-02-SUMMARY.md`
- `.planning/phases/04-verification-docs/04-03-SUMMARY.md`
- `.planning/phases/04-verification-docs/04-01-PLAN.md`
- `.planning/phases/04-verification-docs/04-02-PLAN.md`
- `.planning/phases/04-verification-docs/04-03-PLAN.md`
- `.planning/phases/04-verification-docs/04-CONTEXT.md`
- `web/e2e/collections-flow.spec.ts`
- `web/e2e/collections-visibility-guard.spec.ts`
- `web/src/pages/dashboard/collections-list.tsx`
- `web/src/pages/dashboard/collection-new.tsx`
- `web/src/pages/dashboard/collection-edit.tsx`
- `web/src/pages/dashboard/collection-detail.tsx`
- `web/src/pages/public/public-collection-page.tsx`
- `web/src/features/collection/collection-share-actions.tsx`
- `web/src/features/collection/add-collection-contributor-dialog.tsx`
- `web/src/features/collection/collection-skill-rows.tsx`
- `web/src/app/layout.tsx`
- `web/src/shared/components/quick-start.tsx`
- `web/src/pages/skill-detail.tsx`
- `web/src/index.css`
- `docs/e2e.md`
- `document/docs/04-developer/api/authenticated.md`
- `document/i18n/en/docusaurus-plugin-content-docs/current/04-developer/api/authenticated.md`
