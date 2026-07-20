// Service function manifest (contracts/service-manifest.md) — the seam between the 011 framework
// (which renders + runs a manifest) and the per-service specs (which author each service's full manifest).

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export type ResultKind = "table" | "json" | "detail" | "message";

export type InputType = "text" | "number" | "select" | "textarea" | "boolean";

export interface InputField {
  name: string;
  label: string;
  type: InputType;
  required?: boolean;
  options?: string[]; // for select
  placeholder?: string;
}

export interface ServiceFunction {
  id: string;
  label: string;
  icon?: string; // lucide icon name
  method: HttpMethod;
  path: string; // "/items/{id}" — {param} tokens filled from inputs
  inputs?: InputField[];
  result: ResultKind;
  /** Short human description shown in the workspace header. */
  description?: string;
}

export interface ServiceManifest {
  id: string; // "operations"
  label: string; // "Operations"
  icon: string; // lucide icon name
  basePath: string; // "/api/operations" (edge-relative)
  functions: ServiceFunction[];
}
