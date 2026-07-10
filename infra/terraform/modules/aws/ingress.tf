# Public ALB. Only public services get a target group; the gateway is the default route.

resource "aws_lb" "main" {
  name               = substr("${local.name}-alb", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for s in aws_subnet.public : s.id]
  tags               = local.tags
}

resource "aws_lb_target_group" "public" {
  for_each    = local.public_services
  name        = substr("${local.name}-${each.key}", 0, 32)
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  health_check {
    path                = each.value.health_path
    matcher             = "200-399"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  tags = local.tags
}

resource "aws_acm_certificate" "main" {
  count             = local.use_tls ? 1 : 0
  domain_name       = var.custom_domain
  validation_method = "DNS"
  tags              = local.tags
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_listener" "main" {
  load_balancer_arn = aws_lb.main.arn
  port              = local.use_tls ? 443 : 80
  protocol          = local.use_tls ? "HTTPS" : "HTTP"
  ssl_policy        = local.use_tls ? "ELBSecurityPolicy-TLS13-1-2-2021-06" : null
  certificate_arn   = local.use_tls ? aws_acm_certificate.main[0].arn : null

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.public[local.gateway_key].arn
  }
}

# Route each non-gateway public service by path prefix (e.g. the frontend).
resource "aws_lb_listener_rule" "public" {
  for_each     = { for k, v in local.public_services : k => v if k != local.gateway_key }
  listener_arn = aws_lb_listener.main.arn
  priority     = index(keys(local.public_services), each.key) + 1
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.public[each.key].arn
  }
  condition {
    path_pattern {
      values = ["/${each.key}/*"]
    }
  }
}
