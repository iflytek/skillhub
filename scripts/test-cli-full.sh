#!/bin/bash
# SkillHub CLI 完整集成测试脚本
# 使用方法: 确保后端运行后执行 ./scripts/test-cli-full.sh

set -e

CLI="node /mnt/cfs/chenbaowang/skillhub/skillhub-cli/dist/cli.mjs"
REGISTRY="http://localhost:8080"
PASS=0
FAIL=0
TOTAL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

assert() {
  local desc="$1"
  local cmd="$2"
  local expect_exit="$3"  # 0=success, 1=fail, "contains:xxx"=output should contain
  TOTAL=$((TOTAL + 1))

  local output
  local exit_code=0
  output=$(eval "$cmd" 2>&1) || exit_code=$?

  if [ "$expect_exit" = "0" ]; then
    if [ "$exit_code" -eq 0 ]; then
      echo -e "  ${GREEN}✅ PASS${NC} $desc"
      PASS=$((PASS + 1))
    else
      echo -e "  ${RED}❌ FAIL${NC} $desc (exit=$exit_code)"
      echo "    输出: $(echo "$output" | head -3)"
      FAIL=$((FAIL + 1))
    fi
  elif [ "$expect_exit" = "1" ]; then
    if [ "$exit_code" -ne 0 ]; then
      echo -e "  ${GREEN}✅ PASS${NC} $desc"
      PASS=$((PASS + 1))
    else
      echo -e "  ${RED}❌ FAIL${NC} $desc (expected failure, got success)"
      FAIL=$((FAIL + 1))
    fi
  elif [[ "$expect_exit" == contains:* ]]; then
    local pattern="${expect_exit#contains:}"
    if echo "$output" | grep -q "$pattern"; then
      echo -e "  ${GREEN}✅ PASS${NC} $desc"
      PASS=$((PASS + 1))
    else
      echo -e "  ${RED}❌ FAIL${NC} $desc (expected '$pattern' in output)"
      echo "    输出: $(echo "$output" | head -3)"
      FAIL=$((FAIL + 1))
    fi
  fi
}

echo "============================================================"
echo "  SkillHub CLI 完整集成测试"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  后端: $REGISTRY"
echo "============================================================"

# 0. 检查后端
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  前置检查"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if curl -sf "$REGISTRY/api/v1/health" > /dev/null 2>&1; then
  echo -e "  ${GREEN}✅ 后端运行中${NC}"
else
  echo -e "  ${RED}❌ 后端未运行，请先执行: make dev-server-restart${NC}"
  exit 1
fi

# 1. Bootstrap 登录获取 token
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  步骤 1: Bootstrap 登录"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
BOOTSTRAP=$(curl -s -X POST "$REGISTRY/api/v1/auth/session/bootstrap" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMe!2026"}')
echo "  响应: $(echo "$BOOTSTRAP" | head -c 200)"

# 提取 token — 适配 ApiResponse 包装
TOKEN=$(echo "$BOOTSTRAP" | node -e "
  const d = JSON.parse(require('fs').readFileSync('/dev/stdin','utf8'));
  const data = d.data || d;
  console.log(data.token || data.accessToken || data.apiToken || '');
" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo -e "  ${YELLOW}⚠️  未获取到 token，尝试 mock user${NC}"
  TOKEN="mock-local-user"
fi
echo "  Token: ${TOKEN:0:30}..."

# 2. 本地命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 A: 本地命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

assert "help 显示所有命令" "$CLI --help" "contains:login"
assert "version 输出版本号" "$CLI --version" "contains:0.1.0"

# init 测试
TMPDIR=$(mktemp -d)
cd "$TMPDIR"
assert "init 创建默认 SKILL.md" "$CLI init" "0"
assert "init 重复创建报错" "$CLI init" "1"
assert "init 指定名称" "$CLI init my-test-skill" "0"
assert "init 生成正确 frontmatter" "grep -q 'name: my-test-skill' my-test-skill/SKILL.md" "0"

# list 测试
assert "list 显示已安装技能" "$CLI list" "0"
assert "list --global 过滤" "$CLI list --global" "0"
assert "list --project 过滤" "$CLI list --project" "0"

# remove 测试
assert "remove 不存在的技能" "$CLI remove nonexistent-skill" "contains:not found"

# add 测试
mkdir -p /tmp/cli-test-repo/skills/skill-a /tmp/cli-test-repo/skills/skill-b
cat > /tmp/cli-test-repo/skills/skill-a/SKILL.md << 'EOF'
---
name: skill-a
description: Test skill A
---
# Skill A
EOF
cat > /tmp/cli-test-repo/skills/skill-b/SKILL.md << 'EOF'
---
name: skill-b
description: Test skill B
---
# Skill B
EOF
assert "add --list 预览技能" "$CLI add /tmp/cli-test-repo --list" "contains:skill-a"
assert "add 安装本地技能" "$CLI add /tmp/cli-test-repo --yes" "0"

cd /mnt/cfs/chenbaowang/skillhub

# logout 测试
assert "logout 成功" "$CLI logout" "0"

# 3. 认证命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 B: 认证命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

assert "login 使用 token" "$CLI login --token $TOKEN --registry $REGISTRY" "0"
assert "whoami 显示用户信息" "$CLI whoami --registry $REGISTRY" "0"
assert "logout 清除 token" "$CLI logout" "0"

# 4. 只读命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 C: 只读命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

assert "search 搜索技能" "$CLI search --registry $REGISTRY test" "0"
assert "namespaces 列出命名空间" "$CLI namespaces --registry $REGISTRY" "0"

# 5. 写操作命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 D: 写操作命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 发布测试
PUBLISH_DIR=$(mktemp -d)
mkdir -p "$PUBLISH_DIR"
cat > "$PUBLISH_DIR/SKILL.md" << 'EOF'
---
name: cli-integration-test
description: Skill created by CLI integration test
---
# CLI Integration Test
This is an automated test skill.
EOF

assert "publish 发布技能" "$CLI publish $PUBLISH_DIR --registry $REGISTRY --slug cli-test --ver 1.0.0 --namespace global --name 'CLI Test'" "0"

# 信息查看
assert "info 查看技能详情" "$CLI info --registry $REGISTRY cli-test" "0"
assert "versions 查看版本列表" "$CLI versions --registry $REGISTRY cli-test" "0"
assert "resolve 解析版本" "$CLI resolve --registry $REGISTRY cli-test" "0"

# 交互操作
assert "star 收藏技能" "$CLI star --registry $REGISTRY cli-test" "0"
assert "rating 查看评分" "$CLI rating --registry $REGISTRY cli-test" "0"
assert "rate 评分 (5分)" "$CLI rate --registry $REGISTRY cli-test 5" "0"
assert "rate 无效分数" "$CLI rate --registry $REGISTRY cli-test 6" "1"

# 6. 安装命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 E: 安装命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

assert "download 下载技能包" "$CLI download --registry $REGISTRY cli-test --output /tmp" "0"
assert "install 安装技能" "$CLI install --registry $REGISTRY cli-test --yes" "0"

# 7. 用户命令
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  测试组 F: 用户命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

assert "me skills 列出我的技能" "$CLI me skills --registry $REGISTRY" "0"
assert "me stars 列出收藏" "$CLI me stars --registry $REGISTRY" "0"
assert "reviews my 列出审核" "$CLI reviews my --registry $REGISTRY" "0"
assert "notifications list 列出通知" "$CLI notifications list --registry $REGISTRY" "0"

# 8. 清理
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  清理"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

rm -rf "$TMPDIR" "$PUBLISH_DIR" /tmp/cli-test-repo /tmp/cli-test.zip
echo "  临时文件已清理"

# 9. 汇总
echo ""
echo "============================================================"
echo "  测试结果汇总"
echo "============================================================"
echo "  总计: $TOTAL"
echo -e "  ${GREEN}通过: $PASS${NC}"
echo -e "  ${RED}失败: $FAIL${NC}"
if [ "$FAIL" -eq 0 ]; then
  echo -e "  ${GREEN}✅ 全部通过!${NC}"
else
  echo -e "  ${RED}❌ 有 $FAIL 个测试失败${NC}"
  exit 1
fi
