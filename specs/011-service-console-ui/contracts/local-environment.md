# Contract — Local environment (floci-aws + Floci UI + backend + console)

Brings up the browser-accessible console against the client's local environment, with `floci-aws` **actually
running** (Docker socket) and the **Floci UI accessible** — 0 real cloud.

## Components

- **floci-aws** — `floci/floci:latest` on `:4566`, started with the **host Docker socket mounted**
  (`-v /var/run/docker.sock:/var/run/docker.sock`) and **`-u root`**, so it runs real compute. (`floci-cli
  start` does this by default.)
- **Floci UI** — `floci/floci-ui:latest` on `:4500`, auto-starts when the socket is available; **host-exposed**
  for deployment inspection. (Verified: HTTP 200.)
- **Client AWS deployment** — the 010 flow deploys the client's AWS resources to floci-aws (dummy creds).
- **Backend + edge + frontend** — the 009 stack (identity, edge, client gateway, services) provides the app the
  console calls; the **console (frontend)** is host-exposed and calls services via the edge.

## Rules

- **Only the frontend and the Floci UI are host-exposed**; the backend, edge, datastores, and emulator internals
  stay private (consistent with 009).
- **0 real cloud** credentials/spend — dummy creds only (SC-007).
- The **Floci UI opens without** the *"could not reach the container runtime"* error (the socket is mounted).

## Acceptance

- One documented startup brings up floci-aws (socket + UI), the client's deployed AWS resources, the backend, and
  the console; the browser reaches the console and the Floci UI; a user can log in and run the reference function.
- `docker`-inspecting shows floci-aws can spawn real containers for deployed compute (the Phase-0 finding).

## Note (enabled follow-on)

Because floci-aws runs real compute, a later increment can host the **actual kita app on floci-aws ECS** (build
the service images → make them reachable by Floci's ECS/ECR → route via the ALB) so the app is served *from*
floci-aws. Out of scope for the 011 foundation, which routes service calls via the 009 edge.
