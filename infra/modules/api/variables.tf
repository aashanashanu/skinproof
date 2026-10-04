variable "project_name" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "image_bucket_name" {
  type = string
}

variable "image_bucket_arn" {
  type = string
}

variable "enable_ec2_schedule" {
  type = bool
}

variable "ec2_schedule_timezone" {
  type = string
}

variable "ec2_start_schedule" {
  type = string
}

variable "ec2_backup_schedule" {
  type = string
}

variable "ec2_stop_schedule" {
  type = string
}