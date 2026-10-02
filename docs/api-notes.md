# YouCam API Notes

Status: **live UI sample runs completed on 2026-10-02 (deployed app, Camera test modal)**.

## Live M0 Results (Deployed UI)

Scope executed in deployed app:

1. Opened Camera test in `[hosted demo endpoint omitted]`.
2. Ticked explicit consent checkbox.
3. Uploaded three approved local samples from `sample image/`.
4. Used the same two SD concerns for all runs: `EYE_BAG` and `TEXTURE`.
5. Clicked Analyze sample for each image.

Image metadata (local files):

| Image | Dimensions | Format | File size (bytes) |
| --- | --- | --- | ---: |
| `sample image/1.png` | 1200x1600 | png | 2,105,871 |
| `sample image/2.png` | 1200x1600 | png | 2,109,326 |
| `sample image/3.png` | 1200x1600 | png | 2,110,721 |

Per-run analysis results (captured from browser network response for `POST /api/analysis/skin?concerns=TEXTURE&concerns=EYE_BAG`):

| Run | Image | Success | Elapsed (ms) | Task ID | Mode | Reported unit estimate |
| --- | --- | --- | ---: | --- | --- | ---: |
| 1 | `1.png` | yes (HTTP 200) | 4402 | `9EIPAokAgBqTW99N22h9yg-5W-m7dhs8CGtpPFmM4j25HYNjudcgja30wZlGVeLkSpcgV8WTLEnG8Q2PXjtLzQ` | SD | 9 |
| 2 | `2.png` | yes (HTTP 200) | 4531 | `MFWuactvtdIHxd8qTEWGH6SUkGNJiVm1NdqFLIYCYo1GzOV0Zz_odct8hSLUGLpGSpcgV8WTLEnG8Q2PXjtLzQ` | SD | 9 |
| 3 | `3.png` | yes (HTTP 200) | 4640 | `W_RUUKSFnXzn_xaR7gRUpsAR36yTYZ1EVPsQtu26rfHdJWYhOIkUdLFzz0oDbo13SpcgV8WTLEnG8Q2PXjtLzQ` | SD | 9 |

Returned score fields by concern (raw_score, ui_score, mask count):

| Run | Concern | raw_score | ui_score | mask_count |
| --- | --- | ---: | ---: | ---: |
| 1 | eye_bag | 27.53433585166931 | 58 | 1 |
| 1 | texture | 63.727009296417236 | 74 | 1 |
| 1 | all | null | null | 0 |
| 1 | skin_age | null | null | 0 |
| 1 | resize_image | null | null | 1 |
| 2 | eye_bag | 64.42588567733765 | 74 | 1 |
| 2 | texture | 63.91527056694031 | 74 | 1 |
| 2 | all | null | null | 0 |
| 2 | skin_age | null | null | 0 |
| 2 | resize_image | null | null | 1 |
| 3 | eye_bag | 72.4600076675415 | 77 | 1 |
| 3 | texture | 57.4874222278595 | 71 | 1 |
| 3 | all | null | null | 0 |
| 3 | skin_age | null | null | 0 |
| 3 | resize_image | null | null | 1 |

Observed response shape (real payload):

- Top-level fields: `taskId`, `mode`, `documentedUnitEstimate`, `scores[]`.
- Each score object includes: `concern`, `rawScore`, `uiScore`, `maskUrls[]`.
- Besides requested concerns, the payload also included `all`, `skin_age`, and `resize_image` items in all three runs.

Raw-score trend behavior (higher raw score = healthier/more aesthetically pleasing):

- `eye_bag` raw trend across runs: `27.53 -> 64.43 -> 72.46` (upward trend).
- `texture` raw trend across runs: `63.73 -> 63.92 -> 57.49` (flat then down on run 3).
- UI scores are consistently more favorable/rounded and should not replace raw-score trend calculations.

Account-unit balance verification:

- The deployed UI correctly warns that `documentedUnitEstimate` is a published estimate, not billing truth.
- During this run, no account-balance value was exposed by:
	- the deployed app response payload,
	- direct task-create/task-poll response headers from `yce-api-01.makeupar.com`, or
	- discoverable public skin-analysis API endpoints.
- Therefore, actual `before/after` unit balance still must be confirmed in the provider API console/dashboard for this API key.
- This document does not treat `documentedUnitEstimate=9` as proof of billed-unit deduction.

## Verified Public Documentation

Sources reviewed on 2026-09-30:

- [AI Skin Analysis reference](https://docs.perfectcorp.com/reference/ai_skin_analysis)
- [API Playground guide](https://docs.perfectcorp.com/develop/api_playground)
- [API Server](https://docs.perfectcorp.com/develop/api_server)

The documented server is `https://yce-api-01.makeupar.com`. The workflow is:

1. `POST /s2s/v2.0/file` with file metadata (`content_type`, `file_name`, `file_size`). The response supplies a `file_id` and a signed upload URL with required method/headers.
2. Upload the image bytes to the supplied signed URL using the returned method and headers. The file API call alone does not upload the image.
3. `POST /s2s/v2.0/task/skin-analysis` with `src_file_id`, `dst_actions`, and JSON output format.
4. Poll `GET /s2s/v2.0/task/skin-analysis/{task_id}` until `data.task_status` is `success` or `error`. The docs say successful results are retained for 24 hours and polling does not consume units.

The M0 endpoint currently implements that documented SD flow at `POST /api/analysis/skin` as `multipart/form-data` field `image`, repeated `concerns` query parameters, and required `X-SkinProof-Consent: true` header. It returns task ID, SD mode, a published unit estimate, and per-concern `rawScore`, `uiScore`, and `maskUrls`. The API key is read only from server-side `YOUCAM_API_KEY`.

## SD Concern and Score Contract

The documented SD action allowlist contains 16 actions: `wrinkle`, `droopy_upper_eyelid`, `droopy_lower_eyelid`, `firmness`, `acne`, `moisture`, `eye_bag`, `dark_circle_v2`, `age_spot`, `radiance`, `redness`, `oiliness`, `pore`, `texture`, `tear_trough`, and `skin_type`. These are implemented in the camera test, backend enum, and experiment target selector. Do not mix SD and HD actions in a single request. The docs describe score values in the 1–100 range and state higher raw scores mean healthier/more aesthetically pleasing skin. The `ui_score` is adjusted to be more favorable; use `raw_score` for trend calculations and keep both values for transparency. `skin_type` is categorical; do not treat its category output as a numeric trend score.

The task response documentation shows `data.results.output[]` items with `type`, `raw_score`, `ui_score`, and `mask_urls`. The broader result ZIP includes `score_info.json` and PNG masks. The M0 endpoint currently parses the task response `mask_urls`; it does not yet download/process result ZIP archives.

## Published Unit Schedule

Official published estimates for AI Skin Analysis V2.0/V2.1:

| Concern count | SD units | HD units |
| --- | ---: | ---: |
| 1–4 | 9 | 12 |
| 5–8 | 12 | 16 |
| 9–12 | 14 | 20 |
| 13–16 | 16 | 22 |

The endpoint reports this as `documentedUnitEstimate`. It is not an account balance or measured charge. Confirm actual account usage, quotas, rate limits, and any plan-specific differences in the API console. The public docs reviewed here did not establish account-specific rate limits or validator entitlement.

## Image and Camera Requirements

The docs list JPEG/PNG input, under 10 MB, SD minimum short side 480 px, and recommend a front-facing, evenly lit image with the face occupying about 60–80% of frame width. The API endpoint additionally limits the long side to 4096 px.

The documented browser SDK is `https://plugins-media.makeupar.com/v2.5-camera-kit/sdk.js`. Use `YMK.init({ faceDetectionMode: "skincare", imageFormat: "base64", language: "enu", qualityLevel: "moderate" })`, listen to `faceQualityChanged` and `faceDetectionCaptured`, and mount the SDK at `#YMK-module`. The docs require HTTPS for camera access except localhost. The application includes this flow, but actual browser capture has not been exercised because no hosted HTTPS environment is deployed.

## Live M0 Results Summary

Empirical deployed-app runs are documented above in **Live M0 Results (Deployed UI)**. Account-balance deltas remain a provider-console verification step because no balance value is exposed in the tested API responses.

Do not use `frontend/src/assets/hero.png` as a skin-analysis sample; it is scaffold artwork. Use provider-approved examples or images supplied with explicit consent. Do not commit photos, API keys, signed URLs, or analysis masks.

## Other Application Endpoints

The experiment UI also calls `GET /api/experiments`, `POST /api/experiments`, and `POST /api/experiments/{id}/archive`. These use `X-Demo-Workspace` for browser-profile separation, not authentication. Swagger UI is available at `/swagger-ui.html` when the backend is running.