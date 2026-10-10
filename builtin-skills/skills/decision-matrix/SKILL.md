---
name: decision-matrix
description: Compare options with weighted scoring, pros and cons, pre-mortems, opportunity costs, and ICE prioritization. Use when a user wants to reason through a choice, expose assumptions, or rank alternatives.
version: 1.0.0
license: MIT
---

# Decision Matrix

## What It Does

Apply a transparent framework to compare options, expose trade-offs, and identify what information
could change a choice.

Treat every score as a transparent expression of the user's stated preferences, not as objective truth. Clearly label estimates and assumptions, and never invent missing costs, probabilities, constraints, or preferences.

For medical, legal, financial, safety-critical, or other high-impact decisions, use the frameworks only to organize questions and trade-offs. Do not present the highest score as professional advice or a final decision. Encourage the user to verify material facts and consult an appropriately qualified professional.

---

## Frameworks Available

### 1. Classic Pros & Cons (Benjamin Franklin Method)

**Best for**: Quick decisions with low-to-moderate stakes

| Step | Action |
|------|--------|
| 1 | Draw two columns: PROS and CONS |
| 2 | List every reason for and against — no filtering |
| 3 | **Weigh** each item (not all pros are equal). Assign +1 to +5 for pros, -1 to -5 for cons |
| 4 | Sum the scores, then inspect the strongest items, uncertainty, and any non-negotiables |

**Guardrail**: Pros/cons alone miss hidden assumptions. Always follow with: "What am I not considering?"

### 2. Weighted Decision Matrix (Pugh Matrix)

**Best for**: Comparing multiple options against multiple criteria

```
| Criteria               | Weight (1-5) | Option A | Option B | Option C |
|------------------------|-------------|----------|----------|----------|
| Cost                   |      4      |   8/10   |   6/10   |   9/10   |
| Time to Market         |      3      |   7/10   |   9/10   |   5/10   |
| Strategic Fit          |      5      |   9/10   |   4/10   |   7/10   |
| Team Capacity          |      2      |   6/10   |   8/10   |   4/10   |
| **Weighted Total**     |             |   110    |   87     |   94     |
```

**Steps**:
1. List all viable options (columns in the example)
2. Define criteria that matter (rows in the example)
3. Assign a weight (1-5) to each criterion based on importance
4. Score each option per criterion (1-10)
5. Multiply score × weight, sum across criteria
6. Use the highest total as a starting point, then inspect assumptions, uncertainty, must-haves, and reversibility

### 3. Pre-Mortem

**Best for**: High-stakes decisions where risk mitigation is critical

> "It's 12 months from now and our decision has failed spectacularly. How did it happen?"

| Step | Technique |
|------|-----------|
| 1 | Assume the decision was made and led to disaster |
| 2 | Fast-forward and write the "post-mortem" — what went wrong? |
| 3 | Generate 5-10 plausible failure modes |
| 4 | For each failure, ask: "What could prevent this?" |
| 5 | Incorporate those safeguards into the decision |

Use this to surface plausible failure modes that an ordinary comparison may miss. Do not treat an
imagined failure as a prediction.

### 4. Opportunity Cost Frame

**Best for**: Deciding between two good options (where saying yes to A means saying no to B)

| Frame | Question |
|-------|----------|
| **Cost of yes** | What do I give up by choosing this? |
| **Cost of no** | What do I give up by not choosing this? |
| **Regret test** | If I look back in 5 years, which "no" would I regret more? |
| **Opportunity comparison** | If Option A didn't exist, would I choose Option B? |

Use the answers as discussion prompts, not an automatic selection rule.

### 5. ICE Score (Impact, Confidence, Ease)

**Best for**: Prioritizing many options quickly (features, ideas, experiments)

| Criterion | Scale | Question |
|-----------|-------|----------|
| **Impact** | 1-10 | How significant will the result be if successful? |
| **Confidence** | 1-10 | How sure are we about the expected outcome? |
| **Ease** | 1-10 | How easy/simple is this to execute? |

**Formula**: `ICE Score = Impact × Confidence × Ease`

Sort by score to create a shortlist. Check dependencies, risk, and confidence before selecting work, and re-score when new data emerges.

### 6. The 10/10/10 Rule

**Best for**: Emotional or high-stakes personal decisions

| Time Horizon | Question |
|-------------|----------|
| 10 minutes | How will I feel about this decision in 10 minutes? |
| 10 months | How will I feel about it in 10 months? |
| 10 years | How will I feel about it in 10 years? |

**Purpose**: Shifts perspective from short-term emotion to long-term impact. If the horizons conflict, explain the conflict instead of automatically favoring one horizon.

---

## Trigger Phrases

| Phrase | Action |
|--------|--------|
| "Help me decide between..." | Starts a structured comparison of options |
| "Pros and cons of..." | Generates a weighted pros/cons table |
| "Should I [X] or [Y]?" | Runs a decision matrix or opportunity cost analysis |
| "What am I not considering?" | Surfaces blind spots and hidden assumptions |
| "Run a pre-mortem on..." | Scenarios worst-case outcomes to de-risk the decision |
| "Prioritize these for me..." | Uses ICE or weighted scoring to rank options |
| "Help me think this through..." | Combines frameworks layered for clarity |

---

## Step-by-Step Instructions

### Step 1: Define the Decision Clearly

A fuzzy question gets a fuzzy answer. Be specific:

- ❌ "Should I change jobs?"
- ✅ "Should I accept the offer at Company X ($120k, hybrid, startup) or stay at my current role ($110k, remote, corporate)?"

### Step 2: Identify the Decision Type

| Decision Type | Recommended Framework |
|---------------|---------------------|
| Low stakes, 2 options | Pros & Cons (weighted) |
| Multiple options, many criteria | Weighted Decision Matrix |
| High risk, irreversible | Pre-mortem |
| Scarcity (time/money focus) | Opportunity Cost Frame |
| Prioritizing a long list | ICE Score |
| Emotional/personal | 10/10/10 Rule |

### Step 3: Collect the Data

Gather:
- All realistic options (at least 2, rarely more than 5)
- All relevant criteria
- Objective data where possible (numbers, dates, facts)
- Subjective preferences (gut feel, values, identity)

Ask for critical missing information when it could change the outcome. Otherwise, proceed with clearly labeled assumptions and show how changing them affects the result.

### Step 4: Apply the Framework

Run the framework step by step. Document scores, weights, and reasoning.

### Step 5: Check for Bias

| Bias | Mitigation |
|------|-----------|
| **Confirmation bias** | Actively list reasons *against* your preferred option first |
| **Recency bias** | Consider decisions from 6+ months ago — does this feel different? |
| **Sunk cost** | "If I had no prior investment in this, would I still choose it?" |
| **Status quo bias** | "If this weren't the default, would I pick it?" |

### Step 6: Decide and Commit

- If the evidence strongly favors an option, explain why and identify the remaining uncertainty.
- If scores are close, compare reversibility, information gaps, and the cost of a small experiment. Do not impose an arbitrary 10% threshold.
- Let the user make the final choice, especially for consequential decisions.
- Offer to write down the decision and reasoning; do not persist it unless the user asks.

### Step 7: Review the Outcome

After the decision plays out, revisit your framework. Did your weights reflect reality? Did you miss a criterion? Retrospect improves future decisions.

---

## Examples

### Example 1: Freelancer Deciding Between Two Clients

> **Input**: "Should I take Client A ($5k, urgent, boring) or Client B ($3k, flexible, exciting project)?"
>
> **Process**: Weighted Decision Matrix
>
> | Criteria | Weight | Client A | Client B |
> |----------|--------|----------|----------|
> | Income | 4 | 9 (36) | 5 (20) |
> | Enjoyment | 3 | 3 (9) | 9 (27) |
> | Time Pressure | 2 | 3 (6) | 9 (18) |
> | Portfolio Value | 4 | 4 (16) | 9 (36) |
> | **Total** | | **67** | **101** |
>
> **Result**: Under these stated weights and scores, Client B leads because portfolio value and enjoyment outweigh the income gap. Verify workload, payment risk, and any non-negotiables before choosing.

### Example 2: Solopreneur — "Should I Build Feature X?"

> **Input**: "Should I prioritize building a mobile app or improving onboarding?"
>
> **Process**: ICE + Pre-mortem
>
> ICE:
> - Mobile App: Impact 8, Confidence 4, Ease 2 → ICE = 64
> - Onboarding: Impact 6, Confidence 8, Ease 8 → ICE = 384
>
> Pre-mortem on mobile app decision: "We built the app but no one used it because onboarding was broken." → Clear signal to fix onboarding first.

---

## Quality checks

- Show the arithmetic and retain the user's original units, weights, and scores.
- Identify must-haves before ranking options.
- Label estimates and distinguish evidence from preferences.
- Test whether a modest change in an uncertain weight or score changes the result.
- For close results, compare reversibility and the value of gathering more information.
- Leave consequential choices to the user; do not persist or act on a decision without a separate
  request.
