import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import Login from "../src/pages/Login";
import { AuthProvider } from "../src/auth/AuthContext";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { api } from "../src/api/client";

// Replace the network client so no real HTTP happens; the login flow is exercised through the context.
vi.mock("../src/api/client", () => ({ api: { POST: vi.fn() } }));

const post = api.POST as unknown as Mock;

// A stand-in destination proving a successful login redirected into the app.
function Landing() {
  return <h1>Console home</h1>;
}

function renderApp() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/login"]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/app" element={<Landing />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
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
  document.documentElement.removeAttribute("data-theme");
});

describe("Login (redesigned)", () => {
  it("renders the modern layout: three fields, submit, and a theme toggle", () => {
    renderApp();
    expect(screen.getByRole("heading", { name: /sign in to kita/i })).toBeInTheDocument();
    expect(screen.getByLabelText("Company")).toBeInTheDocument();
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    // theme toggle available pre-login
    expect(screen.getByRole("button", { name: /switch to (dark|light) theme/i })).toBeInTheDocument();
  });

  it("toggling the theme flips data-theme on the document root", async () => {
    const user = userEvent.setup();
    renderApp();
    // no-flash init runs in index.html (not in jsdom); provider applies "light" by default here
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    await user.click(screen.getByRole("button", { name: /switch to dark theme/i }));
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    await user.click(screen.getByRole("button", { name: /switch to light theme/i }));
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
  });

  it("goes idle → submitting → success and redirects into the app", async () => {
    const user = userEvent.setup();
    let resolve!: (v: unknown) => void;
    post.mockReturnValue(new Promise((r) => (resolve = r)));

    renderApp();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    // submitting: button disabled + spinner label, inputs disabled
    expect(screen.getByRole("button", { name: /signing in/i })).toBeDisabled();
    expect(screen.getByLabelText("Company")).toBeDisabled();

    resolve({ response: { ok: true, status: 200 }, data: { client: "client-a", expiresIn: 5400 } });

    expect(await screen.findByText(/Console home/)).toBeInTheDocument();
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
    expect(alert.textContent ?? "").not.toMatch(/username|password/i);
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
    await screen.findByText(/Console home/);

    const serialized = JSON.stringify({ local: dump(localStorage), session: dump(sessionStorage) });
    expect(serialized).not.toContain("s3cret-pw"); // password never persisted
    expect(serialized).not.toContain("eyJ"); // no JWT/JWE compact token persisted
  });
});
