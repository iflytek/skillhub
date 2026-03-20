# Global Select Redesign

## Background

The current shared Select component in the web app is a thin wrapper around the
native HTML `<select>` element.

Current implementation characteristics:

- lives in `web/src/shared/ui/select.tsx`
- only provides shared Tailwind class names
- does not offer a styled popover, selected-item indicator, or theme-level
  dropdown behavior
- inherits the browser's native dropdown rendering, which makes the experience
  visually inconsistent across pages and operating systems

The related issue asks to optimize the global Select component and improve its
visual quality. The intended direction is not a simple class tweak. It is a
design-system-level upgrade for single-select form controls so they look more
polished and align with the existing Tailwind theme tokens.

The user explicitly approved this direction:

- prioritize visual consistency and beautification
- allow migration to Radix Select
- keep colors aligned with the existing Tailwind theme
- stop short of building a search-capable advanced selector

## Goals

- Replace the current native-wrapper Select with a Radix-based themed Select.
- Make Select visually consistent with the existing Input, Card, Dialog, and
  theme token system.
- Improve the perceived quality of dropdown interactions across current form
  flows.
- Keep the component reusable for static single-select form use cases.
- Migrate all current shared Select usage sites to the new component shape.

## Non-Goals

- Do not add search, async loading, multi-select, or creatable options.
- Do not redesign all form controls in this issue.
- Do not introduce a second competing Select implementation.
- Do not change business logic in existing pages beyond what is needed for
  migration.
- Do not turn this into a full design-system overhaul beyond the Select family.

## Current Usage Scope

The shared Select component is currently used in these frontend flows:

- publish page namespace selection
- publish page visibility selection
- token creation expiration selection
- token list filtering
- namespace member role selection
- namespace member add dialog candidate/role selection
- admin user status filtering
- admin user role change dialog
- audit log action filtering

These flows are mostly static single-select scenarios and are compatible with a
Radix-based replacement without needing search or complex data fetching.

## Design Direction

The new shared Select should be implemented as a small Radix UI wrapper family,
similar in shape to established Radix-based form patterns, while still matching
the existing project style instead of importing a foreign visual language.

The key decision is:

- replace the internals with `@radix-ui/react-select`
- keep the public usage focused on simple application forms
- invest in polished visuals and interaction quality
- avoid capability creep

This is the right middle ground between a minor style patch and a heavyweight
custom selection framework.

## Component Architecture

The shared Select should no longer export only one component that renders a
native element. It should become a small group of composable primitives:

- `Select`
- `SelectTrigger`
- `SelectValue`
- `SelectContent`
- `SelectItem`
- `SelectGroup`
- `SelectLabel`
- `SelectSeparator`

Optional helper exports are acceptable if useful for completeness:

- `SelectScrollUpButton`
- `SelectScrollDownButton`

### Responsibilities

#### Select

- owns the selected value and change callback
- provides controlled and uncontrolled operation
- defines disabled/open behavior through Radix

#### SelectTrigger

- renders the closed-field surface
- shows placeholder or selected value
- hosts the chevron icon
- reflects focus, disabled, and open states

#### SelectContent

- renders the floating dropdown panel in a portal
- handles positioning, animation, viewport sizing, and overflow

#### SelectItem

- renders each option row
- shows hover, keyboard focus, and selected state
- includes a visible selected indicator

### API Direction

The component API should optimize for clarity instead of trying to preserve the
native `<option>` contract. Existing call sites will be updated to use the new
composition model rather than hidden compatibility hacks.

Example intended shape:

```tsx
<Select value={visibility} onValueChange={setVisibility}>
  <SelectTrigger>
    <SelectValue placeholder={t('publish.visibility')} />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="PUBLIC">{t('publish.visibilityOptions.public')}</SelectItem>
    <SelectItem value="NAMESPACE_ONLY">{namespaceOnlyLabel}</SelectItem>
    <SelectItem value="PRIVATE">{t('publish.visibilityOptions.private')}</SelectItem>
  </SelectContent>
</Select>
```

This is a deliberate migration because Radix Select has different semantics from
native `<select>`, especially around placeholder handling, empty values, and
rendering.

## Visual Specification

The visual design should feel like a direct sibling of the existing Input
component, not a detached component imported from another UI kit.

### Trigger Surface

- height should align with existing inputs at `h-11`
- width remains full by default unless a page provides a narrower class
- use rounded corners consistent with `--radius`
- use a light field background compatible with current card and input surfaces
- use `border-border` driven outlines instead of hard-coded colors
- text color should align with `foreground`
- placeholder color should align with `muted-foreground`

The trigger should feel slightly more refined than the current input styling,
but still belong to the same family:

- subtle background depth
- cleaner right-side icon treatment
- smoother transition between idle, hover, focus, and open states

### Focus and Open States

- keep visible focus treatment using `ring-primary/40` and border emphasis
- opening the dropdown should subtly reinforce the active state
- icon rotation on open is acceptable and desirable
- open state should not feel like a separate unrelated widget

### Dropdown Panel

- render inside a portal above surrounding layout
- use `popover` / `popover-foreground` themed surface
- rounded corners equal to or slightly tighter than trigger radius
- light border with medium shadow
- modest fade/zoom motion, consistent with existing Radix dropdown behavior
- viewport padding should feel airy rather than cramped

### Item Rows

- text remains readable at form-control scale
- hover and keyboard-focus states should be obvious
- selected item should have both:
  - a highlighted background or text treatment
  - a visible check indicator
- disabled items should have lower opacity and no pointer interaction
- long labels should truncate safely instead of stretching layout

### Theme Integration

The component must use existing Tailwind theme tokens rather than introducing
new hard-coded brand colors. Primary styling should come from:

- `primary`
- `accent`
- `border`
- `muted`
- `popover`
- `foreground`
- `muted-foreground`

This ensures the Select follows current light-theme styling and stays compatible
with the dark theme variables already defined in `web/src/index.css`.

## Interaction Specification

### Supported Behaviors

- click trigger to open
- click item to select and close
- keyboard open via Enter, Space, or Arrow keys
- keyboard navigation through items
- Enter to confirm selection
- Escape to close without committing a new click action
- disabled state prevents interaction

### Placeholder and Empty State Strategy

This is the main migration detail because native Select frequently uses
`<option value="">` to represent "please choose".

For the new component:

- placeholder text should be handled through `SelectValue`
- forms that currently use an empty-string option for "select one" should map
  that state explicitly to placeholder behavior where possible
- if a real empty-string value is needed, its semantics must be reviewed at the
  usage site rather than assumed

In practice, most current usage sites are simple enough that the empty string
should remain the unselected field state and display as placeholder text.

### Width and Overflow

- trigger text should truncate when labels are long
- content width should be at least the trigger width where practical
- long option labels should not break surrounding layout
- dropdown viewport should scroll when the list is tall

## Migration Scope

The implementation should touch the shared component and all current usage sites
that import it.

### Shared UI

- `web/src/shared/ui/select.tsx`

Likely supporting updates:

- `web/package.json`
- `web/pnpm-lock.yaml`

### Feature and Page Call Sites

- `web/src/pages/dashboard/publish.tsx`
- `web/src/features/token/create-token-dialog.tsx`
- `web/src/features/token/token-list.tsx`
- `web/src/features/namespace/add-namespace-member-dialog.tsx`
- `web/src/pages/dashboard/namespace-members.tsx`
- `web/src/pages/admin/users.tsx`
- `web/src/pages/admin/audit-log.tsx`

Every migration should stay focused on rendering adaptation. Existing state,
mutation, validation, and business rules should remain unchanged.

## Compatibility Strategy

The new Select will require usage-site migration because Radix Select does not
consume native `<option>` children. This is acceptable and preferable to trying
to hide incompatible behavior behind a misleading API.

Compatibility principles:

- keep the state model simple: `value` and `onValueChange`
- keep disabled and placeholder behavior explicit
- keep className customization available on trigger/content/items as needed
- avoid one-off props for a single page unless multiple call sites need them

The resulting shared component should be small enough that future contributors
can understand it without learning a custom abstraction layer.

## Accessibility

The redesign must preserve or improve accessibility compared with the native
wrapper.

Requirements:

- trigger must be keyboard reachable
- focus ring must stay visible
- selected state must not rely on color alone
- placeholder vs selected value should remain distinguishable
- disabled state must be communicated visually and behaviorally
- dialog-contained selects must remain operable without focus traps regressing

## Testing Strategy

This issue changes shared frontend behavior, so validation should focus on both
compile safety and user-visible correctness.

### Required Verification

- frontend lint passes
- frontend typecheck passes
- frontend tests pass if impacted by shared UI changes

### Manual Validation Targets

- publish page:
  - namespace select works
  - visibility select works
- token creation dialog:
  - expiration selection works
  - custom expiration branch still behaves correctly
- admin users page:
  - status filtering works
  - role change dialog works
- audit log page:
  - action filter works
- namespace member flows:
  - role selection works

### Regression Risks To Watch

- placeholder not showing for empty current values
- selected value not rendering after controlled state updates
- dropdown panel clipping inside dialogs or cards
- keyboard navigation regressing in modal contexts
- layout breakage from long option labels

## Acceptance Criteria

- the shared Select is migrated from native `<select>` wrapper to a Radix-based
  implementation
- the redesigned Select uses project theme tokens rather than hard-coded colors
- the closed trigger visually aligns with the existing form language
- the open dropdown panel looks polished and consistent across usage sites
- selected items have a clear visual indicator
- all existing shared Select usage sites are migrated and remain functional
- empty placeholder states are handled intentionally rather than by accidental
  browser behavior
- keyboard interaction works in ordinary pages and dialogs

## Recommendation

Implement the global Select as a focused Radix-based single-select component
family and migrate all current usage sites in one pass. This delivers the
requested visual upgrade while staying disciplined about scope. It improves
quality where the issue actually asks for it and avoids prematurely expanding
the component into a search-heavy advanced selector.
