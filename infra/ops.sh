#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra"
BACKUP_DIR="$INFRA_DIR/backups"

usage() {
  cat <<'EOF'
Usage: ./infra/ops.sh <command> [options]

Commands:
  setup                 Review and apply Terraform; back up and restore DB if EC2 is replaced.
  backup                Create a compressed PostgreSQL custom-format dump in private S3.
  restore <s3-uri>      Restore a dump to the EC2 PostgreSQL container (destructive; asks first).
  start                 Start EC2 and wait until it is running.
  stop                  Back up PostgreSQL, then stop EC2 to reduce compute spend.
  status                Show EC2 state and API health when running.
  destroy [--purge-data] Back up first, then review and destroy. Purge explicitly downloads the latest DB dump locally and deletes all objects in the private image bucket.

Examples:
  ./infra/ops.sh setup
  ./infra/ops.sh backup
  ./infra/ops.sh stop
  ./infra/ops.sh start
  ./infra/ops.sh destroy --purge-data

Requirements: terraform, aws CLI, jq, and AWS credentials for the target account.
Set AWS_PROFILE if needed. No command applies/destroys without showing the plan and asking.
EOF
}

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

confirm() {
  local prompt="$1"
  local answer
  printf '%s [y/N] ' "$prompt"
  read -r answer
  [[ "$answer" == "y" || "$answer" == "Y" ]]
}

tf_output() {
  terraform -chdir="$INFRA_DIR" output -raw "$1"
}

aws_region() {
  if terraform -chdir="$INFRA_DIR" output -raw aws_region 2>/dev/null; then
    return
  fi
  if [[ -n "${AWS_REGION:-${AWS_DEFAULT_REGION:-}}" ]]; then
    printf '%s\n' "${AWS_REGION:-$AWS_DEFAULT_REGION}"
    return
  fi
  AWS_PROFILE="${AWS_PROFILE:-default}" aws configure get region
}

instance_state() {
  AWS_PROFILE="${AWS_PROFILE:-default}" aws ec2 describe-instances \
    --region "$(aws_region)" \
    --instance-ids "$(tf_output api_instance_id)" \
    --query 'Reservations[0].Instances[0].State.Name' \
    --output text
}

send_ssm() {
  local instance_id="$1"
  local command_text="$2"
  local region command_id invocation status
  region="$(aws_region)"
  command_id=$(AWS_PROFILE="${AWS_PROFILE:-default}" aws ssm send-command \
    --region "$region" \
    --instance-ids "$instance_id" \
    --document-name AWS-RunShellScript \
    --parameters "$(jq -cn --arg command "$command_text" '{commands:[$command]}')" \
    --comment "SkinProof ops $(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --query 'Command.CommandId' \
    --output text)

  if ! AWS_PROFILE="${AWS_PROFILE:-default}" aws ssm wait command-executed \
      --region "$region" --command-id "$command_id" --instance-id "$instance_id"; then
    invocation=$(AWS_PROFILE="${AWS_PROFILE:-default}" aws ssm get-command-invocation \
      --region "$region" --command-id "$command_id" --instance-id "$instance_id" --output json)
    printf '%s\n' "$invocation" | jq -r '.StandardErrorContent // empty' >&2
    fail "SSM command $command_id failed."
  fi

  invocation=$(AWS_PROFILE="${AWS_PROFILE:-default}" aws ssm get-command-invocation \
    --region "$region" --command-id "$command_id" --instance-id "$instance_id" --output json)
  status=$(printf '%s' "$invocation" | jq -r '.Status')
  [[ "$status" == "Success" ]] || fail "SSM command $command_id ended with status $status."
  printf '%s\n' "$invocation" | jq -r '.StandardOutputContent // empty'
}

wait_for_ssm() {
  local instance_id="$1"
  local region="$(aws_region)"
  local attempt information
  for attempt in $(seq 1 36); do
    information=$(AWS_PROFILE="${AWS_PROFILE:-default}" aws ssm describe-instance-information \
      --region "$region" \
      --filters "Key=InstanceIds,Values=$instance_id" \
      --query 'InstanceInformationList[0].PingStatus' \
      --output text 2>/dev/null || true)
    [[ "$information" == "Online" ]] && return 0
    sleep 10
  done
  fail "Instance $instance_id did not become available through Systems Manager within six minutes."
}

backup_database() {
  local instance_id bucket key uri remote
  instance_id="$(tf_output api_instance_id)"
  bucket="$(tf_output image_bucket)"
  [[ "$(instance_state)" == "running" ]] || fail "EC2 is not running. Start it before creating a database backup."
  wait_for_ssm "$instance_id"
  key="database-backups/skinproof-db-$(date -u +%Y%m%dT%H%M%SZ).dump"
  uri="s3://$bucket/$key"
  remote="set -euo pipefail; docker exec -e PGPASSWORD=\"\$(cat /opt/skinproof/database-password)\" skinproof-database pg_dump --format=custom --no-owner -U skinproof_app -d skinproof | aws s3 cp - '$uri' --sse AES256; echo 'BACKUP_URI=$uri'"
  send_ssm "$instance_id" "$remote"
}

restore_database() {
  local uri="$1" instance_id bucket remote
  instance_id="$(tf_output api_instance_id)"
  bucket="$(tf_output image_bucket)"
  [[ "$uri" == "s3://$bucket/database-backups/"*.dump ]] || fail "Restore URI must point to a .dump in s3://$bucket/database-backups/."
  [[ "$(instance_state)" == "running" ]] || fail "EC2 is not running. Start it before restoring."
  wait_for_ssm "$instance_id"
  remote="set -euo pipefail; was_running=\$(docker inspect -f '{{.State.Running}}' skinproof-api 2>/dev/null || echo false); docker stop skinproof-api >/dev/null 2>&1 || true; restore_file=\$(mktemp /tmp/skinproof-restore.XXXXXX.dump); trap 'rm -f \"\$restore_file\"; if [ \"\$was_running\" = true ]; then docker start skinproof-api >/dev/null; fi' EXIT; aws s3 cp '$uri' \"\$restore_file\"; docker exec -e PGPASSWORD=\"\$(cat /opt/skinproof/database-password)\" -i skinproof-database pg_restore --clean --if-exists --no-owner -U skinproof_app -d skinproof < \"\$restore_file\"; echo 'DATABASE_RESTORE=success'"
  send_ssm "$instance_id" "$remote"
}

start_instance() {
  local instance_id region state
  instance_id="$(tf_output api_instance_id)"
  region="$(aws_region)"
  state="$(instance_state)"
  if [[ "$state" == "stopped" ]]; then
    AWS_PROFILE="${AWS_PROFILE:-default}" aws ec2 start-instances --region "$region" --instance-ids "$instance_id" >/dev/null
  elif [[ "$state" != "running" && "$state" != "pending" ]]; then
    fail "EC2 cannot be started from state '$state'."
  fi
  AWS_PROFILE="${AWS_PROFILE:-default}" aws ec2 wait instance-running --region "$region" --instance-ids "$instance_id"
  printf 'EC2 %s is running.\n' "$instance_id"
}

stop_instance() {
  local instance_id region state
  instance_id="$(tf_output api_instance_id)"
  region="$(aws_region)"
  state="$(instance_state)"
  if [[ "$state" == "stopped" ]]; then
    printf 'EC2 %s is already stopped.\n' "$instance_id"
    return
  fi
  [[ "$state" == "running" ]] || fail "EC2 cannot be stopped from state '$state'."
  backup_database
  AWS_PROFILE="${AWS_PROFILE:-default}" aws ec2 stop-instances --region "$region" --instance-ids "$instance_id" >/dev/null
  AWS_PROFILE="${AWS_PROFILE:-default}" aws ec2 wait instance-stopped --region "$region" --instance-ids "$instance_id"
  printf 'EC2 %s is stopped. Its EBS volume and the S3 backup remain billable.\n' "$instance_id"
}

setup_infrastructure() {
  local plan_file previous_instance planned_replace backup_uri new_instance local_backup bucket
  plan_file=$(mktemp "${TMPDIR:-/tmp}/skinproof-plan.XXXXXX")
  previous_instance=$(terraform -chdir="$INFRA_DIR" output -raw api_instance_id 2>/dev/null || true)

  terraform -chdir="$INFRA_DIR" init -input=false
  terraform -chdir="$INFRA_DIR" fmt -check -recursive
  terraform -chdir="$INFRA_DIR" validate
  terraform -chdir="$INFRA_DIR" plan -input=false -out="$plan_file"
  terraform -chdir="$INFRA_DIR" show -no-color "$plan_file"
  planned_replace=$(terraform -chdir="$INFRA_DIR" show -json "$plan_file" | jq -r '
    [.resource_changes[]? | select(.address == "module.api.aws_instance.this") | .change.actions] |
    any(. == ["delete", "create"] or . == ["create", "delete"])')

  confirm "Apply this infrastructure plan?" || { rm -f "$plan_file"; exit 1; }
  backup_uri=""
  if [[ "$planned_replace" == "true" && -n "$previous_instance" ]]; then
    printf 'The plan replaces EC2, which currently stores PostgreSQL on its root EBS volume. Creating an S3 dump first.\n'
    [[ "$(instance_state)" == "running" ]] || start_instance
    backup_uri=$(backup_database | sed -n 's/^BACKUP_URI=//p')
    [[ -n "$backup_uri" ]] || fail "Database backup did not return a backup URI; refusing EC2 replacement."
  fi

  terraform -chdir="$INFRA_DIR" apply -input=false "$plan_file"
  rm -f "$plan_file"

  new_instance=$(tf_output api_instance_id)
  if [[ -z "$backup_uri" ]] && { [[ -z "$previous_instance" ]] || [[ "$new_instance" != "$previous_instance" ]]; }; then
    local_backup=$(find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -print 2>/dev/null | sort | tail -n 1 || true)
    if [[ -n "$local_backup" ]] && confirm "Restore the locally saved database dump $local_backup into this newly created/replaced EC2 database?"; then
      bucket="$(tf_output image_bucket)"
      backup_uri="s3://$bucket/database-backups/recovery-$(date -u +%Y%m%dT%H%M%SZ).dump"
      AWS_PROFILE="${AWS_PROFILE:-default}" aws s3 cp --region "$(aws_region)" "$local_backup" "$backup_uri" --sse AES256
    fi
  fi

  if [[ -n "$backup_uri" && ( -z "$previous_instance" || "$new_instance" != "$previous_instance" ) ]]; then
    wait_for_ssm "$new_instance"
    restore_database "$backup_uri"
    printf 'Database restored from %s. Deploy the API image to the new EC2 ID %s using CI.\n' "$backup_uri" "$new_instance"
  fi
  printf 'Infrastructure setup complete. Demo URL: %s\n' "$(tf_output demo_url)"
}

destroy_infrastructure() {
  local purge_data="${1:-false}" bucket backup_uri plan_file object_count
  local instance_id
  instance_id="$(tf_output api_instance_id 2>/dev/null || true)"
  bucket="$(tf_output image_bucket 2>/dev/null || true)"
  backup_uri=""

  if [[ -n "$instance_id" ]]; then
    [[ "$(instance_state)" == "running" ]] || start_instance
    backup_uri=$(backup_database | sed -n 's/^BACKUP_URI=//p')
    [[ -n "$backup_uri" ]] || fail "Database backup did not complete; refusing destroy."
  fi

  object_count=0
  if [[ -n "$bucket" ]]; then
    object_count=$(AWS_PROFILE="${AWS_PROFILE:-default}" aws s3api list-objects-v2 \
      --region "$(aws_region)" --bucket "$bucket" --query 'length(Contents || `[]`)' --output text)
  fi

  if [[ "$object_count" != "0" && "$purge_data" != "true" ]]; then
    printf 'Refusing destroy: private bucket %s contains %s object(s).\n' "$bucket" "$object_count" >&2
    printf 'Database backup is at %s. To preserve a local dump then erase all bucket objects explicitly, run:\n' "$backup_uri" >&2
    printf '  ./infra/ops.sh destroy --purge-data\n' >&2
    exit 1
  fi

  if [[ "$purge_data" == "true" ]]; then
    [[ -n "$backup_uri" ]] || fail "No database backup was created; refusing --purge-data destroy."
    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"
    local backup_file="$BACKUP_DIR/$(basename "$backup_uri")"
    AWS_PROFILE="${AWS_PROFILE:-default}" aws s3 cp --region "$(aws_region)" "$backup_uri" "$backup_file"
    chmod 600 "$backup_file"
    printf 'Database dump saved locally at %s. It contains private account/workspace data; keep it secure.\n' "$backup_file"
    confirm "Delete ALL objects (including private images and S3 backups) from $bucket?" || exit 1
    AWS_PROFILE="${AWS_PROFILE:-default}" aws s3 rm --region "$(aws_region)" "s3://$bucket" --recursive
  fi

  plan_file=$(mktemp "${TMPDIR:-/tmp}/skinproof-destroy.XXXXXX")
  terraform -chdir="$INFRA_DIR" plan -destroy -input=false -out="$plan_file"
  terraform -chdir="$INFRA_DIR" show -no-color "$plan_file"
  confirm "Destroy the infrastructure shown above?" || { rm -f "$plan_file"; exit 1; }
  terraform -chdir="$INFRA_DIR" apply -input=false "$plan_file"
  rm -f "$plan_file"
}

main() {
  require_command terraform
  require_command aws
  require_command jq
  [[ -f "$INFRA_DIR/terraform.tfvars" ]] || fail "Missing infra/terraform.tfvars; copy the example and fill in your AWS/GitHub settings first."

  case "${1:-}" in
    setup)
      setup_infrastructure
      ;;
    backup)
      backup_database
      ;;
    restore)
      [[ -n "${2:-}" ]] || fail "Usage: ./infra/ops.sh restore s3://bucket/database-backups/file.dump"
      confirm "Restore $2 over the current SkinProof database?" || exit 1
      restore_database "$2"
      ;;
    start)
      start_instance
      ;;
    stop)
      stop_instance
      ;;
    status)
      instance_id="$(tf_output api_instance_id)"
      state="$(instance_state)"
      printf 'EC2 %s: %s\n' "$instance_id" "$state"
      if [[ "$state" == "running" ]]; then
        curl --fail --silent --show-error --max-time 10 "$(tf_output demo_url)/api/health"
        printf '\n'
      fi
      ;;
    destroy)
      purge_data=false
      [[ "${2:-}" == "--purge-data" ]] && purge_data=true
      destroy_infrastructure "$purge_data"
      ;;
    *)
      usage
      exit 2
      ;;
  esac
}

main "$@"