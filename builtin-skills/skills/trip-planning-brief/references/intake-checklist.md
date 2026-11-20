# Intake Checklist

## Minimum viable input

Blocking (ask once if missing, then stop and wait):

1. Destination or candidate destinations, in visiting order.
2. Dates, or a duration with a month.

Everything below is **assumed and stated**, never asked in a chain.

| Item | Default assumption | What changes |
|---|---|---|
| Traveler count | 1 adult | budget math, table sizes, room count |
| Who travels | not assumed | pace, buffers, rest blocks |
| Budget + currency | ranges only, user currency | tier of lodging/food |
| What the budget covers | per person, excluding shopping | totals vs per-category split |
| Pace | `balanced` | anchors/day and daily radius |
| Fixed points | none | anchors and arrival/departure handling |
| Season/weather | not asserted | fallbacks and clothing handoff |
| Diet/alcohol | not assumed, listed as open question | meal windows and restaurant tier |

## Assumption line format

Put all assumptions in one block at the top of the answer, each as one line:

```
ASSUMED: 1 adult traveler, 3 nights / 4 days, budget given per person excluding shopping,
         pace balanced, no fixed bookings, no dietary limits stated.
```

Never bury an assumption inside the itinerary. If an assumption is load-bearing (it changes the
skeleton), say what would change if it is wrong:

```
If you travel with a child under 6, day 2 loses one anchor and gains a 90 min rest block.
```

## Question templates (max 3, one round)

Good - each changes the skeleton:

- "Which month, and how many nights on the ground?"
- "How many travellers, and does anyone need a slower pace or step-free access?"
- "Is anything already booked that I must schedule around (flight times, hotel area, an event)?"

Bad - open, unbounded, or answerable by assumption:

- "What kind of trip do you like?" (unbounded)
- "What is your budget exactly?" (a range works)
- "Do you want to see everything?" (no plan can)

## Things people forget to state (surface as open questions, do not assume)

- Entry documents, visa lead time and passport validity window.
- Whether the first and last day are actually usable (red-eye, late arrival).
- Luggage storage between cities, and check-in/check-out times.
- Local holidays that close museums or markets.
- Payment habits (cash-only places, foreign card acceptance).
- Whether the trip is work-first with tourism in the gaps.
- Return-to-work buffer after a long-haul return.

## Existing-plan review intake

When the user pastes a plan instead of asking for one, extract silently and report only:

1. Total anchors per day and the day's movement total.
2. Days that violate the pace radius.
3. Claims with no source (hours, prices, "must book ahead").
4. Missing fallbacks and missing meal windows.
5. Any fixed commitment the plan contradicts.

Then answer in the normal output format, with a `Plan Review` note listing the defects found.
