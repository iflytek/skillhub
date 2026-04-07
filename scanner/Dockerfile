# 基础镜像 - Alibaba Cloud Linux 3
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/python:latest

WORKDIR /app

# 升级 pip
RUN pip install --upgrade pip \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com

# 检查 yum 配置并安装依赖
RUN set -ex && \
    echo "=== 检查 yum 仓库配置 ===" && \
    yum repolist || echo "repolist failed" && \
    echo "" && \
    echo "=== 清理 yum 缓存 ===" && \
    yum clean all && \
    rm -rf /var/cache/yum && \
    echo "" && \
    echo "=== 更新 yum 元数据 ===" && \
    yum makecache || yum makecache fast || true && \
    echo "" && \
    echo "=== 安装编译依赖 ===" && \
    yum install -y \
        gcc \
        gcc-c++ \
        make \
        glibc-devel \
        libffi-devel \
        openssl-devel \
        wget \
        ca-certificates && \
    echo "" && \
    echo "=== 安装 Python 包 ===" && \
    pip install --no-cache-dir \
        -i https://mirrors.aliyun.com/pypi/simple/ \
        --timeout 120 \
        --retries 5 \
        --trusted-host mirrors.aliyun.com \
        cisco-ai-skill-scanner && \
    echo "" && \
    echo "=== 清理编译依赖 ===" && \
    yum remove -y \
        gcc \
        gcc-c++ \
        make \
        glibc-devel \
        libffi-devel \
        openssl-devel && \
    yum clean all && \
    rm -rf /var/cache/yum

# 创建 app 用户和组
RUN groupadd -r app && \
    useradd -r -g app -s /sbin/nologin app && \
    mkdir -p /tmp/skillhub-scans && \
    chown -R app:app /tmp/skillhub-scans

USER app

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8000/health || exit 1

CMD ["uvicorn", "skill_scanner.api.api:app", "--host", "0.0.0.0", "--port", "8000", "--root-path", "/skillhub"]
