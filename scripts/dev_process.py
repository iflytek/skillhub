#!/usr/bin/env python3

import argparse
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional


def is_running(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def read_pid(pid_file: Path) -> Optional[int]:
    if not pid_file.exists():
        return None
    content = pid_file.read_text(encoding="utf-8").strip()
    if not content:
        return None
    try:
        return int(content)
    except ValueError:
        return None


def write_pid(pid_file: Path, pid: int) -> None:
    pid_file.parent.mkdir(parents=True, exist_ok=True)
    pid_file.write_text(f"{pid}\n", encoding="utf-8")


def start_process(args: argparse.Namespace) -> int:
    pid_file = Path(args.pid_file)
    log_file = Path(args.log_file)
    cwd = Path(args.cwd)
    existing_pid = read_pid(pid_file)
    if existing_pid and is_running(existing_pid):
        print(existing_pid)
        return 0

    pid_file.unlink(missing_ok=True)
    log_file.parent.mkdir(parents=True, exist_ok=True)

    command = list(args.command)
    if command and command[0] == "--":
        command = command[1:]

    with log_file.open("ab") as log_handle, open(os.devnull, "rb") as devnull:
        process = subprocess.Popen(
            command,
            cwd=cwd,
            stdin=devnull,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )

    time.sleep(0.2)
    if process.poll() is not None:
        pid_file.unlink(missing_ok=True)
        return process.returncode or 1

    write_pid(pid_file, process.pid)
    print(process.pid)
    return 0


def stop_process(args: argparse.Namespace) -> int:
    pid_file = Path(args.pid_file)
    pid = read_pid(pid_file)
    if not pid:
        return 0

    if not is_running(pid):
        pid_file.unlink(missing_ok=True)
        return 0

    os.kill(pid, signal.SIGTERM)
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        if not is_running(pid):
            pid_file.unlink(missing_ok=True)
            return 0
        time.sleep(0.2)

    os.kill(pid, signal.SIGKILL)
    pid_file.unlink(missing_ok=True)
    return 0


def status_process(args: argparse.Namespace) -> int:
    pid_file = Path(args.pid_file)
    pid = read_pid(pid_file)
    if not pid:
        return 1
    if not is_running(pid):
        pid_file.unlink(missing_ok=True)
        return 1
    print(pid)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Manage detached dev processes.")
    subparsers = parser.add_subparsers(dest="action", required=True)

    start_parser = subparsers.add_parser("start")
    start_parser.add_argument("--pid-file", required=True)
    start_parser.add_argument("--log-file", required=True)
    start_parser.add_argument("--cwd", required=True)
    start_parser.add_argument("command", nargs=argparse.REMAINDER)

    stop_parser = subparsers.add_parser("stop")
    stop_parser.add_argument("--pid-file", required=True)
    stop_parser.add_argument("--timeout", type=float, default=10.0)

    status_parser = subparsers.add_parser("status")
    status_parser.add_argument("--pid-file", required=True)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if args.action == "start":
        if not args.command:
            parser.error("start requires a command")
        return start_process(args)
    if args.action == "stop":
        return stop_process(args)
    if args.action == "status":
        return status_process(args)
    return 1


if __name__ == "__main__":
    sys.exit(main())
