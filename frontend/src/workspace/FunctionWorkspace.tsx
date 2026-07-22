// Workspace framework (contracts/service-manifest.md): renders one ServiceFunction as a run-form built
// from its `inputs`, and on Run issues `method basePath+path` through the 009 edge (generic authenticated
// fetch — see api/edge). Path {param} tokens are filled from inputs; missing required inputs block the call.
// The response renders per `result` (table | json | detail | message) with loading / empty / error states.
import { useMemo, useState, type FormEvent } from "react";
import { AlertCircle, Loader2, Play } from "lucide-react";
import { callEdge, type EdgeResult } from "../api/edge";
import type { InputField, ServiceFunction, ServiceManifest } from "../services/types";
import Button from "../ui/Button";
import FieldInput from "./inputs/FieldInput";
import ListInput from "./inputs/ListInput";
import { useResultLabels, type LabelResolver } from "./result/idLabels";
import OutcomeBanner, { classifyOutcome } from "./result/OutcomeView";

type RunState = "idle" | "running" | "done";

type Row = Record<string, unknown>;
type Values = Record<string, unknown>;

function initialValues(inputs: InputField[] | undefined): Values {
  const v: Values = {};
  for (const f of inputs ?? []) v[f.name] = f.type === "list" ? [] : f.type === "boolean" ? false : "";
  return v;
}

/** Build an object body from inputs; dotted names (e.g. "period.startDate") become nested objects. */
function buildBody(inputs: InputField[], values: Values): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  for (const f of inputs) {
    const parts = f.name.split(".");
    let obj = body;
    for (let i = 0; i < parts.length - 1; i++) {
      obj[parts[i]] = (obj[parts[i]] as Record<string, unknown>) ?? {};
      obj = obj[parts[i]] as Record<string, unknown>;
    }
    obj[parts[parts.length - 1]] = values[f.name];
  }
  return body;
}

/** Fill {param} tokens in the path from the current input values (URL-encoded), dropping empty query params. */
function buildPath(basePath: string, path: string, values: Values): string {
  const filled = path.replace(/\{(\w+)\}/g, (_m, name) =>
    encodeURIComponent(String(values[name] ?? "")),
  );
  const full = basePath + filled;
  const q = full.indexOf("?");
  if (q === -1) return full;
  // Blank optional inputs leave empty query params (…&from=&to=) — strip them.
  const kept = full
    .slice(q + 1)
    .split("&")
    .filter((p) => {
      const eq = p.indexOf("=");
      return eq === -1 || p.slice(eq + 1) !== "";
    });
  return kept.length ? `${full.slice(0, q)}?${kept.join("&")}` : full.slice(0, q);
}

export default function FunctionWorkspace({
  service,
  fn,
}: {
  service: ServiceManifest;
  fn: ServiceFunction;
}) {
  // Re-keying by fn.id resets local state when a different function is selected.
  const [values, setValues] = useState<Values>(() => initialValues(fn.inputs));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [state, setState] = useState<RunState>("idle");
  const [result, setResult] = useState<EdgeResult | null>(null);
  const resolveLabel = useResultLabels(fn.resultRefs);

  const hasInputs = (fn.inputs?.length ?? 0) > 0;
  const bodyInputs = useMemo(
    () => (fn.inputs ?? []).filter((f) => !fn.path.includes(`{${f.name}}`)),
    [fn],
  );

  function setField(name: string, value: unknown) {
    setValues((v) => ({ ...v, [name]: value }));
  }

  function validate(): boolean {
    const next: Record<string, string> = {};
    for (const f of fn.inputs ?? []) {
      const val = values[f.name];
      if (f.type === "list") {
        const rows = Array.isArray(val) ? (val as Row[]) : [];
        const needsRow = f.required || (f.minRows ?? 0) > 0;
        if (needsRow && rows.length === 0) {
          next[f.name] = `${f.label} needs at least one row`;
        }
      } else if (f.required && (val === "" || val === undefined)) {
        next[f.name] = `${f.label} is required`;
      }
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function onRun(e?: FormEvent) {
    e?.preventDefault();
    if (!validate()) return;
    setState("running");
    setResult(null);

    const url = buildPath(service.basePath, fn.path, values);
    const isWrite = fn.method !== "GET" && fn.method !== "DELETE";
    let body: unknown;
    if (isWrite && fn.bodyInput) {
      // Send this input's value directly (unwrapped) — e.g. a raw array body.
      body = values[fn.bodyInput];
    } else if (isWrite && bodyInputs.length > 0) {
      body = buildBody(bodyInputs, values);
    }

    const res = await callEdge(fn.method, url, body);
    setResult(res);
    setState("done");
  }

  return (
    <section className="flex flex-1 flex-col gap-4 p-6">
      <header>
        <h1 className="text-lg font-semibold text-text">{fn.label}</h1>
        {fn.description && <p className="mt-1 text-sm text-muted">{fn.description}</p>}
      </header>

      <form onSubmit={onRun} className="flex flex-col gap-4">
        {hasInputs && (
          <div className="grid gap-4 sm:grid-cols-2">
            {(fn.inputs ?? []).map((f) =>
              f.type === "list" ? (
                <div key={f.name} className="sm:col-span-2">
                  <ListInput
                    id={f.name}
                    label={f.label}
                    fields={f.fields ?? []}
                    value={(values[f.name] as Row[]) ?? []}
                    required={f.required}
                    minRows={f.minRows}
                    error={errors[f.name]}
                    onChange={(rows) => setField(f.name, rows)}
                  />
                </div>
              ) : (
                <FieldInput
                  key={f.name}
                  field={f}
                  value={(values[f.name] as string | boolean) ?? (f.type === "boolean" ? false : "")}
                  error={errors[f.name]}
                  onChange={(val) => setField(f.name, val)}
                />
              ),
            )}
          </div>
        )}
        <div>
          <Button type="submit" disabled={state === "running"}>
            {state === "running" ? (
              <Loader2 size={16} aria-hidden className="animate-spin" />
            ) : (
              <Play size={16} aria-hidden />
            )}
            Run
          </Button>
        </div>
      </form>

      <ResultView state={state} result={result} kind={fn.result} resolve={resolveLabel} />
    </section>
  );
}

function ResultView({
  state,
  result,
  kind,
  resolve,
}: {
  state: RunState;
  result: EdgeResult | null;
  kind: ServiceFunction["result"];
  resolve: LabelResolver;
}) {
  if (state === "running") {
    return (
      <div role="status" className="flex items-center gap-2 text-sm text-muted">
        <Loader2 size={16} aria-hidden className="animate-spin" />
        Running…
      </div>
    );
  }
  if (state !== "done" || !result) return null;

  // Governed actions report the back-office taxonomy; classify before the generic error banner so a
  // 422 self-review never reads as a 403 refusal (feature 016, SC-004).
  if (kind === "outcome") {
    const outcome = classifyOutcome(result);
    if (outcome) {
      const data = result.data;
      const detail =
        outcome === "approved" && data && typeof data === "object" && !Array.isArray(data) ? (
          <DetailView obj={data as Record<string, unknown>} resolve={resolve} />
        ) : null;
      return (
        <div className="flex flex-col gap-4">
          <OutcomeBanner outcome={outcome} reason={result.ok ? undefined : result.error} />
          {detail}
        </div>
      );
    }
    // not a back-office outcome (e.g. a 502 at the edge) — fall through to the generic handling
  }

  if (!result.ok) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger"
      >
        <AlertCircle size={16} aria-hidden className="mt-0.5 shrink-0" />
        <span>{result.error ?? "The request failed."}</span>
      </p>
    );
  }

  const data = result.data;
  const isEmpty =
    data == null ||
    (Array.isArray(data) && data.length === 0) ||
    (typeof data === "object" && !Array.isArray(data) && Object.keys(data).length === 0);

  if (isEmpty) {
    return <p className="text-sm text-muted">No results.</p>;
  }

  if (kind === "table" && Array.isArray(data)) return <ResultTable rows={data} resolve={resolve} />;
  if (kind === "detail" && data && typeof data === "object" && !Array.isArray(data)) {
    return <DetailView obj={data as Record<string, unknown>} resolve={resolve} />;
  }
  if (kind === "message") {
    return <p className="text-sm text-text">{typeof data === "string" ? data : String(data)}</p>;
  }
  // json (and any fallback)
  return (
    <pre className="overflow-x-auto rounded border border-border bg-surface p-3 text-xs text-text">
      {JSON.stringify(data, null, 2)}
    </pre>
  );
}

function ResultTable({ rows, resolve }: { rows: unknown[]; resolve: LabelResolver }) {
  const objs = rows.filter((r): r is Record<string, unknown> => !!r && typeof r === "object");
  if (objs.length === 0) {
    return (
      <pre className="overflow-x-auto rounded border border-border bg-surface p-3 text-xs text-text">
        {JSON.stringify(rows, null, 2)}
      </pre>
    );
  }
  return <DataTable objs={objs} resolve={resolve} />;
}

/** The table markup, reused by ResultTable and DetailView sub-tables. */
function DataTable({ objs, resolve }: { objs: Record<string, unknown>[]; resolve: LabelResolver }) {
  const cols = Array.from(new Set(objs.flatMap((o) => Object.keys(o))));
  return (
    <div className="overflow-x-auto rounded border border-border">
      <table className="w-full text-left text-sm">
        <thead className="bg-surface text-muted">
          <tr>
            {cols.map((c) => (
              <th key={c} className="border-b border-border px-3 py-2 font-medium">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {objs.map((row, i) => (
            <tr key={i} className="odd:bg-bg">
              {cols.map((c) => (
                <td key={c} className="border-b border-border px-3 py-2 text-text">
                  {resolve(c, row[c]) ?? formatCell(row[c])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const NO_RESOLVE: LabelResolver = () => null;

// Detail view: scalar fields render as key/value; an array-of-objects field renders as a nested sub-table
// (e.g. the CRM quote's breakdown[]); an array-of-scalars renders as a joined list. (contracts, feature 014)
function DetailView({ obj, resolve }: { obj: Record<string, unknown>; resolve: LabelResolver }) {
  const entries = Object.entries(obj);
  const scalars = entries.filter(([, v]) => !Array.isArray(v));
  const arrays = entries.filter(([, v]) => Array.isArray(v)) as [string, unknown[]][];

  return (
    <div className="flex flex-col gap-4">
      {scalars.length > 0 && (
        <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-[max-content_1fr]">
          {scalars.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-sm font-medium text-muted">{k}</dt>
              <dd className="text-sm text-text">{resolve(k, v) ?? formatCell(v)}</dd>
            </div>
          ))}
        </dl>
      )}
      {arrays.map(([k, arr]) => {
        const objs = arr.filter((r): r is Record<string, unknown> => !!r && typeof r === "object");
        const allObjects = objs.length === arr.length && objs.length > 0;
        return (
          <div key={k} className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-muted">{k}</span>
            {arr.length === 0 ? (
              <span className="text-sm text-muted">—</span>
            ) : allObjects ? (
              <DataTable objs={objs} resolve={NO_RESOLVE} />
            ) : (
              <span className="text-sm text-text">{arr.map((x) => formatCell(x)).join(" · ")}</span>
            )}
          </div>
        );
      })}
    </div>
  );
}

function formatCell(v: unknown): string {
  if (v == null) return "—";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
