# 基础镜像 - Ubuntu 26.04
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/python:latest

WORKDIR /app

# 配置阿里云 Ubuntu 镜像源（加速 apt 下载）
RUN sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || \
    sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list 2>/dev/null || \
    echo "sources.list not found, skipping"

# 升级 pip 并配置镜像源
RUN pip install --upgrade pip \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com

# 安装系统依赖和 Python 包
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    make \
    libc6-dev \
    libffi-dev \
    libssl-dev \
    wget \
    ca-certificates && \
    pip install --no-cache-dir \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com \
    --extra-index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    cisco-ai-skill-scanner && \
    apt-get purge -y gcc g++ make libc6-dev libffi-dev libssl-dev && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 创建 app 用户和组（Ubuntu 语法）
RUN groupadd --system app && \
    useradd --system --gid app --shell /bin/bash app && \
    mkdir -p /tmp/skillhub-scans && \
    chown -R app:app /tmp/skillhub-scans

USER app

EXPOSE 8000

# 健康检查（使用 wget）
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8000/health || exit 1

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]
