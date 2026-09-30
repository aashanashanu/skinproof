# YouCam API Notes

Status: **documentation reviewed; live API tests pending**. The official documentation is available, but this repository has not made a live YouCam request. The API key is not configured in the current environment, no approved sample face images are present, and no AWS credentials are available. Therefore the live response shape, account-specific units, latency, and camera behavior are not measured here.

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

The implemented SD action allowlist is `acne`, `age_spot`, `redness`, and `texture`. The request must not mix SD and HD actions. The docs describe score values in the 1–100 range and state higher raw scores mean healthier/more aesthetically pleasing skin. The `ui_score` is adjusted to be more favorable; use `raw_score` for trend calculations and keep both values for transparency.

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

## Live M0 Results

| Check | Result |
| --- | --- |
| API key/playground test | Pending: key absent from the current environment; never paste it into source or chat. |
| Three sample photos | Pending: no approved sample face photos in the workspace. |
| Actual response, score direction, latency | Pending live calls. |
| Actual billed units and account limits | Pending API console/account observation. |
| Browser Camera Kit capture | Code is present; browser test pending HTTPS deployment and user camera permission. |

Do not use `frontend/src/assets/hero.png` as a skin-analysis sample; it is scaffold artwork. Use provider-approved examples or images supplied with explicit consent. Do not commit photos, API keys, signed URLs, or analysis masks.

## Other Application Endpoints

The experiment UI also calls `GET /api/experiments`, `POST /api/experiments`, and `POST /api/experiments/{id}/archive`. These use `X-Demo-Workspace` for browser-profile separation, not authentication. Swagger UI is available at `/swagger-ui.html` when the backend is running.