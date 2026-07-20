// Service function manifest (contracts/service-manifest.md) — the seam between the 011 framework
// (which renders + runs a manifest) and the per-service specs (which author each service's full manifest).

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export type ResultKind = "table" | "json" | "detail" | "message";

export type InputType =
  | "text"
  | "number"
  | "select"
  | "textarea"
  | "boolean"
  | "reference" // searchable picker, options loaded from a list endpoint (FR-017)
  | "list"; // repeatable group of nested fields (array body)

/** Where a `reference` input loads its options from (contracts/workspace-framework-extensions.md). */
export interface ReferenceSource {
  path: string; // edge-relative list endpoint, e.g. "/api/operations/items"
  valueKey: string; // row field used as the submitted value, e.g. "id"
  labelKeys: string[]; // row fields joined for the visible label, e.g. ["sku","name"]
  labelSep?: string; // joiner (default " — ")
}

export interface InputField {
  name: string;
  label: string;
  type: InputType;
  required?: boolean;
  options?: string[]; // for select
  placeholder?: string;
  source?: ReferenceSource; // for reference
  fields?: InputField[]; // for list — the shape of one row
  minRows?: number; // for list — a required list implies minRows >= 1
}

/** Resolve UUID result columns to human labels from a reference list (contracts/…-framework-extensions.md). */
export interface ResultRef {
  columns: string[]; // result columns whose values are ids to relabel, e.g. ["itemId","componentItemId"]
  source: ReferenceSource; // the list to resolve against (value→label)
}

export interface ServiceFunction {
  id: string;
  label: string;
  icon?: string; // lucide icon name
  method: HttpMethod;
  path: string; // "/items/{id}" — {param} tokens filled from inputs
  inputs?: InputField[];
  result: ResultKind;
  /** When set, the request body is this input's value sent directly (unwrapped) — e.g. a raw array
   *  body such as the DTR ingest `List<DtrRequest>`. Otherwise the body is an object of the inputs. */
  bodyInput?: string;
  /** Optional id→label resolution for result columns. */
  resultRefs?: ResultRef[];
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
