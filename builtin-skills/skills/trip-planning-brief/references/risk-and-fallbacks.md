# Risk and Fallbacks

Every risk line has three parts: **trigger** (what you would observe), **action** (what to do),
**cost** (time or money). A risk without an action is decoration.

## Risk classes

| Class | Trigger to watch | Check action | Fallback |
|---|---|---|---|
| Closure / holiday | gate closed, "closed today" sign | official site + local holiday calendar, `[verify]` | equivalent nearby anchor, indoor option |
| Weather | forecast shift, heat, typhoon season | recheck 24h before, `[verify]` | indoor anchor, swap day order |
| Transit delay | platform announcement, missed connection | know the last feasible departure | taxi budget line, later slot, drop one anchor |
| Over-packing the day | arriving late at anchor 2 | movement budget from pace table | drop lowest-value anchor, name it |
| Energy / fatigue | user says "too much", long queue | rest block, sit-down meal | half-day plan, hotel return |
| Payment | card declined, cash-only sign | carry local cash, know ATM locations | alternate card, ATM, smaller purchase |
| Entry / documents | passport validity, visa lead time | check official source `[verify]` | not fixable on the day - flag early |
| Safety / scams | unsolicited "guide", fake meter | use official taxi app or hotel call | walk to a staffed location |
| Language / signage | no English menu or sign | translation app offline pack | point-and-photo, ask hotel |
| Accessibility | steps, no lift, narrow gate | ask venue in advance `[verify]` | alternate entrance, different venue |
| Medical | pharmacy closed, minor injury | know nearest clinic hours `[verify]` | hotel front desk, insurance hotline |

Do not give medical advice or dosages. Point to a clinic, pharmacy or insurance line.

## Fallback design rules

1. Every day carries at least one fallback that is **indoor or adjacent** - reachable in one leg.
2. A fallback must be a real place or activity, not "rest" or "freestyle" alone.
3. The fallback must be cheaper or equal in cost, so a disruption does not blow the budget.
4. Each fallback states whether it needs a booking `[verify]`.
5. If a day has a timed entry, the fallback covers the case of missing it.

## Stress test prompts (ask yourself, then write the answer)

- What if day 2 rains all day?
- What if the intercity train is cancelled - what is the last workable alternative?
- What if one traveler cannot continue after lunch?
- What if the museum's special exhibition is sold out?
- What if the flight lands 90 minutes late?
- What if the trip loses one full day - which day absorbs it and what is cut?

If two different disruptions both require cutting the same anchor, that anchor was never
load-bearing. Say so, and consider removing it up front.

## Presenting risk

Keep it to three risks in the brief, and the full table in the constraint-check section. Order by
`likelihood x impact`, and name the one risk that would most change the plan. Never present a
risk register as reassurance; it is a list of things that can still go wrong.
