output "gateway_url" {
  value = local.use_tls ? "https://${var.custom_domain}" : "http://${aws_lb.main.dns_name}"
}

output "aggregate_health_url" {
  value = "${local.use_tls ? "https://${var.custom_domain}" : "http://${aws_lb.main.dns_name}"}${var.release_set[local.gateway_key].health_path}"
}

output "service_endpoints" {
  value = local.internal_urls
}

output "environment_name" {
  value = local.name
}

output "db_connection_secret_ref" {
  value = aws_secretsmanager_secret.db.arn
}

output "object_storage_ref" {
  value = aws_s3_bucket.main.id
}

output "resource_ids" {
  value = merge(
    {
      cluster       = aws_ecs_cluster.main.id
      database      = var.emulated ? "emulated" : aws_db_instance.main[0].id
      load_balancer = aws_lb.main.id
      bucket        = aws_s3_bucket.main.id
    },
    { for k, s in aws_ecs_service.svc : "service-${k}" => s.id },
  )
}
