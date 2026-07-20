# Contract — Light/Dark theme

## Behavior

- A **toggle** is present on the login page and in the console shell; it cycles/sets the theme app-wide.
- Values: `light` | `dark` | `system`; **default `system`** (follows `prefers-color-scheme`).
- The choice is **persisted** in `localStorage` and re-applied on load.
- The active theme is expressed as `data-theme="light|dark"` (or a `.dark` class) on the document root, driving
  **CSS variables**; all colors reference the variables so the whole app restyles at once.
- **No flash**: a tiny inline script sets the initial `data-theme` **before first paint** (from localStorage or
  the OS preference), so there is no light→dark flicker on load.

## Acceptance

- Toggling restyles the entire app in **< 200 ms with no flash** (SC-002) and the choice **survives a reload**.
- On first visit with no stored choice, the app matches the OS light/dark setting.
- Both themes meet **WCAG AA** contrast for text and interactive elements (SC-005).
