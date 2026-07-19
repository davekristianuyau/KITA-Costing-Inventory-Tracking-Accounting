# Contract — Service function manifest (the split seam)

The manifest is the interface between the **011 framework** (renders it) and the **per-service specs** (author
it). 011 ships the framework + one reference function; each per-service spec provides that service's full manifest.

## Schema (per service)

```ts
type ServiceManifest = {
  id: string            // "operations"
  label: string         // "Operations"
  icon: string          // lucide icon name
  basePath: string      // "/api/operations" (edge-relative)
  functions: ServiceFunction[]
}

type ServiceFunction = {
  id: string
  label: string
  icon?: string
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
  path: string          // "/items/{id}" — {param} filled from inputs
  inputs?: InputField[] // rendered as the run-form
  result: "table" | "json" | "detail" | "message"
}

type InputField = {
  name: string
  label: string
  type: "text" | "number" | "select" | "textarea" | "boolean"
  required?: boolean
  options?: string[]    // for select
}
```

## Framework behavior (011)

- Renders each service's `functions` in the **left pane**; the selected function's `inputs` become a **run-form**.
- **Run** issues `method basePath+path` through the **009 edge** (openapi-fetch, cookie auth), showing a
  **loading** state, then renders the response per `result` (table/json/detail/message) or a clear **error**.
- Path `{param}` tokens are substituted from `inputs`; missing required inputs block the call with inline
  validation.

## 011 deliverable

- The framework above + **one reference function** per the reference service (a safe read/health call) wired
  end-to-end and covered by a test. Full manifests are **out of scope** here (per-service specs).

## Acceptance

- Given any valid manifest, the framework renders its functions and can run one end-to-end against the edge.
- The reference function returns a real result (or a clear error) from the browser with a visible loading state.
