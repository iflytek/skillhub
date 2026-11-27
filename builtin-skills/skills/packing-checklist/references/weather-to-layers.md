# Weather to Layers

Temperature bands drive layer **count**, not item count. Work in bands, then let the trip's
activities and rain exposure adjust the shell decision.

## Bands

| Band | Day range | Base | Mid | Shell | Extras that actually matter |
|---|---|---|---|---|---|
| Hot-humid | >= 28C | 1 light breathable top | none | none (pack a 60g rain shell if rain chance) | sun hat, 2nd top for sweat, laundry bag |
| Warm | 22-27C | 1 top | 1 light overshirt | light shell for rain | one long layer for air-conditioned interiors |
| Mild | 15-21C | 1 top | 1 mid (fleece/knit) | shell | scarf or buff for wind |
| Cool | 8-14C | 1 base | 1 mid | shell + optional liner | warm hat, gloves thin |
| Cold | 0-7C | thermal base | mid + insulation | insulated outer | hat, gloves, thick socks, lip balm |
| Hard cold | < 0C | thermal base x2 | mid + down | insulated outer | face cover, mittens, hand warmers `[verify]` rules |
| Wet season | any | as per band | as per band | real shell + dry bag | 2nd pair of shoes or sandals that dry |

Rules of thumb:

- **Diurnal swing >= 10C** (desert, mountains, early spring): pack one more mid layer than the
  day band suggests; nights follow the low, not the high.
- **Rain probability >= 40%** for any day in the window: the shell becomes `must`, not
  `conditional`, and shoes must dry or be doubled.
- **Humidity >= 70%**: cotton is a liability. Two thinner tops beat one heavy one.
- **Air-conditioned interiors in a hot climate**: one long-sleeve layer is worth its volume.
- **Altitude**: temperature drops roughly 6C per 1000m; treat the destination's altitude band,
  not the city's, and mark the estimate `[verify]` if the user gave no data.

## Layer arithmetic

`layers = base + mid + shell` where each layer is removable. The number of *physical garments*
is not the layer count: one base worn and one spare base is two garments, one layer.

For a 5-day mild trip with laundry at the sink: 2 base tops, 1 mid, 1 shell, 1 pair of trousers
worn + 0 spare (wash nightly), socks 3, underwear 3.

## Do not do this

- Do not convert a forecast into a promise ("it will be 18C"). Write the band and its source.
- Do not add a down jacket to a "cold at night" city trip when one fleece plus a shell covers it.
- Do not assume the user owns technical fabrics. Describe the property (quick-dry, windproof),
  not a product.
- Do not omit the sun layer in a hot band; sunburn ruins more trips than cold.

## Presenting it

One line in the brief: `Weather: mild 15-21C, 40% rain, one 10C night -> 2 base + 1 mid + shell`.
Then the clothing table rows reference the band in their Reason column, so the user can see why
each garment exists.
