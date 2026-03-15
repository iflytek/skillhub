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

```bash
# 选择存储提供方
SKILLHUB_STORAGE_PROVIDER=s3

# S3 配置
SKILLHUB_STORAGE_S3_ENDPOINT=https://s3.example.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=xxx
SKILLHUB_STORAGE_S3_SECRET_KEY=xxx
```

## 自定义实现

实现 `ObjectStorageService` 接口，注册为 Spring Bean 即可。

## 下一步

- [常见问题](../../reference/faq) - FAQ
