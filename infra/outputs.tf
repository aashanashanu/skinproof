output "demo_url" {
  description = "HTTPS CloudFront URL for the hosted SkinProof demo."
  value       = "https://${module.web.domain_name}"
}

output "frontend_bucket" {
  value = module.web.frontend_bucket_name
}

output "image_bucket" {
  value = module.image_storage.image_bucket_name
}

output "api_image_uri" {
  value = module.api.ecr_repository_url
}

output "api_instance_id" {
  value = module.api.instance_id
}

output "cloudfront_distribution_id" {
  value = module.web.distribution_id
}

output "github_deploy_role_arn" {
  value = module.deployment.role_arn
}

output "database_secret_arn" {
  value     = module.api.database_secret_arn
  sensitive = true
}

output "youcam_secret_name" {
  value = module.api.youcam_secret_name
}

output "estimated_instance_family_note" {
  value = "A t3.small is a starting point, not a cost guarantee; confirm EC2, public IPv4, storage, CloudFront, and egress in the AWS Pricing Calculator before apply."
}