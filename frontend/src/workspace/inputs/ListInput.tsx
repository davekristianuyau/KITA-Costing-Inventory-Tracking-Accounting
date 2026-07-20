// Repeatable list input (contracts/workspace-framework-extensions.md): an editable array of rows, each
// row a group of nested fields. Controlled — value is the array, onChange reports the new array.
import { Plus, Trash2 } from "lucide-react";
import type { InputField } from "../../services/types";
import FieldInput from "./FieldInput";

type Row = Record<string, unknown>;

export interface ListInputProps {
  id: string;
  label: string;
  fields: InputField[];
  value: Row[];
  required?: boolean;
  minRows?: number;
  error?: string;
  onChange: (rows: Row[]) => void;
}

function emptyRow(fields: InputField[]): Row {
  const r: Row = {};
  for (const f of fields) r[f.name] = f.type === "boolean" ? false : "";
  return r;
}

export default function ListInput({
  id,
  label,
  fields,
  value,
  required,
  error,
  onChange,
}: ListInputProps) {
  const rows = value ?? [];

  const addRow = () => onChange([...rows, emptyRow(fields)]);
  const removeRow = (i: number) => onChange(rows.filter((_, idx) => idx !== i));
  const setCell = (i: number, name: string, cell: unknown) =>
    onChange(rows.map((row, idx) => (idx === i ? { ...row, [name]: cell } : row)));

  return (
    <div className="flex flex-col gap-2" data-testid={`list-${id}`}>
      <span className="text-sm font-medium text-text">
        {label}
        {required && <span className="text-danger"> *</span>}
      </span>

      {rows.map((row, i) => (
        <div
          key={i}
          className="flex items-end gap-3 rounded border border-border bg-surface p-3"
        >
          <div className="grid flex-1 gap-3 sm:grid-cols-2">
            {fields.map((f) => (
              <FieldInput
                key={f.name}
                field={f}
                idPrefix={`${id}-${i}`}
                value={(row[f.name] as string | boolean) ?? ""}
                onChange={(cell) => setCell(i, f.name, cell)}
              />
            ))}
          </div>
          <button
            type="button"
            onClick={() => removeRow(i)}
            aria-label="Remove row"
            className="inline-flex h-9 w-9 items-center justify-center rounded text-muted hover:bg-bg hover:text-danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Trash2 size={16} aria-hidden />
          </button>
        </div>
      ))}

      <div>
        <button
          type="button"
          onClick={addRow}
          className="inline-flex items-center gap-1.5 rounded border border-border px-3 py-1.5 text-sm font-medium text-text hover:bg-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Plus size={14} aria-hidden />
          Add row
        </button>
      </div>

      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}
