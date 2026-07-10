# Private network + Serverless VPC connector (Cloud Run egress) + private services access for Cloud SQL.

resource "google_compute_network" "vpc" {
  name                    = "${local.name}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "subnet" {
  name                     = "${local.name}-subnet"
  region                   = local.region
  network                  = google_compute_network.vpc.id
  ip_cidr_range            = "10.30.0.0/20"
  private_ip_google_access = true
}

resource "google_vpc_access_connector" "connector" {
  name          = substr("c${replace(local.name, "-", "")}", 0, 25)
  region        = local.region
  network       = google_compute_network.vpc.name
  ip_cidr_range = "10.8.0.0/28"
}

resource "google_compute_global_address" "psa" {
  name          = "${local.name}-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.vpc.id
}

resource "google_service_networking_connection" "psa" {
  network                 = google_compute_network.vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.psa.name]
}
