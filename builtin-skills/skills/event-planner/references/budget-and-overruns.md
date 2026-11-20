# Budget and Overruns

Events do not usually fail on the planned cost; they fail on the **unplanned** cost. Give ranges,
name the overrun class per line, and keep a contingency that is large enough to absorb the top
realistic failure.

## Category structure

| Category | Driver | Typical range share | Overrun class |
|---|---|---:|---|
| Venue (fee + overtime) | per hour / flat | 15-30% | **high** - overtime and extra setup hours |
| Food | per head x count | 20-35% | **high** - headcount growth, late orders |
| Drinks | per head x hours | 5-25% | **very high** - consumption is unlinear at the end of the night |
| Staffing (service, security, AV op) | per person x hours | 5-15% | medium - hours extend with the event |
| Materials / print / signage | per item | 3-10% | low - but lead-time slips cost express fees |
| AV and tech rental | per item | 5-20% | **high** in hybrid/recorded formats |
| Transport / parking / accessibility | per trip | 2-8% | medium |
| Contingency | 10-20% of subtotal | - | this is the buffer, not a category to spend |

## Range method

1. Take the user's budget as a **cap**, not a target. A plan that lands at exactly the cap has no
   room for a single surprise.
2. For each category produce `low` and `high` from a per-unit driver, not a percentage of the cap:
   `food_low = per_head_low x headcount_low`, `food_high = per_head_high x headcount_high`.
3. Use headcount **low and high** when a confidence band was given - this is the most honest
   single improvement over a point estimate.
4. Add contingency on the subtotal: 10% for a controlled internal event, 15-20% for a booked venue
   with vendors, and never below the cost cap of the most expensive fallback in the emergency sheet.
5. Show `per attendee` for a sanity read.

## Known-miss list (check every time)

- Service charge and gratuity, often 10-20% and often already inside a "per head" quote `[verify]`.
- Corkage / cakeage / external-food fee.
- Venue overtime: hourly, often at 1.5x, triggered by teardown past the contracted hour.
- Cleaning fee and damage deposit (refundable, but it is cash out).
- Delivery, setup and breakdown labour from vendors.
- Power distribution, extra cables, adapters, gaffer tape.
- Staff meals and water (small line, always forgotten).
- Printing express fees when the timeline slipped.
- Cancellation and postponement terms - the real cost of a date change `[verify]`.
- Tax treatment and whether quoted prices include it `[verify]`.
- Accessibility provision (interpreters, step-free access, quiet room) - a line, not an afterthought.

## Contract traps to name

1. **Minimum spend** instead of a room fee - the invoice is the shortfall.
2. **Headcount guarantee** - you pay for the guaranteed count even if fewer arrive.
3. **Overtime auto-extension** - staff stay and bill without a signature.
4. **Damage deposit forfeiture** on ambiguous terms.
5. **Force majeure exclusions** for weather on an outdoor event.
6. **Exclusivity clauses** that forbid outside catering or drink.
7. **Cancellation ladder** - 100% inside 7 days is common `[verify]`.

Never advise signing or not signing; surface the term and say to confirm with the counterparty and
whoever owns the budget.

## Presenting

One range table, one known-miss list, one sentence on which line is most likely to overrun and by
how much. Never present the total as a quote, and never hide the contingency inside another line -
it must be visible or it will be spent twice.
