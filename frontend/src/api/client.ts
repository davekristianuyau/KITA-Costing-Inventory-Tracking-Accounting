// openapi-fetch client. `credentials: "include"` sends the httpOnly session cookie set by the edge on
// every /auth and /api request — the token itself is never read by page scripts (contracts/frontend-login.md).
import createClient from "openapi-fetch";
import type { paths } from "./schema";

export const api = createClient<paths>({
  baseUrl: "/",
  credentials: "include",
});
