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
    const msg =
      (data && typeof data === "object" && "message" in data
        ? String((data as Record<string, unknown>).message)
        : undefined) ?? `Request failed (${res.status}).`;
    return { ok: false, status: res.status, data, error: msg };
  }
  return { ok: true, status: res.status, data };
}
