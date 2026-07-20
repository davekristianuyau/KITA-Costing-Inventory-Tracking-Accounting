# Data Model — Service Console Foundation

Frontend/UX feature; entities are UI/config objects + the reused 009 session. No new server-side storage.

## Session / Client (reused from 009)

| Field | Type | Notes |
|---|---|---|
| `authenticated` | bool | from the httpOnly session cookie (009) |
| `client` | string | tenant id; scopes which services + deployment are shown |
| `username`, `roles` | string / string[] | from the session token |

## Backend Service (a top tab)

| Field | Type | Notes |
|---|---|---|
| `id` | string | e.g. `operations`, `hr`, `crm`, `procurement`, `workflow` |
| `label` | string | tab text |
| `icon` | string | lucide icon name |
| `basePath` | string | edge path prefix, e.g. `/api/operations` |
| `functions` | ServiceFunction[] | the service's manifest (left-pane items) |

- The set of services shown = the client's available services. 011 ships the registry + a reference entry per
  service; per-service specs author the full `functions`.

## Service Function (manifest entry — the split seam)

| Field | Type | Notes |
|---|---|---|
| `id` | string | unique within the service |
| `label` | string | left-pane text |
| `icon` | string | lucide icon name (optional) |
| `method` | enum GET/POST/PUT/PATCH/DELETE | how it's invoked through the edge |
| `path` | string | path template under the service base (e.g. `/items/{id}`) |
| `inputs` | InputField[] | fields the run-form renders (name, label, type, required) |
| `result` | enum `table` \| `json` \| `detail` \| `message` | how the workspace renders the response |

- **Contract**: [contracts/service-manifest.md](contracts/service-manifest.md). 011 renders any manifest into a
  left pane + a generic run-form/result workspace, and ships **one reference function** wired end-to-end.

## Theme Preference

| Field | Type | Notes |
|---|---|---|
| value | enum `light` \| `dark` \| `system` | default `system` (follows `prefers-color-scheme`) |
| persistence | localStorage key | applied pre-paint (no flash); toggled app-wide |

## floci-aws Deployment

| Field | Type | Notes |
|---|---|---|
| emulator | `floci/floci` :4566 | Docker socket mounted, `-u root` → runs real compute |
| ui | `floci/floci-ui` :4500 | deployment console (host-exposed for inspection) |
| resources | (010) | the client's AWS resources deployed via feature 010 |
| credentials | dummy | 0 real cloud (SC-007) |

## Relationships

- One **Client** → the set of **Backend Services** (tabs) → each has many **Service Functions** (left pane) →
  each **Function** opens in the **Workspace**. **Theme** is per-user (localStorage). The **floci-aws
  Deployment** represents the client's cloud target, inspectable via the Floci UI; service calls route via the
  009 edge.

## Out of scope (per-service specs)

- The full `functions` manifests + any bespoke per-operation workspaces for each service.
- Hosting the full app on floci-aws ECS (enabled by the Phase-0 finding; a follow-on).
