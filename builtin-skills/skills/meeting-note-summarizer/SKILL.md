---
name: meeting-note-summarizer
description: Turn meeting notes or transcripts into factual summaries, decisions, questions, and action items. Use when a user wants a concise recap or needs explicit owners and deadlines extracted without filling in missing details.
version: 1.0.0
license: MIT
---

# Meeting Note Summarizer

## What It Does

Takes raw meeting notes, voice transcripts, or bullet-point jumbles and turns them into clean, structured summaries organized by: **Decisions**, **Action Items**, **Key Discussion Points**, and **Next Steps**. No more digging through pages of notes to find what was actually decided.

Preserve the source's level of certainty. Never invent or upgrade tentative statements into facts. In particular, do not add participants, dates, durations, decisions, tasks, owners, deadlines, rationale, or next meetings that are not explicitly supported. Mark missing fields as `Not provided`, `Unassigned`, or `No deadline stated`.

---

## Output Structure

Every summary follows this template (adapted based on meeting type):

```
┌─────────────────────────────────────────┐
│         MEETING SUMMARY                 │
│  Topic: [Meeting Title]                 │
│  Date: [Date or "Not provided"]         │
│  Duration: [Duration or "Not provided"] │
│  Participants: [People or "Not provided"]│
├─────────────────────────────────────────┤
│                                         │
│  🎯 DECISIONS                           │
│  • [What was decided]                   │
│  • [Rationale if stated]                │
│                                         │
│  ✅ ACTION ITEMS                        │
│  • [Task] → [Owner or "Unassigned"]    │
│    → [Deadline or "No deadline stated"]│
│                                         │
│  💬 KEY DISCUSSION POINTS               │
│  • [Topic 1 — 1-2 sentence summary]     │
│  • [Topic 2 — 1-2 sentence summary]     │
│                                         │
│  ⏭️ NEXT STEPS                          │
│  • [Follow-up action]                   │
│  • [Next meeting date / check-in]       │
│                                         │
│  📎 ATTACHMENTS / REFERENCES            │
│  • [Links, docs, resources mentioned]   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Meeting Types & Custom Formats

### 1. Client Call

| Section | Focus |
|---------|-------|
| **Client Status** | How is the client feeling? Satisfied, concerned, urgent? |
| **Scope Changes** | Any new requests, changes, or scope creep? |
| **Feedback** | What did they approve or reject? |
| **Deliverables Due** | What are you committing to deliver? |

### 2. Brainstorming / Creative Session

| Section | Focus |
|---------|-------|
| **Ideas Generated** | List all ideas, however rough |
| **Themes** | Patterns across ideas |
| **Promising Directions** | Which ideas have energy behind them? |
| **Killed Ideas** | What was ruled out and why? |
| **Next Experiment** | What should be tested/prototyped? |

### 3. 1:1 / Coaching Call

| Section | Focus |
|---------|-------|
| **Check-In** | How is the person doing? |
| **Challenges Shared** | What's blocking them? |
| **Advice Given** | What guidance was offered? |
| **Accountability** | What did they commit to trying? |

### 4. Standup / Daily Sync (see also: Daily Standup skill)

| Section | Focus |
|---------|-------|
| **Completed** | What shipped since last sync |
| **In Progress** | What's being actively worked on |
| **Blockers** | What's stuck and who can help |
| **Plan** | What's next |

---

## Trigger Phrases

| Phrase | Action |
|--------|--------|
| "Summarize these notes..." | Takes raw text → structured summary |
| "Here are my meeting notes..." | Parses, organizes, and returns clean summary |
| "Extract action items from..." | Returns only the ✅ Action Items section |
| "What did we decide in..." | Surfaces decisions only |
| "Turn this transcript into..." | Full meeting summary from raw transcript |
| "Client call notes..." | Applies client call format |
| "Brainstorm session notes..." | Applies creative session format |
| "Make this shorter..." | Condenses — 1 sentence per section max |

---

## Step-by-Step Instructions

### Step 1: Receive Input

Accept notes in any format:
- Raw transcript text
- Bullet-point jumble
- Voice memo transcription
- Scattered chat messages
- Existing messy notes

### Step 2: Classify Meeting Type

| Signal | Type |
|--------|------|
| Client, deliverable, feedback | Client Call |
| Ideas, concepts, "what if" | Brainstorm |
| Status, blockers, standup | Standup |
| How are you, coaching, growth | 1:1 / Coaching |
| General | Standard |

If unclear, use the standard format or label the inferred type as tentative. Ask only when the choice materially affects the requested output.

### Step 3: Extract Core Categories

Parse the input and tag each sentence/clause into:

1. **Decisions** — Explicit commitments such as "We decided to..."
2. **Action Items** — Explicit tasks or commitments such as "I'll send the draft by Friday"
3. **Discussion Points** — "We talked about pricing tiers"
4. **Questions Raised** — "Should we pivot to subscription?"
5. **Context / Background** — "The client's budget was approved"

Keep proposals, preferences, and open questions separate from decisions. Treat phrases such as "maybe," "could," "next Tuesday?" and "we liked" as tentative unless the notes record a clear commitment.

### Step 4: Structure the Summary

Apply the output template. Use:
- **Action items** → Include an owner and deadline only when stated; otherwise use explicit unknown markers
- **Decisions** → Bullet points with rationale
- **Discussion** → 1-2 sentence summaries per topic, not verbatim

### Step 5: Flag Missed Items

If the notes are ambiguous, call it out:
- ⚠️ "No clear deadline for the design handoff"
- ⚠️ "Unclear who owns the follow-up with the client"
- ⚠️ "No next meeting scheduled"
- ⚠️ "Next Tuesday was suggested, not confirmed"

### Step 6: Provide a TL;DR

For long supplied transcripts, lead with a one-sentence TL;DR. Do not infer meeting duration from transcript length:

> **Illustrative TL;DR, only when supported by the notes**: The client approved the Q2 campaign direction; the creative brief is due Thursday, while the proposed Tuesday follow-up still needs confirmation.

---

## Examples

### Example 1: Messy Notes → Clean Summary

> **Raw Input**:
> "meeting with sarah about the website redesign. she likes the new hero section but thinks the CTA is too small. we need to make it bigger and maybe red. also the testimonial section needs real photos not stock. she'll send us the client photos by wed. i need to update the mockup by friday. we decided to drop the animations page since it's out of scope. next meeting next tuesday?"
>
> **Output**:
> ```
> ┌─────────────────────────────────────────┐
> │         MEETING SUMMARY                 │
> │  Topic: Website Redesign Feedback       │
> │  Participants: Sarah; others not identified │
> │  Duration: Not provided                 │
> ├─────────────────────────────────────────┤
> │                                         │
> │  🎯 DECISIONS                           │
> │  • Drop the animations page (out of scope) │
> │                                         │
> │  ✅ ACTION ITEMS                        │
> │  • Send client photos → **Sarah** → **Wed** │
> │  • Update mockup → **Speaker ("I"; identity not provided)** → **Fri** │
> │  • Increase CTA size → **Unassigned** → **No deadline stated** │
> │                                         │
> │  💬 KEY DISCUSSION POINTS               │
> │  • Sarah likes the hero section; a larger CTA was requested │
> │  • Making the CTA red was suggested, not decided │
> │  • Testimonials need real client photos, not stock │
> │  • Animations page cut from scope       │
> │                                         │
> │  ⏭️ NEXT STEPS                          │
> │  • Next Tuesday was proposed; confirmation needed │
> │                                         │
> └─────────────────────────────────────────┘
> ```

### Example 2: Brainstorm Session

> **Raw Input**:
> "brainstorm for new newsletter format. someone suggested a weekly roundup of tools. another idea was a deep dive into one topic per issue. also could do founder interviews. sarah said the roundup might be too generic. mark said deep dive would take too long. we all liked the interview idea. tom will reach out to 3 potential guests. let's test one interview format and see open rates."
>
> **Output**:
> ```
> ┌─────────────────────────────────────────┐
> │      BRAINSTORM: NEWSLETTER FORMAT      │
> │  Named participants: Sarah, Mark, Tom; others not identified │
> ├─────────────────────────────────────────┤
> │                                         │
> │  💡 IDEAS GENERATED                     │
> │  • Weekly tool roundup (concern: may be too generic) │
> │  • Single deep-dive (concern: may take too long) │
> │  • Founder interviews (favored; final decision not recorded) │
> │                                         │
> │  ✅ ACTION ITEMS                        │
> │  • Reach out to 3 potential guests → **Tom** → **No deadline stated** │
> │  • Test one interview format → **Unassigned** → **No deadline stated** │
> │                                         │
> │  ⏭️ NEXT STEPS                          │
> │  • Clarify whether the interview direction is approved │
> │  • Assign timing for the test issue and define the open-rate comparison │
> │                                         │
> └─────────────────────────────────────────┘
> ```

---

## Pro Tips

- **Capture decisions explicitly**: Record the decision and its rationale when the source states
  them; keep later recollections labeled as such.
- **Expose missing ownership**: Keep a real task even when its owner or deadline is unknown, and label the gap for follow-up.
- **Flag ambiguity**: If a decision was deferred or a question left unanswered, make that explicit. Don't smooth it over.
- **Draft promptly when useful**: Return a share-ready draft, but do not send or publish it without the user's explicit authorization.
- **Organize only on request**: Offer project tags or a running document, but do not persist meeting
  content unless the user asks and identifies the destination.
