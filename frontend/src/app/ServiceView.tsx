// Service workspace view. US2 renders a placeholder for the selected service; US3 replaces the body with
// the left-pane Sidebar + FunctionWorkspace framework.
import { Navigate, useParams } from "react-router-dom";
import { findService, firstServiceId } from "../services/registry";

export default function ServiceView() {
  const { service } = useParams();
  const manifest = findService(service);
  if (!manifest) return <Navigate to={`/app/${firstServiceId}`} replace />;

  return (
    <section className="p-6">
      <h1 className="text-lg font-semibold text-text">{manifest.label}</h1>
      <p className="mt-1 text-sm text-muted">Select a function from this service to get started.</p>
    </section>
  );
}
