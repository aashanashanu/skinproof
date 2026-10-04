output "user_pool_id" {
  value = aws_cognito_user_pool.this.id
}

output "client_id" {
  value = aws_cognito_user_pool_client.frontend.id
}

output "issuer_url" {
  value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
}

output "hosted_ui_domain" {
  value = "https://${aws_cognito_user_pool_domain.this.domain}.auth.${var.aws_region}.amazoncognito.com"
}
