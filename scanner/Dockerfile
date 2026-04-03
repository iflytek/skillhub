# 基础镜像 - Alibaba Cloud Linux 3
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/python:latest

WORKDIR /app

# 配置阿里云 yum 镜像源（可选，基础镜像可能已配置）
RUN sed -i 's|releasever|3|g' /etc/yum.repos.d/*.repo 2>/dev/null || true

# 升级 pip
RUN pip install --upgrade pip \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com

# 安装编译依赖和 Python 包
RUN yum install -y \
    gcc \
    gcc-c++ \
    make \
    glibc-devel \
    libffi-devel \
    openssl-devel \
    wget && \
    pip install --no-cache-dir \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --timeout 120 \
    --retries 5 \
    --trusted-host mirrors.aliyun.com \
    --extra-index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    cisco-ai-skill-scanner && \
    yum remove -y \
    gcc \
    gcc-c++ \
    make \
    glibc-devel \
    libffi-devel \
    openssl-devel && \
    yum clean all && \
    rm -rf /var/cache/yum

# 创建 app 用户和组（RHEL 语法）
RUN groupadd -r app && \
    useradd -r -g app -s /sbin/nologin app && \
    mkdir -p /tmp/skillhub-scans && \
    chown -R app:app /tmp/skillhub-scans

USER app

EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8000/health || exit 1

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]
