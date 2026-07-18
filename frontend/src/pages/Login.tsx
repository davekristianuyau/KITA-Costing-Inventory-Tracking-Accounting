// Login page (contracts/frontend-login.md): company + username + password, with idle → submitting →
// success/error states. Errors are generic and non-revealing (never say which field was wrong). On success
// the edge has set the httpOnly session cookie; we just redirect to the intended route.
import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AuthError, useAuth } from "../auth/AuthContext";

type Status = "idle" | "submitting" | "error";

const MESSAGES: Record<string, string> = {
  invalid: "Invalid credentials. Please check your details and try again.",
  locked: "This account is temporarily locked. Please try again later.",
  unavailable: "Service temporarily unavailable. Please try again.",
  network: "Unable to reach the server. Please try again.",
};

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/app";

  const [company, setCompany] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);

  const busy = status === "submitting";

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setStatus("submitting");
    setError(null);
    try {
      await login(company, username, password);
      navigate(from, { replace: true });
    } catch (err) {
      const kind = err instanceof AuthError ? err.kind : "unavailable";
      setError(MESSAGES[kind]);
      setStatus("error");
    }
  }

  return (
    <main>
      <h1>Sign in to KITA</h1>
      <form onSubmit={onSubmit} aria-busy={busy}>
        <label htmlFor="company">Company</label>
        <input
          id="company"
          name="company"
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          disabled={busy}
          required
        />

        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          disabled={busy}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={busy}
          required
        />

        <button type="submit" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>

      {error && (
        <p role="alert" data-testid="login-error">
          {error}
        </p>
      )}
    </main>
  );
}
