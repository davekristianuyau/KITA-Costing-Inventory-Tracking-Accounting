# Floci Azure coverage — 001 `modules/azure` (measured 2026-07-19)

Method: `floci/floci-az:latest` on :4577; the Terraform `azurerm` provider discovers the cloud via
`metadata_host`. Floci-Azure **requires TLS** (`FLOCI_AZ_TLS_ENABLED=true`, `FLOCI_AZ_HOSTNAME=floci-az`) — the
`azurerm` provider fetches `GET https://<host>/metadata/endpoints` and refuses plain HTTP.

## C2 (provider → emulator wiring): PARTIAL — TLS trust is the hurdle

- ✅ Floci-Azure serves the ARM metadata endpoint over HTTPS (`/metadata/endpoints` → 200), and `azurerm`
  discovers it (`metadata_host = "floci-az:4577"` builds the right request).
- ⚠️ **TLS cert verification blocks a live apply**: the emulator uses a **self-signed cert**, so the Azure SDK
  fails with `x509: certificate signed by unknown authority`. `SSL_CERT_FILE` did not satisfy it — the CA has to
  be added to the container's system trust store (unlike AWS/GCP, which are plain HTTP). This is a solvable
  setup step, not a dead end.

## Coverage (from the floci-az repo's Terraform compat tests)

| 001 Azure resource / service | Floci-Azure |
|---|---|
| `azurerm_storage_account` + `_container` (Storage) | ✅ |
| `azurerm_key_vault` + `_secret` | ✅ |
| `azurerm_virtual_network` + `azurerm_subnet` (VNet) | ✅ |
| `azurerm_postgresql_flexible_server` + `_database` | ✅ **(the module's exact DB)** |
| `azurerm_resource_group` | ✅ |
| `azurerm_container_app` + `_environment` (**Container Apps** — the compute tier) | ❌ not in compat list |
| `azurerm_log_analytics_workspace`, `azurerm_private_dns_*` | ❌ not in compat list |

Also emulated (not used by the module): VMs (mocked), Redis, Container Registry.

## Implication (more viable than GCP — deferred to implement)

Azure is actually the **richest** of the three emulators for the 001 module: **VNet + PostgreSQL Flexible Server
+ Key Vault + Storage + Resource Group all work** (GCP can't even do the VPC). The gap is **Container Apps** (the
compute tier) + Log Analytics + Private DNS. An `emulated` flag skipping Container Apps/Log-Analytics/Private-DNS
— plus the one-time TLS trust setup — would deploy VNet + Postgres + Key Vault + Storage. Implementation deferred
to a future spec; `fmt`/`validate` of the real module pass today.
