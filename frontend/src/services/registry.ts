// Service registry — the backend services a signed-in client sees as top tabs. In this simulation
// every client has all five services (see spec assumption A1); a real deployment would filter by entitlement.
//
// Every service now carries its full manifest, authored by its own per-service spec (012 operations,
// 013 hr, 014 crm, 015 procurement, 016 workflow).
import type { ServiceManifest } from "./types";
import { operationsManifest } from "./manifests/operations";
import { hrManifest } from "./manifests/hr";
import { crmManifest } from "./manifests/crm";
import { procurementManifest } from "./manifests/procurement";
import { workflowManifest } from "./manifests/workflow";

export const registry: ServiceManifest[] = [
  operationsManifest,
  hrManifest,
  crmManifest,
  procurementManifest,
  workflowManifest,
];

/** Look up a service manifest by its id (the :service route param). */
export function findService(id: string | undefined): ServiceManifest | undefined {
  return registry.find((s) => s.id === id);
}

/** The default landing service (first tab). */
export const firstServiceId = registry[0].id;
