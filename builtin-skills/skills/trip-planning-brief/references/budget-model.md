# Budget Model

Always output **ranges**, with the basis and the assumption that produced each range. A single
number invites the user to treat an estimate as a quote.

## Method

1. Pick a tier: `budget` / `mid` / `comfort`. State the tier, never mix tiers silently.
2. For each category, compute `low = per-unit-low x units`, `high = per-unit-high x units`.
3. Add a buffer line: 10-15% of subtotal for price drift and rounding.
4. Show `total low`, `total high`, and `per person per day` for sanity.
5. List what is **excluded** (shopping, alcohol, insurance, visa fees) explicitly.

## Unit conventions

| Category | Unit | Note |
|---|---|---|
| Lodging | room-night x nights / rooms | rooms = ceil(travelers / occupancy), state occupancy assumed |
| Food | meals x days x tier | 3 meals, or 2 if breakfast is included - say which |
| Local transit | rides/day x days | plus any pass only if the user asked |
| Intercity | legs x mode | schedule and fare class `[verify]` |
| Tickets | items x persons | child/senior rates only if stated |
| Airport transfer | legs x mode | often the most underestimated line |
| SIM / eSIM / roaming | per trip | data-only vs voice |

## Currency

- Use the currency the user gave. Do not convert unless they gave a rate.
- If converting, write the rate and its date in the Basis column: `at 1 USD = 7.2 CNY (user rate)`.
- Never fetch a live exchange rate in this skill; a rate without a date is a false precision.
- Where local cash is required (markets, rural buses, temples), add a cash line.

## Common misses to check every time

- City / tourist tax, resort fee, service charge, cleaning fee.
- Checked-bag fee and seat selection on low-cost carriers.
- Airport train vs taxi difference (often 3-5x).
- Museum special-exhibition surcharge on top of the base ticket.
- Reservation-only restaurants with a deposit or minimum spend.
- Card foreign transaction fee and ATM fee.
- Tipping norms where they exist (state them as `[verify]`).
- Travel insurance and any visa/entry fee.
- Baggage storage / locker between cities.
- One "contingency meal" per trip (missed connection, closed kitchen).

## Sanity checks before answering

- Per-person-per-day total within a plausible band for the destination tier? If not, name why.
- Does lodging exceed 50% of the total? For a city trip that is a signal, not necessarily an error.
- Did any category come out at exactly the same low and high? Then it is a guess, not a range.
- Is the total consistent with the user's stated cap? If it exceeds it, say by how much and what
  the two cheapest cuts would be - do not silently shrink the plan to fit.

## Presentation

Two tables maximum: category ranges, then the excluded/optional list. Keep every number in one
column alignment so the user can scan. Never bold a number to imply it is confirmed.
