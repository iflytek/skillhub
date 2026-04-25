import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { requireToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";

export interface Notification {
  id: number;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export function registerNotifications(program: Command) {
  const cmd = program
    .command("notifications")
    .alias("notif")
    .description("Manage notifications");

  cmd
    .command("list")
    .description("List notifications")
    .option("--unread", "Show unread only")
    .action(async (opts: { unread?: boolean }) => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        const notifs = await client.get<Notification[]>("/api/v1/notifications");
        const filtered = opts.unread ? notifs.filter((n) => !n.read) : notifs;
        if (filtered.length === 0) {
          console.log(opts.unread ? "No unread notifications." : "No notifications.");
          return;
        }
        for (const n of filtered) {
          info(`${n.read ? "✓" : "○"} ${n.title}`);
          dim(`  ${n.message} · ${n.createdAt}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });

  cmd
    .command("read <id>")
    .description("Mark notification as read")
    .action(async (id: string) => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        await client.put(`/api/v1/notifications/${id}/read`);
        success(`Marked notification ${id} as read`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });

  cmd
    .command("read-all")
    .description("Mark all notifications as read")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        await client.put("/api/v1/notifications/read-all");
        success("All notifications marked as read");
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
