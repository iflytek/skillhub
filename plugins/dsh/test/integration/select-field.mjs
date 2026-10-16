import assert from 'node:assert/strict';
const labels = { project: '当前项目', user: '当前用户', all: '全部范围', auto: '自动检测', show: '显示', hide: '隐藏', newest: '最新发布', downloads: '下载量', rating: '评分' };
export async function selectValue(trigger, value) {
  assert(labels[value], `Unknown Select fixture value: ${value}`);
  await trigger.click();
  await trigger.page().getByRole('option', { name: labels[value], exact: true }).click();
  assert.equal((await trigger.innerText()).trim(), labels[value]);
}
export async function selectedValue(trigger) {
  const label = (await trigger.innerText()).trim();
  const entry = Object.entries(labels).find(([, text]) => text === label);
  assert(entry, `Unexpected Select label: ${label}`);
  return entry[0];
}
