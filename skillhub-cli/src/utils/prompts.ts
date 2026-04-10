import * as readline from "readline";
import { Writable } from "stream";

const silentOutput = new Writable({
  write(_chunk, _encoding, callback) {
    callback();
  },
});

const S_STEP_ACTIVE = "\x1b[32m◆\x1b[0m";
const S_STEP_CANCEL = "\x1b[31m■\x1b[0m";
const S_STEP_SUBMIT = "\x1b[32m◇\x1b[0m";
const S_RADIO_ACTIVE = "\x1b[32m●\x1b[0m";
const S_RADIO_INACTIVE = "\x1b[2m○\x1b[0m";
const S_BULLET = "\x1b[32m•\x1b[0m";
const S_BAR = "\x1b[2m│\x1b[0m";
const S_BAR_H = "\x1b[2m─\x1b[0m";
const S_ESC = "\x1b[";
const S_BOLD = "\x1b[1m";
const S_DIM = "\x1b[2m";
const S_UNDERLINE = "\x1b[4m";
const S_INVERSE = "\x1b[7m";
const S_RESET = "\x1b[0m";
const S_CYAN = "\x1b[36m";
const S_GREEN = "\x1b[32m";
const S_YELLOW = "\x1b[33m";
const S_RED = "\x1b[31m";

const bold = (s: string) => `${S_BOLD}${s}${S_RESET}`;
const dim = (s: string) => `${S_DIM}${s}${S_RESET}`;
const cyan = (s: string) => `${S_CYAN}${s}${S_RESET}`;
const green = (s: string) => `${S_GREEN}${s}${S_RESET}`;
const yellow = (s: string) => `${S_YELLOW}${s}${S_RESET}`;
const red = (s: string) => `${S_RED}${s}${S_RESET}`;

function moveUp(n: number): string {
  return `${S_ESC}${n}A`;
}

function clearLine(): string {
  return `${S_ESC}2K`;
}

function clearRender(lastHeight: number): void {
  if (lastHeight > 0) {
    process.stdout.write(moveUp(lastHeight));
    for (let i = 0; i < lastHeight; i++) {
      process.stdout.write(clearLine() + (i < lastHeight - 1 ? moveUp(1) + "\x1b[G" : "\n"));
    }
    process.stdout.write(moveUp(lastHeight));
  }
}

export interface SelectItem {
  value: string;
  label: string;
  hint?: string;
}

export interface SelectSection {
  title: string;
  items: SelectItem[];
  locked?: boolean;
}

export async function multiSelect(
  message: string,
  items: SelectItem[]
): Promise<string[] | null> {
  return new Promise((resolve) => {
    console.log("");
    console.log(message);
    console.log(dim("(输入数字选择，逗号分隔，如 1,3,5，输入 a 全选，n 取消)"));
    console.log("");

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const num = (i + 1).toString().padStart(2, " ");
      console.log(`  [${num}] ${item.label}`);
    }
    console.log("");

    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });

    rl.question("请选择: ", (answer) => {
      rl.close();
      console.log("");

      const trimmed = answer.trim().toLowerCase();

      if (trimmed === "n" || trimmed === "no") {
        resolve(null);
        return;
      }

      if (trimmed === "a" || trimmed === "all") {
        resolve(items.map((i) => i.value));
        return;
      }

      const selected: string[] = [];
      const parts = trimmed.split(",").map((s) => s.trim());

      for (const part of parts) {
        const num = parseInt(part, 10);
        if (!isNaN(num) && num >= 1 && num <= items.length) {
          selected.push(items[num - 1].value);
        }
      }

      if (selected.length === 0) {
        console.log("未选择任何 skill");
        resolve(null);
        return;
      }

      resolve(selected);
    });
  });
}

export async function sectionMultiSelect(
  message: string,
  sections: SelectSection[]
): Promise<string[] | null> {
  return new Promise((resolve) => {
    console.log("");
    console.log(message);
    console.log(dim("(输入数字选择，逗号分隔，如 1,3,5，输入 a 全选，n 取消)"));
    console.log("");

    let selectableIdx = 0;
    for (const section of sections) {
      if (section.locked) {
        console.log(`  ${section.title} ${"[always included]"}`);
        for (const item of section.items) {
          console.log(`    ● ${item.label}${item.hint ? ` ${item.hint}` : ""}`);
        }
      } else {
        console.log(`  ${section.title}`);
        for (const item of section.items) {
          selectableIdx++;
          console.log(`    [${selectableIdx.toString().padStart(2, " ")}] ${item.label}${item.hint ? ` ${item.hint}` : ""}`);
        }
      }
    }
    console.log("");

    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });

    rl.question("请选择: ", (answer) => {
      rl.close();
      console.log("");

      const trimmed = answer.trim().toLowerCase();

      if (trimmed === "n" || trimmed === "no") {
        resolve(null);
        return;
      }

      const selected: string[] = [];
      const parts = trimmed.split(",").map((s) => s.trim());

      selectableIdx = 0;
      for (const section of sections) {
        if (section.locked) {
          selected.push(...section.items.map((i) => i.value));
        } else {
          for (const item of section.items) {
            selectableIdx++;
            if (parts.includes(selectableIdx.toString())) {
              selected.push(item.value);
            }
          }
        }
      }

      if (selected.length === 0) {
        console.log("未选择任何项");
        resolve(null);
        return;
      }

      resolve(selected);
    });
  });
}

export const cancelSymbol = Symbol("cancel");

export interface InteractiveSelectOptions {
  message: string;
  items: SelectItem[];
  initialSelected?: string[];
  lockedSection?: SelectSection;
  hint?: string;
}

export async function interactiveMultiSelect(
  options: InteractiveSelectOptions
): Promise<string[] | typeof cancelSymbol> {
  const {
    message,
    items,
    initialSelected = [],
    lockedSection,
    hint = "↑↓ move, space select, enter confirm",
  } = options;

  const selectableItems = items;

  return new Promise((resolve) => {
    const rl = readline.createInterface({
      input: process.stdin,
      output: silentOutput,
      terminal: false,
    });

    if (process.stdin.isTTY) {
      process.stdin.setRawMode(true);
    }
    readline.emitKeypressEvents(process.stdin, rl);

    let query = "";
    let cursor = 0;
    const selected = new Set<string>(initialSelected);
    let lastRenderHeight = 0;

    const lockedValues = lockedSection ? lockedSection.items.map((i) => i.value) : [];

    const filter = (item: SelectItem, q: string): boolean => {
      if (!q) return true;
      const lowerQ = q.toLowerCase();
      return (
        item.label.toLowerCase().includes(lowerQ) ||
        item.value.toLowerCase().includes(lowerQ)
      );
    };

    const getFiltered = (): SelectItem[] => {
      return selectableItems.filter((item) => filter(item, query));
    };

    const render = (state: "active" | "submit" | "cancel" = "active"): void => {
      clearRender(lastRenderHeight);

      const lines: string[] = [];
      const filtered = getFiltered();

      const icon =
        state === "active" ? S_STEP_ACTIVE : state === "cancel" ? S_STEP_CANCEL : S_STEP_SUBMIT;
      lines.push(`${icon}  ${bold(message)}`);

      if (state === "active") {
        if (lockedSection && lockedSection.items.length > 0) {
          lines.push(`${S_BAR}`);
          const lockedTitle = `${bold(lockedSection.title)} ${dim("── always included")}`;
          lines.push(`${S_BAR}  ${S_BAR_H}${S_BAR_H} ${lockedTitle} ${S_BAR_H.repeat(12)}`);
          for (const item of lockedSection.items) {
            lines.push(`${S_BAR}    ${S_BULLET} ${bold(item.label)}`);
          }
          lines.push(`${S_BAR}`);
          lines.push(
            `${S_BAR}  ${S_BAR_H}${S_BAR_H} ${bold("Additional agents")} ${S_BAR_H.repeat(29)}`
          );
        }

        const searchLine = `${S_BAR}  ${dim("Search:")} ${query}${S_INVERSE} ${S_RESET}`;
        lines.push(searchLine);

        lines.push(`${S_BAR}  ${dim(hint)}`);
        lines.push(`${S_BAR}`);

        const maxVisible = 10;
        const visibleStart = Math.max(
          0,
          Math.min(cursor - Math.floor(maxVisible / 2), filtered.length - maxVisible)
        );
        const visibleEnd = Math.min(filtered.length, visibleStart + maxVisible);
        const visibleItems = filtered.slice(visibleStart, visibleEnd);

        if (filtered.length === 0) {
          lines.push(`${S_BAR}  ${dim("No matches found")}`);
        } else {
          for (let i = 0; i < visibleItems.length; i++) {
            const item = visibleItems[i]!;
            const actualIndex = visibleStart + i;
            const isSelected = selected.has(item.value);
            const isCursor = actualIndex === cursor;

            const radio = isSelected ? S_RADIO_ACTIVE : S_RADIO_INACTIVE;
            const label = isCursor ? `${S_UNDERLINE}${item.label}${S_RESET}` : item.label;
            const hintStr = item.hint ? dim(` (${item.hint})`) : "";

            const prefix = isCursor ? `${cyan("❯")}` : " ";
            lines.push(`${S_BAR} ${prefix} ${radio} ${label}${hintStr}`);
          }

          const hiddenBefore = visibleStart;
          const hiddenAfter = filtered.length - visibleEnd;
          if (hiddenBefore > 0 || hiddenAfter > 0) {
            const parts: string[] = [];
            if (hiddenBefore > 0) parts.push(`↑ ${hiddenBefore} more`);
            if (hiddenAfter > 0) parts.push(`↓ ${hiddenAfter} more`);
            lines.push(`${S_BAR}  ${dim(parts.join("  "))}`);
          }
        }

        lines.push(`${S_BAR}`);
        const allSelectedLabels = [
          ...(lockedSection ? lockedSection.items.map((i) => i.label) : []),
          ...items.filter((item) => selected.has(item.value)).map((item) => item.label),
        ];
        if (allSelectedLabels.length === 0) {
          lines.push(`${S_BAR}  ${dim("Selected: (none)")}`);
        } else {
          const summary =
            allSelectedLabels.length <= 3
              ? allSelectedLabels.join(", ")
              : `${allSelectedLabels.slice(0, 3).join(", ")} +${allSelectedLabels.length - 3} more`;
          lines.push(`${S_BAR}  ${green("Selected:")} ${summary}`);
        }

        lines.push(`${dim("└")}`);
      } else if (state === "submit") {
        const allSelectedLabels = [
          ...(lockedSection ? lockedSection.items.map((i) => i.label) : []),
          ...items.filter((item) => selected.has(item.value)).map((item) => item.label),
        ];
        lines.push(`${S_BAR}  ${dim(allSelectedLabels.join(", "))}`);
      } else if (state === "cancel") {
        lines.push(`${S_BAR}  ${red("Cancelled")}`);
      }

      process.stdout.write(lines.join("\n") + "\n");
      lastRenderHeight = lines.length;
    };

    const cleanup = (): void => {
      process.stdin.removeListener("keypress", keypressHandler);
      if (process.stdin.isTTY) {
        process.stdin.setRawMode(false);
      }
      rl.close();
    };

    const submit = (): void => {
      render("submit");
      cleanup();
      process.stdout.write("\n");
      resolve([...lockedValues, ...Array.from(selected)]);
    };

    const cancel = (): void => {
      render("cancel");
      cleanup();
      process.stdout.write("\n");
      resolve(cancelSymbol);
    };

    const keypressHandler = (_str: string, key: readline.Key): void => {
      if (!key) return;

      const filtered = getFiltered();

      if (key.name === "return") {
        submit();
        return;
      }

      if (key.name === "escape" || (key.ctrl && key.name === "c")) {
        cancel();
        return;
      }

      if (key.name === "up") {
        cursor = Math.max(0, cursor - 1);
        render();
        return;
      }

      if (key.name === "down") {
        cursor = Math.min(filtered.length - 1, cursor + 1);
        render();
        return;
      }

      if (key.name === "space") {
        const item = filtered[cursor];
        if (item) {
          if (selected.has(item.value)) {
            selected.delete(item.value);
          } else {
            selected.add(item.value);
          }
        }
        render();
        return;
      }

      if (key.name === "backspace") {
        query = query.slice(0, -1);
        cursor = 0;
        render();
        return;
      }

      if (key.sequence && !key.ctrl && !key.meta && key.sequence.length === 1) {
        query += key.sequence;
        cursor = 0;
        render();
        return;
      }
    };

    process.stdin.on("keypress", keypressHandler);

    render();
  });
}

export async function interactiveSelect(
  message: string,
  items: SelectItem[]
): Promise<string | typeof cancelSymbol> {
  const options: InteractiveSelectOptions = {
    message,
    items,
  };

  const result = await interactiveMultiSelect(options);

  if (result === cancelSymbol) {
    return cancelSymbol;
  }

  if (result.length === 0) {
    return cancelSymbol;
  }

  return result[0];
}
