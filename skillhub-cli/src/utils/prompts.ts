import { createInterface } from "readline";

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
    console.log("(输入数字选择，逗号分隔，如 1,3,5，输入 a 全选，n 取消)");
    console.log("");

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const num = (i + 1).toString().padStart(2, " ");
      console.log(`  [${num}] ${item.label}`);
    }
    console.log("");

    const rl = createInterface({
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
    console.log("(输入数字选择，逗号分隔，如 1,3,5，输入 a 全选，n 取消)");
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

    const rl = createInterface({
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
