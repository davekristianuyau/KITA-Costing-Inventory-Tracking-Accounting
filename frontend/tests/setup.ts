// Vitest setup: register jest-dom matchers (toBeInTheDocument, toBeDisabled, ...) on Vitest's expect.
import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

// jsdom has no matchMedia — stub it (defaults to light / not-dark) so the theme system can run in tests.
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}
