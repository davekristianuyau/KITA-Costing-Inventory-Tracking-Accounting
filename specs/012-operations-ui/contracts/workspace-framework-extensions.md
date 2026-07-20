# Contract — Workspace framework extensions (shared, from 012)

012 needs three small, **backward-compatible** additions to the 011 workspace framework. They live in the shared
framework (`src/services/types.ts`, `src/workspace/`) because every later per-service UI reuses them — not in an
Operations-only module. Existing 011 manifests and tests keep working unchanged.

## 1. Reference-picker input (`type: "reference"`)

A select whose options load from a **list endpoint** through the 009 edge, so a user picks a record by a
human label instead of typing a UUID.

```ts
// addition to InputField
type: "reference"
source: {
  path: string        // edge-relative list endpoint, e.g. "/api/operations/items"
  valueKey: string    // row field used as the submitted value, e.g. "id"
  labelKeys: string[] // row fields joined for the visible label, e.g. ["sku","name"]
  labelSep?: string   // joiner (default " — ")
}
```

Behavior:

- On the function opening (or first focus), the picker fetches `source.path` via the generic edge fetch and maps
  each row to `{ value: row[valueKey], label: labelKeys.map(k => row[k]).join(labelSep ?? " — ") }`.
- Shows its **own** loading / error / empty state independent of the function's Run result; a load failure lets
  the user retry and does not block unrelated inputs.
- Behaves like any input for **required validation** (a required reference with no selection blocks Run).
- The submitted value is the raw `valueKey` (UUID); it substitutes `{param}` tokens and query values exactly as a
  text input would.

## 2. Enum selects (`type: "select"`, existing)

No code change — used with static `options` for backend enums (ItemType, UomFamily, ValuationMethod, BomType).
Listed here so the manifest's enum inputs are unambiguous.

## 3. Repeatable list input (`type: "list"`)

For request bodies with arrays (BOM `components`, sales-order/receipt `lines`).

```ts
// addition to InputField
type: "list"
fields: InputField[]   // the shape of one row (may include "reference"/"select"/text/number)
minRows?: number       // default 0; a required list implies minRows >= 1
```

Behavior:

- Renders as add/remove rows; each row is the nested `fields`. The submitted value is an array of row objects.
- Required-validation applies per required nested field and to `minRows` (a required list needs ≥ 1 valid row).
- Only the depth Operations needs (one level of nesting) is required; deeper nesting is out of scope.

## 4. id→label resolution in result tables (presentation helper)

A reusable helper resolves item UUID columns to `SKU — name` using the cached `GET /api/operations/items` list,
applied to Operations result tables (movements, availability, explosion). Columns without a known mapping (e.g.
`locationId`) render as-is. This is presentation only — no business logic, no re-computation of any value.

## Acceptance

- A `reference` input renders a populated, labelled select sourced from its list endpoint, validates when
  required, and submits the underlying id.
- Enum `select`s render their static options and submit the enum token.
- A `list` input adds/removes rows and submits an array; a required list blocks Run until it has a valid row.
- Result tables show `SKU — name` for item columns; the additions do not change existing 011 manifest behavior
  (011 `Workspace.test.tsx` stays green).
