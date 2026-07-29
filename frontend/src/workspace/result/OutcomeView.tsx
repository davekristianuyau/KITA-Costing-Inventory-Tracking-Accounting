// The back-office outcome taxonomy (feature 016, FR-008). workflow-service answers every governed action
// with one of four outcomes; each must read differently so the user knows whether to fix, escalate, or wait.
// Above all, rejected-invalid (which includes a self-review) must never look like not-permitted (SC-004).
//
// This component classifies and displays — it never decides. The backend evaluates self-review before role
// authorization; the browser only renders what came back.
import { AlertTriangle, CheckCircle2, Clock, ShieldX } from "lucide-react";
import type { EdgeResult } from "../../api/edge";

export type Outcome = "approved" | "invalid" | "denied" | "unavailable";

/** Map an edge result onto the taxonomy; null when it is not a back-office outcome (fall back to the
 *  generic error banner). Status comes first — the envelope's token is a cross-check, not the source. */
export function classifyOutcome(result: EdgeResult): Outcome | null {
  if (result.ok) return "approved";
  if (result.status === 422 || result.outcome === "REJECTED_INVALID") return "invalid";
  if (result.status === 403 || result.outcome === "REJECTED_NOT_PERMITTED") return "denied";
  if (result.status === 503 || result.outcome === "FAILED_UNAVAILABLE") return "unavailable";
  return null;
}

const STYLES: Record<
  Outcome,
  { title: string; guidance: string; icon: typeof CheckCircle2; className: string }
> = {
  approved: {
    title: "Approved",
    guidance: "Recorded in the activity log.",
    icon: CheckCircle2,
    className: "border-success/40 bg-success/10 text-success",
  },
  invalid: {
    title: "Rejected — invalid",
    guidance: "Correct the request, or have a different person review it.",
    icon: AlertTriangle,
    className: "border-warning/40 bg-warning/10 text-warning",
  },
  denied: {
    title: "Not permitted",
    guidance: "Your role does not grant this action — escalate to someone who holds it.",
    icon: ShieldX,
    className: "border-danger/40 bg-danger/10 text-danger",
  },
  unavailable: {
    title: "Temporarily unavailable",
    guidance: "A service it depends on did not respond. The attempt was recorded — try again shortly.",
    icon: Clock,
    className: "border-muted/40 bg-muted/10 text-muted",
  },
};

export default function OutcomeBanner({
  outcome,
  reason,
}: {
  outcome: Outcome;
  reason?: string;
}) {
  const { title, guidance, icon: Icon, className } = STYLES[outcome];
  return (
    <div
      role={outcome === "approved" ? "status" : "alert"}
      className={`flex items-start gap-2 rounded border px-3 py-2 text-sm ${className}`}
    >
      <Icon size={16} aria-hidden className="mt-0.5 shrink-0" />
      <span className="flex flex-col gap-0.5">
        <span className="font-medium">{title}</span>
        {reason && <span>{reason}</span>}
        <span className="opacity-80">{guidance}</span>
      </span>
    </div>
  );
}
