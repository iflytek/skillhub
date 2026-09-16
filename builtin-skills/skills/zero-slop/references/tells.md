# The Tell Taxonomy

A hundred and thirteen tells in six families, merged from WP:AICATCH (Wikipedia's editor
catalog, built from thousands of caught instances), the de-slop/stop-slop
detector line, petergyang/no-ai-slop, blader/humanizer, the academic
lexicon studies (Kobak, Liang, Juzek & Ward), and community taxonomies of
reader-reported tells. The scorer
(`scripts/slopscore.py`) catches the lexically detectable ones; the rest need
judgment. **Require corroboration** — one "robust" in technical prose
is nothing; five tells in one paragraph is a verdict. Shared idioms humans
still use ("elephant in the room") carry low weights for exactly that reason:
alone they prove nothing, five in a page is the machine's idiom autopilot.

### How to prioritize the catalogue

A 2026 analysis of 89,239 Reddit posts adds a useful check on what readers
notice first. In its reviewed sample, people cited flat rhythm, reflexive
praise, formulaic shape, and polished-but-empty prose more often than most
individual words. Its keyword pass also over-counted ordinary words such as
"however", "thus", "hence", "nuanced", "comprehensive", and "utilize".
Use that result to order the review, not as a probability or a blacklist.

Start with meaning, stance, rhythm, and shape. Then inspect repeated
constructions, assistant residue, and formatting. Treat isolated vocabulary
as weak evidence unless it is generic in context or appears in a cluster. A
lone dash, formal sentence, transition, or supported contrast remains a style
choice. See `evidence.md` for the study, limitations, and adoption decision.

Contextual review names six checks explicitly: paragraph-order dependence, unsupported novelty, self-labeling significance, moral-adjective category error, recap-flattery, and wall-of-text reply.

## 1. Lexical

| Tell | Fix |
|---|---|
| AI vocabulary: delve, tapestry, testament, realm, intricate, interplay, landscape, meticulous, pivotal, garner, bolster, underscore, showcase, foster, boasts | Plain word or the specific thing. "delve into" → "look at"; "the AI landscape" → name the actual companies/tools |
| Marketing register: seamless, frictionless, cutting-edge, game-changer, state-of-the-art, supercharge, paradigm shift, empower | Delete or state the concrete capability |
| Generic benefit stack: a platform, product, or service is paired with two or more interchangeable outcomes such as "more value", "greater efficiency", or "strong capabilities" | Replace the stack with one named capability, measured result, or specific use case; ask for the missing fact rather than inventing it |
| Rider buzzwords (leverage, robust, unlock, harness, streamline) | Fine in plain technical prose; slop when clustered with marketing words |
| Puffery: nestled, breathtaking, rich heritage, renowned, vibrant, groundbreaking | State the fact; let the reader judge importance |
| Legacy phrases: "a testament to", "pivotal moment", "enduring legacy", "evolving landscape", "setting the stage" | Say what happened |
| Copula avoidance: "serves as", "stands as", "functions as", "boasts", "features" | "is" / "has" |
| Stiff synonyms: utilized, authored, attempted, relocated | used, wrote, tried, moved |
| Vague quantifiers: "a wide variety of", myriad, plethora, countless, numerous | The number, or "many", or cut |
| Filler intensifiers: truly, genuinely, incredibly, undoubtedly | Cut; keep only when carrying real emphasis in the writer's voice |
| Degree intensifiers (very, really + adj) | Weak signal alone; cut in clusters |
| Business jargon: circle back, move the needle, low-hanging fruit, deep dive, double-click, boil the ocean, table stakes, north star, hit the ground running | The actual verb |
| Amplified stats: a whopping, a staggering, jaw-dropping, mind-blowing, skyrocket | State the number plainly; it carries its own weight |
| Catalog superlatives: unmatched, unrivaled, top-notch, industry-leading, must-have, hassle-free, second to none, look no further | One concrete differentiator, or nothing |
| Startup-bio vocab: visionary, trailblazing, on a mission to, passionate about, at the intersection of, thought leader | Say what you build and for whom |
| Travel-brochure vocab: picturesque, quintessential, captivating, in the heart of, perfect blend of, something for everyone | The specific detail a visitor would notice |
| Idiom autopilot: double-edged sword, tip of the iceberg, elephant in the room, perfect storm, game changer, best of both worlds, win-win, paves the way, bridge the gap, at the forefront, uncharted territory, new normal, full circle, wild west | Pre-assembled phrase → disassemble: say the actual trade-off, risk, or change |
| 2025+ era shift: emphasizing, enhance, highlight(ing), showcasing now outrank delve | Same fix; keep `data/learned.json` current |

## 2. Structural

| Tell | Fix |
|---|---|
| Listicle stems: "There are several key factors…", "Here are 5…" | Make the first point; structure follows argument |
| "Not only X but also Y" | Pick the stronger of X/Y, state it |
| Dead transitions: Moreover, Furthermore, Additionally at sentence start | "but", "so", "and", or nothing — humans cohere with connective texture, not scaffolding |
| Wrap-up scaffolding: "In conclusion", final paragraph restating the piece | End on the last concrete point or consequence |
| Rule of three: "fast, reliable, and scalable" | Two items, or one, or an actual list with content |
| "Challenges and future prospects" formula | Delete the formula; report the one real challenge |
| Rigid outline: every paragraph topic-sentence + 3 supports + mini-conclusion | Reorder; let paragraph lengths vary; put the best claim first |
| Participial analysis tails: "…, highlighting the importance of X" | Full stop, then the actual consequence ("so users can…") or nothing |
| Inline-header bullet lists (• **Header:** text) | Prose, unless it's truly a list |
| Tiny tables for prose content | Prose |
| Transformation chains: "X becomes Y. Y becomes Z." | One plain causal sentence |
| Synonym cycling (the agent/the assistant/the tool for one referent) | Repeat the clear word |
| Stacked hedges: "might possibly", "could potentially perhaps" | One hedge or none |
| Explainer stems: "in a nutshell", "simply put", "long story short", "when it comes to", "at its core", "in essence" | Cut the stem; start at the content |
| "Here's how/why/a breakdown" stems | Start with the thing itself |
| Imperative flip: "Stop X. Start Y.", "Do this instead" | Make the one claim, with the reason |
| Forecast wrap-ups: "as we move forward", "the road ahead", "as technology continues to evolve" | End on the concrete point or consequence |
| False ranges: "from strategy to culture", where the endpoints share no scale | Name the actual topics or relationship |
| Fragmented heading warm-up: a heading followed by one line that restates it | Delete the warm-up; begin with the first useful sentence |
| Diff-anchored description outside a changelog, release note, migration guide, or incident review | Describe the current behavior so the document stands on its own |
| Mechanical sentence openings: several consecutive sentences begin with the same subject or frame without building deliberate rhythm | Merge or vary the sentences; preserve purposeful anaphora |
| Jargon compression: invented compound terms in place of explanation — "threshold cliff", "length-blind floor", "pinned high forever" | Unpack into the plain explanation once, then a short name only if the document truly reuses it; the fix is unpacking, not a synonym |
| Stat pile-up: several datasets or tests crammed into one paragraph with no connective explanation | One test per paragraph, opening with what the test checks in plain words ("The first test checks that the score falls as humans get more involved"), numbers after the plain-language setup |
| Paragraph-order dependence: prose paragraphs can be shuffled without changing the argument | Rebuild a progression in which each paragraph earns the next; exempt FAQs, reference entries, independent findings, and genuine lists |
| Wall-of-text reply: an answer hides distinct steps or decisions in one unbroken block | Add only the paragraph breaks or list structure the reader needs; length alone is not the signal |

## 3. Rhetorical

| Tell | Fix |
|---|---|
| Empty hedging: "It's worth noting that", "it's important to note" | Delete the stem; keep the content |
| Didactic disclaimers: "it's crucial to remember", "results may vary" | Delete unless a real caveat, then state it precisely |
| Manufactured stakes: "in today's fast-paced world", "now more than ever" | Start where the reader needs to start |
| Performed candor: "let's be honest", "here's the thing", "truth be told" | State the point |
| Rhetorical-question openers: "Ever wondered…?", "What if I told you…?" | The answer, as a statement |
| Unsupported novelty: "the problem nobody is naming" without a comparison or source | Make the narrower supported claim, or ask for the missing basis |
| Self-labeling significance: "this matters", "this is important", or "the key insight" substitutes a label for a consequence | State the concrete consequence and let it carry the weight |
| Moral-adjective category error: a technical choice or metric is called brave, honest, ethical, or courageous without a moral agent or decision | Name the engineering property or trade-off; preserve a real moral judgment when the source supports one |
| Throat-clearing: "The uncomfortable truth is", "Let me be clear" | Cut; the claim stands alone |
| Emphasis crutches: "Make no mistake", "Let that sink in", "Read that again" | Show the weight with the fact itself |
| Meta-commentary: "In this post we'll explore", "Let me walk you through" | Just do it |
| Corrective reveal: "You've been told X. Here's the truth" | Make the claim without the posture |
| Binary contrast reveal: "The answer isn't X. It's Y." | "Y matters more than X" — and at most once per piece |
| Negative parallelism family: "It's not just X, it's Y" / "No X. No Y. Just Z." / "It wasn't A. It wasn't B. It was C." | State the positive claim once |
| Contrast reveal, extended: "isn't about X — it's about Y" (any subject, any separator), "less about X, more about Y", "didn't just X. We Y", "was never about X", "That's not X. That's Y.", "AI won't replace you. Someone using AI will." | State the positive claim once; the meter now catches every separator and subject |
| Fake epiphany: "that's when it hit me", "little did I know", "changed everything", "the rest is history", "fate had other plans" | Tell the event; skip the drumroll |
| Certainty theater: "cannot be overstated", "one thing is certain", "nothing could be further from the truth", "Full stop.", "Period.", "End of story.", "would be an understatement" | Assert it once, plainly; evidence over volume |
| Non-conclusions: "only time will tell", "remains to be seen", "the jury is still out", "the possibilities are endless", "exciting times ahead" | Commit to the call the evidence supports, or cut |
| Crowd priming: "sound familiar?", "we've all been there", "you might be wondering", "believe it or not", "trust me", "hear me out" | Respect the reader; make the claim |
| Borrowed proverbs: "Rome wasn't built in a day", "the proof is in the pudding", "actions speak louder than words" | Your own words or nothing |
| Manufactured-world openers: "Gone are the days", "In a world where", "Imagine a world where", "Picture this:", "It's 2026 and", "It's no secret that" | Start at the specific situation |
| Forced profundity: "You can't have one without the other" | Earn it or cut it |
| Calls to action: "Buckle up", "Let's dive in", "Stay tuned" | Cut |
| Weasel attribution: "Experts agree", "Studies show", "Industry reports suggest" | Name the source or cut the claim; if no source exists, ask the author |
| Canned coverage claims: "featured in prominent media outlets" | Name the outlet and what it said |
| Notability roll-call: outlet names, follower counts, or status markers with no relevance to the point | Keep only the evidence that serves the subject and give its context |
| Unraised-objection defense: "I'm not saying…", "to be clear…", or "some might say…" when no source, reader, or argument raised it | State the positive claim; keep real counterarguments, corrections, safety limits, and FAQ answers |
| Disposable alternative: "a tempting approach would be…" introduced only to reject it and never used again | State the actual constraint; keep alternatives that a reader may genuinely consider |
| Theatrical process framing: "we hired an adversary", "we summoned a skeptic" — personifying an ordinary procedure as a character | Name the actual procedure ("we ran an adversarial review of our own scorer") and let it be ordinary |
| Epigram cadence: a clever-clever aphorism where a plain statement belongs ("a cheap draft turns out to carry an expensive signal: it tells the reader how much of your attention you thought they were worth") | Keep the claim, cut the flourish; one earned aphorism per piece is already a lot |
| Metaphor flourish standing in for a plain statement: "the other half lands on the sender's name" | Say it plainly ("the sender's reputation takes the other half"); judgment call — no safe regex exists |
| Slang-cute idiom: "has receipts", "hits different", "living rent-free" | State the evidence itself; see the slang-costume ban in `overcorrection.md` |
| Hyperbole universals: "nothing on earth", "on the planet", "in history", "known to man" | State the actual scope; the honest comparison is smaller and stronger |
| Cute meta-taglines and campaign framing: "a meter you can argue with", "the fight against X" as a slogan | Describe the thing; "posts about writing quality" beats a campaign poster. "The fight against" is real usage in history and civic prose — flag the marketing register, not the phrase |
| Staccato antithesis: two short balanced sentences, the second landing the twist — "Not perfect. Honest.", "Slop isn't a vibe. It's measurable.", "The draft was cheap. The signal it sent was not." | One plain sentence with the claim; at most one antithesis per piece |
| Unmarked antithesis: the same figure with no negation marker at all, so the whole "not X, it's Y" family walks past it. Four shapes — bare subject swap ("Llama is open-weights. Dolma releases the data."); isocolon, one verb frame with both arguments swapped ("Open weights let you adapt a model. An open stack lets you adapt the machinery that created it."); the stock closer ("Ai2 argues for a principle. This is what that principle looks like."); unmarked reversal ("No frontier lab had to decide. Thai researchers made that call themselves.") | State the claim once, plainly. The meter now catches the last three (`isocolon-ditransitive`, `this-is-what-looks-like`, `no-x-had-to`); bare subject swap stays a judgment call. **Count them** — one is a device, three in a short piece is the register |
| Significance scaffolding: a sentence announcing that a point matters instead of delivering it — "Here's the detail that matters:", "This is what that principle looks like when it works." | Delete the announcement and keep the point. Budget: zero |
| Extended conceit: a process or abstraction dressed as physical drama — billing ("the bill lands on reputation", "gets billed to a reader"), courtroom ("never allowed to convict"), forensics ("rhythm leaves prints"), machinery ("opens the hood"), recipe ("has four ingredients") | At most one metaphor per piece, then plain language; name the actual mechanism |
| Vibe-slang: "just a vibe", "vibe check", "argue with vibes", "has receipts" | The plain word: impression, judgment, evidence |
| One-word drama beat: "Fine." dropped between claims as a rhythm device | Cut it or fold it into the sentence it interrupts |
| Chiasmus and mirrored wordplay: "your ear catches the even pulse your eye forgives" | Once is a flourish; as a default cadence it is performance — say it straight |

The rows from "Theatrical process framing" down are one register:
**performed-writer prose**, an AI imitating a punchy human writer. They are
the meter-side twins of the edgy-slop catalogue in `overcorrection.md` — the
same costume seen at detection time instead of rewrite time. The scorer
catches the mechanical subset (`hired-adversary`, `turns-out-payoff`,
`has-receipts`, `hyperbole-universal`, `argue-with-artifact`,
`vibe-register`, `where-x-lives`, `billed-conceit`, `on-the-tin`,
`minding-own-business`, `economics-brutal`, `opens-the-hood`, the
rider-gated "fight against", and — since v2.5.10 — three of the four unmarked
antithesis shapes: `isocolon-ditransitive`, `this-is-what-looks-like`, and
`no-x-had-to`. Epigram cadence, marked staccato antithesis, bare subject swap,
most conceits, jargon compression, and tagline register still need the
performed-register pass, because their literal forms are legitimate in news,
history, crime, and civic writing.

`isocolon-ditransitive` is worth reading closely, because it marks the boundary
between what a rule can safely reach and what it cannot. It fires only when the
**same verb** is repeated in a give-you frame across a sentence break. That
identity requirement is the whole safety property: rhetorical anaphora repeats
its frame with a *different* verb every time — "we can not dedicate, we can not
consecrate, we can not hallow" — so the rule cannot touch it. Relaxing the
backreference from the verb to the frame was tested and fires on the Gettysburg
Address, the Federalist, and an ESL engineer's email. Do not relax it.

The human-flagged spans that motivated the family live in
`data/corpus/performed-register/` — the mechanical half is regression-tested,
the judgment half is the performed-register pass's fixture list. Files move
between the two halves in both directions: `verdict-arithmetic.txt` graduated
from judgment to mechanical in v2.5.10 when a safe rule finally reached it.

## 4. Punctuation & formatting

| Tell | Fix |
|---|---|
| Em-dash overuse (density; 2+ in a sentence; spaced pairs as drama) | Commas, periods, parentheses; ≤1 per ~150 words; zero on LinkedIn |
| Title Case Headings everywhere | Sentence case |
| Bold spam mid-sentence | Unbold; if it needs emphasis, restructure |
| Emoji as bullets/headers (🚀 ✅ 👉) | Remove |
| Hashtag clusters | Zero in body; move to first comment if needed |
| Markdown artifacts in plain-text contexts | Strip |
| Chatbot markup leakage (oaicite, citeturn0…, [cite: 1], utm_source=chatgpt.com) | Strip — these are proof, not style |
| Placeholders left in ([Your Name], [Company]) | Fill or flag |
| Curly-quote inconsistency | Normalize to the document's convention |

## 5. Tone

| Tell | Fix |
|---|---|
| Assistant voice: "Great question!", "I hope this helps", "I'd be happy to" | Delete |
| Reflexive agreement or praise: approving the premise before checking it, flattering the writer, or refusing to take a supported position | Answer the substance first; agree, qualify, or disagree according to the facts |
| Recap-flattery: a reply opens by praising and paraphrasing the question before answering it | Start with the answer; keep only context the reader actually needs |
| Chatbot residue: "Would you like me to…", "Let me know if you'd like…", "my training data" | Delete — it is proof of paste, not style |
| Knowledge-cutoff residue: "as of my last update", "not widely documented" | Delete; verify the claim |
| Passive or subjectless wording that hides an actor who matters | Name the actor and use the direct verb; keep passive voice when the actor is unknown, irrelevant, or native to the genre |
| Form-letter email: "wanted to reach out", "touch base", "don't hesitate to reach out" | Say the actual ask in the first sentence |
| LinkedIn ritual: "some personal news", "a new chapter", "bittersweet", "couldn't be prouder", "this is your sign", "I'll go first", "today years old" | The fact, then stop; feeling shown through detail |
| Promotional drift in neutral contexts | Neutral statement of fact |
| Uniform flawless register (every sentence equally polished) | Vary: blunt next to careful, casual next to technical |
| Excess positivity, joy-skewed affect | Allow doubt, irritation, dry humor where genuine |
| Fake humanization (edgy-slop) | See `overcorrection.md` — it's still slop |

## 6. Content-emptiness (judgment only — no regex can see these)

| Tell | Test | Action |
|---|---|---|
| Hollowness — no claim at all | Removal test: delete it; anything lost? | Flag, never pad |
| Communicative drift — fluent sentences accumulate without serving a clear point or reader need | Purpose test: what job does this paragraph do here? | Cut it, rebuild it around the real point, or ask for the missing intent |
| Rhetorical scale mismatch — a grand contrast, lesson, or reveal is applied to a trivial or unsupported claim | Proportion test: does the framing match the importance and support of the point? | State the point at its real scale; preserve a contrast when it corrects a real misconception |
| Regression to the mean — specifics smoothed into generic + inflated importance | Compare against source facts | Restore the specific |
| Smooth-but-empty specificity — "modern technologies that ensure reliability" | Can you name the referent? | Name it or cut |
| Superficial analysis — unearned significance commentary | Who says it matters? | State the mechanism or cut |
| Fabricated support — invented citations, stats, anecdotes | Verify every reference | Remove; ask author for real one |
| Speculative gap-filling — "likely supports…" | Is there a source? | Cut or mark as open question |

## What is NOT a tell (do not flag)

Perfect grammar. Formal prose where the genre demands it. A transition word in
isolation. Long sentences that earn their length. Technical vocabulary used
technically. A single em-dash doing real work. First-person hedging that
encodes real uncertainty. Unsourced-but-checkable claims. And any pattern that
is demonstrably the writer's own voice in a sample the AI assistant can read.
A single contrast that corrects a real, supported misconception is not a tell.
The named `--voice` scoring profile is narrower: it exempts only existing
watchlist words found by exact match. One match is enough, but the exceptions
apply only when the profile is selected. The profile does not model the
writer's full style.
