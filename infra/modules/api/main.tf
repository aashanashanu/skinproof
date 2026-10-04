data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

data "aws_caller_identity" "current" {}

resource "random_password" "database" {
  length  = 36
  special = false
}

resource "aws_ecr_repository" "this" {
  name                 = "${var.project_name}-api"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the five newest API images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_secretsmanager_secret" "database" {
  name                    = "${var.project_name}/demo/database"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "youcam_api_key" {
  name                    = "${var.project_name}/demo/youcam-api-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id     = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({ password = random_password.database.result })
}

resource "aws_iam_role" "instance" {
  name = "${var.project_name}-demo-ec2"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "runtime" {
  name = "${var.project_name}-demo-runtime"
  role = aws_iam_role.instance.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.database.arn,
          aws_secretsmanager_secret.youcam_api_key.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = aws_ecr_repository.this.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
        Resource = "${var.image_bucket_arn}/*"
      },
      {
        Effect = "Allow"
        Action = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = [
          "${aws_cloudwatch_log_group.api.arn}:*",
          "${aws_cloudwatch_log_group.database.arn}:*"
        ]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.project_name}-demo-ec2"
  role = aws_iam_role.instance.name
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/${var.project_name}/demo/api"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "database" {
  name              = "/${var.project_name}/demo/database"
  retention_in_days = 14
}

resource "aws_instance" "this" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  iam_instance_profile        = aws_iam_instance_profile.this.name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/api_user_data.sh.tftpl", {
    aws_region      = var.aws_region
    database_secret = aws_secretsmanager_secret.database.arn
    youcam_secret   = aws_secretsmanager_secret.youcam_api_key.arn
    image_bucket    = var.image_bucket_name
    api_user_data = templatefile("${path.module}/templates/api_deploy.sh.tftpl", {
      aws_region    = var.aws_region
      youcam_secret = aws_secretsmanager_secret.youcam_api_key.arn
      image_bucket  = var.image_bucket_name
    })
  })

  root_block_device {
    volume_size           = 16
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens = "required"
  }

  depends_on = [
    aws_iam_role_policy_attachment.ssm_core,
    aws_iam_role_policy.runtime,
    aws_cloudwatch_log_group.api,
    aws_cloudwatch_log_group.database
  ]

  tags = { Name = "${var.project_name}-demo-api" }
}

resource "aws_iam_role" "instance_scheduler" {
  count = var.enable_ec2_schedule ? 1 : 0

  name = "${var.project_name}-ec2-scheduler"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        ArnLike = {
          "aws:SourceArn" = "arn:aws:scheduler:${var.aws_region}:${data.aws_caller_identity.current.account_id}:schedule-group/default"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "instance_scheduler" {
  count = var.enable_ec2_schedule ? 1 : 0

  name = "${var.project_name}-ec2-scheduler"
  role = aws_iam_role.instance_scheduler[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ec2:StartInstances", "ec2:StopInstances"]
        Resource = aws_instance.this.arn
      },
      {
        Effect = "Allow"
        Action = ["ssm:SendCommand"]
        Resource = [
          aws_instance.this.arn,
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"
        ]
      }
    ]
  })
}

resource "aws_scheduler_schedule" "database_backup" {
  count = var.enable_ec2_schedule ? 1 : 0

  name                         = "${var.project_name}-database-backup"
  description                  = "Dump PostgreSQL to the private image bucket before the nightly EC2 stop."
  schedule_expression          = var.ec2_backup_schedule
  schedule_expression_timezone = var.ec2_schedule_timezone
  state                        = "ENABLED"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ssm:sendCommand"
    role_arn = aws_iam_role.instance_scheduler[0].arn
    input = jsonencode({
      DocumentName = "AWS-RunShellScript"
      InstanceIds  = [aws_instance.this.id]
      Parameters = {
        commands = [
          "set -euo pipefail; key=database-backups/skinproof-db-$(date -u +%Y%m%dT%H%M%SZ).dump; docker exec -e PGPASSWORD=\"$(cat /opt/skinproof/database-password)\" skinproof-database pg_dump --format=custom --no-owner -U skinproof_app -d skinproof | aws s3 cp - s3://${var.image_bucket_name}/$key --sse AES256"
        ]
      }
    })
    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 2
    }
  }
}

resource "aws_scheduler_schedule" "instance_stop" {
  count = var.enable_ec2_schedule ? 1 : 0

  name                         = "${var.project_name}-ec2-stop"
  description                  = "Stop the API EC2 instance overnight to reduce compute usage."
  schedule_expression          = var.ec2_stop_schedule
  schedule_expression_timezone = var.ec2_schedule_timezone
  state                        = "ENABLED"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.instance_scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id] })
    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 2
    }
  }
}

resource "aws_scheduler_schedule" "instance_start" {
  count = var.enable_ec2_schedule ? 1 : 0

  name                         = "${var.project_name}-ec2-start"
  description                  = "Start the API EC2 instance each morning."
  schedule_expression          = var.ec2_start_schedule
  schedule_expression_timezone = var.ec2_schedule_timezone
  state                        = "ENABLED"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.instance_scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id] })
    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 2
    }
  }
}

resource "aws_eip" "this" {
  domain   = "vpc"
  instance = aws_instance.this.id
  tags     = { Name = "${var.project_name}-api" }
}