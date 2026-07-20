// id→label resolution for result tables (contracts/workspace-framework-extensions.md). Loads each
// distinct reference source once and builds a value→label map, so UUID columns (itemId, …) render as a
// human label (e.g. "SKU — name"). Generic + config-driven (a function's `resultRefs`) — no service-specific
// paths baked into the shared framework.
import { useEffect, useState } from "react";
import { callEdge } from "../../api/edge";
import type { ResultRef } from "../../services/types";

/** Resolves a (column, value) to a label, or null if not a labelled column / unknown value. */
export type LabelResolver = (column: string, value: unknown) => string | null;

const NOOP: LabelResolver = () => null;

function buildLabel(row: Record<string, unknown>, labelKeys: string[], sep: string): string {
  return labelKeys.map((k) => row[k]).filter((v) => v != null).join(sep);
}

export function useResultLabels(refs: ResultRef[] | undefined): LabelResolver {
  const [resolver, setResolver] = useState<LabelResolver>(() => NOOP);

  // Serialize the config so the effect only re-runs when it actually changes.
  const key = JSON.stringify(refs ?? []);

  useEffect(() => {
    if (!refs || refs.length === 0) {
      setResolver(() => NOOP);
      return;
    }
    let live = true;
    // column -> (value -> label)
    const maps = new Map<string, Map<string, string>>();

    Promise.all(
      refs.map(async (ref) => {
        const res = await callEdge("GET", ref.source.path, undefined);
        if (!res.ok || !Array.isArray(res.data)) return;
        const sep = ref.source.labelSep ?? " — ";
        const valueToLabel = new Map<string, string>();
        for (const raw of res.data) {
          if (!raw || typeof raw !== "object") continue;
          const row = raw as Record<string, unknown>;
          const value = String(row[ref.source.valueKey] ?? "");
          if (value) valueToLabel.set(value, buildLabel(row, ref.source.labelKeys, sep));
        }
        for (const col of ref.columns) maps.set(col, valueToLabel);
      }),
    ).then(() => {
      if (!live) return;
      setResolver(() => (column: string, value: unknown) => {
        const m = maps.get(column);
        if (!m) return null;
        return m.get(String(value)) ?? null;
      });
    });

    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return resolver;
}
