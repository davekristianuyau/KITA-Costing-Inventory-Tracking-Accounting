// Reference picker (FR-017): a searchable select whose options load ONCE from a list endpoint via the
// edge, filter client-side (type-ahead), and cap the rendered list. Submits the underlying value (an id).
import { useEffect, useMemo, useState } from "react";
import { Loader2, RotateCw, Search } from "lucide-react";
import { callEdge } from "../../api/edge";
import type { ReferenceSource } from "../../services/types";
import { inputClass } from "../../ui/Field";
import { cn } from "../../ui/cn";

const RENDER_CAP = 50;

export interface ReferenceInputProps {
  id: string;
  label: string;
  source: ReferenceSource;
  value: string;
  required?: boolean;
  error?: string;
  onChange: (value: string) => void;
}

type Option = { value: string; label: string };
type Status = "loading" | "error" | "ready";

function toOptions(rows: unknown, src: ReferenceSource): Option[] {
  if (!Array.isArray(rows)) return [];
  const sep = src.labelSep ?? " — ";
  return rows
    .filter((r): r is Record<string, unknown> => !!r && typeof r === "object")
    .map((r) => ({
      value: String(r[src.valueKey] ?? ""),
      label: src.labelKeys.map((k) => r[k]).filter((v) => v != null).join(sep),
    }));
}

export default function ReferenceInput({
  id,
  label,
  source,
  value,
  required,
  error,
  onChange,
}: ReferenceInputProps) {
  const [status, setStatus] = useState<Status>("loading");
  const [options, setOptions] = useState<Option[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  // Load once (and on explicit retry) — no refetch on filter.
  useEffect(() => {
    let live = true;
    setStatus("loading");
    setLoadError(null);
    callEdge("GET", source.path, undefined).then((res) => {
      if (!live) return;
      if (res.ok) {
        setOptions(toOptions(res.data, source));
        setStatus("ready");
      } else {
        setLoadError(res.error ?? "Could not load options.");
        setStatus("error");
      }
    });
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source.path, reloadKey]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const matched = q ? options.filter((o) => o.label.toLowerCase().includes(q)) : options;
    return matched.slice(0, RENDER_CAP);
  }, [options, query]);

  const describedBy = error ? `${id}-error` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-text">
        {label}
        {required && <span className="text-danger"> *</span>}
      </label>

      <div className="relative">
        <Search
          size={16}
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted"
        />
        <input
          id={id}
          type="search"
          role="searchbox"
          value={query}
          placeholder="Search…"
          aria-invalid={!!error}
          aria-describedby={describedBy}
          onChange={(e) => setQuery(e.target.value)}
          className={cn(inputClass, "pl-9")}
        />
      </div>

      {status === "loading" && (
        <div role="status" className="flex items-center gap-2 py-2 text-sm text-muted">
          <Loader2 size={14} aria-hidden className="animate-spin" />
          Loading options…
        </div>
      )}

      {status === "error" && (
        <div className="flex items-center justify-between gap-2 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          <span role="alert">{loadError}</span>
          <button
            type="button"
            onClick={() => setReloadKey((k) => k + 1)}
            className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium hover:bg-danger/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <RotateCw size={12} aria-hidden />
            Retry
          </button>
        </div>
      )}

      {status === "ready" && (
        <ul
          className="max-h-56 overflow-y-auto rounded border border-border bg-surface"
          aria-label={`${label} options`}
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-sm text-muted">No matching options.</li>
          ) : (
            filtered.map((o) => {
              const selected = o.value === value;
              return (
                <li key={o.value}>
                  <button
                    type="button"
                    aria-pressed={selected}
                    onClick={() => onChange(o.value)}
                    className={cn(
                      "flex w-full items-center px-3 py-2 text-left text-sm",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      selected ? "bg-primary/10 text-text" : "text-text hover:bg-bg",
                    )}
                  >
                    {o.label}
                  </button>
                </li>
              );
            })
          )}
        </ul>
      )}

      {error && (
        <p id={describedBy} className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
