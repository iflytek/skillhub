# Grocery Aggregation

The value of this skill is one list, summed, in store order. Recipe-by-recipe lists cause duplicate
purchases, half-used bunches and a second trip.

## Aggregation procedure

1. **Extract** every ingredient with quantity and unit from every dish.
2. **Normalise** the unit: grams or millilitres as the base; count units (eggs, onions, lemons) stay
   as counts with an average weight noted if the recipe needs weight.
3. **Match** the same ingredient by name. `Scallion` / `spring onion` / `小葱` are one row;
   `coriander` / `cilantro` are one row.
4. **Sum** quantities in the base unit.
5. **Round up to purchase units** (a 500g pack, a bunch, a 6-pack) and note what is left over.
6. **Group by store section**, in the order you walk the store.
7. **Mark** what is a pantry staple assumed on hand (oil, salt, pepper, soy, vinegar, sugar) and
   exclude it from the buy list - but list it once under "assumed on hand" so nothing is missed.

## Store section order

Produce → Meat & fish → Dairy & eggs → Dry goods & grains → Canned & jarred → Frozen → Bakery →
Herbs & spices → Other (paper, cleaning, foil).

Order matters: heavy and frozen items last, cold chain shortest, and produce first because it is
where substitutions are easiest.

## Purchase-unit leftovers

Always show the leftover, because that is the waste driver:

| Item | Need | Buy | Left over | Plan for the leftover |
|---|---|---|---|---|
| Herbs, soft | 30 g | 1 bunch (~80 g) | ~50 g | freeze chopped in oil, or day-5 dish |
| Yoghurt | 300 g | 500 g tub | 200 g | breakfast or the sauce on day 4 |
| Cream | 150 ml | 200 ml carton | 50 ml | into the Wednesday soup |

If a leftover has no plan, either change the dish or accept the waste out loud.

## Common aggregation errors

- **Two onion counts** from two recipes, listed separately.
- **Grams and counts mixed** (`2 onions` + `300g onion`) - convert.
- **Spice assumed** but never written down, then missing at the stove.
- **Buying a pack for 20g** of an ingredient used once - check whether a substitution or a skip is
  better.
- **Ignoring what is already in the fridge** - ask, or write "assumed on hand: <list>".

## Quantities sanity band

Rough per-adult-dinner bands (adjust, do not quote):

| Category | Per adult per dinner | Note |
|---|---|---|
| Protein (meat/fish) | 150-200 g | 120-150 g if there is a second protein |
| Protein (plant, e.g. legumes) | 100-150 g dry | or 250-300 g cooked |
| Starch (dry) | 70-100 g | rice/pasta dry weight |
| Vegetables | 200-300 g | more when it is the main |
| Sauce/dressing | 40-60 ml | |
| Bread | 60-80 g | for a dinner party |

Use these to sanity-check summed totals: if the list says 3 kg of protein for 6 people over 5 days,
something is wrong.

## Output shape

One table per section (or one table with a Section column). Columns: `Section | Item | Qty | Unit |
Used by | Substitute | Verify`. The `Used by` column is the audit trail - if a row has no user, it
should not be bought.
