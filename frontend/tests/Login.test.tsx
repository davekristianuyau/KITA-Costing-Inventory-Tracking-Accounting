import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import Login from "../src/pages/Login";
import Dashboard from "../src/pages/Dashboard";
import { AuthProvider } from "../src/auth/AuthContext";
import { api } from "../src/api/client";

// Replace the network client so no real HTTP happens; the login flow is exercised through the context.
vi.mock("../src/api/client", () => ({ api: { POST: vi.fn() } }));

const post = api.POST as unknown as Mock;

function renderApp() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/app" element={<Dashboard />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Company"), "acme");
  await user.type(screen.getByLabelText("Username"), "alice");
  await user.type(screen.getByLabelText("Password"), "s3cret-pw");
}

function dump(store: Storage): Record<string, string> {
  const out: Record<string, string> = {};
  for (let i = 0; i < store.length; i++) {
    const k = store.key(i) as string;
    out[k] = store.getItem(k) as string;
  }
  return out;
}

beforeEach(() => {
  post.mockReset();
  sessionStorage.clear();
  localStorage.clear();
});

describe("Login", () => {
  it("goes idle → submitting → success and redirects to the app", async () => {
    const user = userEvent.setup();
    let resolve!: (v: unknown) => void;
    post.mockReturnValue(new Promise((r) => (resolve = r)));

    renderApp();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    // submitting: button disabled + spinner label, inputs disabled
    expect(screen.getByRole("button")).toBeDisabled();
    expect(screen.getByRole("button")).toHaveTextContent("Signing in…");
    expect(screen.getByLabelText("Company")).toBeDisabled();

    resolve({ response: { ok: true, status: 200 }, data: { client: "client-a", expiresIn: 5400 } });

    expect(await screen.findByText(/Signed in/)).toBeInTheDocument();
    expect(screen.getByText("client-a")).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/auth/login", {
      body: { company: "acme", username: "alice", password: "s3cret-pw" },
    });
  });

  it("shows a generic, non-revealing error on 401 and does not redirect", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({ response: { ok: false, status: 401 }, data: undefined });

    renderApp();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/invalid credentials/i);
    // never reveal which field was wrong
    expect(alert.textContent ?? "").not.toMatch(/username|password/i);
    // still on the login page, inputs re-enabled
    expect(screen.getByRole("heading", { name: /sign in to kita/i })).toBeInTheDocument();
    expect(screen.getByLabelText("Company")).toBeEnabled();
  });

  it("shows a locked message on 423", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({ response: { ok: false, status: 423 }, data: undefined });

    renderApp();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/locked/i);
  });

  it("keeps no token or password in JS-readable storage", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({ response: { ok: true, status: 200 }, data: { client: "client-a", expiresIn: 5400 } });

    renderApp();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByText(/Signed in/);

    const serialized = JSON.stringify({ local: dump(localStorage), session: dump(sessionStorage) });
    expect(serialized).not.toContain("s3cret-pw"); // password never persisted
    expect(serialized).not.toContain("eyJ"); // no JWT/JWE compact token persisted
  });
});
