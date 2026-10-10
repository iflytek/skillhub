# Over-correction — the second failure mode

The classic humanizer failure is swapping AI-slop for a louder slop. Readers
clock both. Everything here is a rewrite *output* ban: never introduce these
into text that didn't have them.

## The edgy-slop catalogue

- **Forced contrarianism** — "Everyone says X. They're wrong." (unless the
  source argued it)
- **Fake first person** — "I've seen this a hundred times", "In my
  experience…" injected into authorless prose. Manufactured war stories are
  fabrication, the cardinal sin.
- **Performed candor** — "Let's be real", "Here's the thing", "I'll be
  honest": candor is shown, not announced.
- **Staccato drama** — "This matters. A lot. More than you think." Broetry
  fragmentation is the LinkedIn variant.
- **Em-dash theatrics** — dashes manufacturing emphasis the content didn't
  earn. (Yes, humanizers add these; yes, it reads as AI.)
- **Binary-contrast reveals** — "The answer isn't more tools. It's
  discipline." One per piece max; injecting them is over-correction.
- **Manufactured stakes** — "In a world where…", "Now more than ever".
- **Intensifier padding as personality** — "genuinely", "honestly",
  "literally" sprinkled for flavor.
- **Slang costume** — forced colloquialisms a professional author wouldn't
  use ("chef's kiss", "hits different") unless the voice sample has them.
- **Manufactured informality** — forced lowercase, stray "lol", conspicuous
  swearing, or broken grammar added to look human. Preserve these when they are
  already part of the writer's voice; never inject them as camouflage.
- **Fake errors** — never inject typos or grammar mistakes to fool
  detectors. That's adversarial evasion, not writing, and it degrades the
  text.
- **Performed-writer prose** — theatrical framing of ordinary work ("we
  hired an adversary"), epigram closers, staccato antithesis ("Not perfect.
  Honest."), extended conceits (billing, courtroom, forensics, recipe),
  hyperbole ("nothing on earth"), slang-cute idioms ("has receipts"), and
  cute meta-taglines. The detection-side rows live in `tells.md` §3;
  injecting them is the same costume-swap.

The bar is a *thinking* author, not a *loud* one.

## What NOT to flag (false-positive guard)

From Wikipedia's "ineffective indicators" plus detector-calibration
experience — these alone are NOT evidence of AI:

- Perfect grammar and spelling
- Formal or technical register where the genre demands it
- A transition word, an em-dash, a "however" in isolation
- Long sentences that earn their length
- Rule-of-three used once, deliberately, for rhythm
- Domain jargon used correctly for a domain audience
- Calibrated hedging in research/medical/legal writing
- Text merely being unsourced (check it, don't flag it)

Require corroboration. A paragraph needs multiple independent tells, or a failed
removal test, before it's slop.

This governs lexical flags only. It does not apply to the performed-register
family: register is a property of the piece, not of a paragraph. Four unmarked
antithesis pairs across four paragraphs *is* the corroboration — each one is
locally defensible, and the repetition is the whole finding.

## Signs of human writing — preserve on sight

When a draft shows these, protect them through the rewrite; deleting them is
damage:

- A claim someone could disagree with, stated without cover
- The specific odd fact ($1.1M, 4,000 users, "episode 142")
- Selective hedging at the edge of the author's knowledge
- Humor, irritation, dry asides, self-interruption
- Digressions that carry personality; asymmetric structure
- Insider references assumed, not explained
- The author's pet phrases and punctuation habits (voice sample rules)
- Mistakes of passion — a run-on in an excited passage. Leave it.

## Idempotence check

Run the finished rewrite through the scorer and this file once more. If your
rewrite added any catalogue item above, you traded costumes. Prefer the
smaller edit: the best de-slop is usually deletion of the hedge plus nothing.
