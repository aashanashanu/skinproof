module "network" {
  source       = "./modules/network"
  project_name = var.project_name
  aws_region   = var.aws_region
}

module "api" {
  source            = "./modules/api"
  project_name      = var.project_name
  aws_region        = var.aws_region
  instance_type     = var.instance_type
  subnet_id         = module.network.public_subnet_id
  security_group_id = module.network.api_security_group_id
}

module "web" {
  source            = "./modules/web"
  project_name      = var.project_name
  api_origin_domain = module.api.origin_domain
}

module "image_storage" {
  source          = "./modules/storage"
  project_name    = var.project_name
  frontend_domain = module.web.domain_name
}

module "deployment" {
  source                      = "./modules/deployment"
  project_name                = var.project_name
  aws_region                  = var.aws_region
  github_repository           = var.github_repository
  github_owner                = var.github_owner
  github_owner_id             = var.github_owner_id
  github_name                 = var.github_name
  github_repository_id        = var.github_repository_id
  github_branch               = var.github_branch
  github_environment          = var.github_environment
  ecr_repository_arn          = module.api.ecr_repository_arn
  frontend_bucket_arn         = module.web.frontend_bucket_arn
  cloudfront_distribution_arn = module.web.distribution_arn
  api_instance_arn            = module.api.instance_arn
}

module "budget" {
  source       = "./modules/budget"
  project_name = var.project_name
  alert_email  = var.budget_alert_email
}