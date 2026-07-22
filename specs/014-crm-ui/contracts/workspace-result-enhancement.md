# Contract — Workspace result enhancement (shared, from 014)

014 needs one small, **backward-compatible** addition to the 011/012/013 workspace framework's **result
rendering** (in `FunctionWorkspace`'s `DetailView`). It lives in the shared framework because any service with an
object result that carries a nested list benefits (the CRM quote is the first).

## Detail sub-table for array-of-objects fields

In the **detail** result view, when a field's value is an **array of objects**, render it as a compact nested
**sub-table** (same styling as the top-level result table) instead of stringifying it.

- Scalar and plain-object fields render as today (key → value; `resultRefs` id→label still applies to scalars).
- An **array of objects** field (e.g. the quote's `breakdown[]` = `[{tierCode, origin, baseApplied,
  amountRemoved}, …]`) renders under its key as a sub-table with a column per row key.
- An **array of scalars** (e.g. `flags[]`) renders as a comma/'·'-joined list (or short chips).
- Empty arrays render as an empty-state dash, not an error.

No new result **kind** is introduced — this is purely how `detail` renders nested arrays; existing `detail`
results (single-level objects) are unaffected.

## Acceptance

- A `detail` result whose object has an array-of-objects field renders that field as a readable sub-table
  (columns = the array rows' keys), so the quote's `breakdown[]` shows every discount step itemized (SC-003).
- Existing 011/012/013 detail results (no nested arrays) render identically to before — their tests stay green.
