import chalk from "chalk";

export function log(msg: string) {
  console.log(msg);
}

export function success(msg: string) {
  console.log(chalk.green(msg));
}

export function error(msg: string) {
  console.error(chalk.red(msg));
}

export function warn(msg: string) {
  console.warn(chalk.yellow(msg));
}

export function info(msg: string) {
  console.log(chalk.cyan(msg));
}

export function dim(msg: string) {
  console.log(chalk.dim(msg));
}
