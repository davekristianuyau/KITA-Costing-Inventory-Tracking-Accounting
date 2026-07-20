// Service registry — the backend services a signed-in client sees as top tabs. In this simulation
// every client has all five services (see spec assumption A1); a real deployment would filter by entitlement.
//
// Each manifest carries ONE reference function (the split seam). Operations' reference function is wired
// and verified end-to-end (GET /api/operations/items exists and returns data); the others are placeholder
// reference entries that the per-service specs replace with each service's full manifest.
import type { ServiceManifest } from "./types";
import { operationsManifest } from "./manifests/operations";
import { hrManifest } from "./manifests/hr";
import { crmManifest } from "./manifests/crm";

export const registry: ServiceManifest[] = [
  operationsManifest,
  hrManifest,
  crmManifest,
  {
    id: "procurement",
    label: "Procurement",
    icon: "ShoppingCart",
    basePath: "/api/procurement",
    functions: [
      {
        id: "suppliers",
        label: "Suppliers",
        icon: "ShoppingCart",
        method: "GET",
        path: "/suppliers",
        result: "table",
        description: "Reference entry — the procurement spec authors the full manifest.",
      },
    ],
  },
  {
    id: "workflow",
    label: "Workflow",
    icon: "Workflow",
    basePath: "/api/workflow",
    functions: [
      {
        id: "definitions",
        label: "Definitions",
        icon: "Workflow",
        method: "GET",
        path: "/definitions",
        result: "table",
        description: "Reference entry — the workflow spec authors the full manifest.",
      },
    ],
  },
];

/** Look up a service manifest by its id (the :service route param). */
export function findService(id: string | undefined): ServiceManifest | undefined {
  return registry.find((s) => s.id === id);
}

/** The default landing service (first tab). */
export const firstServiceId = registry[0].id;
