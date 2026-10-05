# Shared Reports and Retailer Mock

Share links are immutable 30-day snapshots, not access to a live experiment. Owners create them from an experiment's results and can revoke active links from the same panel after refreshing. The plaintext token is shown only at creation; the API stores only its SHA-256 hash. Invalid, expired, and revoked links all return the same unavailable response. Public snapshots intentionally omit self-reported confound codes and the private verdict explanation so sensitive health context is not exposed by sharing.

## Embed Contract

The results panel can copy an iframe URL for an explicitly shared report:

```html
<iframe src="https://YOUR-SKINPROOF-HOST/?share=TOKEN&amp;embed=1"
        title="Shared SkinProof report"
        loading="lazy"
        referrerpolicy="no-referrer"
        style="width:100%;height:560px;border:0"></iframe>
```

This demo permits framing only from the SkinProof origin (`frame-ancestors 'self'`). It is not yet enabled for third-party retailer domains. To test the retailer-style surface, host the containing harness on the same origin and use an explicitly shared report token; never pass a workspace or experiment ID. Synthetic reports are visibly labeled, and every report includes the cosmetic-trend disclaimer. No retailer analytics or partner data is collected.

Images remain excluded unless the owner opts in. An opted-in image URL is signed for 15 minutes and may continue to work for that period after the report is revoked. Shared pages are `noindex`, `noarchive`, and `no-referrer`; API responses are not cached. The browser print stylesheet can save the same snapshot as PDF without adding a server-side renderer.

## Reminder Settings

Signed-in accounts can record a disabled-by-default opt-in, IANA timezone, weekday, local time, and weekly or biweekly cadence. Disabling the checkbox records an unsubscribe timestamp; account data deletion removes these settings. No scheduler or email provider is configured, and this preference endpoint must not be interpreted as an active delivery promise. Enable sending only after verified SES sender setup, production access, unsubscribe-path review, and cost approval.