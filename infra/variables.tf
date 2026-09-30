variable "aws_region" {
  description = "AWS region for the demo environment. Review regional pricing before apply."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short project identifier used to name AWS resources."
  type        = string
  default     = "skinproof"
}

variable "instance_type" {
  description = "EC2 instance type for the API and demo database."
  type        = string
  default     = "t3.small"
}

variable "github_repository" {
  description = "GitHub repository in owner/name format for OIDC deployment trust."
  type        = string
}

variable "github_owner" {
  description = "GitHub owner login used in immutable OIDC subject claims."
  type        = string
}

variable "github_owner_id" {
  description = "GitHub numeric owner ID used in immutable OIDC subject claims."
  type        = string
}

variable "github_name" {
  description = "GitHub repository name used in immutable OIDC subject claims."
  type        = string
}

variable "github_repository_id" {
  description = "GitHub numeric repository ID used in immutable OIDC subject claims."
  type        = string
}

variable "github_branch" {
  description = "Branch allowed to deploy the hosted demo."
  type        = string
  default     = "main"
}

variable "github_environment" {
  description = "GitHub Actions environment required in the OIDC subject claim."
  type        = string
  default     = "main"
}

variable "budget_alert_email" {
  description = "Optional email for AWS Budget notifications. Budget alerts are not a spend cap."
  type        = string
  default     = ""
}