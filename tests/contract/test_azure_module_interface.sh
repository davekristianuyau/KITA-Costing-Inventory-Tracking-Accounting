#!/usr/bin/env bash
# T040: the azure module declares the full module-interface input/output surface.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"
assert_module_interface "$TF/modules/azure" "azure"
