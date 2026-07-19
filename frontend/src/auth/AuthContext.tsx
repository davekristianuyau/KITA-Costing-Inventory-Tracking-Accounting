// Auth state + actions. The real session is the edge's httpOnly, JWE cookie (not readable here); this
// context only tracks UI state and remembers the resolved client id (non-sensitive) so protected routes
// survive a refresh. No token or password is ever stored in JS-readable storage (contracts/frontend-login.md).
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "../api/client";

const CLIENT_KEY = "kita.client";

/** Why a login failed — drives the generic, non-revealing message shown to the user. */
export type AuthErrorKind = "invalid" | "locked" | "unavailable" | "network";

export class AuthError extends Error {
  constructor(readonly kind: AuthErrorKind) {
    super(kind);
    this.name = "AuthError";
  }
}

interface AuthContextValue {
  authenticated: boolean;
  client: string | null;
  login: (company: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Clear local session state (e.g. after a 401 from the edge). */
  expire: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [client, setClient] = useState<string | null>(() => sessionStorage.getItem(CLIENT_KEY));

  const login = useCallback(
    async (company: string, username: string, password: string) => {
      let response: Response;
      let data: { client: string; expiresIn: number } | undefined;
      try {
        const result = await api.POST("/auth/login", {
          body: { company, username, password },
        });
        response = result.response;
        data = result.data;
      } catch {
        throw new AuthError("network");
      }
      if (response.ok && data) {
        sessionStorage.setItem(CLIENT_KEY, data.client);
        setClient(data.client);
        return;
      }
      if (response.status === 423) throw new AuthError("locked");
      if (response.status === 401) throw new AuthError("invalid");
      throw new AuthError("unavailable");
    },
    [],
  );

  const clearLocal = useCallback(() => {
    sessionStorage.removeItem(CLIENT_KEY);
    setClient(null);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.POST("/auth/logout");
    } finally {
      clearLocal();
    }
  }, [clearLocal]);

  const value = useMemo<AuthContextValue>(
    () => ({ authenticated: client !== null, client, login, logout, expire: clearLocal }),
    [client, login, logout, clearLocal],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}
