#!/usr/bin/env bash
# T039: the gcp module declares the full module-interface input/output surface.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"
assert_module_interface "$TF/modules/gcp" "gcp"
