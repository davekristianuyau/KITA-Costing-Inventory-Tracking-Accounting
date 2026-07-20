// Card primitive — a bordered surface panel using theme tokens.
import type { HTMLAttributes } from "react";
import { cn } from "./cn";

export default function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("rounded-lg border border-border bg-card shadow-sm", className)}
      {...rest}
    />
  );
}
