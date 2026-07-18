// Protected-route wrapper: an unauthenticated user hitting an app route is redirected to /login,
// preserving where they were headed so login can send them back (contracts/frontend-login.md).
import { Navigate, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { authenticated } = useAuth();
  const location = useLocation();
  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}
