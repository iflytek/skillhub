---
name: daily-standup-journal
description: Generate concise daily standups, reflection prompts, and weekly retrospectives for individuals or teams. Use for planning a day, surfacing blockers, reviewing user-provided entries, or drafting a check-in without assuming prior history.
version: 1.0.0
license: MIT
---

# Daily Standup & Journal

## What It Does

Generate a structured check-in for a solo workday, team sync, reflection, or retrospective. Keep the
result proportional to the user's requested depth.

Default to an in-session response only. Do not save, retrieve, or share journal content unless the user explicitly requests it and identifies the destination. Never claim to remember earlier entries that are not present in the current authorized context.

---

## Session Types

### 1. Daily Solo Standup (5-Minute Check-In)

**Best for**: Freelancers, solopreneurs, remote workers

| Prompt | Why It Matters |
|--------|----------------|
| What am I **committed to** finishing today? | Clarifies intention |
| What will **distract** me, and how do I prevent it? | Anticipates friction |
| What is one thing I can **defer or delete**? | Reduces scope creep |
| What **energy level** am I at? (1-10) | Captures the user's self-reported capacity without diagnosing it |
| What is the **one metric** that tells me today was a win? | Creates a finish line |

**Format**: Invite brief answers unless the user asks for a deeper reflection.

### 2. Daily Team Standup (Async)

**Best for**: Small remote teams, freelance collaborators

| Question | Focus |
|----------|-------|
| What did I **accomplish** yesterday? | Progress visibility |
| What will I **work on** today? | Intentionality |
| What **blockers** do I need help with? | Surface roadblocks |
| What **one thing** would make today productive? | Proactive planning |

**Pro tip**: Keep responses under 3 sentences each. Use a shared doc or channel. Read everyone's before starting your day.

### 3. Evening Reflection (Gratitude + Growth)

**Best for**: Personal development, habit tracking

| Prompt | Purpose |
|--------|---------|
| What **went well** today? | Reinforce positive patterns |
| What **challenged** me? | Identify growth edges |
| What **did I learn**? | Consolidate insights |
| What **would I do differently**? | Meta-learning |
| What am I **grateful for**? | Emotional resilience |

### 4. Weekly Retrospective

**Best for**: Solopreneurs, small teams, end-of-week review

#### Section A: Wins & Losses

```
| Win | Why It Mattered |
|-----|----------------|
| [event] | [impact] |

| Loss / Miss | Lesson Learned |
|-------------|----------------|
| [event] | [takeaway] |
```

#### Section B: Energy Map

If the user wants an energy map, ask them to rate each day using their own scale:
```
Mon: [rating] — [user observation]
Tue: [rating] — [user observation]
Wed: [rating] — [user observation]
Thu: [rating] — [user observation]
Fri: [rating] — [user observation]
```

#### Section C: Metrics Check

| Metric | This Week | Last Week | Δ | Notes |
|--------|-----------|-----------|---|-------|
| Revenue/Bookings | | | | |
| Hours Worked | | | | |
| Deep Work Hours | | | | |
| Clients/Projects Moved | | | | |

#### Section D: Next Week Commitments

1. **Start**: What new habit or project begins?
2. **Stop**: What drained energy or produced no value?
3. **Continue**: What's working well?

### 5. Monthly Theme Generator

**Best for**: Setting direction, building momentum

| Prompt | Reflection |
|--------|------------|
| What word describes this month? | Identify the emotional tone |
| What was the **biggest shift**? | Track trajectory |
| What **surprised** me? | Surface unexpected lessons |
| What am I **most proud of**? | Celebrate progress |
| What needs **more attention** next month? | Forward focus |
| **One sentence** to capture this month: | Narrative summary |

---

## Trigger Phrases

| Phrase | Action |
|--------|--------|
| "Run my daily standup" | Generates the solo standup prompts |
| "Quick check-in" | Abbreviated standup (1-2 questions) |
| "Evening journal" | Generates reflection prompts |
| "Weekly retro" | Full weekly retrospective structure |
| "Month in review" | Monthly theme and reflection prompts |
| "I feel stuck today" | Adaptive standup focused on blockers + clarity |
| "End of day review" | Evening reflection with gratitude |
| "Morning pages" | Stream-of-consciousness vs structured hybrid |

---

## Step-by-Step Instructions

### Step 1: Identify the Session Type

Infer solo/team, planning/reflection, and time horizon from the request. Ask one short question only
when the missing choice would materially change the output; otherwise use the standard daily
check-in and state the assumption.

### Step 2: Generate the Prompts

Based on type, serve the relevant prompt set. Allow customization:
- "Skip the gratitude part today"
- "Add a question about client outreach"
- "Make it shorter — I'm in a hurry"

### Step 3: Process the Responses

Summarize what the user actually supplied and distinguish observations from hypotheses:

- **Patterns**: If the user supplied several entries, note repeated language and cite the relevant days.
- **Blockers**: "You've had the same blocker for 2 days. Want to brainstorm a fix?"
- **Trends**: Describe a possible trend only when the supplied entries support it; ask the user whether the interpretation fits.

Do not infer mood, health, sleep quality, burnout, or causes from an energy score or sparse journal entry.

### Step 4: Offer Optional Export

By default, return the entry in the conversation and do not persist it. If the user asks for a rolling log, confirm the file or service, show the exact content to be written, and obtain confirmation before writing or sharing it. Do not overwrite an existing log without explicit approval.

### Step 5: Surface Action Items

When the user's response supports action and they want planning help, suggest one or two concrete
actions:
- "Reserve a focus block during the user's stated high-energy period"
- "Consider moving the Friday call if the user says that time repeatedly causes problems"

Present these as suggestions. Do not change a calendar, cancel a meeting, send a message, or share a journal without explicit authorization.

---

## Sample Sessions

### Solo Freelancer Standup

> **Morning Input**: "Run my standup"
>
> **Output**:
> ```
> DAILY STANDUP
>
> 1. What are you committed to finishing today?
> 2. What might distract or block you?
> 3. What can you defer or remove?
> 4. What is your current energy level (optional, 1-10)?
> 5. What observable outcome would make today a win?
> ```

### Weekly Retro

> **Input**: "Weekly retro"
>
> **Output**:
> ```
> WEEKLY RETROSPECTIVE
>
> WINS
> • [Add outcomes from this week's supplied entries]
>
> MISSES OR LESSONS
> • [Add only what the user reported]
>
> POSSIBLE PATTERNS
> • [Evidence-backed pattern, or "Not enough information"]
>
> NEXT WEEK
> Start: [User choice]
> Stop: [User choice]
> Continue: [User choice]
> ```

---

## Quality checks

- Use only entries supplied in the current authorized context.
- Keep observations separate from interpretations.
- Do not infer health, mood, motivation, or performance from sparse entries.
- Offer a share-ready draft only when useful; never send or expose journal content without explicit
  authorization.
- Do not create a recurring review or reminder unless the user asks.
