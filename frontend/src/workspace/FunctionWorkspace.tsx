// Workspace framework (contracts/service-manifest.md): renders one ServiceFunction as a run-form built
// from its `inputs`, and on Run issues `method basePath+path` through the 009 edge (generic authenticated
// fetch — see api/edge). Path {param} tokens are filled from inputs; missing required inputs block the call.
// The response renders per `result` (table | json | detail | message) with loading / empty / error states.
import { useMemo, useState, type FormEvent } from "react";
import { AlertCircle, Loader2, Play } from "lucide-react";
import { callEdge, type EdgeResult } from "../api/edge";
import type { InputField, ServiceFunction, ServiceManifest } from "../services/types";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import { cn } from "../ui/cn";

type RunState = "idle" | "running" | "done";

type Values = Record<string, string | boolean>;

function initialValues(inputs: InputField[] | undefined): Values {
  const v: Values = {};
  for (const f of inputs ?? []) v[f.name] = f.type === "boolean" ? false : "";
  return v;
}

/** Fill {param} tokens in the path from the current input values (URL-encoded). */
function buildPath(basePath: string, path: string, values: Values): string {
  const filled = path.replace(/\{(\w+)\}/g, (_m, name) =>
    encodeURIComponent(String(values[name] ?? "")),
  );
  return basePath + filled;
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

  const hasInputs = (fn.inputs?.length ?? 0) > 0;
  const bodyInputs = useMemo(
    () => (fn.inputs ?? []).filter((f) => !fn.path.includes(`{${f.name}}`)),
    [fn],
  );

  function setField(name: string, value: string | boolean) {
    setValues((v) => ({ ...v, [name]: value }));
  }

  function validate(): boolean {
    const next: Record<string, string> = {};
    for (const f of fn.inputs ?? []) {
      if (f.required && (values[f.name] === "" || values[f.name] === undefined)) {
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
    const useBody = fn.method !== "GET" && fn.method !== "DELETE" && bodyInputs.length > 0;
    const body = useBody
      ? Object.fromEntries(bodyInputs.map((f) => [f.name, values[f.name]]))
      : undefined;

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
            {(fn.inputs ?? []).map((f) => (
              <InputControl
                key={f.name}
                field={f}
                value={values[f.name]}
                error={errors[f.name]}
                onChange={(val) => setField(f.name, val)}
              />
            ))}
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

      <ResultView state={state} result={result} kind={fn.result} />
    </section>
  );
}

function InputControl({
  field,
  value,
  error,
  onChange,
}: {
  field: InputField;
  value: string | boolean;
  error?: string;
  onChange: (v: string | boolean) => void;
}) {
  const id = `fn-input-${field.name}`;
  const describedBy = error ? `${id}-error` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      {field.type !== "boolean" && (
        <label htmlFor={id} className="text-sm font-medium text-text">
          {field.label}
          {field.required && <span className="text-danger"> *</span>}
        </label>
      )}
      {field.type === "textarea" ? (
        <textarea
          id={id}
          value={value as string}
          placeholder={field.placeholder}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          onChange={(e) => onChange(e.target.value)}
          className={cn(inputClass, "h-24 py-2")}
        />
      ) : field.type === "select" ? (
        <select
          id={id}
          value={value as string}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          onChange={(e) => onChange(e.target.value)}
          className={inputClass}
        >
          <option value="">Select…</option>
          {(field.options ?? []).map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      ) : field.type === "boolean" ? (
        <label htmlFor={id} className="flex items-center gap-2 text-sm font-medium text-text">
          <input
            id={id}
            type="checkbox"
            checked={value as boolean}
            onChange={(e) => onChange(e.target.checked)}
            className="h-4 w-4 rounded border-border"
          />
          {field.label}
        </label>
      ) : (
        <input
          id={id}
          type={field.type === "number" ? "number" : "text"}
          value={value as string}
          placeholder={field.placeholder}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          onChange={(e) => onChange(e.target.value)}
          className={inputClass}
        />
      )}
      {error && (
        <p id={describedBy} className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function ResultView({
  state,
  result,
  kind,
}: {
  state: RunState;
  result: EdgeResult | null;
  kind: ServiceFunction["result"];
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

  if (kind === "table" && Array.isArray(data)) return <ResultTable rows={data} />;
  if (kind === "detail" && data && typeof data === "object" && !Array.isArray(data)) {
    return <DetailView obj={data as Record<string, unknown>} />;
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

function ResultTable({ rows }: { rows: unknown[] }) {
  const objs = rows.filter((r): r is Record<string, unknown> => !!r && typeof r === "object");
  if (objs.length === 0) {
    return (
      <pre className="overflow-x-auto rounded border border-border bg-surface p-3 text-xs text-text">
        {JSON.stringify(rows, null, 2)}
      </pre>
    );
  }
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
                  {formatCell(row[c])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DetailView({ obj }: { obj: Record<string, unknown> }) {
  return (
    <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-[max-content_1fr]">
      {Object.entries(obj).map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="text-sm font-medium text-muted">{k}</dt>
          <dd className="text-sm text-text">{formatCell(v)}</dd>
        </div>
      ))}
    </dl>
  );
}

function formatCell(v: unknown): string {
  if (v == null) return "—";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
