# Pace, Radius and Daily Load

The single most common planning failure is not "wrong place" - it is **too many movements per
day**. Transit is systematically underestimated because plans are built from map distance, while
real door-to-door time includes waiting, walking to the platform, finding the exit, queues and
getting lost. A workable rule: **estimate transit, then add 25-40%** unless the user supplied
real numbers.

## Pace table

| Pace | Anchors / day | Areas / day | Daily movement budget | Typical user phrasing |
|---|---:|---:|---|---|
| `relaxed` | 1-2 | 1 | <= 60 min total transit | "not too packed", "with kids", "带老人" |
| `balanced` | 2-3 | 1-2 | <= 120 min total transit | default, unstated preference |
| `packed` | 3-4 | 2-3 | <= 180 min total transit | "see as much as possible", "第一次来想多看" |

An **anchor** is a place that justifies the day: a museum, a hike, a district walk, a show. Meals,
coffee and shopping are not anchors. Two anchors in the same building count as one.

## Daily shape rules

- **Arrival day** is a half day: immigration, baggage, transfer, check-in, one nearby walk, early
  dinner. Do not schedule a timed entry on arrival day.
- **Departure day** reserves bag-drop, transfer and a 2h airport buffer; one light anchor at most.
- **Timezone shift >= 5h**: the first day after arrival ends early; put the most demanding anchor
  on day 2 or 3, not day 1.
- **Consecutive load**: never place three `packed` days in a row without a `relaxed` day between
  them, unless the user explicitly asks for it.
- **Meal windows** are protected: lunch 11:30-13:30, dinner 18:00-20:00 local. A plan that puts an
  anchor across both is a defect.
- **One indoor fallback per outdoor anchor.** Rain is not an edge case; it is the default failure.

## Cluster rule

Group candidate places into clusters that a person can walk between in <= 20 minutes, or reach in
one transit leg without a transfer. Then:

- one cluster per day for `relaxed`;
- one cluster plus an adjacent one for `balanced`;
- two clusters plus one adjacent for `packed`.

If a day needs two transfers each way, it is two days of content in one day's clothes.

## Red flags to name explicitly

- A day whose transit total exceeds its activity total.
- "Morning in A, afternoon in B, dinner in C" with A and C on opposite sides of the city.
- A market, a museum and a viewpoint scheduled inside one 4-hour window.
- Any plan that assumes a fixed 30-minute city transfer with no margin.
- Back-to-back timed entries (tickets with a slot) with transit between them and no buffer.

## Repair moves, cheapest first

1. Move one anchor to the adjacent day.
2. Swap a far anchor for a nearby equivalent.
3. Convert a "see it" into a "walk past it on the way".
4. Cut the weakest anchor and name what was cut and why.
5. Split the day into two half-days and state the trade.

Always say which move was applied. Silent repairs look like the original plan was fine.
