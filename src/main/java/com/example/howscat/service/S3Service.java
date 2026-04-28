package com.example.howscat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public S3Service(
            @Value("${cloud.aws.credentials.access-key:}") String accessKey,
            @Value("${cloud.aws.credentials.secret-key:}") String secretKey,
            @Value("${cloud.aws.region:ap-northeast-2}") String region,
            @Value("${cloud.aws.s3.bucket:}") String bucket
    ) {
        this.bucket = bucket;
        this.region = region;
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        } else {
            this.s3Client = null;
        }
    }

    public String upload(MultipartFile file, String folder) throws IOException {
        if (s3Client == null || bucket.isBlank()) {
            throw new IllegalStateException("S3 환경변수가 설정되지 않았습니다.");
        }
        String ext = getExtension(file.getOriginalFilename());
        String key = folder + "/" + UUID.randomUUID() + ext;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request,
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
