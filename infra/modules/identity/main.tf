resource "random_id" "domain_suffix" {
  byte_length = 4
}

resource "aws_cognito_user_pool" "this" {
  name                     = "${var.project_name}-users"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "Confirm your SkinProof account"
    email_message        = "Your SkinProof verification code is {####}."
  }

  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

resource "aws_cognito_user_pool_client" "frontend" {
  name                                 = "${var.project_name}-spa"
  user_pool_id                         = aws_cognito_user_pool.this.id
  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = ["https://${var.frontend_domain}/"]
  logout_urls                          = ["https://${var.frontend_domain}/"]
  prevent_user_existence_errors        = "ENABLED"
  enable_token_revocation              = true

  access_token_validity  = 1
  id_token_validity      = 1
  refresh_token_validity = 7

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

}

resource "aws_cognito_user_pool_domain" "this" {
  domain       = "${var.project_name}-${random_id.domain_suffix.hex}"
  user_pool_id = aws_cognito_user_pool.this.id
}
