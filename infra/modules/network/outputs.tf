output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "api_security_group_id" {
  value = aws_security_group.api_origin.id
}

output "cloudfront_origin_prefix_list_id" {
  value = data.aws_ec2_managed_prefix_list.cloudfront_origin.id
}