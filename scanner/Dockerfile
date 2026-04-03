# 基础镜像（需要在云效中配置镜像加速器）
FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/python:latest

WORKDIR /app

# 2. 升级 pip 并配置镜像源
RUN pip install --upgrade pip -i https://mirrors.aliyun.com/pypi/simple/ \
      --timeout 60 --retries 5 --trusted-host mirrors.aliyun.com

# 原始的构建命令
RUN apk add --no-cache --virtual .build-deps gcc musl-dev libffi-dev && \
    pip install --no-cache-dir cisco-ai-skill-scanner && \
    apk del .build-deps && \
    addgroup -S app && \
    adduser -S app -G app && \
    mkdir -p /tmp/skillhub-scans && \
    chown app:app /tmp/skillhub-scans


# 优化使用阿里云加速
# 1.使用阿里云 Alpine 镜像源加速 apk 下载
# RUN #sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories


# 3. 安装依赖包（使用阿里云 PyPI 镜像，增加超时时间和重试次数）
#RUN apk add --no-cache gcc musl-dev libffi-dev wget && \
#      pip install --no-cache-dir \
#      -i https://mirrors.aliyun.com/pypi/simple/ \
#      --timeout 120 \
#      --retries 5 \
#      --trusted-host mirrors.aliyun.com \
#      --extra-index-url https://pypi.tuna.tsinghua.edu.cn/simple \
#      cisco-ai-skill-scanner && \
#      apk del gcc musl-dev libffi-dev

# 创建 app 用户和组
RUN addgroup -S app && \
    adduser -S app -G app && \
    mkdir -p /tmp/skillhub-scans && \
    chown app:app /tmp/skillhub-scans

USER app

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://127.0.0.1:8000/health || exit 1

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]
