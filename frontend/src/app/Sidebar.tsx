// Left pane — the selected service's functions (contracts/navigation.md). Each is a link to
// /app/:service/:function; the active one is highlighted. Uses NavLink so back/forward and deep links work.
// Functions may declare a `group`; contiguous runs sharing one render as a labelled group (feature 016).
import { NavLink } from "react-router-dom";
import Icon from "../ui/Icon";
import { cn } from "../ui/cn";
import type { ServiceFunction, ServiceManifest } from "../services/types";

/** Split functions into contiguous runs by `group`, preserving manifest order. */
function toGroups(functions: ServiceFunction[]): { group?: string; functions: ServiceFunction[] }[] {
  const runs: { group?: string; functions: ServiceFunction[] }[] = [];
  for (const fn of functions) {
    const last = runs[runs.length - 1];
    if (last && last.group === fn.group) last.functions.push(fn);
    else runs.push({ group: fn.group, functions: [fn] });
  }
  return runs;
}

export default function Sidebar({ service }: { service: ServiceManifest }) {
  return (
    <nav aria-label={`${service.label} functions`} className="flex flex-col gap-0.5 p-2">
      {toGroups(service.functions).map((run, i) =>
        run.group ? (
          <div key={run.group} role="group" aria-label={run.group} className="flex flex-col gap-0.5">
            <span
              aria-hidden
              className={cn(
                "px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted",
                i > 0 && "pt-3",
              )}
            >
              {run.group}
            </span>
            {run.functions.map((fn) => (
              <FunctionLink key={fn.id} serviceId={service.id} fn={fn} />
            ))}
          </div>
        ) : (
          run.functions.map((fn) => <FunctionLink key={fn.id} serviceId={service.id} fn={fn} />)
        ),
      )}
    </nav>
  );
}

function FunctionLink({ serviceId, fn }: { serviceId: string; fn: ServiceFunction }) {
  return (
    <NavLink
      to={`/app/${serviceId}/${fn.id}`}
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
  );
}
