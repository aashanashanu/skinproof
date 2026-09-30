variable "project_name" {
  type = string
}

variable "monthly_limit_usd" {
  type    = string
  default = "25"
}

variable "alert_email" {
  type    = string
  default = ""
}