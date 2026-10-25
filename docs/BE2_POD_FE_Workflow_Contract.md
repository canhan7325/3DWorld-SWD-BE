# BE2 POD FE Workflow Contract

`GET /api/print/requests/getId/{printRequestId}` remains CUSTOMER owner-scoped. Its existing fields are unchanged and it now adds `workflow`. The server derives this object from the request, first quote, current quote, review decision and immutable submitted-revision events. FE must refresh Detail after each action and never POST an arbitrary request status.

```json
{
  "workflow": {
    "displayStatus": "DRAFT",
    "quoteDeadline": null,
    "paymentExpiresAt": null,
    "remainingRevisions": null,
    "currentFileVersion": 1,
    "currentSpecVersion": 1,
    "allowedActions": {
      "editFile": true,
      "editSpecification": true,
      "confirmPreview": true,
      "submit": false,
      "requestRevision": false,
      "acceptQuote": false,
      "pay": false,
      "cancel": true
    }
  }
}
```

`quoteDeadline` and `remainingRevisions` are `null` before the first quote. `paymentExpiresAt` is currently always `null`: BE2 has no approved BE1 payment-session read contract. `allowedActions` expresses current UI eligibility, never authorization by itself. `pay` is shown only for an accepted current quote/pair; BE1 still has to enforce the `FULL` stage and current quote source.

| Persisted request state / condition | Derived `displayStatus` | Main Customer actions |
| --- | --- | --- |
| Draft, current File/Spec V1 | `DRAFT` | Edit, confirm preview, then Submit when rights and preview are confirmed. |
| Submitted without active quote | `SUBMITTED` | Wait for Staff review; no generic edit. |
| Active correction decision | `REQUIRES_REVISION` | Correct File/Spec, reconfirm preview, resubmit; deadline gate applies after first quote. |
| Current issued quote | `WAITING_QUOTE_ACCEPTANCE` | Accept current quote or request revision before first-quote deadline, subject to remaining revisions. |
| Unaccepted quote deadline passed, before scheduler commits | `QUOTE_EXPIRED` | Quote acceptance/revision and correction actions false; refresh Detail for committed `CANCELLED`. |
| Current accepted quote | `PAYMENT_PENDING` | Pay through BE1 `FULL` source; no ordinary edit/cancel. Passing quote deadline alone does not cancel it. |
| Verified queue state | `QUEUED` | No Customer edit/cancel/revision. |
| Cancelled | `CANCELLED` | All mutation actions false. |

The other workflow properties retain the same shape for every row. For Submitted/Revision/Quoted/Payment/Queued/Cancelled responses, `currentFileVersion` and `currentSpecVersion` reflect persisted pointers. `quoteDeadline` stays the first quote's fixed timestamp when a quote exists; `remainingRevisions` is `3 - confirmed post-quote resubmissions` with floor zero. Unavailable payment deadline remains `null`. The backend uses `WAITING_QUOTE_ACCEPTANCE` and `PAYMENT_PENDING` for display only; no such request status is persisted.

The `QUOTE_EXPIRED` display is also derived, not a database status. It closes UI actions during the interval between the server deadline and the background cancellation. Invalid first-Quote timestamps yield `quoteDeadline=null` and do not enable Quote acceptance; they require data reconciliation.

Existing action routes remain: `POST /api/print/requests/create`, `POST /api/print/requests/getId/{id}/file/correct`, `PATCH /api/print/requests/getId/{id}/specifications`, `POST /api/print/requests/getId/{id}/preview/confirm`, `POST /api/print/requests/getId/{id}/submit`, `POST /api/print/requests/getId/{id}/cancel`, `POST /api/print/requests/getId/{id}/quotes/{quoteId}/request-revision`, and `POST /api/print/requests/getId/{id}/quotes/{quoteId}/accept`. Staff routes are listed in `BE2_V2_API_Registry.md`. All mutations recheck server-side eligibility under the request lock where applicable.
