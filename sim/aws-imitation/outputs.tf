output "name_prefix" {
  value = local.name
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "storage_bucket" {
  value = aws_s3_bucket.main.bucket
}

output "db_secret_name" {
  value = aws_secretsmanager_secret.db.name
}

output "db_standin_host" {
  description = "The feature-008 stack DB the imitated deployment points at (ECS/RDS are LocalStack Pro)."
  value       = local.db_host
}
