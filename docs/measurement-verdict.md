# Measurement and Verdict Contract

This is a cosmetic trend summary, not a medical assessment. The API notes document the provider's published score direction: numeric raw scores run from 1 to 100, and higher means a more favorable cosmetic score. `ui_score` is not used for trend calculations. `skin_type` is categorical and never enters numeric trend calculations.

## Eligible Measurements

A measurement is eligible only when its capture passed the guided quality checks, its analysis succeeded, its target concern has a finite numeric raw score from 1 through 100, and it is not marked simulated. Samples are ordered by capture time and deduplicated by image SHA-256 so repeated uploads cannot create more evidence or skew an average.

At least two eligible baseline captures and two eligible follow-up captures are required. A verdict also requires at least four unique eligible captures spanning at least four weeks from the first baseline to the latest eligible capture. Otherwise, the label is `INCONCLUSIVE` with a specific missing-evidence explanation.

## Noise Floor

The minimum noise floor is currently **3 raw-score points for every numeric concern**. These are provisional policy floors, not empirically measured YouCam repeatability values; there are no consented calibration image sets in the repository. The floor is explicit per supported numeric action in `VerdictEngine` so a future concern-specific calibration can replace individual values without changing the verdict contract.

With fewer than five unique eligible captures, the paired-baseline estimate is the median absolute pairwise difference among baseline scores. With exactly two baseline captures this is their absolute difference. The effective noise floor is the larger of that estimate and the concern's minimum.

Once five unique eligible captures exist, noise switches to the root-mean-square residual of an ordinary least-squares linear fit of raw score against elapsed capture time in days. The effective floor remains the larger of the residual estimate and the concern minimum. Calibration is upserted per experiment and target concern after each successful analysis; verdict snapshots preserve the value used at computation time.

## Verdict Rules

- Baseline is the arithmetic mean of all eligible baseline captures.
- Current is the arithmetic mean of the latest two or three eligible follow-up captures (whichever are available).
- Change is `current - baseline`; positive change is a more favorable cosmetic score under the documented raw-score direction.
- `NO_DETECTABLE_CHANGE` applies only when the absolute change is strictly below the noise floor.
- `IMPROVED` or `WORSE` requires absolute change at least twice the noise floor and a supporting trend: either the latest three eligible captures are each beyond baseline by more than the noise floor in the same direction, or the elapsed-time linear-fit slope has the same sign as the change.
- Other evidence patterns are `INCONCLUSIVE`. Equality at one noise-floor unit is therefore not called unchanged; equality at twice the floor can qualify if the trend agrees.
- Confidence starts `HIGH` for a directional change at least three times the floor, otherwise `MEDIUM`; unchanged results start `HIGH` up to half a floor and `MEDIUM` above that. Other results start `LOW`.
- Each distinct confound lowers confidence one level (`HIGH` to `MEDIUM`, then `LOW`). Two or more major confounds force `INCONCLUSIVE`. Severity is assigned server-side from the accepted confound code, not supplied by the client.

Accepted confound codes are `CAPTURE_GAP`, `QUALITY_DRIFT`, `SUN_EXPOSURE`, `TRAVEL`, `STRESS`, `ILLNESS`, `NEW_PRODUCT`, and `PROTOCOL_CHANGE`. M9 check-ins are implemented and store self-reported flags separately from automatic suggestions and the user's confirmations. Check-ins are keyed by experiment and date; submitting the same date updates that record and recomputes the verdict from all currently reported and confirmed flags.

The automatic suggestions use valid, successfully analyzed, non-simulated captures taken by the check-in date. `CAPTURE_GAP` is suggested when the latest eligible analysis is more than 14 days before the check-in date. `QUALITY_DRIFT` is suggested when at least one baseline and two follow-up captures exist and the average quality of up to the three most recent follow-ups differs from the baseline average by at least 5 points. Suggestions are not applied to the verdict until explicitly confirmed. Self-reported codes are limited to `SUN_EXPOSURE`, `TRAVEL`, `STRESS`, `ILLNESS`, `NEW_PRODUCT`, and `PROTOCOL_CHANGE`; users cannot self-report detector-only codes.

All generated explanation strings are cosmetic-measurement language and include relevant averages, change, noise comparison, evidence caveat, and confounds. They do not claim to diagnose, treat, cure, or prove a health outcome.

## API

- `GET /api/experiments/{experimentId}/verdict` returns the latest stored target-concern verdict, or creates an initial `INCONCLUSIVE` snapshot if none exists.
- `POST /api/experiments/{experimentId}/verdict` recomputes and appends a snapshot. Its optional body is `{"confounds":["NEW_PRODUCT"]}`; omit the body or use an empty array to clear explicit confounds.
- Both routes require an authenticated owner or an explicit demo workspace and scope access through the owning experiment.
- `GET /api/experiments/{experimentId}/check-ins` lists dated check-ins. `POST` to the same route upserts the date and returns reported flags, suggestions, and confirmed suggestions. Check-ins are disabled for experiments containing only simulated captures.
- Successful YouCam jobs recompute the target verdict and noise calibration. A verdict-storage error is logged separately and never retries an already successful provider call.

Migration V4 adds `noise_calibrations` and append-only `verdicts`. No new resource or infrastructure apply is required for the application rollout.