FROM python:3.11-slim-bookworm

WORKDIR /app

RUN pip install --no-cache-dir --only-binary=:all: \
        "cisco-ai-skill-scanner==2.1.0" && \
    groupadd --system app && \
    useradd --system --gid app --home-dir /nonexistent --no-create-home app && \
    install -d -o app -g app /tmp/skillhub-scans

COPY skillhub_scanner_app.py /app/skillhub_scanner_app.py

USER app

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2).read()"]

CMD ["uvicorn", "skillhub_scanner_app:app", "--host", "0.0.0.0", "--port", "8000"]
