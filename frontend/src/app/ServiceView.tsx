// Service workspace view (contracts/navigation.md): the selected service's left-pane functions +
// the workspace for the active function. /app/:service defaults to the service's first function.
import { Navigate, useParams } from "react-router-dom";
import { findService, firstServiceId } from "../services/registry";
import Sidebar from "./Sidebar";
import FunctionWorkspace from "../workspace/FunctionWorkspace";

export default function ServiceView() {
  const { service, function: fnId } = useParams();
  const manifest = findService(service);
  if (!manifest) return <Navigate to={`/app/${firstServiceId}`} replace />;

  const fn = fnId
    ? manifest.functions.find((f) => f.id === fnId)
    : manifest.functions[0];

  // Default a bare /app/:service to its first function so the URL is deep-linkable and the pane highlights.
  if (!fnId && fn) return <Navigate to={`/app/${manifest.id}/${fn.id}`} replace />;

  return (
    <div className="flex flex-1">
      <aside className="w-56 shrink-0 border-r border-border bg-surface">
        <Sidebar service={manifest} />
      </aside>
      <div className="flex flex-1 flex-col">
        {fn ? (
          <FunctionWorkspace key={fn.id} service={manifest} fn={fn} />
        ) : (
          <div className="p-6 text-sm text-muted">This service has no functions yet.</div>
        )}
      </div>
    </div>
  );
}
