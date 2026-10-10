#!/usr/bin/env python3
"""slopscore — check writing for common AI-style patterns.

The 0-to-100 writing score covers familiar phrases, sentence variety,
readability, formatting, and tone. Lower is better. The score describes the
writing; it does not identify who wrote it, decide whether the ideas are useful,
or check whether every claim is true. Zero Slop handles those questions in its
editorial review.

Usage (runnable from any cwd; data resolves relative to this script):
    python3 slopscore.py <file>            # pretty report
    python3 slopscore.py --json <file>     # machine-readable
    python3 slopscore.py --dna a.md b.md   # show what changed
    python3 slopscore.py --fidelity a.md b.md  # facts kept? anything added?
    cat text | python3 slopscore.py        # stdin
    python3 slopscore.py --explain <file>  # report + reasons + line-by-line map
    python3 slopscore.py --heatmap <file>  # line-by-line map only
    python3 slopscore.py --portfolio <dir> # repeated wording across related drafts
    python3 slopscore.py --batch <dir> --json --gate 25  # machine-readable CI gate
    python3 slopscore.py --formal <file>   # use the rules for professional writing

The phrase lists live beside this script in ../data/patterns.json and
../data/learned.json. Editing those files requires no code change.
"""
import bisect
import functools
import hashlib
import json
import math
import re
import sys
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
SHAPE_SOLO_THRESHOLD = 0.62  # calibrated, see calibrate.py --shape
MAX_BATCH_FILES = 1_000
MAX_BATCH_FILE_BYTES = 10 * 1024 * 1024
MAX_BATCH_TOTAL_BYTES = 100 * 1024 * 1024


# Where personal voice profiles live — outside the repo, since they are the
# user's own writing. One file per author, git-ignored by construction.
import os

# Personal voice profiles are read only when the caller explicitly selects one.
HOME = Path(os.environ.get("ZERO_SLOP_HOME") or Path.home() / ".zero-slop").expanduser()
VOICE_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}\Z")


class PatternData(dict):
    """JSON-compatible pattern mapping with an out-of-band compiled plan."""


def _voice_path(name):
    """Resolve a profile name without letting it become a filesystem path."""
    if not VOICE_NAME.fullmatch(name or "") or name in (".", ".."):
        raise ValueError(
            "voice name must be 1-64 letters, digits, dots, underscores, or hyphens"
        )
    root = (HOME / "voices").resolve()
    path = (root / f"{name}.json").resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:  # defense in depth if the name rule changes
        raise ValueError("voice profile resolves outside the voice directory") from exc
    return path


def _merge_learned(base, learned_path):
    """Merge one validated layer; malformed entries never break scoring."""
    if not learned_path.exists():
        return
    try:
        learned = json.loads(learned_path.read_text(encoding="utf-8"))
        if not isinstance(learned, dict):
            raise ValueError("learned data must be an object")
        raw_patterns = learned.get("patterns", [])
        if not isinstance(raw_patterns, list):
            raise ValueError("learned patterns must be a list")
        valid_patterns = []
        for q in raw_patterns:
            if not isinstance(q, dict):
                continue
            name, rx, weight, category = (q.get("name"), q.get("rx"),
                                          q.get("w"), q.get("cat"))
            if (not isinstance(name, str) or not 1 <= len(name) <= 128
                    or not isinstance(category, str) or not 1 <= len(category) <= 64
                    or not isinstance(rx, str)
                    or not isinstance(weight, (int, float))
                    or isinstance(weight, bool)
                    or not math.isfinite(weight) or not 0 <= weight <= 10
                    or len(rx) > 2000
                    or re.search(r"\\[1-9]|\(\?<*[=!]|\([^()]*[+*][^()]*\)[+*]", rx)):
                continue
            try:
                re.compile(rx)
            except re.error:
                continue
            valid_patterns.append(q)

        # Later layers win by name. This is how a private false-positive update
        # can lower one shared weight without editing the installed taxonomy.
        by_name = {q["name"]: q for q in base["patterns"]}
        order = [q["name"] for q in base["patterns"]]
        for q in valid_patterns:
            if q["name"] not in by_name:
                order.append(q["name"])
            by_name[q["name"]] = q
        base["patterns"] = [by_name[name] for name in order]
        for field in ("lexicon", "riders"):
            raw = learned.get(field, {})
            if not isinstance(raw, dict):
                continue
            clean = {term: weight for term, weight in raw.items()
                     if isinstance(term, str) and 1 <= len(term) <= 80
                     and isinstance(weight, (int, float))
                     and not isinstance(weight, bool)
                     and math.isfinite(weight) and 0 <= weight <= 10}
            base.setdefault(field, {}).update(clean)
    except (json.JSONDecodeError, UnicodeDecodeError, OSError, ValueError, TypeError):
        return


def load_patterns(voice=None):
    base = json.loads((DATA_DIR / "patterns.json").read_text(encoding="utf-8"))
    _merge_learned(base, DATA_DIR / "learned.json")       # reviewed, shared
    if voice:
        _apply_voice(base, voice)
    return PatternData(base)


def _apply_voice(base, name):
    """Apply one explicitly selected private scoring profile.

    ``keep`` zero-weights existing lexicon and rider terms. ``mute`` lists the
    labels of existing patterns, but the sample-based builder does not populate
    it. This changes only the local score; it does not infer or model the
    writer's full style, and an unselected profile has no effect.
    """
    prof_path = _voice_path(name)
    if not prof_path.exists():
        return
    try:
        prof = json.loads(prof_path.read_text(encoding="utf-8"))
        if not isinstance(prof, dict):
            return
        keep_raw, mute_raw = prof.get("keep", []), prof.get("mute", [])
        if not isinstance(keep_raw, list) or not isinstance(mute_raw, list):
            return
    except (json.JSONDecodeError, UnicodeDecodeError, OSError, TypeError):
        return
    keep = {k.lower() for k in keep_raw if isinstance(k, str)}
    for term in list(base.get("lexicon", {})):
        if term.lower() in keep:
            base["lexicon"][term] = 0
    for term in list(base.get("riders", {})):
        if term.lower() in keep:
            base["riders"][term] = 0
    for pat in base["patterns"]:
        if pat["name"] in {m for m in mute_raw if isinstance(m, str)}:
            pat["w"] = 0


SENT_SPLIT = re.compile(r"(?<=[.!?])[\")”’]?\s+(?=[A-Z“\"(0-9])")
WORD = re.compile(r"[A-Za-z’']+")


# A quoted span longer than this is a passage, not a named tell, and stays in
# scope. Short enough to exempt "delve" or "it's not just X, it's Y"; short
# enough that quoting cannot be used to smuggle paragraphs past the meter.
QUOTE_SKIP_LIMIT = 200

_BLOCKQUOTE_SCAN_RX = re.compile(r"(?m)^[ \t]*>[ \t]?.*$")
_INLINE_QUOTE_RXS = (
    re.compile(rf'"[^"\n]{{0,{QUOTE_SKIP_LIMIT}}}"'),
    re.compile(rf"“[^”\n]{{0,{QUOTE_SKIP_LIMIT}}}”"),
)


def mask_quoted(text):
    """Blank quoted material for the pattern meter, keeping every offset.

    Naming a cliche in order to discuss it is the opposite of committing it,
    and step 0 of SKILL.md has always said to skip quotes. Only the phrase
    meter and the lexicon honour that: rhythm, readability, word variety and
    formatting still read the quotation, because a quote a writer chose to
    include is part of how the finished page reads.

    Spans are replaced character for character, so sentence offsets, word
    counts and hit positions are identical to the unmasked text.
    """
    def blank(match):
        return re.sub(r"[^\n]", " ", match.group(0))

    text = _BLOCKQUOTE_SCAN_RX.sub(blank, text)
    for rx in _INLINE_QUOTE_RXS:
        text = rx.sub(blank, text)
    return text

# Normalise only detector-evasion characters, never ordinary non-Latin prose.
# A Cyrillic or Greek lookalike is mapped only when it appears in the same word
# as an ASCII letter (for example, dеlvе). This keeps Russian and Greek text
# untouched while preventing an invisible substitution from bypassing a known
# phrase. Adapted from the normalisation pre-pass in conorbronsdon/
# avoid-ai-writing, reviewed at commit 40328bd292bc682d46010a6f9ac2cdbf4fb4ceca.
ZERO_WIDTH_RX = re.compile(r"[\u200b-\u200d\ufeff\u2060]")
SUSPICIOUS_UNICODE_RX = re.compile(
    r"[\u200b-\u200d\ufeff\u2060\u0370-\u03ff\u0400-\u04ff"
    r"\u00a0\u1680\u2000-\u200a\u202f\u205f\u3000\uff01-\uff5e]"
)
# A run of non-breaking or typographic spaces defeats a phrase rule as surely as
# a zero-width joiner, and full-width Latin defeats it while still reading as
# ordinary prose. Both are folded to ASCII for matching only; the draft the
# writer gets back keeps its original characters.
UNICODE_SPACE_RX = re.compile(r"[\u00a0\u1680\u2000-\u200a\u202f\u205f\u3000]")
FULLWIDTH_RX = re.compile(r"[\uff01-\uff5e]")
CJK_RX = re.compile(r"[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]")
MIXED_SCRIPT_WORD_RX = re.compile(r"[A-Za-z\u0370-\u03ff\u0400-\u04ff]+")
LOOKALIKES = {
    "а": "a", "е": "e", "о": "o", "р": "p", "с": "c", "х": "x",
    "у": "y", "к": "k", "м": "m", "н": "h", "в": "b", "т": "t",
    "А": "A", "Е": "E", "О": "O", "Р": "P", "С": "C", "Х": "X",
    "У": "Y", "К": "K", "М": "M", "Н": "H", "В": "B", "Т": "T",
    "ο": "o", "Ο": "O", "α": "a", "Α": "A", "ρ": "p", "Ρ": "P",
}


def normalize_for_detection(text):
    """Return detector text plus a count of hidden/lookalike characters."""
    # Smart punctuation and accented prose are common, but neither requires a
    # word-by-word mixed-script pass. Stop after one fast search unless the
    # text actually contains a hidden, Cyrillic, or Greek code point.
    if text.isascii() or not SUSPICIOUS_UNICODE_RX.search(text):
        return text, {"zero_width": 0, "homoglyphs": 0}
    text, zero_width = ZERO_WIDTH_RX.subn("", text)
    # Typographic spaces are ordinary in real prose, so they are folded for
    # matching but never counted as evidence of tampering.
    text = UNICODE_SPACE_RX.sub(" ", text)
    fullwidth = 0
    if FULLWIDTH_RX.search(text):
        counts_as_evasion = not CJK_RX.search(text)
        text, replaced = FULLWIDTH_RX.subn(
            lambda m: chr(ord(m.group(0)) - 0xFEE0), text
        )
        # Full-width Latin inside CJK text is normal typography, not evasion.
        if counts_as_evasion:
            fullwidth = replaced
    if not re.search(r"[\u0370-\u03ff\u0400-\u04ff]", text):
        return text, {"zero_width": zero_width, "homoglyphs": fullwidth}
    homoglyphs = 0

    def mixed_word(match):
        nonlocal homoglyphs
        token = match.group(0)
        if not re.search(r"[A-Za-z]", token):
            return token
        out = []
        for char in token:
            replacement = LOOKALIKES.get(char)
            if replacement is not None:
                homoglyphs += 1
                out.append(replacement)
            else:
                out.append(char)
        return "".join(out)

    return MIXED_SCRIPT_WORD_RX.sub(mixed_word, text), {
        "zero_width": zero_width,
        "homoglyphs": homoglyphs + fullwidth,
    }


@functools.lru_cache(maxsize=1024)
def _pattern_regex(rx, multiline):
    """Compile a weighted pattern once without changing its match semantics."""
    return re.compile(rx, re.I | (re.M if multiline else 0))


def _pattern_plan(data):
    """Compile and validate the current pattern layer once per loaded profile."""
    cached = getattr(data, "_compiled_pattern_plan", None)
    if cached is not None:
        return cached
    plan = []
    for pattern in data["patterns"]:
        hints = pattern.get("hints")
        if not (isinstance(hints, list) and hints
                and all(isinstance(hint, str) for hint in hints)):
            hints = None
        plan.append((pattern.get("w"), pattern["cat"], pattern["name"],
                     _pattern_regex(pattern["rx"], bool(pattern.get("m"))),
                     pattern["rx"].lower(), hints))
    compiled = tuple(plan)
    if isinstance(data, PatternData):
        data._compiled_pattern_plan = compiled
    return compiled


@functools.lru_cache(maxsize=16)
def _term_scan_plan(entries):
    """Build a bounded, reusable first-character index for term scanning.

    The previous implementation ran one full document scan per term. This plan
    scans word starts once, then tests only terms that can begin there. Odd
    private terms that do not start with a word character keep the old path.
    """
    buckets = {}
    fallback = []
    for order, (term, weight) in enumerate(entries):
        if not weight:
            continue
        # Python's IGNORECASE has a few non-ASCII equivalences that ``casefold``
        # does not map back to one character (İ/i is the common example). Keep
        # those uncommon private terms on the reference path so indexing cannot
        # silently miss a match.
        if (term and term[0].isascii()
                and (term[0].isalnum() or term[0] == "_")):
            key = term[0].casefold()
            buckets.setdefault(key, []).append(
                (order, term, weight, re.compile(re.escape(term) + r"\w*", re.I))
            )
        else:
            fallback.append(
                (order, term, weight,
                 re.compile(r"\b" + re.escape(term) + r"\w*", re.I))
            )
    groups = []
    by_group = {}
    for index, (key, rows) in enumerate(buckets.items()):
        group = f"c{index}"
        groups.append(f"(?P<{group}>{re.escape(key)})")
        by_group[group] = rows
    starter = re.compile(r"\b(?:" + "|".join(groups) + r")", re.I) if groups else None
    return starter, by_group, tuple(fallback)


def _term_candidates(text, terms):
    """Return the old term-match vector with one document-wide starter scan."""
    entries = tuple(terms.items())
    starter, by_group, fallback = _term_scan_plan(entries)
    found = []
    if starter is not None:
        for start_match in starter.finditer(text):
            start = start_match.start()
            for order, term, weight, pattern in by_group[start_match.lastgroup]:
                match = pattern.match(text, start)
                if match is not None:
                    found.append((start, match.end(), order, term, weight,
                                  match.group(0).lower()))
    for order, term, weight, pattern in fallback:
        for match in pattern.finditer(text):
            found.append((match.start(), match.end(), order, term, weight,
                          match.group(0).lower()))
    found.sort(key=lambda row: (row[0], -row[1], row[2]))
    return [(start, end, term, weight, quote)
            for start, end, _, term, weight, quote in found]


def strip_noise(text):
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    # Markdown table rules are layout syntax, not repeated dashes in prose.
    # Leave the table's words available to the language and rhythm checks, but
    # remove delimiter rows such as ``|---|---:|`` before punctuation scoring.
    text = re.sub(
        r"(?m)^[ \t]*\|?[ \t]*:?-{3,}:?[ \t]*"
        r"(?:\|[ \t]*:?-{3,}:?[ \t]*)+\|?[ \t]*$",
        " ",
        text,
    )
    # Inline `code` spans still render as visible prose, so their words are
    # scored; only the backticks go. Fenced blocks are genuinely code.
    text = re.sub(r"`([^`\n]*)`", r"\1", text)
    # URLs are otherwise noise, but a model-specific tracking parameter is a
    # machine artifact in its own right. Preserve only the artifact token so
    # ordinary URL text cannot affect prose rhythm or vocabulary.
    text = re.sub(
        r"https?://\S+",
        lambda m: " " + " ".join(re.findall(
            r"(?:utm_source=(?:chatgpt(?:\.com)?|openai(?:\.com)?|"
            r"copilot(?:\.com)?|claude\.ai|perplexity\.ai|gemini\.google\.com)"
            r"|referrer=grok\.com)", m.group(0), re.I
        )) + " ",
        text,
    )
    return text


def _sentence_spans(text):
    """(start, end) spans of ``sentences(text)`` in ``text`` coordinates.

    Newlines inside a paragraph flatten to spaces, which preserves length, so
    a span's slice differs from its sentence string only by that replacement.
    Rider hits are sentence-scoped but dedup against pattern hits needs
    document offsets; this keeps one sentence definition for both.
    """
    spans = []
    start = 0
    breaks = [m.span() for m in re.finditer(r"\n\s*\n", text)]
    for para_end, next_start in breaks + [(len(text), len(text))]:
        flat = text[start:para_end].replace("\n", " ")
        prev = 0
        cuts = [m.span() for m in SENT_SPLIT.finditer(flat)]
        for cut_start, cut_end in cuts + [(len(flat), len(flat))]:
            seg = flat[prev:cut_start]
            core = seg.strip()
            if len(WORD.findall(core)) >= 2:
                lead = len(seg) - len(seg.lstrip())
                spans.append((start + prev + lead,
                              start + prev + lead + len(core)))
            prev = cut_end
        start = next_start
    return spans


def sentences(text):
    return [text[a:b].replace("\n", " ") for a, b in _sentence_spans(text)]


def _merge_spans(spans):
    merged = []
    for s, e in sorted(spans):
        if merged and s <= merged[-1][1]:
            if e > merged[-1][1]:
                merged[-1] = (merged[-1][0], e)
        else:
            merged.append((s, e))
    return merged


def _span_covered(span, merged):
    """True if [s, e) intersects any interval in a merged, sorted list."""
    s, e = span
    i = bisect.bisect_left(merged, (e,))
    return i > 0 and merged[i - 1][1] > s


def cv(values):
    if len(values) < 2:
        return 1.0
    m = sum(values) / len(values)
    if m == 0:
        return 1.0
    var = sum((v - m) ** 2 for v in values) / (len(values) - 1)
    return math.sqrt(var) / m


def score_text(text, data, formal=False):
    if not isinstance(text, str):
        raise TypeError("text must be a string")
    raw = text
    text, normalization = normalize_for_detection(strip_noise(text))
    words = WORD.findall(text)
    n_words = len(words)
    word_den = max(n_words, 1)
    type_token_ratio = (len({word.casefold() for word in words}) / word_den
                        if n_words >= 200 else None)
    sent_spans = _sentence_spans(text)
    sents = [text[a:b].replace("\n", " ") for a, b in sent_spans]
    # Same string with quotations blanked out, used only by the phrase meter
    # and the lexicon. Offsets match `text` exactly.
    scan_text = mask_quoted(text)
    scan_sents = [scan_text[a:b].replace("\n", " ") for a, b in sent_spans]
    hits = []
    pattern_spans = []  # (start, end, lower-rx, compiled-rx) for dedup below

    # One stray hidden character can come from a rich-text paste. A cluster is
    # worth reporting, but the normalised wording is scanned at either count.
    if normalization["zero_width"] + normalization["homoglyphs"] >= 2:
        hits.append({
            "cat": "artifact", "name": "normalization-bypass", "w": 5,
            "quote": (f"{normalization['zero_width']} hidden and "
                      f"{normalization['homoglyphs']} lookalike characters"),
        })
    # The incumbent's published 1,654-paragraph provenance corpus gives this
    # conservative long-form signal 22.46x machine/human lift (20/779 versus
    # 1/875). Keep it weak and cluster-dependent: narrow vocabulary is normal
    # in some technical writing and never convicts on its own.
    if type_token_ratio is not None and type_token_ratio < 0.40:
        hits.append({
            "cat": "rhythm", "name": "low-word-variety", "w": 1.5,
            "quote": f"{type_token_ratio:.0%} distinct words across {n_words} words",
        })

    # 1. Pattern tells (regex, weighted). A reviewed pattern may include literal
    # hints that are guaranteed to cover every branch. They cheaply skip a full
    # regex scan when none is present; patterns without that guarantee run as
    # before.
    lowercase_text = None
    for weight, category, name, compiled, lower_rx, hints in _pattern_plan(data):
        if not weight:
            continue
        if hints:
            if lowercase_text is None:
                lowercase_text = scan_text.lower()
            if not any(hint in lowercase_text for hint in hints):
                continue
        for m in compiled.finditer(scan_text):
            hits.append({
                "cat": category, "name": name, "w": weight,
                "quote": m.group(0)[:90].strip(),
            })
            pattern_spans.append((m.start(), m.end(), lower_rx, compiled))

    # 2. Lexicon. Two tiers, because context decides. Always-on terms
    # ("delve", "tapestry") almost never appear in honest prose. Rider terms
    # ("robust", "landscape", "elevated") are ordinary technical vocabulary
    # and only count when a marketing-register trigger shares their sentence —
    # so "elevated write volume" in a runbook is silent while "elevate your
    # brand with our seamless platform" fires. Sentence-scoped, not global.
    #
    # A term a pattern already charges is the same evidence counted twice —
    # "is a testament to" must convict the phrase once, not the phrase plus
    # the word. The pattern owns the term when its regex writes the term out
    # ("testament" in puffery-testament) or matches the term's own text
    # ("game.?chang" on "game-changing"); an independent tell that merely
    # lands inside another tell's span — a lexicon word inside a
    # rhetorical-structure match — still counts. Overlapping lexicon stems
    # ("game-chang", "game-changing") collapse to one hit the same way.
    claimed = _merge_spans([(s, e) for s, e, _, _ in pattern_spans])

    def _pattern_owns(span, term, matched):
        if not _span_covered(span, claimed):
            return False
        s, e = span
        return any(ps < e and s < pe
                   and (term in rx_lower or compiled.search(matched))
                   for ps, pe, rx_lower, compiled in pattern_spans)

    candidates = [candidate for candidate in _term_candidates(scan_text, data["lexicon"])
                  if not _pattern_owns(candidate[:2], candidate[2], candidate[4])]
    last_end = 0
    for s, e, term, w, quote in candidates:
        if s < last_end:
            continue
        last_end = e
        hits.append({"cat": "lexicon", "name": term, "w": w, "quote": quote})
    riders, triggers = data.get("riders", {}), data.get("rider_triggers", [])
    if riders:
        for (a, _), sent in zip(sent_spans, scan_sents):
            sl = sent.lower()
            if not any(t in sl for t in triggers):
                continue
            for term, w in riders.items():
                if not w:
                    continue
                for m in re.finditer(r"\b" + re.escape(term) + r"\w*", sent, re.I):
                    if _pattern_owns((a + m.start(), a + m.end()),
                                     term, m.group(0)):
                        continue
                    hits.append({"cat": "rider", "name": term, "w": w,
                                 "quote": m.group(0).lower()})

    pattern_weight = sum(h["w"] for h in hits)
    # Density window is floored at 60 words (a single tell in a 7-word tweet
    # must not read as 100/100) and the long-text dilution is bounded by also
    # tracking absolute weight: a 2000-word piece cannot hide 20 tells. The
    # absolute floor scales with length past 1,000 words, because a fixed
    # floor convicts on sheer accumulation — weight 42 anywhere meant a
    # book-length text with one mild tell every couple thousand words scored
    # the same as a tell-dense post and could never pass the gate.
    tell_density = 100.0 * pattern_weight / max(n_words, 60)
    weight_floor = min(pattern_weight / 3.0, 14.0) * min(1.0, 1000.0 / word_den)
    tell_density = max(tell_density, weight_floor)

    # 3. Rhythm: burstiness = coefficient of variation of sentence lengths.
    # Human prose ~0.55-0.75; machine prose clusters ~0.25-0.45.
    slens = [len(WORD.findall(s)) for s in sents]
    burstiness = cv(slens)
    # Short texts give unstable CV estimates — scale the penalty in by length.
    length_conf = min(1.0, len(sents) / 8.0)
    uniformity_penalty = 0.0 if formal else (
        max(0.0, (0.42 - burstiness)) * 35 * length_conf)

    # 4. Punctuation / formatting densities (per 100 words)
    # Fenced code is not prose. Counting CLI flags such as `--gate` as dash-heavy
    # style made technical READMEs look machine-written, so formatting channels
    # operate on the same code-stripped text as the language channels.
    emdash = 100.0 * len(re.findall(r"—|--", text)) / max(n_words, 120)
    # Capped: dash-heavy but otherwise excellent prose (Lincoln, Dickinson)
    # must not be convicted on punctuation alone.
    emdash_penalty = min(max(0.0, emdash - 0.6) * 6, 8.0)
    emoji = len(re.findall(r"[\U0001F300-\U0001FAFF✅✨⚡\U0001F449\U0001F447\U0001F680\U0001F525]", text))
    emoji_penalty = min(emoji * 2.0, 12)
    # Bold as mid-sentence emphasis is the tell (WP:AICATCH); bold used as a
    # label at the start of a line/list item is ordinary document formatting.
    bold = 0
    for match in re.finditer(r"\*\*[^*\n]{2,60}\*\*", raw):
        prefix = raw[raw.rfind("\n", 0, match.start()) + 1:match.start()]
        if re.match(r"[\s>*#-]*(?:\d+\.\s*)?$", prefix):
            continue
        if re.match(r"[ \t]*\|", prefix):
            continue  # bold totals in a Markdown table are ordinary layout
        bold += 1
    bold_penalty = min(max(0, bold - 1) * 1.5, 9)
    hashtags = len(re.findall(r"(?<!\S)#\w+", text))
    hashtag_penalty = min(hashtags * 1.2, 8)

    # 5. Register: contraction scarcity in casual genres reads machine-formal.
    contractions = len(re.findall(r"\b\w+[’'](?:t|s|re|ve|ll|d|m)\b", text))
    contraction_rate = 100.0 * contractions / word_den
    formality_penalty = 0.0 if formal else (
        3.0 if contraction_rate < 0.4 and n_words > 80 else 0.0)

    # 6. Followability: density without accessibility reads machine-compressed,
    # not expert. Signals: noun-phrase chains (many commas, no verbs between),
    # heavy polysyllabic ratio, and overlong sentences. Formal genres exempt
    # (their register legitimately runs denser).
    poly_ratio = sum(1 for w in words if len(w) >= 9) / word_den
    chain_frac = sum(1 for s in sents if s.count(",") >= 4) / max(len(sents), 1)
    overlong_frac = sum(1 for L in slens if L > 38) / max(len(slens), 1)
    followability_penalty = 0.0 if formal else min(
        max(0.0, poly_ratio - 0.14) * 40 + chain_frac * 9 + overlong_frac * 7,
        12.0)

    # Clusters convict, singles don't. Em-dash density and missing contractions
    # are stylistic habits, not evidence on their own — 19th-century oratory and
    # plenty of excellent formal prose trip both. So corroborate them against
    # lexical evidence: with no tells present they contribute little. Emoji and
    # hashtags stay at full strength (they convict alone), and burstiness is an
    # independent statistical signal, so neither is scaled. Bold emphasis rides
    # in the stylistic sum below: heavy mid-sentence bold is a real tell in
    # company, but on its own it is a formatting habit, and seven bold spans
    # with zero other evidence must not reach the gate.
    # The floor was 0.45, which handed style 45% weight on text with no lexical
    # evidence whatsoever. Measured against genuine human technical prose that
    # convicted 5 of 8 documents: AGENTS.md scored 59.2 on one weight-2.5 hit in
    # 392 words. Corroboration has to be earned, so the floor is now low enough
    # that dashes and formal register alone cannot carry a verdict.
    corroboration = min(1.0, 0.10 + tell_density / 2.5)
    stylistic = ((emdash_penalty + formality_penalty) * corroboration
                 + uniformity_penalty + followability_penalty + bold_penalty)
    # No lexical evidence at all means no cluster, and the rule is that
    # clusters convict. Style alone (dashes, long sentences, formal register,
    # even rhythm, bold-heavy emphasis) describes plenty of excellent human
    # prose — 19th-century oratory, dense technical writing — so with zero
    # emoji or hashtag spam, style can raise suspicion but must never convict.
    # The cap releases gradually as lexical evidence accumulates. A step
    # release at density 1.5 rebuilt the cliff this clamp exists to prevent:
    # one weight-1 arrow in a 66-word note crossed the threshold and unlocked
    # the whole stylistic budget in a single jump, 20 to 87. Interpolating the
    # cap between density 1.5 and 4 means each increment of lexical evidence
    # buys a proportional amount of style; a lone weak hit still charges its
    # own density, but never someone else's category.
    if emoji == 0 and hashtags == 0:
        release = min(1.0, max(0.0, (tell_density - 1.5) / 2.5))
        stylistic = min(stylistic, 3.5 + release * max(0.0, stylistic - 3.5))
    evidence = (
        tell_density * 1.15
        + stylistic
        + emoji_penalty
        + hashtag_penalty
    )
    ai_likelihood = round(100 / (1 + math.exp(-(evidence - 9.0) / 4.0)), 1)

    cats = {}
    for h in hits:
        cats[h["cat"]] = round(cats.get(h["cat"], 0) + h["w"], 1)

    return {
        "score_kind": "heuristic_surface_meter",
        "calibrated_probability": False,
        "ai_likelihood": ai_likelihood,
        "evidence": round(evidence, 2),
        "tell_density_per_100w": round(tell_density, 2),
        "n_words": n_words,
        "n_sentences": len(sents),
        "type_token_ratio": (None if type_token_ratio is None
                              else round(type_token_ratio, 3)),
        "burstiness": round(burstiness, 3),
        "emdash_per_100w": round(emdash, 2),
        "emoji_count": emoji,
        "bold_spans": bold,
        "hashtags": hashtags,
        "contraction_per_100w": round(contraction_rate, 2),
        "followability_penalty": round(followability_penalty, 2),
        "poly_ratio": round(poly_ratio, 3),
        "comma_chain_frac": round(chain_frac, 3),
        "overlong_frac": round(overlong_frac, 3),
        "normalization": normalization,
        "categories": cats,
        "hits": hits,
    }


# ── cross-draft portfolio channel ────────────────────────────────────────────
# A single draft cannot reveal that ten unrelated posts all begin with the same
# five words. The Slop Index measures opener repetition across repeated samples
# of one prompt, and Shaib et al. (arXiv:2509.19163) identify repetition and
# templatedness as separate slop dimensions. This channel reports that evidence
# across a directory of drafts. It deliberately stays outside the 0–100 score:
# the current corpus is too small to calibrate a safe universal weight, and
# repeated domain language can be legitimate.
PORTFOLIO_STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from",
    "has", "have", "he", "her", "his", "i", "in", "is", "it", "its", "of",
    "on", "or", "our", "she", "that", "the", "their", "they", "this", "to",
    "was", "we", "were", "will", "with", "you", "your",
}


def portfolio_metrics(documents, opener_words=5, phrase_words=5):
    """Return interpretable repetition evidence across several drafts.

    ``documents`` is an iterable of ``(name, text)`` pairs. Exact opener and
    phrase matches are normalized to lowercase words. The result is a
    diagnostic, not a score or authorship verdict.
    """
    if (not isinstance(opener_words, int) or isinstance(opener_words, bool)
            or opener_words < 1 or not isinstance(phrase_words, int)
            or isinstance(phrase_words, bool) or phrase_words < 1):
        raise ValueError("opener_words and phrase_words must be positive integers")
    docs, names = [], set()
    for name, text in documents:
        name = str(name)
        if name in names:
            raise ValueError(f"duplicate document name: {name}")
        if not isinstance(text, str):
            raise ValueError(f"document {name!r} is not text")
        names.add(name)
        docs.append((name, WORD.findall(strip_noise(text).lower())))
    out = {
        "score_kind": "portfolio_template_diagnostic",
        "calibrated_probability": False,
        "measured": len(docs) >= 3,
        "n_documents": len(docs),
        "opener_words": opener_words,
        "phrase_words": phrase_words,
        "repeated_openers": [],
        "shared_phrases": [],
        "reason": "",
    }
    if len(docs) < 3:
        out["reason"] = "needs at least 3 drafts"
        return out

    opener_docs = {}
    phrase_docs = {}
    for name, words in docs:
        if len(words) >= opener_words:
            opener = " ".join(words[:opener_words])
            opener_docs.setdefault(opener, set()).add(name)
        seen = set()
        for i in range(max(0, len(words) - phrase_words + 1)):
            gram_words = words[i:i + phrase_words]
            # Common glue shared by several documents is not a useful template.
            if all(w in PORTFOLIO_STOPWORDS for w in gram_words):
                continue
            seen.add(" ".join(gram_words))
        for phrase in seen:
            phrase_docs.setdefault(phrase, set()).add(name)

    repeated = [(opener, sorted(names)) for opener, names in opener_docs.items()
                if len(names) >= 2]
    repeated.sort(key=lambda item: (-len(item[1]), item[0]))
    repeated_opener_texts = {opener for opener, _ in repeated}
    shared = [(phrase, sorted(names)) for phrase, names in phrase_docs.items()
              if len(names) >= 2 and phrase not in repeated_opener_texts]
    shared.sort(key=lambda item: (-len(item[1]), item[0]))
    out["repeated_openers"] = [
        {"text": opener, "documents": names, "document_count": len(names)}
        for opener, names in repeated
    ]
    out["shared_phrases"] = [
        {"text": phrase, "documents": names, "document_count": len(names)}
        for phrase, names in shared[:20]
    ]
    return out


def render_portfolio(result):
    """Plain-language portfolio report for the command-line interface."""
    out = ["", "  RELATED DRAFTS · repeated wording", "",
           "  This check is separate from the 0-to-100 writing score."]
    if not result["measured"]:
        return out + [f"  Not checked: {result['reason']}.", ""]
    out.append(f"  Drafts checked: {result['n_documents']}")
    if result["repeated_openers"]:
        out.append("  repeated openings:")
        for row in result["repeated_openers"][:8]:
            out.append(f"    {row['document_count']:>2} drafts  {row['text']!r}")
    else:
        out.append("  repeated openings: none")
    if result["shared_phrases"]:
        out.append("  shared five-word phrases:")
        for row in result["shared_phrases"][:8]:
            out.append(f"    {row['document_count']:>2} drafts  {row['text']!r}")
    else:
        out.append("  shared five-word phrases: none")
    return out + ["  Suggestion: vary repeated openings and stock wording while keeping facts and voice.", ""]



# ── shape channel ─────────────────────────────────────────────────────────────
# Broetry (every sentence its own paragraph) is invisible to every other
# channel: paragraph structure is flattened before scoring, so identical words
# in 26 paragraphs or 1 score the same to the decimal. Worse, broetry's
# fragment/long-sentence mix INFLATES burstiness, so the rhythm channel that
# exists to catch machine cadence is satisfied by the tell itself.
#
# This is reported as its own axis and never folded into ai_likelihood, for
# two reasons. Mechanically, anything added to `stylistic` dies at the
# corroboration clamp exactly when broetry is the only tell. Conceptually,
# broetry is a slop tell, not a machine tell: LinkedIn writers invented it
# years before GPT-3, and it demonstrably performs on the platform. Whether to
# trade reach for a human voice is the author's call, not the meter's.
STRUCT_MARK = re.compile(r"^\s*(?:[-–—*+•>#]|\d+[.)]|\|)")
DIALOGUE_OPEN = re.compile("^[\"“‘']")


def shape_metrics(text, genre="general"):
    """Paragraph-shape signals. Gated by genre; abstains when unreliable."""
    out = {"genre": genre, "measured": False, "solo_frac": None,
           "prose_paras": 0, "max_fragment_run": 0, "broetry": None,
           "reason": ""}
    if genre != "social":
        out["reason"] = f"not measured (genre={genre}; shape signals apply to social posts)"
        return out
    raw = [p.strip() for p in re.split(r"\n\s*\n", strip_noise(text)) if p.strip()]
    # Guards BEFORE the metric — these genres are structurally identical to
    # broetry and score harder than the real thing.
    prose = [p for p in raw
             if not STRUCT_MARK.match(p)                       # lists, headings, tables
             and not DIALOGUE_OPEN.match(p)                    # dialogue
             and len(WORD.findall(p)) >= 3]                    # stubs
    out["prose_paras"] = len(prose)
    if len(prose) < 8:                                         # mirrors length_conf
        out["reason"] = f"abstains ({len(prose)} prose paragraphs; needs 8+)"
        return out
    solo = sum(1 for p in prose if len(sentences(p)) <= 1)
    # Lists and dialogue were excluded from ``prose`` above, so they must also be
    # excluded from the fragment-run half of the verdict. Otherwise three short
    # bullets after an ordinary post can manufacture a broetry failure.
    frag, run, best = [len(WORD.findall(s))
                       for s in sentences("\n\n".join(prose))], 0, 0
    for L in frag:
        run = run + 1 if L < 7 else 0
        best = max(best, run)
    out.update(measured=True, solo_frac=round(solo / len(prose), 2),
               max_fragment_run=best, reason="")
    out["broetry"] = out["solo_frac"] >= SHAPE_SOLO_THRESHOLD and best >= 3
    return out


def band(score):
    if score < 25:
        return "clear"
    if score < 50:
        return "some issues"
    if score < 75:
        return "needs work"
    return "major rewrite"


# Plain-English names and fixes, keyed by pattern category. The internal
# category is a maintenance label; a writer needs to know what it is and what
# to do instead.
CAT_MEANING = {
    "linkedin":      ("canned LinkedIn phrase", "say what happened without the stock opening"),
    "marketing":     ("promotional language", "name what it does; cut the adjectives"),
    "scaffolding":   ("empty setup", "delete the opening and keep the point"),
    "hedging":       ("empty hedge", "commit, or cut the sentence"),
    "lexicon":       ("overused AI-style word", "use the plain word"),
    "rider":         ("buzzword used as promotion", "use the plain word, or drop the hype around it"),
    "performed":     ("performed writer's voice", "say the thing plainly instead of performing it"),
    # Covers both the negation-marked family ("it's not X, it's Y") and the
    # bare balanced pairs added in v2.5.10 (isocolon, "This is what X looks
    # like", "No X had to…; Y did"), which carry no negation marker at all.
    "contrast":      ("two-part contrast used as a formula", "state the claim once, plainly; at most one per piece"),
    "puffery":       ("unearned significance", "state the fact, let the reader judge"),
    "drama":         ("manufactured drama", "the fact should carry the weight"),
    "triads":        ("rule of three", "two items, or one, or a real list"),
    "filler":        ("filler word", "cut it; the sentence survives"),
    "stakes":        ("manufactured stakes", "start where the reader needs to start"),
    "verbs":         ("weak verb", "use the direct verb"),
    "assistant":     ("assistant voice", "delete; you are not a chatbot"),
    "artifact":      ("unfinished template language", "fill it in or remove it"),
    "overcorrection":("forced edgy phrasing", "restore a natural speaking voice"),
    "spec-notation": ("shorthand inside a sentence", "write it as a sentence"),
    "cliche":        ("stock cliché", "disassemble it: say the actual trade-off or change"),
    "rhetorical":    ("staged question or setup", "make the point without the setup"),
    "email":         ("form-letter email phrase", "say the actual ask in the first sentence"),
    "misc":          ("generic AI-style wording", "rewrite plainly"),
}


def _severity(w):
    """Absolute bands, so bars mean the same thing in every document."""
    if w >= 10: return "heavy", 8
    if w >= 5:  return "moderate", 5
    if w >= 2:  return "mild", 3
    return "trace", 2


def render_heatmap(text, data, formal=False, max_rows=8, width=8):
    """A map a writer can act on: where the slop is, how bad, and what to do."""
    clean = strip_noise(text)
    doc = score_text(text, data, formal=formal)
    paras = [p for p in re.split(r"\n\s*\n", clean) if p.strip()]
    rows = []
    for pi, para in enumerate(paras, 1):
        for s in sentences(para):
            w, cats, quotes = 0.0, [], []
            _sl = s.lower()   # hoisted out of the hit loop: this was
                              # recomputed once per hit, giving O(sentences x hits)
            for h in doc["hits"]:
                q = h["quote"].lower()
                if q and q in _sl:
                    w += h["w"]
                    cats.append(h["cat"])
                    quotes.append(q)
            rows.append({"para": pi, "sent": s, "w": round(w, 1),
                         "cats": cats, "quotes": quotes})
    total = len(rows)
    dirty = [r for r in rows if r["w"] > 0]
    out = []
    if not total:
        return out
    if not dirty:
        out.append(f"  WRITING CHECK · {total} sentences · no flagged phrases")
        out.append("  " + "·" * min(total, 40) + "   all clean")
        return out

    out.append(f"  WHERE TO EDIT · {total} sentences · {len(dirty)} flagged "
               f"· strongest first")
    out.append("")
    for r in sorted(dirty, key=lambda r: -r["w"])[:max_rows]:
        label, fill = _severity(r["w"])
        bar = "█" * fill + "░" * (width - fill)
        # quote the trigger, not the whole sentence — that is what to change
        trig = max(r["quotes"], key=len)[:46]
        out.append(f'  {bar}  {label:<8} ¶{r["para"]}  “{trig}”')
        seen, notes = set(), []
        for c in r["cats"]:
            if c in seen:
                continue
            seen.add(c)
            name, fix = CAT_MEANING.get(c, (c, "rewrite plainly"))
            notes.append(f"{name} — {fix}")
        for n in notes[:2]:
            out.append(f'  {" " * width}            {n}')
    if len(dirty) > max_rows:
        out.append(f'  {" " * width}            …and {len(dirty)-max_rows} more')
    out.append("")
    # document shape: one block per paragraph, so clustering is visible
    shape = []
    for pi in range(1, len(paras) + 1):
        pw = sum(r["w"] for r in rows if r["para"] == pi)
        shape.append("█" if pw >= 10 else "▓" if pw >= 5 else "▒" if pw > 0 else "·")
    out.append(f'  draft overview  {" ".join(shape)}   █ heavy  ▓ moderate  '
               f'▒ mild  · clean')
    return out


def gate_value():
    """Return (threshold, raw_token) for --gate, consuming its argument."""
    if "--gate" not in sys.argv:
        return None, None
    i = sys.argv.index("--gate")
    try:
        tok = sys.argv[i + 1]
        value = float(tok)
        if not math.isfinite(value) or not 0 <= value <= 100:
            raise ValueError
        return value, tok
    except (IndexError, ValueError):
        raise SystemExit("--gate needs a finite threshold from 0 to 100")


CHANNELS = [
    # label, how to pull the number, which direction is better, how to show it
    ("word choice",   lambda r: sum(h["w"] for h in r["hits"]
                                    if h["cat"] in ("lexicon", "rider")), "low"),
    ("phrasing",      lambda r: sum(h["w"] for h in r["hits"]
                                    if h["cat"] not in ("lexicon", "rider")), "low"),
    ("sentence variety", lambda r: r["burstiness"], "high"),
    ("readability",   lambda r: r["followability_penalty"], "low"),
    ("formatting",    lambda r: r["emdash_per_100w"] + r["emoji_count"]
                                + r["hashtags"], "low"),
]


# What counts as a fact worth preserving. Deliberately narrow: things a reader
# could check, and things whose invention is the failure the skill forbids.
FACT_RX = [
    ("figure",  r"(?<![\w.])\$?\d[\d,]*(?:\.\d+)?\s*(?:%|percent|x|bn|m|k|million|billion)?(?![\w])"),
    # A name never spans a line break. Allowing \s+ here let a run swallow the
    # paragraph boundary after a heading -- "Leverage\n\nThe", "Mishra\n\nPaste"
    # -- and the invented run then read as a dropped entity in any rewrite that
    # repunctuated the section.
    ("name",    r"\b(?:[A-Z][a-z]{2,}(?:[ \t]+[A-Z][a-z]+)*)\b"),
    ("quote",   r"[\u201c\"]([^\u201d\"]{6,120})[\u201d\"]"),
    ("url",     r"https?://\S+"),
]
# Sentence-initial capitals are not names. Neither are these.
NOT_NAMES = set("""The This That These Those We They It He She You I A An And But Or
So Then Now Here There When While If After Before Our Their His Her Its My Your
Most Many Some Every Each Both All No Not One Two Three Four Five Six Seven
Eight Nine Ten First Second Third Last Next Why How What Which Who Where
See Read Use Run Add Set Get Let Note Also Just Only Even Still Yet Once
More Less Best Worst Same Other Another Such Very Much Well Then Than
Shipped Built Made Added Fixed Moved Cut Kept Found Gave Took Went Came
Said Did Had Was Were Been Being Done Going Getting Started Stopped
Because Since Though Although Unless Until Whether Given Once Yet""".split())

# Spelled-out numbers, mapped to digits. A rewrite that turns "18 months" into
# "Eighteen months" is faithful, but the raw extractor read "18" as a dropped
# figure and "Eighteen" as an invented name — two false alarms from one honest
# edit. Normalising both texts to digits before extracting cancels that, and
# because the same transform runs on the original and the rewrite, it can never
# manufacture a mismatch that was not already there.
NUM_WORDS = {
    "zero": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
    "six": "6", "seven": "7", "eight": "8", "nine": "9", "ten": "10",
    "eleven": "11", "twelve": "12", "thirteen": "13", "fourteen": "14",
    "fifteen": "15", "sixteen": "16", "seventeen": "17", "eighteen": "18",
    "nineteen": "19", "twenty": "20", "thirty": "30", "forty": "40",
    "fifty": "50", "sixty": "60", "seventy": "70", "eighty": "80",
    "ninety": "90", "hundred": "100", "thousand": "1000",
    "million": "1000000", "billion": "1000000000",
}
# Only normalise numbers of eleven or more. "one".."ten" double as articles and
# pronouns ("one of them", "two ways"), so digitising them invents figures that
# were never quantities; from eleven up, a spelled number is almost always a real
# count ("eighteen months", "forty percent", "a hundred users").
_NUM_RX = re.compile(
    r"\b(" + "|".join(w for w, d in NUM_WORDS.items() if int(d) >= 11) + r")\b", re.I)


def _spell_to_digits(text):
    return _NUM_RX.sub(lambda m: NUM_WORDS[m.group(0).lower()], text)


# Common English words that legitimately start sentences and so get capitalised,
# but are not names — "Draw the diagram", "Usually it works", "Start here". The
# entity regex cannot tell these from "Priya" or "Acme" (which are never ordinary
# words), so an explicit frequency list carries the difference. This is a
# precision aid only: a word here is skipped as a name in BOTH texts, so it can
# widen a miss but never invent a false fabrication flag.
COMMON_WORDS = set("""
about above across again against along already also although always among another
any anyone around away back become been before behind below better between beyond
build building built call called celebrate change changed come coming could deploy
deployment deployments double doing down draw during each either enough every
everyone everything except finally find found from give given going gone great grow
growing hard help here however instead into keep kept later least leave less look
looking made make making many maybe might migrate more most move moving much must
never next nobody nothing often once only other over people perhaps ready really
right run running same say saying send sent set ship shipping should show shown
since some someone something soon start started still stop such take taken talk
tell than that their them then there these they thing things think this those
though through today together too took toward tried true trying turn under until
upon usually using very want was way well went were what when where which while
whole will with within without work working would writing agree agreeing
monday tuesday wednesday thursday friday saturday sunday none plenty seats reps
fix sit mid ambiguity team teams user users product feature features day days week
weeks month months year years time thing done anyway besides meanwhile therefore
worse worst harder easier simpler faster slower bigger smaller lots plus minus
are artificial bookmark despite hey modern please researchers save unpopular welcome
""".split())
NOT_NAME_WORDS = {word.lower() for word in NOT_NAMES} | COMMON_WORDS

# These ordinary subjects are capitalised by sentence position. Exempt only
# the generic construction, not the word everywhere: "Efficiency raised $4M"
# and "Efficiency Labs" still contain protected names. A quoted construction
# remains protected by the independent quotation check.
_ABSTRACT_SUBJECT = re.compile(
    r"(?:^|(?<=[.!?])\s+|\n[ \t]*)"
    r"(?P<subject>Efficiency|Productivity|Innovation|Quality|Reliability|Success|Customer)"
    r"(?: (?:(?:experience|service|support|satisfaction)))?"
    r" is (?:paramount|crucial|essential|important|key|everything)\b",
    re.M,
)


def _peel_entity(run, prose, other):
    """The entity inside a title-case run, or None if the run holds no name.

    A capitalised ordinary word glues itself to the name that follows it --
    "With Claude", "In March", "At Acme". Discarding the whole run loses the
    entity, so the rewrite that punctuates the sentence differently gets
    reported as having dropped a name that is still sitting in it. Peel the
    leading word and re-test what remains.
    """
    while run:
        if run in NOT_NAMES or len(run) < 3:
            return None
        low = run.lower()
        tokens = re.findall(r"[a-z]+", low)
        # A title-cased run made entirely of ordinary sentence words is not an
        # entity (for example, "Shipped Tuesday").
        if tokens and all(token in NOT_NAME_WORDS for token in tokens):
            return None
        single = " " not in run
        # A capitalised common word ("Draw", "Usually", "Start"), an adverb
        # ("Finally"), or a sentence-opening gerund ("Watching") is not an
        # entity; a real name never is.
        if single and (low in COMMON_WORDS
                       or low.endswith("ly") or low.endswith("ing")):
            return None
        # A word is only a name if it is never used as an ordinary lowercase
        # word -- not here, and not in the text we compare against.
        # "Under"/"Shipped" appear lowercased somewhere in normal prose;
        # "Priya"/"Acme" do not. Strip the capitalised forms first so the
        # entity cannot vouch for itself.
        head = run.split()[0]
        blob = re.sub(r"\b" + re.escape(head) + r"\b", " ", prose + " " + other)
        if not re.search(r"\b" + re.escape(head.lower()) + r"\b", blob):
            return run
        if single:
            return None
        run = run.split(" ", 1)[1]
    return None


def facts(text, _other=""):
    """Checkable claims in a draft: figures, named entities, quotes, links."""
    # URLs contain lowercase forms of the names they point at ("acme.io" made
    # "Acme" look like a sentence opener in the source and an invention in the
    # rewrite), so entity detection runs on the text with links removed.
    urls = text  # links keep their spelled forms; numbers in a slug are not facts
    prose = _spell_to_digits(re.sub(r"https?://\S+", " ", text))
    # The first word in a prose-style Markdown heading is capitalised by
    # position, not necessarily a named entity ("## Private learning"). Keep
    # real multi-token title-case names such as "Basis Ventures" intact.
    prose = re.sub(
        r"(?m)^(#{1,6}\s+)([A-Z][a-z]{2,})(?=\s+(?![A-Z][a-z]+\b))",
        lambda m: m.group(1) + m.group(2).lower(), prose,
    )
    # Ordered-list markers describe structure, not quantities. Treating the
    # ``1.`` in a three-item list as a dropped fact penalises a faithful prose
    # rewrite and hides real numeric changes in noise.
    prose = re.sub(r"(?m)^\s*\d+[.)]\s+", "", prose)
    other = _spell_to_digits(_other)
    other = re.sub(r"(?m)^\s*\d+[.)]\s+", "", other)
    ordinary_subject_positions = {m.start("subject") for m in _ABSTRACT_SUBJECT.finditer(prose)}
    out = {}
    for kind, rx in FACT_RX:
        found = set()
        flags = re.I if kind == "figure" else 0
        for m in re.finditer(rx, urls if kind == "url" else prose, flags):
            v = (m.group(1) if m.lastindex else m.group(0)).strip()
            if kind == "name":
                if m.start() in ordinary_subject_positions:
                    continue
                v = _peel_entity(v, prose, other)
                if not v:
                    continue
            if kind == "figure":
                v = v.replace(",", "").lstrip("$").rstrip().lower()
                v = re.sub(r"\s*percent$", "%", v)
                v = re.sub(r"\s*(million|bn|billion|m|k)$",
                           lambda x: {"million":"m","billion":"bn"}.get(x.group(1), x.group(1)), v)
            if kind == "url":
                # a link at the end of a sentence carries the full stop
                v = v.rstrip(".,;:)]}\u201d\"'")
            if v:
                found.add(v)
        out[kind] = found
    return out


LIMITATION_RX = re.compile(
    r"\b(?:not|no|never|without|unmeasured|unknown|uncertain|unable|cannot|can't|"
    r"didn't|doesn't|isn't|wasn't|weren't|hasn't|haven't|hadn't)\b",
    re.I,
)
LIMITATION_STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "did",
    "do", "does", "for", "from", "had", "has", "have", "he", "her", "his",
    "i", "in", "is", "it", "its", "no", "not", "of", "on", "or", "our",
    "she", "that", "the", "their", "they", "this", "to", "was", "we", "were",
    "with", "without", "you", "never", "unable", "cannot", "unknown", "uncertain",
}
LIMITATION_CANON = {
    "measure": "measure", "measured": "measure", "measuring": "measure",
    "measurement": "measure", "measurements": "measure",
    "track": "track", "tracked": "track", "tracking": "track",
    "test": "test", "tested": "test", "testing": "test",
    "verify": "verify", "verified": "verify", "verifying": "verify",
    "verification": "verify",
    "assess": "assess", "assessed": "assess", "assessing": "assess",
    "assessment": "assess",
}


def limitation_claims(text):
    """Conservative signatures for explicitly qualified or negative claims.

    These signatures are intentionally a backstop, not semantic equivalence.
    If a rewrite substantially rephrases a limitation, the assistant must
    compare it manually rather than silently accepting a possible reversal.
    """
    out = set()
    for sentence in sentences(text):
        if not LIMITATION_RX.search(sentence):
            continue
        words = []
        for word in re.findall(r"[A-Za-z][A-Za-z'-]*", sentence.lower()):
            canonical = LIMITATION_CANON.get(word, word)
            if canonical not in LIMITATION_STOP_WORDS and len(canonical) > 1:
                words.append(canonical)
        if words:
            out.add(" ".join(sorted(set(words))))
    return out


# Interior states the author has to have supplied. The benchmark's one
# fabrication was exactly this shape — "by test day the real thing felt
# familiar" — and an entity check cannot see it, because no name or figure moved.
# First-person emotional state and the body-as-feeling idiom. Kept deliberately
# tight: "I felt/was <emotion>", "my heart/stomach ...", not every clause with
# a feeling verb, because the goal is catching an INVENTED inner state, and the
# comparison below cancels any that were already in the source.
INTERIOR_STATE_RX = re.compile(
    r"\b(?:I|we)\s+(?:was|were|am|felt|feel|got)\s+"
    r"(?:(?:very|really|extremely|quite|so)\s+)?(?P<state>[A-Za-z]+)", re.I)
INTERIOR_COGNITION_RX = re.compile(
    r"\b(?:I|we)\s+(?P<cognition>remember(?:ed)?|recall(?:ed)?|realise(?:d)?|"
    r"realize(?:d)?|knew|fear(?:ed)?|hope(?:d)?|worr(?:y|ied)|panic(?:ked)?|"
    r"struggl(?:e|ed)|doubt(?:ed)?)\b", re.I)
INTERIOR_BODY_RX = re.compile(
    r"\b(?:my|our)\s+(?P<body>heart|stomach|gut|chest|hands|mind)\b", re.I)
INTERIOR_IMPERSONAL_RX = re.compile(
    r"\bit\s+felt\s+(?P<impersonal>surreal|unreal|impossible|inevitable|like)\b", re.I)
INTERIOR_BARE_RX = re.compile(
    r"\bfelt\s+(?P<bare>familiar|natural|surreal|foreign|inevitable|effortless)\b", re.I)
COGNITION_CANON = {
    "remembered": "remember", "recalled": "remember", "recall": "remember",
    "realised": "realize", "realise": "realize", "realized": "realize",
    "feared": "fear", "hoped": "hope", "worried": "worry",
    "panicked": "panic", "struggled": "struggle", "doubted": "doubt",
}


def interior_claims(text):
    """Inner-state assertions, reduced to a comparable core so paraphrase of an
    existing one does not read as a new invention."""
    out = {m.group("state").lower() for m in INTERIOR_STATE_RX.finditer(text)}
    for m in INTERIOR_COGNITION_RX.finditer(text):
        word = m.group("cognition").lower()
        out.add(COGNITION_CANON.get(word, word))
    out.update("body:" + m.group("body").lower()
               for m in INTERIOR_BODY_RX.finditer(text))
    out.update(m.group("impersonal").lower()
               for m in INTERIOR_IMPERSONAL_RX.finditer(text))
    out.update(m.group("bare").lower() for m in INTERIOR_BARE_RX.finditer(text))
    return out


# Exact or logical document structures that an editorial rewrite must not
# silently alter. The content checks are intentionally narrow and deterministic;
# the AI assistant still compares full meaning and format after this script.
FENCED_CODE_RX = re.compile(
    r"(?ms)^(?:```|~~~)[^\n]*\n.*?^(?:```|~~~)[ \t]*$"
)
YAML_FRONTMATTER_RX = re.compile(r"\A---\n.*?\n---(?=\n|\Z)", re.S)
INLINE_CODE_RX = re.compile(r"`[^`\n]+`")
BLOCKQUOTE_LINE_RX = re.compile(r"^[ \t]*>[^\n]*$", re.M)
HEADING_RX = re.compile(r"^(#{1,6})[ \t]+(.+?)[ \t]*$", re.M)
PATH_RX = re.compile(
    r"(?<![\w:])((?:\.\.?/|/)[A-Za-z0-9._~\-]+"
    r"(?:/[A-Za-z0-9._~\-]+)*|(?:[A-Za-z0-9._~\-]+/)+"
    r"[A-Za-z0-9._~\-]+\.[A-Za-z0-9._~\-]+|"
    r"[A-Za-z]:\\[A-Za-z0-9._\\~\-]+)"
)


def _mask_fenced(text):
    return FENCED_CODE_RX.sub(
        lambda match: re.sub(r"[^\n]", " ", match.group(0)), text
    )


def _line_blocks(text, predicate):
    """Consecutive matching lines, without swallowing adjacent prose."""
    blocks, current = [], []
    for line in text.splitlines():
        if predicate(line):
            current.append(line)
        elif current:
            blocks.append("\n".join(current))
            current = []
    if current:
        blocks.append("\n".join(current))
    return blocks


def _blockquote_blocks(text):
    return _line_blocks(text, lambda line: bool(re.match(r"^[ \t]*>", line)))


def _normalize_blockquote(block):
    return "\n".join(
        re.sub(r"^[ \t]*>[ \t]?", "", line).rstrip()
        for line in block.splitlines()
    ).rstrip()


def _table_blocks(text):
    blocks = _line_blocks(
        text,
        lambda line: bool(re.match(r"^[ \t]*\|.*\|[ \t]*$", line)),
    )
    return [block for block in blocks if len(block.splitlines()) >= 2]


def _normalize_table(block):
    rows = []
    for line in block.splitlines():
        cells = [re.sub(r"\s+", " ", cell.strip())
                 for cell in line.strip().strip("|").split("|")]
        if cells and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
            cells = ["-" for _ in cells]
        rows.append("|".join(cells))
    return "\n".join(rows)


def _missing_items(left, right):
    """Multiset subtraction: duplicate protected spans stay significant."""
    remaining = list(right)
    missing = []
    for item in left:
        try:
            remaining.remove(item)
        except ValueError:
            missing.append(item)
    return missing


def structure_changes(before, after):
    """Blocking changes to code, reference blocks, paths, and hierarchy."""
    findings = []

    def add(code, message, added=False):
        findings.append({"code": code, "message": message, "added": added})

    original_code = FENCED_CODE_RX.findall(before)
    edited_code = FENCED_CODE_RX.findall(after)
    if len(original_code) != len(edited_code):
        add("code-block-count",
            f"fenced code block count changed: {len(original_code)} to {len(edited_code)}",
            len(edited_code) > len(original_code))
    elif any(left != right for left, right in zip(original_code, edited_code)):
        add("code-block-modified", "a fenced code block changed")

    original_yaml = YAML_FRONTMATTER_RX.search(before)
    edited_yaml = YAML_FRONTMATTER_RX.search(after)
    original_yaml = original_yaml.group(0) if original_yaml else None
    edited_yaml = edited_yaml.group(0) if edited_yaml else None
    if original_yaml != edited_yaml:
        add("frontmatter-modified", "YAML front matter changed",
            original_yaml is None and edited_yaml is not None)

    original_prose, edited_prose = _mask_fenced(before), _mask_fenced(after)
    # URL path segments are already checked as URLs and are not filesystem
    # paths. Mask them here so sentence punctuation cannot manufacture a path
    # mismatch ("https://acme.io/blog" versus the same link before a full stop).
    original_path_prose = re.sub(r"https?://\S+", " ", original_prose)
    edited_path_prose = re.sub(r"https?://\S+", " ", edited_prose)
    protected = [
        ("blockquote", [_normalize_blockquote(x) for x in _blockquote_blocks(original_prose)],
         [_normalize_blockquote(x) for x in _blockquote_blocks(edited_prose)]),
        ("table", [_normalize_table(x) for x in _table_blocks(original_prose)],
         [_normalize_table(x) for x in _table_blocks(edited_prose)]),
        ("inline-code", INLINE_CODE_RX.findall(before), INLINE_CODE_RX.findall(after)),
        ("path", PATH_RX.findall(original_path_prose), PATH_RX.findall(edited_path_prose)),
    ]
    for label, original, edited in protected:
        missing = _missing_items(original, edited)
        added = _missing_items(edited, original)
        if missing:
            code = f"{label}-missing" if label in {"inline-code", "path"} else f"{label}-modified"
            add(code, f"{len(missing)} {label} item(s) changed or disappeared")
        if added:
            add(f"{label}-added", f"{len(added)} new {label} item(s) appeared", True)

    original_headings = [(len(markers), text) for markers, text
                         in HEADING_RX.findall(before)]
    edited_headings = [(len(markers), text) for markers, text
                       in HEADING_RX.findall(after)]
    if len(original_headings) != len(edited_headings):
        add("heading-count",
            f"heading count changed: {len(original_headings)} to {len(edited_headings)}",
            len(edited_headings) > len(original_headings))
    elif any(left[0] != right[0]
             for left, right in zip(original_headings, edited_headings)):
        add("heading-level", "heading hierarchy changed")
    return findings


# An unsourced figure -- "the 10x move", "tenfold", "~70% of pilots fail" -- is
# an intensifier wearing a number's clothes. The gate exists to protect facts,
# and a figure with no source behind it is not one. Protecting it anyway made
# the gate report the honest cut as a dropped fact, and rerank sorts on
# fidelity first, so the rewrite that KEPT the fake precision won. That is the
# gate preserving slop, which is the opposite of its job.
#
# Which figures are load-bearing is a contextual judgment, so this script does
# not make it. No pattern can separate "fell 40%" from "10x better" reliably --
# the difference is whether a source stands behind the number, which lives in
# the surrounding document, not in the digits. The tool's job is to hand the
# reviewer the evidence; the ruling belongs to the assistant running the
# verifier role (SKILL step 7, "Unsourced statistics") or to the writer. A
# figure is protected until someone with context says otherwise, so the default
# behaviour here is exactly as strict as it was before.


def figure_contexts(text, figures):
    """Each figure with the sentence it sits in, so a reviewer can rule on it.

    Evidence, not a verdict: the caller decides whether a dropped figure was a
    measured fact that must be restored or an unsourced flourish that was right
    to cut.
    """
    out = {}
    for figure in figures:
        for sentence in sentences(text):
            if re.search(r"\b" + re.escape(figure) + r"\b", sentence, re.I):
                out[figure] = " ".join(sentence.split())
                break
        else:
            out[figure] = ""
    return out


def load_adjudication(path, original):
    """Load explicit dropped-figure rulings bound to one exact source text.

    The file is intentionally small and closed-schema. It cannot weaken name,
    quote, URL, feeling, or structure checks, and it cannot excuse a number that
    was not present in the source it names.
    """
    source = Path(path)
    try:
        if source.stat().st_size > 65_536:
            raise ValueError("adjudication file exceeds 64 KiB")
        payload = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read adjudication file: {exc}") from exc
    if not isinstance(payload, dict):
        raise ValueError("adjudication file must be a JSON object")
    expected = {"schema", "original_sha256", "allow_dropped_figures"}
    if set(payload) != expected or payload.get("schema") != 1:
        raise ValueError("adjudication file must use schema 1 and only documented keys")
    digest = payload.get("original_sha256")
    actual = hashlib.sha256(original.encode("utf-8")).hexdigest()
    if not isinstance(digest, str) or digest != actual:
        raise ValueError("adjudication source hash does not match the original text")
    raw = payload.get("allow_dropped_figures")
    if not isinstance(raw, list) or len(raw) > 100:
        raise ValueError("allow_dropped_figures must be a list of at most 100 figures")
    original_figures = facts(original)["figure"]
    allowed = []
    for item in raw:
        if not isinstance(item, str) or not item.strip() or len(item) > 80:
            raise ValueError("each allowed figure must be a short non-empty string")
        parsed = facts(item)["figure"]
        if len(parsed) != 1:
            raise ValueError(f"allowed figure is not one unambiguous figure: {item!r}")
        canonical = next(iter(parsed))
        if canonical not in original_figures:
            raise ValueError(f"allowed figure is absent from the original: {item!r}")
        allowed.append(canonical)
    if len(allowed) != len(set(allowed)):
        raise ValueError("allow_dropped_figures contains a duplicate")
    return set(allowed)


def fidelity(before, after, adjudicated=None):
    """Did the rewrite keep every fact, and did it add any?

    ``adjudicated`` is the set of dropped figures a reviewer with context has
    ruled unsourced, so cutting them is an improvement rather than a loss. It
    is empty unless someone explicitly rules, which keeps the deterministic
    default strict: this function never decides on its own that a number was
    only rhetoric.

    The benchmark's worst result was a rewrite that invented a feeling the
    author never described — the exact thing hard rule 1 forbids — and nothing
    in the gate measured it. Preservation is checkable; invention is the half
    that matters, because a dropped figure is visible to the author and an
    added one is not.
    """
    a, b = facts(before, after), facts(after, before)
    structure = structure_changes(before, after)
    rows, kept_all, invented_any = [], True, False
    adjudicated = set(adjudicated or ())
    unsourced, dropped_items = set(), set()
    def entity_tokens(entity):
        return {w for w in re.findall(r"[a-z]+", entity.lower())
                if w not in NOT_NAME_WORDS}

    def entity_match(entity, candidates):
        """Exact names and honest shortenings match; partial renames do not."""
        left = entity_tokens(entity)
        if not left:
            return False
        for candidate in candidates:
            right = entity_tokens(candidate)
            if right and (left == right or left < right or right < left):
                return True
        return False
    # Interior experience is the fabrication the judges actually caught, and the
    # one no entity check sees: nothing was renamed, a feeling was added.
    ia, ib = interior_claims(before), interior_claims(after)
    new_interior = ib - ia
    for kind, _ in FACT_RX:
        if kind == "name":
            dropped = {e for e in a[kind] if not entity_match(e, b[kind])}
            added = {e for e in b[kind] if not entity_match(e, a[kind])}
            kept = a[kind] - dropped
            # A word capitalised once at a heading or sentence start reads
            # exactly like a product name to any lexical rule -- "Embedded
            # governance", "Models + Context = Leverage". Which one it is
            # depends on the document, so the reviewer rules and the tool
            # supplies the sentence rather than guessing.
            unsourced |= dropped & adjudicated
            dropped = dropped - adjudicated
            dropped_items |= dropped
        else:
            kept = a[kind] & b[kind]
            dropped = a[kind] - b[kind]
            added = b[kind] - a[kind]
            if kind == "figure" and dropped:
                # Figures the reviewer has ruled unsourced were right to cut,
                # so they stop failing preservation. Nothing is ruled without
                # that explicit judgment, and adding a figure is still an
                # invention however it was ruled.
                unsourced |= dropped & adjudicated
                dropped = dropped - adjudicated
                dropped_items |= dropped
        if not (a[kind] or b[kind]):
            continue
        rows.append((kind, kept, dropped, added))
        if dropped:
            kept_all = False
        if added:
            invented_any = True
    before_limitations = limitation_claims(before)
    after_limitations = limitation_claims(after)
    kept_limitations = before_limitations & after_limitations
    dropped_limitations = before_limitations - after_limitations
    added_limitations = after_limitations - before_limitations
    if before_limitations or after_limitations:
        rows.append(("qualifier", kept_limitations, dropped_limitations,
                     added_limitations))
        kept_all = kept_all and not dropped_limitations
        invented_any = invented_any or bool(added_limitations)
    if new_interior:
        rows.append(("feeling", set(), set(), new_interior))
        invented_any = True
    if structure:
        kept_all = False
        invented_any = invented_any or any(row["added"] for row in structure)
    return {"rows": rows, "preserved": kept_all, "invented": invented_any,
            "interior": new_interior, "structure": structure,
            "unsourced": unsourced,
            # The sentence each dropped figure came from, so whoever rules on
            # it can see whether a source stood behind the number.
            "figure_evidence": figure_contexts(before, dropped_items)}


def reorder_ratio(before, after):
    """How much of the surviving material the rewrite actually moved.

    0.0 means every kept sentence is still in its original order; 1.0 means the
    order was inverted. Cutting and reordering are different edits with
    different results: subtraction leaves the surviving prose sitting exactly
    where the model would have put it, while moving the payoff changes what the
    reader meets first. Nothing in the gate could tell the two apart, so a
    compression-only rewrite passed every check the ladder's order rung was
    supposed to enforce.
    """
    def shingles(text):
        out = []
        for sentence in sentences(text):
            words = {w for w in re.findall(r"[a-z]{4,}", sentence.lower())
                     if w not in NOT_NAME_WORDS}
            if words:
                out.append(words)
        return out

    src, dst = shingles(before), shingles(after)
    if len(src) < 2 or len(dst) < 2:
        return 0.0
    order = []
    for target in dst:
        best, best_at = 0.0, None
        for i, source in enumerate(src):
            union = len(target | source)
            overlap = len(target & source) / union if union else 0.0
            if overlap > best:
                best, best_at = overlap, i
        if best >= 0.3 and best_at is not None:
            order.append(best_at)
    if len(order) < 2:
        return 0.0
    pairs = inversions = 0
    for i in range(len(order)):
        for j in range(i + 1, len(order)):
            pairs += 1
            if order[i] > order[j]:
                inversions += 1
    return round(inversions / pairs, 3) if pairs else 0.0


# The shared rewrite-quality objective. One definition of "a better rewrite",
# used by scripts/rerank.py to pick the best of N candidates. Fidelity is
# reported alongside, never folded in, so a candidate can never win by dropping
# or inventing a fact however clean it reads.
RW_GATE = {"email": 35, "research": 40, "professional": 40}
RW_GATE_DEFAULT = 25
RW_FORMAL = {"research", "professional"}
# "structure" exists because the other four terms all saturate on a draft that
# arrives clean: deslop is ~0 when there is no slop to remove, and gate, rhythm
# and length each cap at 1.0, so every candidate scored an identical 0.55 and
# the ranking fell through to the fidelity tier. A meter with no opinion about
# which rewrite is better is the reason a worse rewrite could win.
RW_WEIGHTS = {"deslop": 0.40, "gate": 0.20, "rhythm": 0.12, "length": 0.13,
              "structure": 0.15}
RW_REORDER_FULL = 0.20


def rewrite_score(before_text, after_text, genre=None, data=None,
                  adjudicated=None):
    """Score one rewrite: a soft quality in [0,1] plus its fidelity flags."""
    if data is None:
        data = load_patterns()
    formal = genre in RW_FORMAL
    b = score_text(before_text, data, formal=formal)
    a = score_text(after_text, data, formal=formal)
    b_ai = b["ai_likelihood"] or 1e-9
    clamp = lambda x: max(0.0, min(1.0, x))
    deslop = clamp((b_ai - a["ai_likelihood"]) / b_ai)
    gate = 1.0 if a["ai_likelihood"] <= RW_GATE.get(genre, RW_GATE_DEFAULT) else 0.0
    # Formal genres score with the rhythm-uniformity penalty switched off,
    # because an even pulse is native to an abstract rather than a tell. The
    # objective was still paying for burstiness there, so a casualised abstract
    # outranked one that kept its register -- the composite penalising formal
    # writing for being formal, which is the thing --formal exists to stop.
    rhythm = 1.0 if formal else clamp(a.get("burstiness", 0.0) / 0.45)
    bw, aw = len(before_text.split()), len(after_text.split())
    length = 1.0 if not bw or aw / bw >= 0.6 else clamp((aw / bw) / 0.6)
    reorder = reorder_ratio(before_text, after_text)
    structure = clamp(reorder / RW_REORDER_FULL)
    soft = sum(RW_WEIGHTS[k] * v for k, v in
               {"deslop": deslop, "gate": gate, "rhythm": rhythm,
                "length": length, "structure": structure}.items())
    fid = fidelity(before_text, after_text, adjudicated)
    return {"soft": round(soft, 4), "deslop": round(deslop, 3), "gate": gate,
            "rhythm": round(rhythm, 3), "length": round(length, 3),
            "structure": round(structure, 3), "reorder": reorder,
            "unsourced": sorted(fid["unsourced"]),
            "figure_evidence": fid["figure_evidence"],
            "after_ai": a["ai_likelihood"], "before_ai": b["ai_likelihood"],
            "burstiness": round(a.get("burstiness", 0.0), 3),
            "high_tells": sum(1 for h in a.get("hits", []) if h.get("w", 0) >= 4),
            "preserved": fid["preserved"], "invented": fid["invented"]}


def render_fidelity(before, after, adjudicated=None):
    r = fidelity(before, after, adjudicated)
    out = ["", "  FACT AND MEANING CHECK · original vs edited text", ""]
    for kind, kept, dropped, added in r["rows"]:
        out.append(f"  {kind:<8} {len(kept)} kept"
                   + (f" · {len(dropped)} DROPPED" if dropped else "")
                   + (f" · {len(added)} ADDED" if added else ""))
        for v in sorted(dropped)[:4]:
            out.append(f"           dropped  {v[:56]!r}")
        for v in sorted(added)[:4]:
            out.append(f"           ADDED    {v[:56]!r}   <-- not in the source")
    if not r["rows"]:
        out.append("  no checkable facts in either text")
    if r.get("interior"):
        out.append("  the author never said these; an added feeling is still a "
                   "fabrication")
    if r.get("structure"):
        out.append("  protected document content changed:")
        for finding in r["structure"][:8]:
            out.append(f"           {finding['code']:<23} {finding['message']}")
    if r.get("unsourced"):
        for figure in sorted(r["unsourced"]):
            out.append(f"  ruled cut {figure!r} (reviewer marked it unsourced)")
    out += ["",
            "  Result: " + ("facts preserved; nothing added"
                             if r["preserved"] and not r["invented"] else
                             ("SOURCE CONTENT CHANGED" if not r["preserved"] else "")
                             + (" · CONTENT INVENTED" if r["invented"] else "")),
            "  This checks figures, names, quotes, links, explicit limitations,",
            "  stated feelings, code,",
            "  front matter, tables, blockquotes, inline identifiers, paths, and headings.",
            "  Your AI assistant still compares the full meaning because a changed claim",
            "  or emphasis may use all the same names and numbers.", ""]
    return out


def dna(before, after, data, formal=False, width=22):
    """Side-by-side channel anatomy of a draft and its rewrite.

    The composite says a draft got better; it never says what *kind* of better.
    A writer who sees that the whole score was vocabulary learns to stop
    reaching for those words, which outlasts the edit. Bars are scaled per
    channel against the worse of the two texts, so each row reads as its own
    before-and-after rather than against an arbitrary ceiling.
    """
    a, b = score_text(before, data, formal), score_text(after, data, formal)
    out = ["", "  WHAT CHANGED · before → after", ""]
    for label, get, better in CHANNELS:
        x, y = get(a), get(b)
        top = max(x, y) or 1.0
        fx, fy = x / top, y / top
        bar = "".join("█" if i < round(fx * width) else
                      ("▁" if i < round(max(fx, fy) * width) else " ")
                      for i in range(width))
        gone = (x - y) if better == "low" else (y - x)
        mark = "improved" if gone > 1e-9 else ("unchanged" if abs(gone) < 1e-9 else "WORSE")
        fmt = (lambda v: f"{v:.2f}") if max(x, y) < 10 else (lambda v: f"{v:g}")
        out.append(f"  {label:<14}{bar}  {fmt(x):>6} → {fmt(y):<6} {mark}")
    out += ["",
            f"  writing score {a['ai_likelihood']:.1f} → {b['ai_likelihood']:.1f}"
            f"   ({band(a['ai_likelihood'])} → {band(b['ai_likelihood'])})",
            f"  length        {a['n_words']} → {b['n_words']} words "
            f"({(b['n_words']-a['n_words'])/max(a['n_words'],1)*100:+.0f}%)",
            f"  flagged phrases {len(a['hits'])} → {len(b['hits'])}"]
    kept = {h["name"] for h in b["hits"]}
    fixed = [h["name"] for h in a["hits"] if h["name"] not in kept]
    if fixed:
        out.append("  fixed         " + ", ".join(sorted(set(fixed))[:6]))
    if kept:
        out.append("  still present " + ", ".join(sorted(kept)[:6]))
    # A shorter text with the same tells is not a better text.
    if b["n_words"] < a["n_words"] * 0.75 and len(b["hits"]) >= len(a["hits"]):
        out.append("  note          got shorter without fixing flagged phrases — "
                   "check this is an edit, not a deletion")
    return out + [""]


def _required_option_value(argv, flag):
    if flag not in argv:
        return None
    if argv.count(flag) > 1:
        raise SystemExit(f"{flag} may be supplied only once")
    index = argv.index(flag)
    if index + 1 >= len(argv) or argv[index + 1].startswith("--"):
        raise SystemExit(f"{flag} needs a value")
    return argv[index + 1]


def _read_text_file(path):
    try:
        return Path(path).read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise SystemExit(f"cannot read {path}: {exc}") from exc


def _text_files(root_arg):
    root = Path(root_arg)
    if not root.exists():
        raise SystemExit(f"directory does not exist: {root}")
    if not root.is_dir():
        raise SystemExit(f"expected a directory, got: {root}")
    root = root.resolve()
    files = []
    total_bytes = 0
    for path in root.rglob("*"):
        if path.suffix.lower() not in (".md", ".txt", ".markdown"):
            continue
        if path.is_symlink():
            raise SystemExit(f"symbolic links are not allowed in recursive input: {path}")
        try:
            resolved = path.resolve(strict=True)
            resolved.relative_to(root)
        except (OSError, ValueError) as exc:
            raise SystemExit(f"input resolves outside the selected directory: {path}") from exc
        if not resolved.is_file():
            continue
        size = resolved.stat().st_size
        if size > MAX_BATCH_FILE_BYTES:
            raise SystemExit(
                f"input file exceeds {MAX_BATCH_FILE_BYTES} bytes: {path}"
            )
        files.append(resolved)
        total_bytes += size
        if len(files) > MAX_BATCH_FILES:
            raise SystemExit(f"recursive input exceeds {MAX_BATCH_FILES} text files")
        if total_bytes > MAX_BATCH_TOTAL_BYTES:
            raise SystemExit(
                f"recursive input exceeds {MAX_BATCH_TOTAL_BYTES} total bytes"
            )
    return sorted(files)


def main():
    argv = sys.argv[1:]
    if "--help" in argv or "-h" in argv:
        print(__doc__)
        return 0
    value_flags = {"--gate", "--genre", "--voice", "--adjudication"}
    bool_flags = {"--json", "--explain", "--formal", "--fidelity", "--dna",
                  "--portfolio", "--batch", "--heatmap"}
    unknown = [arg for arg in argv if arg.startswith("--")
               and arg not in value_flags | bool_flags]
    if unknown:
        raise SystemExit(f"unknown option: {unknown[0]}")
    for flag in value_flags:
        _required_option_value(argv, flag)
    modes = [flag for flag in ("--fidelity", "--dna", "--portfolio", "--batch")
             if flag in argv]
    if len(modes) > 1:
        raise SystemExit("choose only one mode: " + ", ".join(modes))
    if "--adjudication" in argv and "--fidelity" not in argv:
        raise SystemExit("--adjudication is valid only with --fidelity")

    gv, _ = gate_value()
    # Values that belong to a flag (--gate 25, --genre social, --voice manav)
    # are not positional file arguments. Drop each flag and the token after it.
    VALUE_FLAGS = value_flags
    args, skip = [], False
    for a in argv:
        if skip:
            skip = False
            continue
        if a in VALUE_FLAGS:
            skip = True
            continue
        if not a.startswith("--"):
            args.append(a)
    as_json = "--json" in sys.argv
    explain = "--explain" in sys.argv
    formal = "--formal" in sys.argv
    genre = "general"
    if "--genre" in sys.argv:
        genre = _required_option_value(argv, "--genre")
    if formal: genre = "formal"
    voice = None
    if "--voice" in sys.argv:
        voice = _required_option_value(argv, "--voice")
    try:
        data = load_patterns(voice=voice)
    except ValueError as exc:
        sys.exit(str(exc))

    if "--fidelity" in sys.argv:
        if len(args) != 2:
            sys.exit("--fidelity needs exactly two files: before and after")
        before, after = _read_text_file(args[0]), _read_text_file(args[1])
        adjudicated = None
        ruling_path = _required_option_value(argv, "--adjudication")
        if ruling_path:
            try:
                adjudicated = load_adjudication(ruling_path, before)
            except ValueError as exc:
                raise SystemExit(str(exc)) from exc
        for line in render_fidelity(before, after, adjudicated):
            print(line)
        r = fidelity(before, after, adjudicated)
        sys.exit(0 if (r["preserved"] and not r["invented"]) else 1)

    if "--dna" in sys.argv:
        if len(args) != 2:
            sys.exit("--dna needs exactly two files: before and after")
        for line in dna(_read_text_file(args[0]), _read_text_file(args[1]),
                        data, formal=formal):
            print(line)
        return

    if "--portfolio" in sys.argv:
        if len(args) > 1:
            raise SystemExit("--portfolio accepts one directory")
        root = Path(args[0]) if args else Path(".")
        files = _text_files(root)
        if not files:
            raise SystemExit(f"no .md, .txt, or .markdown files under {root}")
        result = portfolio_metrics((str(p), _read_text_file(p)) for p in files)
        if as_json:
            print(json.dumps(result, ensure_ascii=False, indent=1))
        else:
            for line in render_portfolio(result):
                print(line)
        return

    if "--batch" in sys.argv:
        if len(args) > 1:
            raise SystemExit("--batch accepts one directory")
        root = Path(args[0]) if args else Path(".")
        files = _text_files(root)
        if not files:
            raise SystemExit(f"no .md, .txt, or .markdown files under {root}")
        rows = []
        for p in files:
            r = score_text(_read_text_file(p), data, formal=formal)
            rows.append((r["ai_likelihood"], p, band(r["ai_likelihood"])))
        rows.sort(key=lambda x: -x[0])
        worst = max(sc for sc, _, _ in rows)
        passed = gv is None or worst <= gv
        if as_json:
            print(json.dumps({
                "result_kind": "batch_score",
                "directory": str(root),
                "documents": len(rows),
                "max_score": worst,
                "gate_applied": gv is not None,
                "gate": gv,
                "passed": passed,
                "items": [
                    {"file": str(p), "score": sc, "band": b}
                    for sc, p, b in rows
                ],
            }, ensure_ascii=False, indent=1))
        else:
            for sc, p, b in rows:
                print(f"{sc:6.1f}  {b:12s} {p}")
        sys.exit(1 if gv is not None and worst > gv else 0)

    if len(args) > 1:
        raise SystemExit("score mode accepts one file, or '-' for stdin")
    # No file argument, or the conventional "-", means read stdin.
    text = sys.stdin.read() if (not args or args[0] == "-") else _read_text_file(args[0])
    r = score_text(text, data, formal=formal)
    if as_json:
        print(json.dumps(r, ensure_ascii=False, indent=1))
        if gv is None:
            return
        # --json --gate is documented CI usage; returning here exited 0 on a
        # failing document, so a broken gate silently passed every build.
        sh_j = shape_metrics(text, genre=genre)
        sys.exit(0 if (r["ai_likelihood"] <= gv and not sh_j.get("broetry")) else 1)
    print(f"Writing score: {r['ai_likelihood']}/100  [{band(r['ai_likelihood'])}]")
    print("  Lower is better. This describes the writing, not who wrote it.")
    unique_hits = []
    seen_quotes = set()
    for hit in sorted(r["hits"], key=lambda item: -item["w"]):
        key = hit["quote"].strip().lower()
        if key and key not in seen_quotes:
            seen_quotes.add(key)
            unique_hits.append(hit)
    print(f"  Flagged phrases : {len(unique_hits)} across {r['n_words']} words")
    variety = "natural" if r["burstiness"] >= 0.45 else "too even"
    print(f"  Sentence variety: {variety}")
    print(f"  Punctuation      : {r['emoji_count']} emoji, {r['bold_spans']} bold spans, "
          f"{r['hashtags']} hashtags, {r['emdash_per_100w']:.2f} em dashes per 100 words")
    if r["followability_penalty"] > 2:
        print(f"  Readability      : needs work — "
              f"{r['comma_chain_frac']:.0%} of sentences chain clauses with commas; "
              f"{r['overlong_frac']:.0%} are unusually long")
    else:
        print("  Readability      : clear")
    if r["categories"]:
        top = sorted(r["categories"].items(), key=lambda kv: -kv[1])[:8]
        labels = [CAT_MEANING.get(k, (k, ""))[0] for k, _ in top]
        print("  Main issues      : " + ", ".join(labels))
    sh = shape_metrics(text, genre=genre)
    r["shape"] = sh
    print("  Page layout      : " + (
        f"too many short, one-sentence paragraphs ({sh['solo_frac']:.0%}); "
        f"longest fragment run {sh['max_fragment_run']}" if sh.get("broetry")
        else (f"looks natural ({sh['solo_frac']:.0%} one-sentence paragraphs)" if sh["measured"]
              else "not checked for this kind of writing")))
    print("  What Zero Slop checked: word choice, formatting, sentence rhythm, "
          "readability, and tone" + (", plus page layout" if sh["measured"] else ""))
    print("  What your AI assistant reviews: strength of the ideas, voice, factual accuracy, "
          "and whether the writing is performing rather than saying"
          + ("" if sh["measured"] else "; page layout was not checked"))
    if explain:
        if unique_hits:
            print(f"\n  Flagged phrases ({len(unique_hits)}), strongest first:")
            for h in unique_hits:
                name, fix = CAT_MEANING.get(h["cat"], ("generic wording", "rewrite plainly"))
                print(f"    {h['quote']!r} — {name}; {fix}")
        else:
            # A clean pattern channel is the case where the register pass matters
            # most, so this line must not read as "nothing left to do".
            print("\n  Flagged phrases: none. The remaining score comes from sentence rhythm and formatting.")
            print("  This channel cannot see performed register — balanced two-part contrasts,")
            print("  epigram cadence, announced significance. Run the register pass before")
            print("  calling the draft clean.")
    if "--heatmap" in sys.argv or explain:
        for line in render_heatmap(text, data, formal=formal):
            print(line)
    if gv is not None:
        ok = r["ai_likelihood"] <= gv and not sh.get("broetry")
        why = "" if ok else (" (page layout needs work)" if sh.get("broetry") and r["ai_likelihood"] <= gv else "")
        verdict = "PASSED" if ok else "NEEDS WORK"
        print(f"  Check against {gv:g}: {verdict}{why}. This covers writing patterns and "
              f"layout; your AI assistant still reviews the ideas, voice, and facts.")
        sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
