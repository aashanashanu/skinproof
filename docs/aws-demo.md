# AWS Demo Environment

The demo is designed to run without a developer machine hosting the app. Terraform provisions private S3 buckets, a CloudFront HTTPS distribution, Cognito User Pools, a small EC2 instance, PostgreSQL in a private Docker network, ECR, Secrets Manager, CloudWatch log groups, Systems Manager access, GitHub OIDC deployment, and a $25 monthly AWS Budget. Use `./infra/ops.sh` for one-command setup, DB backup/restore, EC2 start/stop/status, and guarded teardown.

## Cost and safety gate

Do not run `terraform apply` until the AWS Pricing Calculator has been checked for the chosen region, EC2 instance, public IPv4 address, EBS volume, ECR storage, CloudFront usage, S3 usage, and data transfer. The $25 AWS Budget is an alert, not a hard cap; actual charges can exceed it. Email notifications are created only when `budget_alert_email` is set. `t3.small` is a starting point, not a verified estimate.

The demo environment is live at `[hosted demo endpoint omitted]`; AWS resources and charges are active. Review the full Terraform plan before applying infrastructure changes. In particular, a change to EC2 `user_data` replaces the instance and its local database volume, so do not apply a plan containing that replacement without a deliberate migration. The image bucket is intentionally not force-destroyed, so remove its objects deliberately before teardown. Terraform state contains generated database credentials and must be kept private; configure encrypted remote state before treating this as production infrastructure.

## Database choice and cost reduction

The selected low-cost option is **PostgreSQL in the Docker network on the existing EC2 host plus compressed S3 backups**, not RDS. The live database measured about 8 MB (7 experiments, 27 captures). Stopping and starting the same EC2 instance preserves its attached EBS volume and Docker volume; no live database migration is needed for overnight shutdown. The `database-backups/` prefix uses the existing private, AES-256 encrypted image bucket and expires after 30 days. Backups contain account/workspace records and must be handled as private data.

An AWS Pricing API lookup for `us-east-1` returned `db.t4g.micro` Single-AZ PostgreSQL compute at `$0.016/hour` (about `$11.68/month`) and GP3 storage at `$0.115/GB-month` (about `$2.30/month` for 20 GB). This is roughly `$14/month` before backup storage, I/O, transfer, and taxes, **in addition to** the API EC2 host. The current `t3.small` compute rate is `$0.0208/hour` (about `$15.18/month`). RDS is therefore not the low-cost choice for this small demo unless the EC2/API architecture is separately downsized and the extra operational complexity is justified.

Other cost controls:

- Stop EC2 while it is not in use. `./infra/ops.sh stop` first creates an S3 database dump, then stops the instance; `./infra/ops.sh start` starts it again. EBS, S3, CloudFront, and the allocated public IPv4/EIP can still incur charges while EC2 is stopped.
- Optional EventBridge Scheduler jobs can back up at 22:00, stop at 22:30, and start at 07:00 in `America/Chicago`. Set `enable_ec2_schedule = true` in `infra/terraform.tfvars` and run `./infra/ops.sh setup` to enable; it is off by default so availability is not silently changed.
- Keep the ECR lifecycle limit at five images and CloudWatch log retention at 14 days. Review S3 object retention and avoid storing unneeded face images.
- The September 2026 Cost Explorer query returned effectively zero service spend, likely because the environment was newly provisioned or billing had not settled. Do not treat it as a monthly cost forecast; review a complete billing period and AWS Pricing Calculator before relying on the `$25` alert.

## One-command operations

From the repository root, configure `infra/terraform.tfvars` first, then use:

```sh
./infra/ops.sh setup
./infra/ops.sh backup
./infra/ops.sh stop
./infra/ops.sh start
./infra/ops.sh status
./infra/ops.sh destroy --purge-data
```

`setup` initializes and validates Terraform, prints the complete plan, and asks before applying. If the plan replaces EC2, it first requires a successful S3 DB dump and restores it onto the replacement instance. The restored API image must then be deployed; update the GitHub `API_INSTANCE_ID` variable if the instance ID changed. `destroy` always backs up and shows/asks about the Terraform destroy plan. Because the image bucket is non-empty, complete teardown requires the explicit `--purge-data` flag: the newest DB dump is copied to the ignored local `infra/backups/` directory with mode `0600`, then **all** image and backup bucket objects are deleted before Terraform destroy. Keep that local dump somewhere secure if it is needed; it is not committed.

`./infra/ops.sh restore s3://<image-bucket>/database-backups/<dump>.dump` restores an explicit dump over the current DB after confirmation. Start EC2 first. `ops.sh` requires Terraform, AWS CLI, and `jq`, and uses the active AWS credentials (set `AWS_PROFILE` when needed). `terraform destroy` itself remains available for advanced use but bypasses the backup/download guard and is not the recommended teardown path.

## Terraform modules

`infra/main.tf` is the composition root. It passes explicit variables and outputs between these child modules:

- `modules/network`: VPC, public subnet and route, and the API security group restricted to CloudFront origins.
- `modules/api`: ECR, database and YouCam API-key secrets, EC2 instance and Elastic IP, instance IAM profile, CloudWatch logs, and boot/deploy scripts.
- `modules/identity`: Cognito User Pool, public SPA app client, hosted UI domain, verified-email sign-up, and exact CloudFront callback/logout URLs.
- `modules/web`: private frontend bucket, CloudFront distribution, and `/api/*` origin behavior.
- `modules/storage`: private, encrypted image bucket and browser upload CORS policy.
- `modules/deployment`: GitHub OIDC provider, least-privilege deployment role, and policy.
- `modules/budget`: monthly budget and optional email alerts.

The current Terraform state is present for the live modular deployment. Review any state/configuration drift before applying; never use a broad apply to resolve a narrowly scoped runtime change.

## Provisioning outline

Use AWS CloudShell (the browser-hosted AWS terminal) or another approved AWS administration environment; do not run the app locally. Confirm the account and region, install Terraform in that environment, and prepare `infra/terraform.tfvars` from the example with the actual GitHub `owner/repository` and a budget email. Keep that file and Terraform state out of git.

1. Confirm `infra/terraform.tfvars` has the AWS account, GitHub repository identity, and budget alert email. To enable nightly stop/start, set `enable_ec2_schedule = true` there; leave false to operate manually.
2. Run `./infra/ops.sh setup`. It initializes and validates Terraform, shows the full plan, asks before applying, and creates an S3 dump before an EC2 replacement. Keep the printed plan and backup URI for the operational record.
3. Store the YouCam API key as the secret string in the Secrets Manager secret named by `youcam_secret_name`; do not put the key in Terraform variables, Git, or GitHub workflow variables.
4. Add GitHub repository variables named `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `FRONTEND_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, `ECR_REPOSITORY_URI`, `API_INSTANCE_ID`, `COGNITO_HOSTED_UI_DOMAIN`, `COGNITO_CLIENT_ID`, and `COGNITO_ISSUER_URL`. Copy the identity values from Terraform outputs. Recheck `API_INSTANCE_ID` after EC2 replacement.
5. Push to the configured deployment branch. GitHub Actions runs the backend tests and frontend build, publishes the API image and static app, then deploys the API over Systems Manager.
6. Verify `https://<CloudFront-domain>/api/health`, create an experiment, and confirm a second browser profile sees a separate demo workspace.

The first GitHub OIDC deployment provider is account-level. If that identity provider already exists in the AWS account, inspect and import/reuse it rather than creating a duplicate.

## Network and data boundaries

- CloudFront serves the React bundle over HTTPS and proxies `/api/*` to the EC2 API origin.
- EC2 accepts port 8080 only from the AWS-managed CloudFront origin-facing prefix list; SSH is not open. Use Systems Manager for administration.
- PostgreSQL has no host-published port and is reachable only on the EC2 Docker network.
- The frontend and image S3 buckets block public access and enable server-side encryption. Frontend reads are authorized through CloudFront Origin Access Control.
- The initial demo workspace UUID is a browser-profile namespace, not authentication. Do not put sensitive personal information or real skin images into this first slice.
- Cognito SPA sign-in uses authorization code + PKCE; the app client has no client secret. The API validates access-token issuer, signature, expiry, token-use, and client ID. Never put the user-pool app client secret or YouCam credentials in frontend configuration.
- Existing demo data can be claimed by signing in from the same browser profile, which sends the legacy random UUID as migration proof once. That UUID was a bearer capability in demo mode; do not copy/share it. New authenticated records are stored under the Cognito subject and never use `X-Demo-Workspace` as their owner key.
- Anonymous demo endpoints remain separate from account-owned experiments. Cognito sign-in is an identity boundary for the demo application, not a claim that this stack has undergone production security certification.