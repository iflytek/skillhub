# Timeline Anchors

Work backwards from show time. The percentages matter more than the exact days: a 90-day event and
a 10-day event need the same *shape*, compressed.

## Anchor table

| Anchor | Small (<=40 people, own space) | Medium (40-150, booked venue) | Large (150+, multi-vendor) |
|---|---|---|---|
| Decision + purpose fixed | T-14 | T-30 | T-60 to T-90 |
| Budget approved | T-12 | T-25 | T-60 |
| Venue contracted | T-12 | T-21 | T-60 |
| Invitations out | T-10 | T-18 | T-45 |
| Headcount lock | T-5 | T-10 | T-14 |
| Vendors ordered (food, AV, print) | T-7 | T-10 | T-21 |
| Final numbers to vendor | T-3 | T-4 | T-7 |
| Materials in hand | T-2 | T-3 | T-5 |
| Setup + staff brief | T-1 | T-1 | T-2 |
| Show | T-0 | T-0 | T-0 |
| Teardown + reconciliation | T+0 to T+1 | T+0 to T+2 | T+1 to T+5 |

If the decision date is already inside an anchor (e.g. 5 days out for a 40-person event), do not
compress everything: **cut scope**. Name what is dropped (printed signage, a speaker, a seated
format) rather than pretending a 21-day task fits in 4.

## Anchor rationale (keep these, they are load-bearing)

- **Headcount lock before final numbers**: vendors price per head, and a number that moves after
  the order is a re-quote. Lock once, then only move up.
- **Invitations before headcount lock**: without a deadline and an RSVP mechanism, the count is a
  guess with a 20-30% error band.
- **Materials before setup day**: printing and delivery lead times are the most common single-day
  slips. In-hand at T-2 keeps one spare day.
- **Setup window is a real constraint**: a booked venue often allows 2 hours, not 5. Confirm the
  access window `[verify]` before the timeline assumes it.
- **Staff brief before doors**: a 10-minute brief with the door owner, the AV owner and the
  person with the venue contact is worth more than a longer plan.

## Task decomposition rules

Each timeline line must be:
- **atomic** - one deliverable, one owner;
- **dependent** - names what it waits for (or `none`);
- **verifiable** - "done when" is an observation, not an intention ("contract signed and stored",
  not "handle venue");
- **owned by a role** - "venue", "food", "AV", "door", "content", "money", "comms". Roles can be
  the same person; that is a finding worth stating, because one person holding four roles is the
  top cause of small-event failure.

## Anti-patterns

| Anti-pattern | Why it fails | Fix |
|---|---|---|
| "Finalize details" as a task | no deliverable, no end | split into contracts, orders, briefs |
| Owner = "team" | nobody is accountable | assign one role per line |
| Parallel tasks that share a person | looks fine, slips silently | serialize or reassign |
| Timeline in relative days only ("T-3") without dates | nobody converts it | give both |
| No teardown / reconciliation | damages, deposits and invoices land late | add T+0 to T+1 lines |
| Assumes venue access all day | the setup window is the real bottleneck | confirm access `[verify]` |

## Presenting

One table, chronological, most recent first is wrong - read top-down from T-30 to T+1 so the user
sees the runway. Mark lines already complete with `[done]` only if the user said so.
