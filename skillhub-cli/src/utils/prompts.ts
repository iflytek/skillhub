import { createInterface } from "readline";

export interface SelectItem {
  value: string;
  label: string;
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
