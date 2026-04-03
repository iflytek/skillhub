# 基础镜像（需要在云效中配置镜像加速器）
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/python:latest

WORKDIR /app

# 2. 升级 pip 并配置镜像源
RUN pip install --upgrade pip -i https://mirrors.aliyun.com/pypi/simple/ \
      --timeout 60 --retries 5 --trusted-host mirrors.aliyun.com

# 安装系统依赖（Debian/Ubuntu 系统使用 apt-get）
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    libc6-dev \
    libffi-dev \
    wget && \
    pip install --no-cache-dir \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com \
    --extra-index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    cisco-ai-skill-scanner && \
    apt-get purge -y gcc libc6-dev libffi-dev && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# 创建 app 用户和组（Debian/Ubuntu 语法）
RUN groupadd -r app && \
    useradd -r -g app app && \
    mkdir -p /tmp/skillhub-scans && \
    chown app:app /tmp/skillhub-scans

USER app

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://127.0.0.1:8000/health || exit 1

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]
