import { $ } from "npm:zx";

/**
 * Create a parentless commit with an empty tree to carry reward/statistic tags.
 *
 * Tags pointing at product commits become the nearest tag in `git describe`,
 * so downstream builds would report versions like `reward-42-3-gabc1234`
 * instead of the latest release tag. Tags on a detached commit are never
 * reachable from any branch and keep release tags authoritative.
 */
export async function createDetachedCommit(message: string) {
  const emptyTree = (await $`git hash-object -w -t tree /dev/null`).stdout.trim();

  return (await $`git commit-tree ${emptyTree} -m ${message}`).stdout.trim();
}
