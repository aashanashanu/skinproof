# AWS Demo Environment

The demo is designed to run without a developer machine hosting the app. Terraform provisions private S3 buckets, a CloudFront HTTPS distribution, a small EC2 instance, PostgreSQL in a private Docker network, ECR, Secrets Manager, CloudWatch log groups, Systems Manager access, GitHub OIDC deployment, and a $25 monthly AWS Budget.

## Cost and safety gate

Do not run `terraform apply` until the AWS Pricing Calculator has been checked for the chosen region, EC2 instance, public IPv4 address, EBS volume, ECR storage, CloudFront usage, S3 usage, and data transfer. The $25 AWS Budget is an alert, not a hard cap; actual charges can exceed it. Email notifications are created only when `budget_alert_email` is set. `t3.small` is a starting point, not a verified estimate.

The demo environment is live at `[hosted demo endpoint omitted]`; AWS resources and charges are active. Review the full Terraform plan before applying infrastructure changes. In particular, a change to EC2 `user_data` replaces the instance and its local database volume, so do not apply a plan containing that replacement without a deliberate migration. The image bucket is intentionally not force-destroyed, so remove its objects deliberately before teardown. Terraform state contains generated database credentials and must be kept private; configure encrypted remote state before treating this as production infrastructure.

## Terraform modules

`infra/main.tf` is the composition root. It passes explicit variables and outputs between these child modules:

- `modules/network`: VPC, public subnet and route, and the API security group restricted to CloudFront origins.
- `modules/api`: ECR, database and YouCam API-key secrets, EC2 instance and Elastic IP, instance IAM profile, CloudWatch logs, and boot/deploy scripts.
- `modules/web`: private frontend bucket, CloudFront distribution, and `/api/*` origin behavior.
- `modules/storage`: private, encrypted image bucket and browser upload CORS policy.
- `modules/deployment`: GitHub OIDC provider, least-privilege deployment role, and policy.
- `modules/budget`: monthly budget and optional email alerts.

The current Terraform state is present for the live modular deployment. Review any state/configuration drift before applying; never use a broad apply to resolve a narrowly scoped runtime change.

## Provisioning outline

Use AWS CloudShell (the browser-hosted AWS terminal) or another approved AWS administration environment; do not run the app locally. Confirm the account and region, install Terraform in that environment, and prepare `infra/terraform.tfvars` from the example with the actual GitHub `owner/repository` and a budget email. Keep that file and Terraform state out of git.

1. Run `terraform init` and `terraform fmt -check -recursive` in `infra/`.
2. Run `terraform validate` and `terraform plan -out=skinproof.plan`; inspect all resources, recurring costs, IAM policies, security groups, and bucket retention.
3. Apply only after the reviewed plan fits the account budget.
4. Save the outputs `github_deploy_role_arn`, `demo_url`, `frontend_bucket`, `cloudfront_distribution_id`, `api_image_uri`, `api_instance_id`, and `youcam_secret_name`.
5. Store the YouCam API key as the secret string in the Secrets Manager secret named by `youcam_secret_name`; do not put the key in Terraform variables, Git, or GitHub workflow variables.
6. Add GitHub repository variables named `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `FRONTEND_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, `ECR_REPOSITORY_URI`, and `API_INSTANCE_ID`.
7. Push to the configured deployment branch. GitHub Actions runs the backend tests and frontend build, publishes the API image and static app, then deploys the API over Systems Manager.
8. Verify `https://<CloudFront-domain>/api/health`, create an experiment, and confirm a second browser profile sees a separate demo workspace.

The first GitHub OIDC deployment provider is account-level. If that identity provider already exists in the AWS account, inspect and import/reuse it rather than creating a duplicate.

## Network and data boundaries

- CloudFront serves the React bundle over HTTPS and proxies `/api/*` to the EC2 API origin.
- EC2 accepts port 8080 only from the AWS-managed CloudFront origin-facing prefix list; SSH is not open. Use Systems Manager for administration.
- PostgreSQL has no host-published port and is reachable only on the EC2 Docker network.
- The frontend and image S3 buckets block public access and enable server-side encryption. Frontend reads are authorized through CloudFront Origin Access Control.
- The initial demo workspace UUID is a browser-profile namespace, not authentication. Do not put sensitive personal information or real skin images into this first slice.