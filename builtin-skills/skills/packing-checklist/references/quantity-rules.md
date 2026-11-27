# Quantity Rules

Quantities come from formulas so the list survives a change of trip length. A bare number is a
guess; a number with its formula can be re-derived.

## Core formulas

```
active_days   = trip_days - travel_days_without_change
wash_cycle    = { none: active_days, sink: 2, machine: 3 }
tops          = ceil(active_days / wash_cycle) + 1 spare
bottoms       = max(1, ceil(active_days / (wash_cycle * 2)))          # trousers tolerate more wears
underwear     = active_days if wash_cycle == active_days else 3-4
socks         = ceil(active_days / wash_cycle) + 1
consumables   = days * uses_per_day, capped by "can I buy it there?"
```

Notes that matter more than the formula:

- **One spare rule**: exactly one spare top, not two. The second spare is the most commonly
  wasted item on a 1-week trip.
- **Long trips do not scale linearly.** Beyond 10 days, laundry cadence matters more than day
  count: a 14-day trip with machine laundry needs the same clothing as a 6-day trip without.
- **Travel days** (long flights, overnight trains) count as an active day for underwear but not
  for tops you would change twice.
- **Consumables** (sunscreen, contact solution, shampoo): buy-on-arrival is often the right
  answer. State the assumption "buyable at destination `[verify]`" instead of packing 500ml.
- **Medication**: quantity is `days + 2` and must never be rationed; do not reduce it to save
  space, and never suggest splitting a dose.

## Luggage budget arithmetic

Give a volume budget in litres, then assign slots. Approximate slot costs (use as a planning
convention, not a measurement):

| Slot | Approx volume | Note |
|---|---:|---|
| Worn clothing | 0 (on body) | free space is the point |
| Base top | 1.0 L each | thin knit ~0.7 L |
| Mid layer | 2.0 L | fleece ~1.5 L |
| Shell | 1.0 L | packs to a fist |
| Trousers | 2.0 L per pair | worn pair is free |
| Shoes (packed) | 4.0 L per pair | worn pair is free |
| Toiletry kit | 1.5 L | decanted |
| Electronics pouch | 1.5 L | charger + cables + adapter |
| Laptop + charger | 3.0 L | often the biggest optional |
| Day bag (empty) | 1.0 L | |
| Camera body + lens | 2.0-3.5 L | lens dependent |

Carry-on convention: 40 L total, of which roughly 30 L is usable after the bag's own structure.
Say the number you used so the user can disagree with it.

## Trimming order (cut in this order, cheapest first)

1. Duplicate toiletries and full-size liquids.
2. The third pair of shoes.
3. The second "just in case" mid layer.
4. Books and paper.
5. The laptop, if the trip is not work.
6. The umbrella when a shell is packed (or vice versa).
7. Second camera lens.
8. Towel, if the accommodation provides one `[verify]`.

Cut weight next only after volume: weight limits are rarely the binding constraint for clothing,
but they bind hard for batteries, shoes and liquids.

## Presenting quantities

Good: `T-shirt | 3 (5 days / wash every 2) | must | base layer, 2 wears each`.
Bad: `T-shirt | 3 | must | clothes`.

If the user changes the trip length, the formula lets them recompute instead of asking again.
State the wash cycle you assumed in the brief - it is the single number that changes the list most.
