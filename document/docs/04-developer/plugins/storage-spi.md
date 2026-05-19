---
title: 存储 SPI
sidebar_position: 2
description: 存储服务提供方扩展
---

# 存储 SPI

## SPI 接口

```java
public interface ObjectStorageService {
    void store(String key, InputStream content, String contentType);
    InputStream retrieve(String key);
    void delete(String key);
    boolean exists(String key);
}
```

## 内置实现

### LocalFileStorageService

本地文件系统实现，用于开发环境。

### S3StorageService

S3 协议兼容实现，支持：
- AWS S3
- MinIO
- 阿里云 OSS
- 腾讯云 COS
- 其他 S3 兼容存储

## 配置

### 静态凭据（Access Key / Secret Key）

```bash
# 选择存储提供方
SKILLHUB_STORAGE_PROVIDER=s3

# S3 配置
SKILLHUB_STORAGE_S3_ENDPOINT=https://s3.example.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=xxx
SKILLHUB_STORAGE_S3_SECRET_KEY=xxx
```

### IAM 认证

部署在 AWS 上时，可以不配置 Access Key / Secret Key，让 SDK 自动使用 IAM 角色认证（[Default Credentials Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)）：

```bash
SKILLHUB_STORAGE_PROVIDER=s3
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_REGION=us-east-1
# 留空或不设置 ACCESS_KEY / SECRET_KEY，SDK 自动使用 IAM 认证
SKILLHUB_STORAGE_S3_ACCESS_KEY=
SKILLHUB_STORAGE_S3_SECRET_KEY=
```

支持的 IAM 认证方式（按 SDK 优先级）：
- 环境变量（`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`）
- Java 系统属性
- Web Identity Token（EKS IRSA）
- AWS 配置文件（`~/.aws/credentials`）
- EC2 Instance Profile
- ECS Task Role

## 自定义实现

实现 `ObjectStorageService` 接口，注册为 Spring Bean 即可。

## 下一步

- [常见问题](../../reference/faq) - FAQ
