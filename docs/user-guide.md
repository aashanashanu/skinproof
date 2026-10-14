# SkinProof User Guide

SkinProof helps you track cosmetic skin-score trends while trying one product. It is not medical advice, does not diagnose or treat conditions, and cannot prove a product caused a change.

## Start an Experiment

1. Open the hosted application and sign in with your account. Experiments are private to the signed-in account. If you still have an older demo workspace in this browser, sign in from that same browser to claim it.
2. Choose **New experiment** and enter one product, one supported skin concern, a start date, and trial length. Keep the protocol notes useful and repeatable, such as usual application timing and capture conditions.
3. Keep other routine changes to a minimum while the trial is active. A second new product or a changed routine makes it harder to interpret the trend.

## Archive, Restore, or Delete

Use **Archive** on an active experiment to move it out of the active list without deleting its history. Expand the **Archived** section to find it later. **Restore** returns it to the active list. **Delete** permanently removes the experiment, check-ins, verdicts, capture records, and private images; confirm the warning only when you are sure. Deleting an experiment also invalidates its shared report links.

## Capture and Analyze

For a useful comparison, take captures at a consistent time, distance, angle, and lighting. Create two baseline captures at the beginning, marking each as baseline, then add follow-up captures regularly.

Before capture, select the consent checkbox. SkinProof stores real captures in private AWS storage and sends them to YouCam for analysis only after consent. Use **Open guided camera** and allow camera access on the HTTPS site. If the camera is unavailable, use **Upload photo fallback**; manually uploaded photos do not have camera pose and face-position checks verified.

Choose the experiment's target concern and mark whether the capture is a baseline. After **Save capture and analyze**, the analysis runs asynchronously. `QUEUED`, `PROCESSING`, and `RETRY_WAIT` are in-progress states. A failed analysis can be retried from its capture dialog; the capture remains saved.

## Read Results

The results view shows capture history, raw-score trends, the noise floor, and the current verdict. Higher raw scores are more favorable for supported numeric concerns; the provider's adjusted UI score is shown for reference but is not used for trend calculations. See [Measurement and Verdict Contract](measurement-verdict.md) for exact thresholds and evidence requirements.

To use an already captured image as a baseline, expand its entry under **Analysis details** and choose **Make baseline**. This is available for valid, successfully analyzed follow-up captures. The capture joins the existing baseline set and the verdict updates without uploading the image or running analysis again. Use images from the beginning of the experiment for a meaningful starting comparison.

Detection masks are retained in private storage after analysis and loaded through an owner-checked endpoint. For older captures, opening the comparison retains masks only if the provider's original image links still work. Already-expired legacy masks cannot be recovered from saved scores; they show an unavailable message. SkinProof does not automatically run another paid analysis to replace missing masks.

The verdict may be **Improved**, **No detectable change**, **Worse**, or **Inconclusive**. At least two eligible baseline captures, two follow-up captures, four unique valid captures, and four weeks of observation are needed for a directional result. An inconclusive result is intentional when evidence is insufficient or ambiguous.

## Weekly Check-ins

In results, record changes such as unusual sun exposure, illness, travel, stress, adding another product, or changing the protocol. SkinProof may suggest a capture-gap or quality-drift flag from the history. Suggestions are not treated as reported events unless you confirm them. Confirmed flags can lower confidence or make a result inconclusive; they do not change the score arithmetic.

Check-ins are disabled for experiments containing only synthetic captures.

## Ingredient Check

Paste an ingredient list to compare recognized names with SkinProof's deterministic starter rules. The result can flag common irritation-group overlaps and unrecognized names; it does not recommend treatment or determine whether a product is safe for you. The check is stateless and is not saved to an experiment.

## Share a Report

From an experiment's results, create a share link. It is a read-only snapshot that expires after 30 days; it does not give access to the live experiment. Copy the token link when it is created and use the share history to revoke an active link later.

Images are excluded unless you explicitly opt in. Anyone with an image-enabled link can view its included images. Individual image URLs last 15 minutes and may continue working briefly after the report is revoked. The same-origin iframe mock and browser print-to-PDF are available from the report view; third-party retailer framing is not enabled.

## Account and Reminder Settings

The reminder panel stores opt-in and schedule preferences only. **Email reminders are not sent.** Delete my account data permanently removes your owned experiments, captures, and reminder preferences. Deletion cannot be undone.

## Demo Data and Privacy

Synthetic examples are illustrative only and are labeled in the interface. They are not real YouCam outcomes and are excluded from real trend calculations. Real captures are sensitive: do not upload another person's image, and remember that consent sends the image to YouCam for analysis. For details, see the [README privacy notes](../README.md#privacy-consent-and-retention) and [verified API notes](api-notes.md).

## Troubleshooting

- **Camera will not open:** allow camera permission, use the hosted HTTPS page, or use the photo-upload fallback.
- **Analysis is still running:** leave the saved capture in place and refresh results shortly; retry only if the capture reaches `FAILED`.
- **Results are inconclusive:** check the required capture count and elapsed time, then review the noise floor and confirmed confounds.
- **The API is unavailable:** retry after a short interval. Avoid repeated uploads while the service is unavailable; an accepted capture is saved before analysis completes.