<div align="center">
  <img src="./skillhub-logo.svg" alt="SkillHub Logo" width="120" height="120" />
  <h1>SkillHub</h1>
  <p>エンタープライズグレードのオープンソースなエージェントスキルレジストリ — 組織全体で再利用可能なスキルパッケージを公開・発見・管理できます。</p>
</div>

<div align="center">

[![DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/iflytek/skillhub)
[![Docs](https://img.shields.io/badge/docs-zread.ai-4A90E2?logo=gitbook&logoColor=white)](https://zread.ai/iflytek/skillhub)
[![Discord](https://img.shields.io/badge/discord-join-5865F2?logo=discord&logoColor=white)](https://discord.gg/qHYvtDNPHS)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Build](https://github.com/iflytek/skillhub/actions/workflows/publish-images.yml/badge.svg)](https://github.com/iflytek/skillhub/actions/workflows/publish-images.yml)
[![Docker](https://img.shields.io/badge/docker-ghcr.io-2496ED?logo=docker&logoColor=white)](https://ghcr.io/iflytek/skillhub)
[![Java](https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![React](https://img.shields.io/badge/react-19-61DAFB?logo=react&logoColor=black)](https://react.dev)

</div>

<div align="center">

[English](./README.md) | [中文](./README_zh.md) | [日本語](./README_ja.md)

</div>

---

<div align="center">
  <img src="https://xfyun-doc.xfyun.cn/lc-sp-skillhub-demo-1775551643410.gif" alt="SkillHub Demo" width="800" />
</div>

SkillHub は、チームにプライベートで統制の効いたエージェントスキルの共有の場を提供するセルフホスト型プラットフォームです。スキルパッケージを公開し、名前空間にプッシュすれば、他のメンバーが検索で見つけたり CLI でインストールしたりできます。ファイアウォール内でのオンプレミス展開を前提に構築されており、パブリックなレジストリと同等の洗練された体験を提供します。

## ドキュメント

- 📖 **[ユーザーガイド](https://iflytek.github.io/skillhub/)** — スキルの公開、検索、CLI の使い方などのユーザー向けガイド
- 🛠️ **[開発者ドキュメント](https://zread.ai/iflytek/skillhub)** — アーキテクチャ、API リファレンス、ローカル開発、デプロイと運用

## 主な特徴

- **セルフホスト & プライベート** — 自社のインフラ上にデプロイできます。
  独自のスキルをファイアウォール内に保ち、完全なデータ主権を維持できます。
  `make dev-all` の一発でローカル起動できます。
- **公開 & バージョン管理** — セマンティックバージョニング、カスタムタグ
  （`beta`、`stable`）、自動的な `latest` 追跡に対応したエージェントスキル
  パッケージをアップロードできます。
- **発見** — 名前空間、ダウンロード数、評価、新着順でフィルタできる
  全文検索。可視性ルールにより、ユーザーには権限のあるものだけが
  表示されます。
- **チーム名前空間** — チームスコープまたはグローバルスコープのもとに
  スキルを整理できます。各名前空間は独自のメンバー、ロール（Owner /
  Admin / Member）、公開ポリシーを持ちます。
- **レビュー & ガバナンス** — チーム管理者は自分の名前空間内でレビューし、
  プラットフォーム管理者がグローバルスコープへの昇格を統制します。
  ガバナンス操作はコンプライアンスのために監査ログに記録されます。
- **ソーシャル機能** — スキルにスターを付けたり、評価したり、ダウンロード数を
  追跡したりできます。組織のベストプラクティスを中心としたコミュニティを
  構築できます。
- **アカウント統合** — 複数の OAuth ID と API トークンを単一のユーザー
  アカウントに統合できます。
- **API トークン管理** — CLI やプログラムからのアクセス用に、プレフィックス
  ベースの安全なハッシュ化を用いたスコープ付きトークンを生成できます。
- **CLI ファースト** — ネイティブな REST API に加え、既存の ClawHub 形式の
  レジストリクライアント向けの互換レイヤーを備えています。ネイティブな
  CLI API が主にサポートされる経路であり、プロトコル互換性は引き続き
  拡充されています。
- **プラガブルなストレージ** — 開発用のローカルファイルシステム、本番用の
  S3 / MinIO。設定で切り替えられます。
- **国際化** — i18next による多言語対応。

## クイックスタート

ローカルのフルスタックを起動するには:

```bash
rm -rf /tmp/skillhub-runtime
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

デフォルトのコマンドは `latest` の安定版リリースイメージを取得します。`main` の最新ビルドを使いたい場合は `--version edge` を指定してください。

**公開 URL の設定（本番環境で推奨）:**

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --public-url https://skillhub.your-company.com
```

`--public-url` パラメータは、あなたの SkillHub インスタンスの公開アクセス URL を設定します。これにより次が保証されます:
- CLI のインストールコマンドが正しいレジストリ URL を表示する
- エージェントのセットアップ手順が正しい skill.md の URL を表示する
- OAuth コールバックとデバイス認証リンクが正しく動作する

**中国国内のユーザー向け（Aliyun ミラー）:**

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com --version latest
```

デプロイで問題が発生した場合は、既存のランタイムホームを削除して再試行してください。

## SkillHub CLI

コマンドラインからエージェントスキルをインストール・管理できます:

```bash
# CLI をインストール
npm install -g @astron-team/skillhub

# または直接実行
npx @astron-team/skillhub@latest version

# ログイン
skillhub login --token sk_xxx --registry https://skill.xfyun.cn

# スキルの検索とインストール
skillhub search pdf
skillhub install pdf-parser --agent codex

# インストール済みスキルの一覧
skillhub list
```

📖 完全ガイド: [docs/skillhub/en/guide/cli.md](docs/skillhub/en/guide/cli.md)

### 前提条件

- Docker & Docker Compose

### ローカル開発

```bash
make dev-all
```

> **中国国内の開発者向け**: Maven の依存関係のダウンロードがタイムアウトする場合は、Aliyun ミラーを設定してください。詳細は [ローカル開発ガイド](https://iflytek.github.io/skillhub/quickstart.html#本地开发) を参照してください。

その後、以下を開きます:

- Web UI: `http://localhost:3000`
- バックエンド API: `http://localhost:8080`

デフォルトでは、`make dev-all` は `local` プロファイルでバックエンドを起動します。
このモードでは、ローカル開発は以下のモック認証ユーザーを保持し、さらに
デフォルトでパスワードベースのブートストラップ管理者アカウントを作成します:

- 通常の公開操作や名前空間操作用の `local-user`
- レビューや管理フロー用の `SUPER_ADMIN` 権限を持つ `local-admin`

ローカル開発では `X-Mock-User-Id` ヘッダーでこれらを利用します。

ローカルのブートストラップ管理者は `application-local.yml` でデフォルトで有効です:

- ユーザー名: `admin`
- パスワード: `ChangeMe!2026`
- 無効にするには、バックエンドを起動する前に `BOOTSTRAP_ADMIN_ENABLED=false` を設定してください。

すべて停止するには:

```bash
make dev-all-down
```

ローカルの依存関係をリセットしてクリーンな状態から開始するには:

```bash
make dev-all-reset
```

利用可能なすべてのコマンドを見るには `make help` を実行してください。

便利なバックエンドコマンド:

```bash
make test
make test-backend-app
make build-backend-app
```

`server/` 以下で `./mvnw -pl skillhub-app clean test` を直接実行しないでください。
`skillhub-app` は同じリポジトリ内の兄弟モジュールに依存しており、単体での clean ビルドは
ローカル Maven リポジトリの古い成果物にフォールバックすることがあります。これにより誤解を招く
`cannot find symbol` やシグネチャ不一致のエラーが発生します。`-am` を使うか、上記の
`make test-backend-app` および `make build-backend-app` ターゲットを使用してください。

開発ワークフロー全体（ローカル開発 → ステージング → PR）については、[docs/dev-workflow.md](docs/dev-workflow.md) を参照してください。

### API コントラクトの同期

Web クライアント用の OpenAPI 型はリポジトリにチェックインされています。
バックエンドの API コントラクトが変更された場合は、SDK を再生成して、
更新された生成ファイルをコミットしてください:

```bash
make generate-api
```

より厳密なエンドツーエンドの差分チェックには、以下を実行します:

```bash
./scripts/check-openapi-generated.sh
```

これはローカルの依存関係を起動し、バックエンドを立ち上げ、フロントエンドの
スキーマを再生成し、チェックイン済みの SDK が古い場合は失敗します。

### コンテナランタイム

公開されているランタイムイメージは GitHub Actions によってビルドされ、GHCR にプッシュされます。
これは、自分のマシンでバックエンドやフロントエンドをビルドせずに、すぐに使える
ローカル環境を求める人向けのサポートされた経路です。
公開されたイメージは `linux/amd64` と `linux/arm64` の両方を対象としています。

**curl によるクイックデプロイ:**

```bash
# デフォルト（GHCR イメージ）
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --public-url https://skillhub.your-company.com

# Aliyun ミラー（中国国内のユーザー向けに推奨）
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com --version latest
```

**デプロイパラメータ:**

| パラメータ | 説明 | 例 |
|-----------|-------------|---------|
| `--public-url <url>` | 公開アクセス URL（推奨） | `--public-url https://skill.example.com` |
| `--version <tag>` | 特定のイメージタグ | `--version v0.2.0` |
| `--aliyun` | Aliyun ミラーを使用（中国） | `--aliyun` |
| `--home <dir>` | ランタイムディレクトリ | `--home /opt/skillhub` |
| `--no-scanner` | セキュリティスキャナーを無効化 | `--no-scanner` |

> **重要**: 本番デプロイでは、CLI のインストールコマンドとエージェントのセットアップ手順が正しい URL を表示するように `--public-url` を設定してください。

**手動デプロイ:**

1. ランタイム環境テンプレートをコピーします。
2. イメージタグを選びます。
3. Docker Compose でスタックを起動します。

```bash
cp .env.release.example .env.release
```

推奨されるイメージタグ:

- 最新の安定版リリースには `SKILLHUB_VERSION=latest`（デフォルト）
- 最新の `main` ビルドには `SKILLHUB_VERSION=edge`
- 固定されたリリースには `SKILLHUB_VERSION=vX.Y.Z`

ランタイムを起動します:

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

その後、以下を開きます:

- Web UI: `SKILLHUB_PUBLIC_BASE_URL` に対応するアドレス
- バックエンド API: `http://localhost:8080`

停止するには:

```bash
docker compose --env-file .env.release -f compose.release.yml down
```

ランタイムスタックは独自の Compose プロジェクト名を使用するため、
`make dev-all` のコンテナと衝突することはありません。

本番用の Compose スタックは、デフォルトで `docker` プロファイルのみになりました。
ローカルのモック認証は有効になりません。リリーステンプレート（`.env.release.example`）は
デフォルトでブートストラップ管理者を有効にするため、`runtime.sh` による
ゼロコンフィグのクイックスタートがそのまま動作します:

- ユーザー名: `admin`
- パスワード: `ChangeMe!2026`

推奨される本番のベースライン:

- `SKILLHUB_PUBLIC_BASE_URL` を最終的な HTTPS のエントリポイントに設定する
- PostgreSQL / Redis を `127.0.0.1` にバインドしたままにする
- `SKILLHUB_STORAGE_S3_*` を使って外部の S3 / OSS を利用する
- `BOOTSTRAP_ADMIN_PASSWORD` を強力なパスワードに変更する（`validate-release-config.sh` はデフォルトの `ChangeMe!2026` を拒否します）
- 初期セットアップ後にブートストラップ管理者をローテーションまたは無効化する
- `docker compose up -d` の前に `make validate-release-config` を実行する

GHCR パッケージがプライベートのままの場合は、`docker compose up -d` の前に
`docker login ghcr.io` を実行してください。

### アップロード許可リストの上書き

スキルパッケージのアップロード検証では、
[`SkillPackagePolicy.java`](./server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/SkillPackagePolicy.java) のデフォルトの拡張子許可リストを使用します。
`SkillPublishProperties` は、`skillhub.publish.allowed-file-extensions` に対して
デフォルトで同じリストを使用します。

実行時にデフォルトの許可リストを置き換える必要がある場合は、以下を設定します:

```bash
SKILLHUB_PUBLISH_ALLOWED_FILE_EXTENSIONS=.md,.json,.xsd,.xsl,.dtd,.docx,.xlsx,.pptx
```

Spring Boot はこの環境変数を `skillhub.publish.allowed-file-extensions` にバインドします。
設定されると、デフォルトの許可リストに追加するのではなく、置き換えます。

### モニタリング

Prometheus + Grafana のモニタリングスタックが [`monitoring/`](./monitoring) の下にあります。
バックエンドの Actuator Prometheus エンドポイントをスクレイプします。

起動するには:

```bash
cd monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

その後、以下を開きます:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`（`admin` / `admin`）

デフォルトでは Prometheus は `http://host.docker.internal:8080/actuator/prometheus` を
スクレイプするため、先にバックエンドをローカルのポート `8080` で起動してください。

## Kubernetes

基本的な Kubernetes マニフェストが [`deploy/k8s/`](./deploy/k8s) の下にあります:

- `configmap.yaml`
- `secret.yaml.example`
- `backend-deployment.yaml`
- `frontend-deployment.yaml`
- `services.yaml`
- `ingress.yaml`

独自の secret を作成した後に、これらを適用します:

```bash
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/backend-deployment.yaml
kubectl apply -f deploy/k8s/frontend-deployment.yaml
kubectl apply -f deploy/k8s/services.yaml
kubectl apply -f deploy/k8s/ingress.yaml
```

## スモークテスト

軽量なスモークテストスクリプトが [`scripts/smoke-test.sh`](./scripts/smoke-test.sh) にあります。

ローカルのバックエンドに対して実行します:

```bash
./scripts/smoke-test.sh http://localhost:8080
```

## アーキテクチャ

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   Web UI    │     │  CLI Tools  │     │  REST API    │
│  (React 19) │     │             │     │              │
└──────┬──────┘     └──────┬──────┘     └──────┬───────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌──────▼──────┐
                    │   Nginx     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Spring Boot │  Auth · RBAC · Core Services
                    │   (Java 21) │  OAuth2 · API Tokens · Audit
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼───┐  ┌─────▼────┐  ┌────▼────┐
       │PostgreSQL│  │  Redis   │  │ Storage │
       │    16    │  │    7     │  │ S3/MinIO│
       └──────────┘  └──────────┘  └─────────┘
```

**バックエンド（Spring Boot 3.2.3、Java 21）:**
- クリーンアーキテクチャを採用したマルチモジュール Maven プロジェクト
- モジュール: app、domain、auth、search、storage、infra
- Flyway マイグレーションを用いた PostgreSQL 16
- セッション管理用の Redis
- スキルパッケージ保存用の S3/MinIO

**フロントエンド（React 19、TypeScript、Vite）:**
- ルーティングに TanStack Router
- データフェッチに TanStack Query
- スタイリングに Tailwind CSS + Radix UI
- 型安全な API クライアントに OpenAPI TypeScript
- 国際化に i18next

## エージェントプラットフォームでの利用

SkillHub は複数のエージェントプラットフォームのスキルレジストリバックエンドとして機能します。以下のいずれかのクライアントをあなたの SkillHub インスタンスに向けることで、スキルの公開、発見、インストールができます。

### [OpenClaw](https://github.com/openclaw/openclaw)

[OpenClaw](https://github.com/openclaw/openclaw) はオープンソースのエージェントスキル CLI です。あなたの SkillHub エンドポイントをレジストリとして使うように設定します:

```bash
# レジストリ URL を設定
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# 必要に応じて一度だけ認証
clawhub login --token YOUR_API_TOKEN

# スキルの検索とインストール
npx clawhub search email
npx clawhub install my-skill
npx clawhub install my-namespace--my-skill

# グローバル名前空間に公開
npx clawhub publish ./my-skill --slug my-skill --version 1.0.0

# my-space のようなチーム名前空間に公開
npx clawhub publish ./my-skill --slug my-space--my-skill --version 1.0.0
```

`my-space--my-skill` は標準的な互換 slug です。SkillHub はこれを
名前空間 `my-space` とスキル slug `my-skill` として解析します。

> 💡 **ヒント**: 上記のコマンドは OpenClaw だけでなく、インストールディレクトリ（`--dir`）を指定することで、他の CLI Coding Agent やエージェントアシスタントにも適用できます。例: `npx clawhub --dir ~/.claude/skills install my-skill`

📖 **[OpenClaw 連携の完全ガイド →](./docs/openclaw-integration.md)**

### [AstronClaw](https://agent.xfyun.cn/astron-claw)

[AstronClaw](https://agent.xfyun.cn/astron-claw) は OpenClaw のコア機能の上に構築されたクラウド AI アシスタントで、WeChat Work、DingTalk、Feishu などのエンタープライズプラットフォームを通じて 24 時間 365 日のオンラインサービスを提供します。130 を超える公式スキルを備えた組み込みのスキルシステムを特徴としています。セルフホストの SkillHub レジストリに接続することで、ワンクリックでのスキルインストール、リポジトリ検索、対話ベースの自動インストール、さらには組織内でのカスタムスキル管理を有効にできます。

### [Loomy](https://loomy.xunfei.cn/)

[Loomy](https://loomy.xunfei.cn/) は実際のオフィスシーンに焦点を当てたデスクトップ AI ワークパートナーです。ローカルファイルやシステムツールと深く統合し、個人や小規模チームのための効率的な自動化ワークフローを構築します。Loomy をあなたの SkillHub レジストリに接続することで、組織固有のスキルを簡単に発見・インストールし、ローカルデスクトップの自動化と生産性を高められます。

### [astron-agent](https://github.com/iflytek/astron-agent)

[astron-agent](https://github.com/iflytek/astron-agent) は iFlytek の Astron エージェントフレームワークです。SkillHub に保存されたスキルは astron-agent から参照・ロードでき、開発から本番まで統制されたバージョン管理付きのスキルライフサイクルを実現します。

---

> 🌟 **Show & Tell** — SkillHub で何かを作りましたか？ぜひ聞かせてください！
> あなたのユースケース、連携、デプロイの体験談を
> [**Discussions → Show and Tell**](https://github.com/iflytek/skillhub/discussions/categories/show-and-tell) カテゴリで共有してください。

## コントリビュート

コントリビュートを歓迎します。変更したい内容について、まず issue を立てて
議論してください。

- コントリビューションガイド: [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- 行動規範: [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md)

## 📞 サポート

- 💬 **コミュニティディスカッション**: [GitHub Discussions](https://github.com/iflytek/skillhub/discussions)
- 🐛 **バグ報告**: [Issues](https://github.com/iflytek/skillhub/issues)
- 👾 **Discord**: [サーバーに参加](https://discord.gg/qHYvtDNPHS)
- 👥 **WeChat Work グループ**:

  ![WeChat Work Group](https://github.com/iflytek/astron-agent/raw/main/docs/imgs/WeCom_Group.png)

## ライセンス

Apache License 2.0
