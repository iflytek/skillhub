# Phase 6: color-scheme-refresh - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-04-17
**Phase:** 06-color-scheme-refresh
**Areas discussed:** Brand palette direction, Semantic token model, Dark mode strategy, Migration scope & rollout, Effects cleanup

---

## Brand palette direction

| Option | Description | Selected |
|--------|-------------|----------|
| Refined Indigo/Violet | Эволюция текущего бренда с меньшей насыщенностью | |
| Enterprise Blue/Slate | Корпоративно-нейтральная и стабильная палитра | ✓ |
| Teal/Blue Modern | Более свежий стиль с большим visual drift риском | |
| Свой вариант | Freeform выбор | |

**User's choice:** Enterprise Blue/Slate.
**Notes:** Для оценки вариантов был запрошен и создан HTML-превью артефакт.

| Option | Description | Selected |
|--------|-------------|----------|
| Single-accent | Только primary как action color | ✓ |
| Dual-accent soft | Два акцента с мягким вторым тоном | |
| Dual-accent vivid | Выразительный второй акцент | |
| You decide | Оставить решение агенту | |

**User's choice:** Single-accent.

| Option | Description | Selected |
|--------|-------------|----------|
| Cool slate | Холодные нейтральные поверхности | ✓ |
| Warm neutral | Более теплые нейтралы | |
| Mixed | Смешанная стратегия | |
| You decide | Оставить решение агенту | |

**User's choice:** Cool slate.

| Option | Description | Selected |
|--------|-------------|----------|
| Минимизировать | Ограничить gradient/glow | |
| Оставить умеренно | Сохранить умеренный декор | |
| Почти везде flat | Почти полный отказ от gradient/glow | ✓ |
| You decide | Оставить решение агенту | |

**User's choice:** Flat-first.

---

## Semantic token model

| Option | Description | Selected |
|--------|-------------|----------|
| Semantic-first | Только semantic tokens в UI-коде | ✓ |
| Hybrid | Semantic + ограниченный raw доступ | |
| Utility-color-first | Свободное использование ad-hoc цветов | |
| You decide | Оставить решение агенту | |

**User's choice:** Semantic-first.

| Option | Description | Selected |
|--------|-------------|----------|
| Strict | Hard gate против hardcoded colors | ✓ |
| Moderate | Запрет только для нового кода | |
| Soft | Только guideline | |
| You decide | Оставить решение агенту | |

**User's choice:** Strict enforcement.

| Option | Description | Selected |
|--------|-------------|----------|
| Полный минимум | surface/content/action/state/stroke | ✓ |
| Без state-группы | Минимальный core без status tokens | |
| Расширенный сразу | Сразу добавить extended token sets | |
| You decide | Оставить решение агенту | |

**User's choice:** Полный минимум групп.

| Option | Description | Selected |
|--------|-------------|----------|
| Conservative mapping | Сдержанный бренд-to-semantic mapping | ✓ |
| Closer brand mapping | Более выраженный accent | |
| Monotone action mapping | Почти монохромный blue/slate | |
| You decide | Оставить решение агенту | |

**User's choice:** Conservative mapping.

---

## Dark mode strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Full parity now | Полная parity light/dark в этой фазе | ✓ |
| Light-first + dark baseline | Light как приоритет, dark базово | |
| Light-only for now | Только light target в этой фазе | |
| You decide | Оставить решение агенту | |

**User's choice:** Full parity now.

| Option | Description | Selected |
|--------|-------------|----------|
| Gate-by-screens | Обязательный screen matrix | ✓ |
| Gate-by-components | Проверка в основном на уровне компонентов | |
| Visual smoke only | Легкий ручной прогон | |
| You decide | Оставить решение агенту | |

**User's choice:** Gate-by-screens.

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated dark tokens | Отдельные dark значения для state | ✓ |
| Shared values | Одинаковые значения для light/dark | |
| Per-component tuning | Локальный подбор по компонентам | |
| You decide | Оставить решение агенту | |

**User's choice:** Dedicated dark state tokens.

| Option | Description | Selected |
|--------|-------------|----------|
| System + manual toggle | Системная тема плюс user switch | ✓ |
| System-only | Только auto follow system | |
| Manual-only | Только ручной режим | |
| You decide | Оставить решение агенту | |

**User's choice:** System + manual toggle.

---

## Migration scope & rollout

| Option | Description | Selected |
|--------|-------------|----------|
| Staged in-phase | 2-3 волны внутри одной фазы | ✓ |
| Big-bang | Один большой проход | |
| Minimal slice | Ограниченный partial scope | |
| You decide | Оставить решение агенту | |

**User's choice:** Staged in-phase.

| Option | Description | Selected |
|--------|-------------|----------|
| Theme core only | Tokens + theme infra + base primitives | ✓ |
| Theme core + layout shell | Плюс app shell | |
| Theme core + landing | Плюс landing | |
| You decide | Оставить решение агенту | |

**User's choice:** Wave 1 = Theme core only.

| Option | Description | Selected |
|--------|-------------|----------|
| Wave 2 business, Wave 3 rest | Приоритет бизнес-экранов | |
| Wave 2 landing-first | Приоритет landing | |
| Wave 2 only critical | Хвосты в отдельную фазу | |
| You decide | Sequencing оставлен агенту | ✓ |

**User's choice:** You decide.

| Option | Description | Selected |
|--------|-------------|----------|
| Strict completion gate | Закрытие только после полного завершения волн и проверок | ✓ |
| Wave-based partial completion | Разрешен partial close | |
| Best-effort gate | Мягкий критерий завершения | |
| You decide | Оставить решение агенту | |

**User's choice:** Strict completion gate.

---

## Effects cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal motion | Только функциональные анимации | |
| Balanced motion | Умеренная декоративность | ✓ |
| Expressive motion | Выраженные декоративные эффекты | |
| You decide | Оставить решение агенту | |

**User's choice:** Balanced motion.

| Option | Description | Selected |
|--------|-------------|----------|
| Landing-only | Декор только на marketing | |
| Landing + selected highlights | Ограниченно на отдельных витринах | |
| Global but toned-down | Глобально, но сильно ослаблено | ✓ |
| You decide | Оставить решение агенту | |

**User's choice:** Global but toned-down.

| Option | Description | Selected |
|--------|-------------|----------|
| Strict cap | Жесткий лимит интенсивности на token-уровне | ✓ |
| Guideline cap | Рекомендованные лимиты без hard gate | |
| Case-by-case | Без общего лимита | |
| You decide | Оставить решение агенту | |

**User's choice:** Strict cap.

| Option | Description | Selected |
|--------|-------------|----------|
| Keep API, tweak values | Сохранить старые utility-классы | |
| Deprecate + migrate | Поэтапная замена | |
| Remove now | Удалить сразу в этой фазе | ✓ |
| You decide | Оставить решение агенту | |

**User's choice:** Remove now.

---

## the agent's Discretion

- Wave 2/Wave 3 sequencing within staged rollout.
- Fine-grained technical exemptions for color hard-gate (non-UI assets only).

## Deferred Ideas

None.
