# Quickstart — Service Console Foundation

Validates the console foundation locally with Docker + Node. Run from the repo root on branch
`011-service-console-ui`. **0 real cloud.**

## 1. Beautiful login + theme (US1)

```bash
cd frontend && npm run dev    # or the built app served by the sim
```

**Expect**: a modern login page (current icons, clean layout) with company/username/password and a **theme
toggle**; toggling switches light/dark **instantly with no flash** and the choice survives a reload; a valid
sign-in proceeds, an invalid one shows a clear generic error.

## 2. Console shell — one tab per service (US2)

**Expect**: after sign-in you land in the console with **one top tab per service** available to your client;
selecting a tab opens that service's workspace; the theme toggle + your identity/client are visible; sign-out
returns to `/login`.

## 3. Per-service workspace framework + reference function (US3)

**Expect**: a selected service shows a **left pane of its functions** beside a **workspace**; selecting the
**reference function** and running it calls the real backend **through the edge**, shows a **loading** state,
then a **result** (or a clear error); switching left-pane items swaps the workspace without a full reload.

## 4. Local floci-aws environment + Floci UI (US4)

```bash
# One documented startup — floci-aws WITH the Docker socket (-v /var/run/docker.sock… -u root, so it runs
# real compute + the UI can reach the runtime), the client's imitated AWS deploy, and the 009 stack:
bash sim/console/console-up.sh client-a     # floci-aws :4566 (private) + Floci UI :4500 + console :8080
open http://localhost:8080/login            # company=client-a user=alice password=demo-pass
open http://localhost:4500/                 # Floci UI — inspect the deployment (no "container runtime" error)
bash sim/console/console-smoke.sh client-a  # login → run reference function → UI reachable → 0 real cloud
bash sim/console/console-down.sh            # independent teardown
```

**Expect**: `floci-aws` is running with the client's AWS resources deployed, the **Floci UI opens (HTTP 200)**,
the console is reachable in the browser, and logging in + running the reference function works end-to-end with
**0** real cloud credentials.

## 5. Automated checks

```bash
cd frontend && npm test           # Vitest: login, theme, tabs, sidebar, workspace, reference function
cd frontend && npm run build      # type-check + production build
```

**Expect**: all green; theme/nav/workspace behaviors and the reference function covered.

Details: [contracts/](./contracts/), [data-model.md](./data-model.md), [research.md](./research.md).
Full per-service functionality arrives in the per-service specs (starting with Operations).
