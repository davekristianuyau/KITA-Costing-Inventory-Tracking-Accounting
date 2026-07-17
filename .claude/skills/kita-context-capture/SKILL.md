---
name: kita-context-capture
description: Capture reusable implementation context to memory after finishing a spec or an implementation slice, so future slices read memory instead of re-reading the codebase. Use after /speckit-implement completes a spec or a committed slice, or whenever you've just learned stable conventions/design decisions worth not re-deriving.
---

# KITA context capture

Persist what matters after implementing a spec/slice so the next session does **not** re-read sibling
services or re-derive conventions. Re-reading stable reference code every slice is the expensive path;
this skill replaces it with a durable memory write.

## When to run

- Right after a `/speckit-implement` slice is verified and (about to be) committed.
- After finishing a spec.
- Any time you extracted stable, reusable facts (build config, house patterns, a locked design
  decision) that you'd otherwise re-read next time.

Do **not** capture things the repo already records well (code structure obvious from the tree, git
history, CLAUDE.md). Capture the non-obvious: conventions you had to read several files to infer,
design decisions and their rationale, gotchas, and current progress/resume point.

## Steps

1. **Locate memory**: `C:\Users\audav\.claude\projects\d--Projects-KITA-Costing-Inventory-Tracking-Accounting\memory\`
   (the project memory dir). It already exists — write directly.

2. **Update, don't duplicate.** Check for an existing file that already covers the topic and edit it.
   Typical files:
   - `kita-backend-service-conventions.md` (`type: reference`) — stable house patterns shared by all
     backend services. Update only when a genuinely new convention appears.
   - `<service>-<spec>-progress.md` (`type: project`) — per-spec design decisions + slice progress +
     the resume point. Update every slice.
   - A `type: feedback` file when the user gives working-style guidance.

3. **Write one fact per file**, with frontmatter (`name`, `description`, `metadata.type` of
   user|feedback|project|reference). For feedback/project, include **Why** and **How to apply**.
   Link related memories with `[[name]]`. Convert relative dates to absolute.

4. **Record the resume point** in the progress memory: which tasks are done (`[X]`), what slice is
   next, and the per-slice rhythm/commands — enough to continue on another device without re-reading
   the codebase.

5. **Update `MEMORY.md`**: one line per memory (`- [Title](file.md) — hook`). Never put memory body
   content in the index.

## Next slice

On the next slice, read the reference + progress memories first and act from them. Open a specific
source file only when the memory is genuinely insufficient for the exact task — not as a routine
warm-up.
