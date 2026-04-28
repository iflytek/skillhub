#!/usr/bin/env python3
"""
Ralph - 自主 AI Agent 循环执行器（含 Validator）
"""

import json
import sys
import subprocess
import time
import os
import argparse
import threading
from pathlib import Path

import dashboard

# 配置
MAX_ITERATIONS = 100
TIMEOUT_SECONDS = 30 * 60

# Agent 选择：支持 "claude"、"codex"、"opencode"
# 用法：python ralph.py [agent] [model]
# 示例：
#   python ralph.py claude claude-sonnet-4-6
#   python ralph.py codex gpt-4o
#   python ralph.py opencode deepseek-chat
AGENT = sys.argv[1] if len(sys.argv) > 1 else "codex"
MODEL = sys.argv[2] if len(sys.argv) > 2 else None


def build_cmd(prompt: str) -> tuple[list[str], str]:
    """
    根据 AGENT 配置构建命令。
    返回 (cmd, stdin_mode) 元组：
      - cmd: 子进程命令列表
      - stdin_mode: "pipe" 表示通过 stdin 传 prompt；"arg" 表示通过命令行参数传递
    """
    if AGENT == "claude":
        cmd = ["claude", "--print", "--dangerously-skip-permissions"]
        if MODEL:
            cmd.extend(["--model", MODEL])
        # "--" 确保 prompt 不会被误解析为 flag（即使 prompt 以 "-" 开头）
        cmd.extend(["--", prompt])
        return cmd, "arg"

    if AGENT == "codex":
        cmd = ["codex", "exec", "--dangerously-bypass-approvals-and-sandbox"]
        if MODEL:
            cmd.extend(["--model", MODEL])
        cmd.append(prompt)
        return cmd, "arg"

    if AGENT == "opencode":
        cmd = ["opencode", "run"]
        if RALPH_AGENT_FILE.exists():
            cmd.extend(["--agent", str(RALPH_AGENT_FILE)])
        else:
            cmd.extend(["--agent", "build"])
        if MODEL:
            cmd.extend(["--model", MODEL])
        # opencode 的 run 子命令从 stdin 读取 prompt
        return cmd, "pipe"

    raise ValueError(f"不支持的 Agent 类型: {AGENT}")



def build_process_cmd(prompt: str) -> tuple[list[str], str]:
    """
    构建子进程命令。
    prompt 通过 stdin 传递（避免 heredoc/特殊字符被 shell 解析破坏）。
    返回 (cmd, stdin_data) 元组，stdin_data 为 None 时表示不需要通过 stdin 传数据。
    """
    cmd, _ = build_cmd(prompt)
    if AGENT == "claude":
        # claude --print 天然安全地通过命令行参数接收 prompt
        return cmd, None
    if AGENT == "opencode":
        # opencode run 从 stdin 读取 prompt
        return cmd, prompt
    # codex / 其他：移除 heredoc 风险的 prompt 追加，
    # 改由 agent 自己读取指令文件（CLAUDE.md / VALIDATOR.md）
    # 即 cmd 只保留 agent + 选项，不含 prompt
    return cmd, None

# 目录配置
SCRIPT_DIR = Path(__file__).parent.resolve()
PROJECT_ROOT = SCRIPT_DIR.parent.parent
CLAUDE_INSTRUCTION_FILE = SCRIPT_DIR / "CLAUDE.md"
VALIDATOR_INSTRUCTION_FILE = SCRIPT_DIR / "VALIDATOR.md"
PRD_FILE = SCRIPT_DIR / "prd.json"
RALPH_AGENT_FILE = SCRIPT_DIR / "ralph-agent.json"


def run_developer(iteration: int) -> bool:
    """
    调用开发 Agent
    返回值：是否超时
    """
    print(f"\n{'='*64}\n  迭代 {iteration}/{MAX_ITERATIONS}\n{'='*64}")

    if not CLAUDE_INSTRUCTION_FILE.exists():
        print(f"❌ 错误: {CLAUDE_INSTRUCTION_FILE} 不存在")
        return False

    prompt = CLAUDE_INSTRUCTION_FILE.read_text()
    cmd, stdin_data = build_process_cmd(prompt)

    try:
        process = subprocess.Popen(
            cmd,
            cwd=str(PROJECT_ROOT),
            stdin=subprocess.PIPE if stdin_data is not None else None,
        )

        if stdin_data is not None:
            def write_stdin():
                if process.stdin:
                    process.stdin.write(stdin_data.encode("utf-8"))
                    process.stdin.close()
            t = threading.Thread(target=write_stdin)
            t.start()

        start_time = time.time()

        while True:
            ret_code = process.poll()
            if ret_code is not None:
                print("\n✓ 开发迭代完成")
                return False

            elapsed_time = time.time() - start_time
            if elapsed_time > TIMEOUT_SECONDS:
                print(f"\n⚠️  开发 Agent 超时! 已运行 {int(elapsed_time)} 秒")
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                print("   进程已终止，将在下一次迭代重试")
                return True

            time.sleep(60)

    except Exception as e:
        print(f"\n❌ 开发 Agent 错误: {e}")
        return False

def run_validator(iteration: int) -> None:
    """
    调用 Validator Agent，由其自行读取 progress.txt 中最后一个 story 进行验证
    """
    print(f"\n{'='*64}\n  验证迭代 {iteration} - Validator 开始工作\n{'='*64}")

    if not VALIDATOR_INSTRUCTION_FILE.exists():
        print(f"⚠️  警告: {VALIDATOR_INSTRUCTION_FILE} 不存在，跳过验证")
        return

    prompt = VALIDATOR_INSTRUCTION_FILE.read_text()
    cmd, stdin_data = build_process_cmd(prompt)

    try:
        process = subprocess.Popen(
            cmd,
            cwd=str(PROJECT_ROOT),
            stdin=subprocess.PIPE if stdin_data is not None else None,
        )

        if stdin_data is not None:
            def write_stdin():
                if process.stdin:
                    process.stdin.write(stdin_data.encode("utf-8"))
                    process.stdin.close()
            t = threading.Thread(target=write_stdin)
            t.start()

        start_time = time.time()

        while True:
            ret_code = process.poll()
            if ret_code is not None:
                print("\n✓ 验证完成")
                return

            elapsed_time = time.time() - start_time
            if elapsed_time > TIMEOUT_SECONDS * 2:
                print(f"\n⚠️  Validator 超时! 已运行 {int(elapsed_time)} 秒")
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                print("   Validator 进程已终止，跳过本次验证")
                return

            time.sleep(60)

    except Exception as e:
        print(f"\n❌ Validator 错误: {e}")
def get_current_story_id() -> str | None:
    """返回 prd.json 中第一个 passes=False 且 blocked=False 的 story ID"""
    try:
        prd = json.loads(PRD_FILE.read_text())
        for story in prd.get("userStories", []):
            if not story.get("passes", False) and not story.get("blocked", False):
                return story.get("id")
    except Exception:
        pass
    return None


def all_stories_resolved() -> bool:
    """
    检查 prd.json，判断是否所有 story 都已完成或被 blocked
    """
    try:
        prd = json.loads(PRD_FILE.read_text())
        stories = prd.get("userStories", [])
        for story in stories:
            passes = story.get("passes", False)
            blocked = story.get("blocked", False)
            if not passes and not blocked:
                return False
        return True
    except Exception as e:
        print(f"⚠️  读取 prd.json 失败: {e}")
        return False


def format_duration(seconds: float) -> str:
    """将秒数格式化为易读的时间字符串"""
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    if h > 0:
        return f"{h}小时 {m}分钟 {s}秒"
    elif m > 0:
        return f"{m}分钟 {s}秒"
    else:
        return f"{s}秒"


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="Ralph - 自主 AI Agent 循环执行器")
    parser.add_argument("--remote", action="store_true", help="允许远程访问 Dashboard (绑定 0.0.0.0)")
    parser.add_argument("agent", nargs="?", help="Agent 类型: claude, codex, opencode")
    parser.add_argument("model", nargs="?", help="模型名称")
    args = parser.parse_args()

    global AGENT, MODEL
    AGENT = args.agent if args.agent else "codex"
    MODEL = args.model

    host = "0.0.0.0" if args.remote else "127.0.0.1"

    agent_info = f"Agent: {AGENT}"
    if MODEL:
        agent_info += f", Model: {MODEL}"
    print(f"启动 Ralph - 最大迭代次数: {MAX_ITERATIONS}, {agent_info}")
    total_start_time = time.time()

    dashboard.start(max_iterations=MAX_ITERATIONS, host=host)

    for i in range(1, MAX_ITERATIONS + 1):
        try:
            # 第一步：调用开发 Agent
            current_story = get_current_story_id()
            dashboard.set_state(iteration=i, phase="developing", current_story=current_story)
            timed_out = run_developer(i)

            # 开发 Agent 超时，跳过 Validator，直接进入下一次迭代重试
            if timed_out:
                dashboard.set_state(phase="idle")
                print("⏭️  开发 Agent 超时，跳过验证，下一次迭代继续开发...")
                time.sleep(2)
                continue

            # 第二步：开发 Agent 正常完成，调用 Validator Agent
            dashboard.set_state(phase="validating")
            run_validator(i)

            # 第三步：检查是否全部完成（passes:true 或 blocked:true）
            dashboard.set_state(phase="idle")
            if all_stories_resolved():
                dashboard.set_state(phase="done")
                elapsed = time.time() - total_start_time
                print("✅ 所有任务已完成或已标记为 BLOCKED!")
                print(f"⏱️  总运行时间: {format_duration(elapsed)}")
                sys.exit(0)

        except KeyboardInterrupt:
            elapsed = time.time() - total_start_time
            print(f"\n\n⚠️  用户中断")
            print(f"⏱️  总运行时间: {format_duration(elapsed)}")
            sys.exit(130)

    elapsed = time.time() - total_start_time
    print(f"\n已达到最大迭代次数 ({MAX_ITERATIONS})")
    print(f"⏱️  总运行时间: {format_duration(elapsed)}")
    sys.exit(1)


if __name__ == "__main__":
    main()
