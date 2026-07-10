# Shared managed PostgreSQL (private). PROD gets longer backup retention + PITR is on by default.

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = [for s in aws_subnet.private : s.id]
  tags       = local.tags
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_instance" "main" {
  identifier                 = "${local.name}-db"
  engine                     = "postgres"
  engine_version             = "16"
  instance_class             = var.size == "small" ? "db.t3.micro" : "db.t3.medium"
  allocated_storage          = 20
  db_name                    = "kita"
  username                   = "kita"
  password                   = random_password.db.result
  db_subnet_group_name       = aws_db_subnet_group.main.name
  vpc_security_group_ids     = [aws_security_group.db.id]
  publicly_accessible        = false
  storage_encrypted          = true
  backup_retention_period    = var.db_backup_retention_days
  skip_final_snapshot        = var.env != "prod"
  deletion_protection        = var.env == "prod"
  auto_minor_version_upgrade = true
  tags                       = merge(local.tags, { Name = "${local.name}-db" })
}

resource "aws_secretsmanager_secret" "db" {
  name = "${local.name}-db-credentials"
  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    url      = "jdbc:postgresql://${aws_db_instance.main.address}:5432/kita"
    username = "kita"
    password = random_password.db.result
  })
}
