# US4 — imitate a client's AWS production deployment locally, against Floci (a drop-in LocalStack
# replacement; no real cloud, SC-005). Reuses the real 001 `common` naming module and the 001 AWS module's
# networking/storage/secrets resources (VPC/subnet/SG + S3 + Secrets Manager). The client's running
# feature-008 stack is the compute/DB stand-in (research D6).

provider "aws" {
  region                      = var.region
  access_key                  = "test" # Floci dummy — no real credentials (SC-005)
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  s3_use_path_style           = true

  endpoints {
    s3             = var.floci_endpoint
    secretsmanager = var.floci_endpoint
    ec2            = var.floci_endpoint
    iam            = var.floci_endpoint
    sts            = var.floci_endpoint
  }
}

# Reuse the actual 001 naming/tagging module → resource names match a real deployment ({client}-{env}).
module "naming" {
  source      = "../../infra/terraform/modules/common"
  client_name = var.client_name
  env         = var.env
  tags        = { imitation = "localstack" }
}

locals {
  name    = module.naming.name_prefix
  tags    = module.naming.tags
  db_host = var.db_standin_host != "" ? var.db_standin_host : "kita-${var.client_name}-postgres-1"
}

# --- Networking (mirrors 001 network.tf; Community-supported subset) ---
resource "aws_vpc" "main" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(local.tags, { Name = "${local.name}-vpc" })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.tags, { Name = "${local.name}-igw" })
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  availability_zone       = "${var.region}a"
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, 0)
  map_public_ip_on_launch = true
  tags                    = merge(local.tags, { Name = "${local.name}-public" })
}

resource "aws_security_group" "service" {
  name        = "${local.name}-svc-sg"
  description = "Service-to-service traffic (private)"
  vpc_id      = aws_vpc.main.id
  ingress {
    description = "intra-service"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name}-svc-sg" })
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db-sg"
  description = "PostgreSQL access from services only"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.service.id]
  }
  tags = merge(local.tags, { Name = "${local.name}-db-sg" })
}

# --- Object storage (mirrors 001 storage.tf) ---
resource "aws_s3_bucket" "main" {
  bucket = "${local.name}-storage"
  tags   = local.tags
}

resource "aws_s3_bucket_public_access_block" "main" {
  bucket                  = aws_s3_bucket.main.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "main" {
  bucket = aws_s3_bucket.main.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# --- DB credentials in Secrets Manager (mirrors 001 database.tf secret); URL points at the stand-in DB ---
resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_secretsmanager_secret" "db" {
  name = "${local.name}-db-credentials"
  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    url      = "jdbc:postgresql://${local.db_host}:5432/kita"
    username = "kita"
    password = random_password.db.result
  })
}
