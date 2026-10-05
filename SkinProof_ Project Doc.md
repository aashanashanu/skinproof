# SkinProof: "Did it actually work?"

**Hackathon:** YouCam API Skin AI & eCommerce VTO Hackathon (Devpost, Perfect Corp), Sep 29 to Nov 2, 2026 **One-line pitch:** Run a measured, personal experiment on any skincare product and get an honest verdict on whether it did anything for your skin.

> This is the original product and hackathon planning brief, not the current operating guide. For implemented behavior and how to use the hosted app, see the [README](README.md), [documentation map](docs/README.md), and [user guide](docs/user-guide.md).

---

## 1. Overview

### 1.1 Problem

People spend months and real money on skincare based on marketing and gut feel. Skin changes slowly, lighting varies daily, and memory is unreliable, so nobody can tell if a product worked, the season changed, or the photo was just better. Retailers absorb the cost through returns, churn, and distrust.

### 1.2 Solution

Every product trial is an **experiment** with a baseline, a target concern, a fixed capture protocol, a measurement-noise estimate, and a verdict: **Improved / No detectable change / Worse / Inconclusive**. "Inconclusive" is a first-class outcome; honesty is the product.

### 1.3 Users

- **Consumer (primary):** wants proof before repurchasing.
- **Brand / retailer (secondary, mock only):** wants fewer refund disputes and honest efficacy signals via an embeddable "Try it with proof" flow.

### 1.4 Goals

1. A complete, coherent flow: setup, capture, analysis, verdict, report.
2. Non-trivial use of YouCam skin analysis (plus capture quality gating).
3. A statistically defensible verdict that never overclaims.
4. A demo that lands in 3 minutes.

### 1.5 Non-goals

- Medical diagnosis or treatment advice.
- Product recommendations or affiliate selling.
- Social features, native mobile apps, payments.
- Full B2B platform (one mock screen only).

### 1.6 Judging alignment

| Criterion | How SkinProof answers it |
| --- | --- |
| Technological implementation | Async analysis pipeline, quality gating, noise calibration, tested verdict engine |
| Design | One coherent flow with timeline, slider, report |
| Potential impact | Wasted spend and distrust; consumer plus brand value |
| Quality of idea | Measures whether skincare works instead of recommending more |

---

## 2. Scope

### 2.1 Must have (MVP)

Experiment setup, guided capture with quality gate, analysis pipeline with caching, noise calibration, verdict engine, timeline view, ingredient check, seeded demo data.

### 2.2 Should have

Weekly check-ins with confound flags, before/after slider with mask overlays, shareable report.

### 2.3 Could have

Retail widget mock, reminders (email), PDF export, LLM-assisted ingredient list parsing.

### 2.4 Out of scope

Auth providers beyond a simple login, multi-user teams, real brand integrations, clinical validation.

### 2.5 Global rules

- All results labeled **cosmetic trend, not medical advice**.
- Simulated data is always labeled as simulated.
- The YouCam API key stays server-side only.

---

## 3. Architecture and Stack

| Layer | Choice | Notes |
| --- | --- | --- |
| Frontend | React or Next.js | YouCam camera kit is JavaScript |
| Backend | Java, Spring Boot | REST, OpenAPI/Swagger |
| Database | PostgreSQL | JSONB for per-concern scores |
| Storage | S3 (or local disk in dev) | Encrypted, signed URLs, no public images |
| Async | Job table plus scheduled poller (or SQS) | Kafka is overkill here |
| Charts | Recharts or Chart.js | Noise band as shaded area |
| Hosting | AWS (ECS/App Runner or a single EC2), or any simple host | Keep deploy trivial |
| Tests | JUnit for verdict engine and rules; Playwright smoke test optional |  |

### 3.1 Flow

```
Browser (camera kit capture + quality check)
   -> Backend: store image (S3), create Capture row
   -> Job: create YouCam analysis task, poll until done
   -> Store scores + masks (cache by image hash)
   -> Verdict engine recomputes on each new valid analysis
   -> UI: timeline, slider, verdict, report
```

### 3.2 Data model (core tables)

| Table | Key fields |
| --- | --- |
| users | id, email, created_at |
| experiments | id, user_id, product_name, target_concern, start_date, duration_weeks, status, protocol_notes |
| products | id, experiment_id, name, ingredients_raw, ingredients_parsed (jsonb) |
| captures | id, experiment_id, taken_at, image_key, quality_score, quality_flags, is_baseline, is_valid |
| analyses | id, capture_id, task_id, status, concern_scores (jsonb), mask_keys (jsonb), image_hash, cost_units |
| checkins | id, experiment_id, week, sun_exposure, illness, travel, stress, new_product_added, notes |
| verdicts | id, experiment_id, computed_at, label, confidence, delta, noise_floor, explanation, confound_flags |
| ingredient_rules | id, active, aliases, commonly_used_for (concerns), irritation_group, notes |
| reports | id, experiment_id, share_token, created_at |

### 3.3 Key API endpoints (design target)

- `POST /experiments`, `GET /experiments/{id}`, `GET /experiments`
- `POST /experiments/{id}/captures` (upload + metadata)
- `GET /experiments/{id}/timeline`
- `GET /experiments/{id}/verdict`
- `POST /experiments/{id}/checkins`
- `POST /ingredients/check`
- `POST /experiments/{id}/report`, `GET /reports/{token}`

Confirm exact YouCam endpoints, parameters, score direction, and rate/credit limits in the YouCam docs during M0. Do not assume them.

---

## 4. Work Packages (pick one at a time)

Each package lists goal, requirements, acceptance criteria, and dependencies. Estimates are rough working days for one developer.

### M0. Spike and setup (1 to 2 days)

**Goal:** de-risk the YouCam API and get a running skeleton. **Requirements**

- Get a free API key and test in the YouCam API playground.
- Run skin analysis end to end on 3 sample photos; record response shape, score direction (higher = better or worse), latency, and credit cost.
- Test the camera kit capture mode for skin analysis in a browser.
- Create repo, Spring Boot service, React app, PostgreSQL, docker-compose for local dev, Swagger UI. **Acceptance**
- A script or endpoint returns parsed concern scores for a sample photo.
- `docs/api-notes.md` records findings and limits. **Depends on:** none

### M1. Basic auth and user session (0.5 day)

**Requirements:** simple email login or magic link (or a demo-user mode); all data scoped to user. **Acceptance:** two users cannot see each other's experiments or images. **Depends on:** M0

### M2. Experiment setup wizard (1 day)

**Requirements**

- Create an experiment: product name, target concern (limited to concerns the analysis returns), duration (default 8 weeks), start date.
- Show the protocol screen: same time of day, same lighting, same spot, no other new products during the trial.
- Experiment list and detail pages. **Acceptance:** user can create, view, and archive experiments; target concern list matches API output. **Depends on:** M1

### M3. Guided capture with quality gate (2 days)

**Requirements**

- Integrate the camera kit in skin-analysis capture mode.
- Enforce lighting, pose, angle, and distance checks; surface human-readable rejection reasons ("too dark, move toward a window").
- Compute and store a quality score and flags per capture.
- Upload to storage; never expose public image URLs.
- Support photo upload as a fallback when the camera is unavailable. **Acceptance:** a bad photo is rejected with a clear reason; a good one is stored with a quality score. **Depends on:** M2

### M4. Analysis pipeline (2 days)

**Requirements**

- On capture, create a YouCam analysis task; poll until done; handle timeout, failure, and retry with backoff.
- Store scores and mask references.
- **Cache by image hash**; never re-run an identical image.
- Track credit usage per analysis; a per-experiment daily cap to protect the budget.
- Optionally run the Skin Analysis Validator before analysis if available. **Acceptance:** capture to stored scores works asynchronously; a duplicate upload does not spend credits; failures show a retry state in the UI. **Depends on:** M3

### M5. Noise calibration (1 day)

**Requirements**

- Day-0 flow takes **two back-to-back captures** and marks both as baseline.
- Compute the initial noise floor per concern from their difference, with a minimum floor per concern to avoid a false-precision zero.
- After 5 or more valid captures, update noise using residuals from a linear fit.
- Display the noise band in the UI with a plain explanation. **Acceptance:** noise floor is stored per experiment and concern; it updates as data grows; unit tested. **Depends on:** M4

### M6. Verdict engine (2 days)

**Requirements** Normalize so that higher = better before any math.

- **Baseline:** mean of day-0 captures. **Current:** mean of the latest 2 to 3 valid captures.
- **Change (Δ)** = current minus baseline on the target concern.
- **Minimums:** at least 4 valid captures and at least 4 weeks; otherwise the verdict is **Inconclusive** with reason "not enough data yet".
- **Improved / Worse:** |Δ| is at least 2 times the noise floor **and** the trend agrees (the last 3 valid captures are all beyond baseline by more than the noise floor, or the fit slope has the same sign).
- **No detectable change:** |Δ| is below the noise floor and the minimums are met.
- **Otherwise:** Inconclusive.
- **Confidence** (High / Medium / Low) starts from how far Δ exceeds the noise floor and drops one level per confound flag; two or more major confounds force Inconclusive.
- Generate a plain-language explanation: numbers, noise comparison, and caveats.
- Recompute on every new valid analysis and store the verdict history. **Acceptance:** at least 15 unit tests covering each label, boundary values, insufficient data, and confound downgrades; explanation text never uses medical claims. **Depends on:** M5

### M7. Timeline and comparison UI (2 days)

**Requirements**

- Trend chart for the target concern with the shaded noise band and baseline line.
- Verdict card (label, confidence, explanation, confounds).
- Before/after slider between baseline and latest capture.
- Toggle mask overlays for the target concern.
- Secondary concerns view (optional collapsible).
- Empty and loading states for pending analyses. **Acceptance:** a seeded experiment renders the full view; the slider and overlay work on desktop and mobile widths. **Depends on:** M6

### M8. Ingredient check (1.5 days)

**Requirements**

- Input: pasted ingredient list or a pick from common actives.
- Curated rule table of about 25 to 30 actives (retinoids, AHAs/BHAs, vitamin C, niacinamide, benzoyl peroxide, azelaic acid, SPF filters, and similar) with aliases, commonly-used-for concerns, and an irritation group.
- Three checks:
  1. **Plausibility:** contains an active commonly used for the target concern, or say plainly that it does not.
  2. **Stacking:** multiple irritation-group actives in the same routine.
  3. **Experiment hygiene:** adding a second new active mid-trial triggers a confound warning and a confidence downgrade.
- Wording: "commonly used for", never "will treat".
- Optional: LLM only to normalize messy pasted text into ingredient names; rules make every decision. **Acceptance:** rule table is data-driven (seed file); unknown ingredients are listed as "not recognized" instead of guessed; unit tests for each check. **Depends on:** M2 (M6 for confound linkage)

### M9. Check-ins and confound detection (1 day)

**Requirements**

- Weekly check-in form: sun exposure, illness, travel, stress, new product added.
- Auto-detect confounds: quality drift versus baseline, capture gaps of more than 2 weeks, second new product, self-reported events.
- Feed flags into the verdict confidence. **Acceptance:** each flag appears in the verdict card and lowers confidence per M6 rules. **Depends on:** M6, M8

### M10. Shareable report (1 day)

**Requirements**

- One-page report: protocol, timeline chart, verdict, caveats, disclaimer.
- Share via unguessable token link; user can revoke it.
- Images excluded by default; opt-in to include before/after.
- PDF export is a could-have. **Acceptance:** report link works while logged out and hides images unless the user opted in; revocation works. **Depends on:** M7

### M11. Retail widget mock (0.5 to 1 day)

**Requirements**

- One static or lightly interactive mock of a product page with a "Try it with proof" button leading into the experiment setup, and a small brand-view screen with aggregate, anonymized outcomes (clearly labeled as mock data). **Acceptance:** shown in the demo as the B2B vision; no real brand integration claimed. **Depends on:** M2

### M12. Demo data seeding (1 day)

**Requirements**

- Seed script creating: (a) a completed 8-week experiment with a clear **Improved** verdict, (b) an **Inconclusive** experiment caused by confounds, (c) an in-progress experiment for the live capture demo.
- Prefer **real weekly captures** taken from now until the deadline.
- To fill gaps, the Skin Simulation API may generate a synthetic progression from one photo; label those captures **Simulated demo data** in the UI and README. **Acceptance:** one command resets and reseeds the demo account; labels are visible. **Depends on:** M6 (M7 for visual check)

### M13. Privacy, polish, docs, submission (3 to 4 days)

**Requirements**

- Privacy: explicit consent before first capture, delete-my-data (experiments, images, analyses), encrypted storage, signed URLs, short retention note.
- Accessibility basics: contrast, keyboard use, alt text.
- README, architecture diagram, setup instructions, API notes.
- 3-minute demo video following section 6; Devpost write-up.
- Check the Rules page for exact submission requirements. **Acceptance:** fresh clone runs with documented steps; video and write-up ready before the deadline. **Depends on:** all MVP packages

---

## 5. Non-Functional Requirements

- **Privacy:** face images are sensitive; no third-party sharing, no model training, per-user isolation.
- **Reliability:** analysis failures never lose a capture; retries are idempotent.
- **Cost control:** hash caching, per-experiment daily cap, credit logging.
- **Performance:** capture to result ideally under a minute; UI stays responsive while polling.
- **Honesty:** no medical claims; all verdict text reviewed against a banned-phrases list ("cure", "treat", "diagnose", "proves").

---

## 6. Demo Flow (3 minutes)

| Time | Beat | What to show |
| --- | --- | --- |
| 0:00 to 0:20 | Hook | "This serum has 4.7 stars. Did it work on *your* skin?" |
| 0:20 to 0:50 | Setup | Create an experiment, pick the target concern, paste ingredients; the check shows a plausible active plus a warning that a second new active would confound the test |
| 0:50 to 1:20 | Capture | Live capture: the quality gate rejects a dark photo with a reason, then accepts a good one |
| 1:20 to 1:40 | Noise floor | Two day-0 captures and the resulting noise band, explained in one sentence |
| 1:40 to 2:40 | Payoff | Open the seeded 8-week experiment: trend with noise band, before/after slider, mask overlay, **Improved** verdict with explanation. Then the second experiment ending **Inconclusive** because of confounds |
| 2:40 to 3:00 | Close | Shareable report and the "Try it with proof" retail mock; disclaimer on screen |

**Backup plan:** pre-record the capture segment and keep seeded data ready in case the live API is slow.

---

## 7. Timeline (about 5 weeks)

| Week | Focus | Packages |
| --- | --- | --- |
| 1 | Foundation | M0, M1, M2, start real weekly captures |
| 2 | Core pipeline | M3, M4, M5 |
| 3 | Intelligence and UI | M6, M7 |
| 4 | Differentiators | M8, M9, M10, M11, M12 |
| 5 | Ship | M13, buffer, video, submission |

Cut order if time runs short: M11, then PDF export, then secondary concerns view, then M10.

---

## 8. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Scores vary between photos | Quality gate, noise floor, honest Inconclusive |
| Medical overclaiming | Wording rules, disclaimer, banned-phrases test |
| Too little real data at demo time | Start capturing now; label simulated data clearly |
| API credit exhaustion | Hash cache, daily cap, use bonus credits from the share program |
| API details differ from assumptions | M0 spike, `api-notes.md` |
| Scope creep | MVP list in section 2.1 is the gate |

---

## 9. Open Questions to Resolve in M0

1. Score direction and range for each concern.
2. Which concerns are supported by the standard versus HD skin analysis mode.
3. Is the Skin Analysis Validator available on the free key?
4. Credit cost per analysis and rate limits.
5. Are mask overlays returned as images or coordinates?
6. Submission format requirements (video length, repo visibility) from the Rules page.

---

## 10. Definition of Done (project level)

- A first-time user completes setup, capture, and sees a verdict without instructions.
- Verdict engine tests pass; no medical claims in any output.
- Seeded demo account reproduces all three demo experiments with one command.
- README, diagram, video, and Devpost write-up are complete and submitted before Nov 2.