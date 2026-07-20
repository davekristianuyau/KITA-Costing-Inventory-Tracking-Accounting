// Field primitive — a labelled input row. Associates <label> with the control by id for a11y.
import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "./cn";

export const inputClass =
  "h-10 w-full rounded border border-border bg-surface px-3 text-sm text-text placeholder:text-muted " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60";

export interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  hint?: ReactNode;
}

const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { label, hint, id, className, ...rest },
  ref,
) {
  const inputId = id ?? rest.name;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-text">
        {label}
      </label>
      <input ref={ref} id={inputId} className={cn(inputClass, className)} {...rest} />
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
});

export default Field;
