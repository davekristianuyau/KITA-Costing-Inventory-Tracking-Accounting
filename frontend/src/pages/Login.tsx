// Login page (contracts/frontend-login.md + 011 redesign): a branded, uncluttered sign-in card built
// with Tailwind/Radix/lucide. Company + username + password with idle → submitting → error states.
// Errors are generic and non-revealing. A theme toggle is available pre-auth. On success the edge has set
// the httpOnly session cookie; we just redirect to the intended route. 009 auth is preserved unchanged.
import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AlertCircle, Boxes, Loader2, Lock, User } from "lucide-react";
import { AuthError, useAuth } from "../auth/AuthContext";
import ThemeToggle from "../theme/ThemeToggle";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import { cn } from "../ui/cn";

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
    <main className="relative flex min-h-screen items-center justify-center bg-bg px-4 py-10">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-lg bg-primary text-primary-fg shadow-sm">
            <Boxes size={28} aria-hidden />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-text">Sign in to KITA</h1>
          <p className="mt-1 text-sm text-muted">Costing, Inventory &amp; Accounting console</p>
        </div>

        <div className="rounded-lg border border-border bg-card p-6 shadow-sm">
          <form onSubmit={onSubmit} aria-busy={busy} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="company" className="text-sm font-medium text-text">
                Company
              </label>
              <input
                id="company"
                name="company"
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                disabled={busy}
                autoComplete="organization"
                required
                className={inputClass}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="username" className="text-sm font-medium text-text">
                Username
              </label>
              <div className="relative">
                <User
                  size={16}
                  aria-hidden
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted"
                />
                <input
                  id="username"
                  name="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={busy}
                  autoComplete="username"
                  required
                  className={cn(inputClass, "pl-9")}
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="password" className="text-sm font-medium text-text">
                Password
              </label>
              <div className="relative">
                <Lock
                  size={16}
                  aria-hidden
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted"
                />
                <input
                  id="password"
                  name="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={busy}
                  autoComplete="current-password"
                  required
                  className={cn(inputClass, "pl-9")}
                />
              </div>
            </div>

            {error && (
              <p
                role="alert"
                data-testid="login-error"
                className="flex items-start gap-2 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger"
              >
                <AlertCircle size={16} aria-hidden className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </p>
            )}

            <Button type="submit" disabled={busy} className="mt-1 w-full">
              {busy ? (
                <>
                  <Loader2 size={16} aria-hidden className="animate-spin" />
                  Signing in…
                </>
              ) : (
                "Sign in"
              )}
            </Button>
          </form>
        </div>
      </div>
    </main>
  );
}
