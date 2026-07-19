# Contract — Navigation model

One tab per service; the tabs are the service selection. URL-driven so views are linkable and back/forward works.

## Routes

- `/login` — the redesigned login page (public).
- `/app` — protected; redirects to the first available service.
- `/app/:service` — protected; the service's workspace: **top tabs** (one per service, `:service` active) +
  **left pane** of that service's functions + workspace showing the service's default/first function.
- `/app/:service/:function` — protected; the same, with `:function` selected in the workspace.

## Behavior

- **Top tabs** render one tab per service available to the signed-in client; selecting a tab navigates to
  `/app/:service`. Accessible tab semantics (Radix Tabs: roving focus, arrow-key nav, `aria-selected`).
- **Left pane** lists the selected service's functions; selecting one navigates to `/app/:service/:function` and
  swaps the workspace **without a full page reload**.
- **Protected routes**: unauthenticated access → redirect to `/login` (reuse 009's guard); session expiry (401
  from the edge) → clear + redirect to `/login`.
- **Sign-out** returns to `/login`; the **theme toggle** and the user/client identity are visible in the shell.

## Acceptance

- Signing in lands on `/app/<first-service>` with tabs + left pane + workspace rendered.
- Switching tab changes the service (and its left-pane functions); switching left-pane item changes the
  workspace, both reflected in the URL, both without a full reload.
- Keyboard-only users can move across tabs, the left pane, and the workspace form.
