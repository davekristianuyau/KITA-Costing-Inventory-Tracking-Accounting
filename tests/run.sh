#!/usr/bin/env bash
# Run KITA infra bash tests. Default: contract tests only (no cloud credentials needed).
# Usage: tests/run.sh [contract|integration|all]
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
suite="${1:-contract}"

case "$suite" in
  contract) dirs=("$here/contract") ;;
  integration) dirs=("$here/integration") ;;
  all) dirs=("$here/contract" "$here/integration") ;;
  *) echo "usage: run.sh [contract|integration|all]" >&2; exit 2 ;;
esac

pass=0 fail=0
for d in "${dirs[@]}"; do
  [ -d "$d" ] || continue
  for t in "$d"/test_*.sh; do
    [ -e "$t" ] || continue
    if bash "$t"; then
      pass=$((pass + 1))
    else
      echo ">>> FAILED: $(basename "$t")" >&2
      fail=$((fail + 1))
    fi
  done
done

echo "----"
echo "passed: $pass  failed: $fail"
[ "$fail" -eq 0 ]
