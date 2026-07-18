// Protected placeholder landing page. Confirms the user reached the app for their resolved client and
// offers sign-out. A real screen consuming /api (through the edge) lands in a later slice (T016+).
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Dashboard() {
  const { client, logout } = useAuth();
  const navigate = useNavigate();

  async function onSignOut() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <main>
      <h1>KITA</h1>
      <p>
        Signed in — client <strong>{client}</strong>.
      </p>
      <button type="button" onClick={onSignOut}>
        Sign out
      </button>
    </main>
  );
}
