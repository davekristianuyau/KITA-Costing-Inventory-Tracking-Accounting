import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import App from "../src/App";
import { AuthProvider } from "../src/auth/AuthContext";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { registry } from "../src/services/registry";
import { api } from "../src/api/client";

vi.mock("../src/api/client", () => ({ api: { POST: vi.fn(), request: vi.fn() } }));
const post = api.POST as unknown as Mock;

function renderAt(path: string, authed = true) {
  if (authed) sessionStorage.setItem("kita.client", "acme");
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  post.mockReset();
  sessionStorage.clear();
  localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

describe("Console shell", () => {
  it("renders one top tab per registry service after auth", () => {
    renderAt("/app/operations");
    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(registry.length);
    for (const svc of registry) {
      expect(screen.getByRole("tab", { name: new RegExp(svc.label, "i") })).toBeInTheDocument();
    }
    // the active tab matches the URL
    expect(screen.getByRole("tab", { name: /operations/i })).toHaveAttribute("aria-selected", "true");
  });

  it("selecting a tab navigates to that service", async () => {
    const user = userEvent.setup();
    renderAt("/app/operations");
    await user.click(screen.getByRole("tab", { name: /procurement/i }));
    expect(screen.getByRole("tab", { name: /procurement/i })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: /operations/i })).toHaveAttribute("aria-selected", "false");
  });

  it("shows the theme toggle, the signed-in client, and a sign-out control", () => {
    renderAt("/app/operations");
    expect(screen.getByRole("button", { name: /switch to (dark|light) theme/i })).toBeInTheDocument();
    expect(screen.getByText(/acme/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
  });

  it("redirects a bare /app to the first service", () => {
    renderAt("/app");
    expect(screen.getByRole("tab", { name: new RegExp(registry[0].label, "i") })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("redirects an unauthenticated user to /login", () => {
    renderAt("/app/operations", false);
    expect(screen.getByRole("heading", { name: /sign in to kita/i })).toBeInTheDocument();
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
  });

  it("signs out back to the login page", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({ response: { ok: true, status: 200 }, data: undefined });
    renderAt("/app/operations");
    await user.click(screen.getByRole("button", { name: /sign out/i }));
    expect(await screen.findByRole("heading", { name: /sign in to kita/i })).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/auth/logout");
  });
});
