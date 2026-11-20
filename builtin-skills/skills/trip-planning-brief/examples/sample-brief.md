# Sample Brief - Kyoto, 3 nights, 2 adults, balanced pace

Worked example of a complete output. Copy the shape, not the numbers.
Nothing in this file is real: prices, hours and transit are placeholders, and every
one of them carries `[verify]` exactly as the skill requires.

---

## 1. Trip Brief

```
Destination:        Kyoto, Japan (single city, no intercity legs)
Window:             2026-04-10 (Fri) -> 2026-04-13 (Mon)  |  3 nights / 4 days  |  TZ: UTC+9
Travelers:          2 adults  [no mobility, dietary or budget constraints stated]
Budget basis:       JPY 240,000 for the group, excluding shopping and alcohol
Pace:               balanced -> 2-3 anchors/day, cumulative transit <= 120 min/day
Fixed points:       none stated (assume no timed tickets, no booked transfers)
Three anchors:      one temple district walk, one market morning, one garden afternoon
Three risks:        April crowds at the eastern temples; rain on the garden day;
                    a temple closed on a weekday with no notice
Status:             PLAN ONLY - nothing booked, priced live, or verified on the ground
```

ASSUMED: 2 adults share one twin room; breakfast included at the hotel; no rail pass;
no checked luggage; local transit by bus and metro at city rates.
If a third traveller joins, lodging becomes two rooms and the total rises by roughly
one room-night band - named here rather than guessed inside the table.

## 2. Day Plan

| Day | Block | Place / Activity | Duration | Move (mode + min) | Cost est. | Fallback | Verify |
|---|---|---|---|---|---|---|---|
| D1 Fri | 15:00-16:00 | Hotel check-in, eastern district | 1h00 | walk 10 min | 0 | lobby wait, luggage hold | check-in time `[verify]` |
| D1 Fri | 16:30-18:00 | Temple district walk (one loop) | 1h30 | walk 15 min | est. 500 JPY | covered arcade walk | closing time `[verify]` |
| D1 Fri | 18:30-20:00 | Dinner near hotel | 1h30 | walk 10 min | est. 6,000 JPY | hotel restaurant | kitchen hours `[verify]` |
| D2 Sat | 09:00-11:30 | Market morning + breakfast stalls | 2h30 | metro 20 min | est. 3,000 JPY | indoor food hall | Sat opening hours `[verify]` |
| D2 Sat | 12:00-13:00 | Lunch, market edge | 1h00 | walk 5 min | est. 4,000 JPY | station concourse | queue length `[verify]` |
| D2 Sat | 14:00-17:00 | Craft quarter, two workshops | 3h00 | bus 25 min | est. 8,000 JPY | city museum (indoor) | workshop slots `[verify]` |
| D2 Sat | 18:30-20:30 | Dinner, riverside | 2h00 | walk 12 min | est. 9,000 JPY | hotel restaurant | reservation policy `[verify]` |
| D3 Sun | 09:30-12:00 | Garden afternoon moved to morning | 2h30 | bus 30 min | est. 1,200 JPY | botanical glasshouse | garden hours `[verify]` |
| D3 Sun | 12:30-13:30 | Lunch near garden gate | 1h00 | walk 8 min | est. 3,500 JPY | convenience store picnic | Sunday hours `[verify]` |
| D3 Sun | 15:00-17:30 | Old-town lanes, no fixed entry | 2h30 | walk 20 min | 0 | department store food floor | none needed |
| D3 Sun | 18:30-20:00 | Dinner, old town | 1h30 | walk 10 min | est. 7,000 JPY | hotel restaurant | Sunday closures `[verify]` |
| D4 Mon | 09:00-10:30 | Short walk + packing | 1h30 | walk 5 min | 0 | lobby | checkout time `[verify]` |
| D4 Mon | 11:00-12:30 | Station transfer, lunch | 1h30 | metro 20 min | est. 2,500 JPY | station bento | platform change `[verify]` |

Movement totals: D1 35 min, D2 62 min, D3 68 min, D4 25 min - all inside the balanced budget.
D3 is a Sunday: the garden was moved to the morning because weekend entry queues build after
11:00 `[verify]`. Nothing was scheduled on the arrival afternoon beyond a walk.

## 3. Budget

| Category | Low | High | Basis | Assumption |
|---|---:|---:|---|---|
| Lodging | 66,000 | 96,000 | per room-night x 3 | twin room, eastern district, breakfast included `[verify]` |
| Local transit | 6,000 | 9,000 | 4-6 rides/day x 4 days | no pass purchased |
| Intercity | 0 | 0 | none in scope | airport transfer excluded, see misses |
| Food | 60,000 | 90,000 | 3 meals/day x 4 days x group | alcohol excluded |
| Tickets & activities | 10,000 | 16,000 | 4-6 entries x 2 persons | special exhibitions excluded `[verify]` |
| Buffer | 14,000 | 21,000 | 10-15% of subtotal | price drift, rounding |

Range total: JPY 156,000 - 232,000. The stated cap of 240,000 sits inside the high end, so the
plan is feasible but has no room for a large unplanned purchase.

Known misses (not in the table above): city accommodation tax, airport transfer both ways,
travel insurance, eSIM data, coin lockers on D4, card foreign-transaction fee, and any
reservation deposit at a restaurant that requires one.
Cheapest two cuts if the high end is too much: drop one workshop on D2 (est. 8,000) and move
D3 dinner to a counter restaurant (est. 3,000-4,000 saved).

## 4. Constraint Check

- Daily movement vs pace budget - `ok` (35/62/68/25 min against 120 min).
- Consecutive high-load days - `ok`; D2 is the heaviest and is followed by a lighter D3 morning.
- Meal windows vs blocks - `ok`; every dinner block starts at or after 18:00.
- Weekday vs closure risk - `tight`; D3 is a Sunday and D4 a Monday, both need an hours check
  per venue before locking the order `[verify]`.
- Arrival and departure realism - `ok`; arrival afternoon holds one walk, departure leaves 2h
  before the transfer.
- Weather exposure - `tight`; two of three outdoor anchors have only partial indoor cover.
- Accessibility - not assessed; no requirement was stated by the traveller.
- Timezone - no shift beyond 1h, no jet-lag handling needed.

## 5. Open Questions

1. Confirm the exact check-in and check-out times, or accept a luggage-hold on D4.
2. Is a rail or bus pass already owned? It changes the transit line only.
3. Any dietary limit for either traveller? It changes the D2 and D3 dinner picks.
4. Does anyone want a fixed timed entry (garden, workshop), or keep it flexible?
5. Is the Monday departure a morning or evening train? A morning train removes the D4 lunch block.

## 6. Notice

Planning output only. Nothing is booked or paid, prices are ranges built on stated assumptions,
and opening hours, fares, closure days and entry rules all need confirmation before departure.
