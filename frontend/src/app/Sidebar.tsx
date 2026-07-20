// Left pane — the selected service's functions (contracts/navigation.md). Each is a link to
// /app/:service/:function; the active one is highlighted. Uses NavLink so back/forward and deep links work.
import { NavLink } from "react-router-dom";
import Icon from "../ui/Icon";
import { cn } from "../ui/cn";
import type { ServiceManifest } from "../services/types";

export default function Sidebar({ service }: { service: ServiceManifest }) {
  return (
    <nav aria-label={`${service.label} functions`} className="flex flex-col gap-0.5 p-2">
      {service.functions.map((fn) => (
        <NavLink
          key={fn.id}
          to={`/app/${service.id}/${fn.id}`}
          className={({ isActive }) =>
            cn(
              "flex items-center gap-2 rounded px-3 py-2 text-sm font-medium",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              isActive ? "bg-primary/10 text-text" : "text-muted hover:bg-card hover:text-text",
            )
          }
        >
          {fn.icon && <Icon name={fn.icon} size={16} />}
          <span className="truncate">{fn.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
