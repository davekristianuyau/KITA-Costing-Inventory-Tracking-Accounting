// Renders a single manifest InputField (text / number / select / textarea / boolean / reference).
// Shared by FunctionWorkspace and ListInput rows. The `list` kind is handled by the parent, not here.
import type { InputField } from "../../services/types";
import { inputClass } from "../../ui/Field";
import { cn } from "../../ui/cn";
import ReferenceInput from "./ReferenceInput";

export interface FieldInputProps {
  field: InputField;
  value: string | boolean;
  error?: string;
  idPrefix?: string;
  onChange: (value: string | boolean) => void;
}

export default function FieldInput({ field, value, error, idPrefix = "fn", onChange }: FieldInputProps) {
  const id = `${idPrefix}-input-${field.name}`;
  const describedBy = error ? `${id}-error` : undefined;

  if (field.type === "reference" && field.source) {
    return (
      <ReferenceInput
        id={id}
        label={field.label}
        source={field.source}
        value={value as string}
        required={field.required}
        error={error}
        onChange={(v) => onChange(v)}
      />
    );
  }

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
