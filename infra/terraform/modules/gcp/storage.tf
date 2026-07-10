resource "google_storage_bucket" "main" {
  name                        = "${local.name}-storage"
  location                    = local.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  labels                      = local.labels
}
