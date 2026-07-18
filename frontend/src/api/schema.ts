// Minimal hand-authored API types for the endpoints the frontend calls directly. The full backend API
// surface is generated from contracts/openapi.yaml via `npm run gen:api` (overwrites this file) once a
// screen consumes it; for the login slice we only need the identity auth contract (contracts/identity-api.md).

export interface ErrorBody {
  message: string;
}

export interface paths {
  "/auth/login": {
    post: {
      requestBody: {
        content: {
          "application/json": { company: string; username: string; password: string };
        };
      };
      responses: {
        200: { content: { "application/json": { client: string; expiresIn: number } } };
        401: { content: { "application/json": ErrorBody } };
        423: { content: { "application/json": ErrorBody } };
      };
    };
  };
  "/auth/logout": {
    post: {
      responses: {
        204: { content: never };
      };
    };
  };
}
