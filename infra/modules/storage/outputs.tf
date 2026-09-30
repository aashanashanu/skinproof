output "image_bucket_name" {
  value = aws_s3_bucket.images.id
}

output "image_bucket_arn" {
  value = aws_s3_bucket.images.arn
}