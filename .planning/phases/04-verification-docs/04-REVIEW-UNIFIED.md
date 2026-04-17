---
phase: 04-verification-docs
title: "Phase 04 — единый отчёт ревью"
generated: 2026-04-17
sources:
  - id: gsd
    file: 04-REVIEW.md
    note: "gsd-code-reviewer, depth standard, 8 files"
  - id: codex
    file: 04-REVIEW-codex.md
    note: "codex review, gpt-5.3-codex"
  - id: opencode
    file: 04-REVIEW-opencode.md
    note: "не завершено (таймаут CLI)"
  - id: qwen
    file: 04-REVIEW-qwen.md
    note: "не выполнено (401 API при OK auth status)"
scope_files: 8
findings_merged:
  critical: 0
  warning: 4
  info: 5
  meta: 2
  total_code_related: 9
status: issues
---

# Phase 04 — единый список замечаний

Консолидация результатов **`04-REVIEW.md`** (GSD), **`04-REVIEW-codex.md`** (Codex), плюс статусы **OpenCode** и **Qwen** (отдельные заметки без находок по коду). Один и тот же смысловой дефект из разных источников **слит в одну строку**; в колонке «Источники» перечислены все совпавшие отчёты.

## Обзор

| Уровень | Кол-во (уникальных тем) |
|--------|---------------------------|
| Critical | 0 |
| Warning | 4 |
| Info | 5 |
| Мета (инструменты, не код) | 2 |

**Область ревью (8 файлов):** см. `files_reviewed_list` в `04-REVIEW.md`.

---

## Сводная таблица замечаний

| ID | Уровень | Кратко | Файлы / зона | Источники |
|----|---------|--------|--------------|-----------|
| **U-W-01** | warning | В доке указан неверный **публичный** HTTP-маршрут коллекции (`/api/web/users/...` вместо `/api/web/public/collections/...` и аналога v1). | `document/docs/04-developer/api/authenticated.md`, EN mirror | GSD WR-001; Codex WR-CODEX-02 |
| **U-W-02** | warning | В доке указан неверный маршрут **списка** коллекций: `GET /api/web/collections` вместо **`GET /api/web/me/collections`** (список «мои» vs get-by-id). | те же `authenticated.md` | Codex WR-CODEX-01 |
| **U-W-03** | warning | Вторичная сессия в Playwright: **`createFreshSession(secondaryPage)` без `testInfo`** → `parallelIndex` по умолчанию 0, риск кеша/флаков при параллельных воркерах. | `web/e2e/collections-flow.spec.ts` (~L29) | Codex WR-CODEX-03 |
| **U-W-04** | warning | Удаление контрибьютора: два клика по **Remove** через `.first()` / `.nth(1)` без привязки к строке — хрупко при смене порядка UI. | `web/e2e/collections-flow.spec.ts` (~L108–110) | GSD WR-002 |
| **U-I-01** | info | `docs/e2e.md`: дерево каталога и число spec **устарели** относительно фактического `web/e2e`. | `docs/e2e.md` | GSD IN-001 |
| **U-I-02** | info | `SkillCollectionSecurityIT`: вспомогательные assert’ы допускают несколько статусов/форм ответа → **слабее регрессия** при стабилизации контракта API. | `SkillCollectionSecurityIT.java` (~L559–607) | GSD IN-002 |
| **U-I-03** | info | Guard E2E: заголовок **только на английском** (`Collection not found`) — ломается при локали/смене копирайта. | `collections-visibility-guard.spec.ts` (~L66) | GSD IN-003 |
| **U-I-04** | info | `document/package.json`: **override** на `webpack` — зафиксировать причину (CVE/баг) и план пересмотра при апгрейде Docusaurus. | `document/package.json` | GSD IN-004 |
| **U-I-05** | info | Сессия Codex ушла в широкий обход репозитория (`git diff`, чтение несвязанных UI-файлов) — для узкого ревью лучше **жёстко ограничить** дифф/вложения. | процесс ревью | Codex (раздел Info) |
| **U-M-01** | meta | **OpenCode:** прогон не завершён (таймаут CLI / нет вывода в лог). | — | `04-REVIEW-opencode.md` |
| **U-M-02** | meta | **Qwen:** ревью кода не выполнено (`401` на API при «OK» в `qwen auth status`). | — | `04-REVIEW-qwen.md` |

---

## Рекомендации по приоритету (код и доки)

1. **U-W-01, U-W-02** — исправить таблицу маршрутов в **обоих** `authenticated.md` (ZH + EN), сверить с `SkillCollectionController` / `PublicSkillCollectionController` и `RouteSecurityPolicyRegistry`.
2. **U-W-03** — передать **`testInfo`** в `createFreshSession` для вторичной страницы (сигнатура уже поддерживает `testInfo?` в `session.ts`).
3. **U-W-04** — заменить порядковые клики на **стабильный селектор** (строка таблицы / `data-testid`).
4. **U-I-01–U-I-04** — по очереди: актуализировать runbook, ужесточить assert’ы когда зафиксируете контракт API, локализуемый/стабильный assert для not-found, документировать pin `webpack`.

---

## Ссылки на исходные отчёты

| Файл | Содержание |
|------|------------|
| `04-REVIEW.md` | GSD code review (детализация WR/IN) |
| `04-REVIEW-codex.md` | Codex (маршруты + `testInfo`) |
| `04-REVIEW-codex.raw.txt` | Полный транскрипт Codex |
| `04-REVIEW-opencode.md` | Статус OpenCode |
| `04-REVIEW-qwen.md` | Статус Qwen / 401 |

После появления результатов **OpenCode** и **Qwen** этот файл можно дополнить новыми строками в таблице (сохранив префиксы `U-*` или введя под-номера).
