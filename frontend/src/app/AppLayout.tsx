// Console shell (contracts/navigation.md): a top bar (brand, theme toggle, identity + sign-out) above
// one-tab-per-service navigation, with the selected service's workspace rendered in the <Outlet/>.
import { Outlet, useNavigate } from "react-router-dom";
import { Boxes, LogOut, UserRound } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import ThemeToggle from "../theme/ThemeToggle";
import Button from "../ui/Button";
import TopTabs from "./TopTabs";

export default function AppLayout() {
  const { client, logout } = useAuth();
  const navigate = useNavigate();

  async function onSignOut() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="flex min-h-screen flex-col bg-bg text-text">
      <header className="border-b border-border bg-surface">
        <div className="flex h-14 items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded bg-primary text-primary-fg">
              <Boxes size={18} aria-hidden />
            </span>
            <span className="text-sm font-semibold tracking-tight">KITA</span>
          </div>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <span className="flex items-center gap-1.5 text-sm text-muted">
              <UserRound size={16} aria-hidden />
              <span className="max-w-[10rem] truncate text-text">{client}</span>
            </span>
            <Button variant="outline" size="sm" onClick={onSignOut}>
              <LogOut size={16} aria-hidden />
              Sign out
            </Button>
          </div>
        </div>

        <div className="px-4">
          <TopTabs />
        </div>
      </header>

      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
