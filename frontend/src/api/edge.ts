// Generic authenticated edge call for DYNAMIC manifest paths. The typed openapi-fetch `api` client only
// knows /auth/* (its schema), so the workspace framework — which runs arbitrary per-service manifest paths —
// uses a plain fetch with `credentials: "include"` to send the httpOnly session cookie set by the edge.
// (The token itself is never read by page scripts.)
import type { HttpMethod } from "../services/types";

export interface EdgeResult<T = unknown> {
  ok: boolean;
  status: number;
  data: T | null;
  /** A human-readable message when the call did not succeed. */
  error?: string;
  /** Back-office outcome token from the error envelope, when the service sent one (feature 016) —
   *  e.g. REJECTED_INVALID / REJECTED_NOT_PERMITTED / FAILED_UNAVAILABLE. */
  outcome?: string;
}

export async function callEdge<T = unknown>(
  method: HttpMethod,
  url: string,
  body?: unknown,
): Promise<EdgeResult<T>> {
  let res: Response;
  try {
    res = await fetch(url, {
      method,
      credentials: "include",
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    return { ok: false, status: 0, data: null, error: "Unable to reach the server." };
  }

  let data: T | null = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text) as T;
    } catch {
      // non-JSON body — expose it as a message-shaped payload
      data = text as unknown as T;
    }
  }

  if (!res.ok) {
    // Services differ: most send { message }, the back-office sends { outcome, reason, status }.
    const body = data && typeof data === "object" ? (data as Record<string, unknown>) : undefined;
    const msg =
      (body && "message" in body ? String(body.message) : undefined) ??
      (body && "reason" in body && body.reason != null ? String(body.reason) : undefined) ??
      `Request failed (${res.status}).`;
    const outcome = body && "outcome" in body ? String(body.outcome) : undefined;
    return { ok: false, status: res.status, data, error: msg, outcome };
  }
  return { ok: true, status: res.status, data };
}
