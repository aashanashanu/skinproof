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

variable "github_branch" {
  description = "Branch allowed to deploy the hosted demo."
  type        = string
  default     = "main"
}

variable "budget_alert_email" {
  description = "Optional email for AWS Budget notifications. Budget alerts are not a spend cap."
  type        = string
  default     = ""
}