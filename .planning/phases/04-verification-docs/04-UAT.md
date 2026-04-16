---
status: deferred
phase: 04-verification-docs
policy: milestone_acceptance
deferred_to: "milestone v1.0 (after phases 05–06)"
updated: 2026-04-17
source:
  - .planning/phases/04-verification-docs/04-01-SUMMARY.md
  - .planning/phases/04-verification-docs/04-02-SUMMARY.md
  - .planning/phases/04-verification-docs/04-03-SUMMARY.md
---

# Phase 04 — UAT (отложено)

Пошаговый UAT по фазе 04 **не выполняется отдельно**. Решение: сфокусироваться на фазах **05** и **06**, а **приёмку вехи v1.0** провести **одним блоком после завершения фаз 5–6** (включая сценарии коллекций из списка ниже, если они ещё актуальны).

Инженерная готовность фазы 04 по-прежнему зафиксирована в **`04-VERIFICATION.md`** (`status: passed`).

## Чеклист для финальной приёмки вехи (перенос из прежнего UAT)

Ниже — бывшие пункты UAT; при закрытии вехи прогнать/проверить и отметить результат в отдельном артефакте (например `MILESTONE-UAT.md` или обновление `STATE.md`).

1. **E2E** — `cd web && pnpm exec playwright test e2e/collections-flow.spec.ts --config=playwright.config.ts`
2. **E2E** — `cd web && pnpm exec playwright test e2e/collections-visibility-guard.spec.ts --config=playwright.config.ts`
3. **Доки API** — ZH + EN `authenticated.md`: маршруты коллекций, роли, ожидания по private / not-found.
4. **Runbook** — `docs/e2e.md`: команды и упоминание collection spec + CI.
5. **Backend** — таргетные тесты: `SkillCollectionSecurityIT`, `SkillCollectionDomainServiceTest` (команды из `04-01-SUMMARY.md`).

## Summary (формально)

| Поле | Значение |
|------|----------|
| total | 5 |
| passed | — (перенесено на приёмку вехи) |
| deferred | 5 |
| skipped (per-phase) | 5 |

## Gaps

Нет открытых зазоров по фазе 04, кроме сознательно перенесённой ручной приёмки на конец вехи.
