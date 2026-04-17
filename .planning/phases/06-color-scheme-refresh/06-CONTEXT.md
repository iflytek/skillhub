# Phase 6: color-scheme-refresh - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Переработать color system веб-приложения SkillHub в production-ready вид: semantic token-driven палитра, выровненная light/dark стратегия, контролируемые visual effects и предсказуемый rollout по волнам внутри одной фазы. Scope ограничен visual/theming слоем и не добавляет новые product capabilities.

</domain>

<decisions>
## Implementation Decisions

### Brand palette direction
- **D-01:** Базовый direction: **Enterprise Blue/Slate** (вариант B).
- **D-02:** **Single-accent model**: только `Primary Blue` как action color; `Accent` не конкурирует с CTA.
- **D-03:** Surface/background тон: **cool slate**.
- **D-04:** Visual style: **flat-first**, почти полный отказ от gradient/glow брендинга.

### Semantic token model
- **D-05:** Контракт: **semantic-first** для UI-кода.
- **D-06:** Политика: **strict запрет hardcoded colors** в `web/src/**`: literal `#hex`, `rgb(...)`, `hsl(...)` (с числовыми каналами), forbidden palette utility-классы и inline literal color styles.
  Разрешены только semantic variable-driven значения (`var(--token)`, `hsl(var(--token))`).
  `web/src/index.css` не исключается из strict gate: literal values допустимы только в token-definition регионах между маркерами `/* COLOR_POLICY_ALLOW_START:token-definitions */` и `/* COLOR_POLICY_ALLOW_END:token-definitions */`; вне этих регионов политика идентична остальным файлам.
  Исключения ведутся через формальный allowlist-манифест (`path`, `pattern`, `reason`, `owner`, `approved_by`, `expires_at`) с обязательным expiry.
- **D-06a:** Для `check:colors --mode changed` base-ref resolution обязателен и deterministic: приоритет `--base-ref` -> `CHECK_COLORS_BASE_REF` -> `origin/main` -> `git merge-base HEAD HEAD~1`; при невозможности разрешения — fail с actionable error.
- **D-07:** Обязательные token-группы: `surface`, `content`, `action`, `state`, `stroke`.
- **D-08:** Mapping brand -> semantic: **conservative mapping** (enterprise-blue primary, subdued accent/support).

### Dark mode strategy
- **D-09:** Target: **full light/dark parity now** в рамках Phase 6.
- **D-10:** Quality gate: **screen matrix** для ключевых экранов в light и dark.
- **D-10a:** Parity matrix обязана быть machine-checkable через `check:parity-matrix` (schema, required fields, `artifact_path` existence, `PASS/N/A` decision rules).
- **D-11:** `state` colors в dark: **dedicated dark semantic tokens**.
- **D-12:** UX режима темы: **system + manual toggle** с сохранением пользовательского выбора.
- **D-12a:** `THEME_TOGGLE_RELEASED` — только **UX release flag** (видимость control), не security gate и не policy gate.
- **D-12b:** No-FOUC validation выполняется по deterministic protocol: bootstrap выставляет `data-theme-bootstrap="done"` и `data-theme-mode="<light|dark>"` до React mount; проверки опираются на marker presence, а не на тайминговые эвристики.

### Migration scope & rollout
- **D-13:** Rollout: **staged in-phase** (несколько волн внутри одной фазы).
- **D-14:** Wave 1 scope: **theme core only** (tokens + theme infra + policy gates + primitive contracts/tests).
  Полная visual migration `button/card/input/tabs` intentionally переносится в Wave 2, чтобы Wave 1 завершался на стабильном foundation и проверяемых контрактах без смешения миграционных рисков.
- **D-15:** Порядок Wave 2/Wave 3: **the agent's discretion**.
- **D-16:** Phase completion gate: **strict completion** (все запланированные волны + parity checks + accessibility gate для contrast/focus/keyboard).

### Effects cleanup
- **D-17:** Motion baseline: **balanced motion** с machine-checkable limits: decorative infinite motion запрещён на product-critical surfaces; transitions для interactive UI в диапазоне `100-300ms`.
- **D-18:** Decorative effects: разрешены глобально, но **toned-down**.
- **D-19:** Glow/shadow: **strict intensity cap** на token-уровне: `--effect-shadow-alpha-max <= 0.24`, `--effect-glow-alpha-max <= 0.18`, `--effect-blur-max <= 16px`; нарушение блокируется `check:motion`.
- **D-20:** Legacy effect utility-классы (`glow-orb-*`, `feature-icon`, `animate-float` и т.п.): **удаляются сразу** с перепривязкой usage в этой фазе.

### the agent's Discretion
- Sequencing для Wave 2/Wave 3 (при соблюдении strict completion gate и согласованного scope).
- Точный набор технических исключений для hardcoded colors (если они относятся к статическим media assets, а не UI surface).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone contracts
- `.planning/ROADMAP.md` - Phase 6 goal and dependency boundary.
- `.planning/PROJECT.md` - milestone context and scope constraints.
- `.planning/REQUIREMENTS.md` - existing product constraints to preserve while changing visual layer.
- `TODO.md` - source intent for replacing "AI slop" palette with a coherent production scheme.

### Phase and UX history
- `.planning/phases/03-web-ui/03-CONTEXT.md` - established web UX decisions that must remain behaviorally intact.
- `.planning/phases/05-ux-add-visible-skills/05-CONTEXT.md` - latest collection UX contract that theme refresh must not regress.
- `.planning/phases/03-web-ui/03-UI-SPEC.md` - prior UI quality constraints and color usage guidance.
- `.planning/phases/04-verification-docs/04-UI-REVIEW.md` - known UI quality gaps, including color consistency issues.

### Theme implementation anchors
- `web/src/index.css` - current token definitions, custom utilities, and effect classes.
- `web/tailwind.config.ts` - semantic color wiring, shadows, animations, and design token exposure.
- `web/src/app/layout.tsx` - app shell styling patterns and global visual entry points.
- `web/src/pages/landing.tsx` - marketing-heavy gradient/effects usage needing cleanup decisions.
- `web/LANDING_PAGE_REDESIGN.md` - documented visual intent of current landing redesign.

### Discuss-phase artifacts
- `.planning/phases/06-color-scheme-refresh/06-color-variants.html` - visual palette options reviewed and used for direction selection.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/index.css`: central place for CSS variables and shared utility classes; natural anchor for token normalization.
- `web/tailwind.config.ts`: already maps semantic tokens (`background`, `foreground`, `primary`, etc.) into Tailwind utilities.
- Shared UI primitives (`web/src/shared/ui/button.tsx`, `card.tsx`, `input.tsx`, `dialog.tsx`) can carry most color semantics once tokens are stabilized.

### Established Patterns
- Mixed styling model: semantic token classes coexist with hardcoded utility colors and inline styles.
- Existing `.dark` token block exists but parity is incomplete due to non-semantic color usage in pages/components.
- Motion/effects currently combine functional transitions with decorative glow/gradient utilities.

### Integration Points
- Theme refactor must propagate through app shell (`layout.tsx`), dashboard pages, collections flows, search and landing.
- Enforce rules should target `web/src/**` to prevent reintroduction of hardcoded UI colors.
- Light/dark parity validation should be executed at page level, not only component story level.

</code_context>

<specifics>
## Specific Ideas

- Пользователь запросил визуальный выбор палитры через отдельную HTML-страницу; создан и использован артефакт `.planning/phases/06-color-scheme-refresh/06-color-variants.html`.
- Зафиксирован explicit курс на enterprise visual tone и отказ от "AI slop" aesthetic.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 06-color-scheme-refresh*
*Context gathered: 2026-04-17*
