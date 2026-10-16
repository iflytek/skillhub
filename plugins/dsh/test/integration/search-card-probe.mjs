import { revealReceipt } from './receipt-disclosure.mjs';
import { selectValue, selectedValue } from './select-field.mjs';
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const entry = process.env.DSH_PROBE_URL;
const output = process.env.DSH_PROBE_OUTPUT;
const fixture = { searchFixtureTitle: process.env.DSH_SEARCH_FIXTURE_TITLE };
if (!entry || !output || !fixture.searchFixtureTitle) throw new Error('Set DSH_PROBE_URL, DSH_PROBE_OUTPUT and DSH_SEARCH_FIXTURE_TITLE for the isolated Profile');
const browser = await chromium.launch({ headless: true,
  ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}),
});
// Host tool-group selectors use English independently of the operating system.
const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'en-US'});const errors=[];
let installSubmissions = 0;
page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/startInstall')) installSubmissions++; });
let installationVerified = false;
let reloadInstallationVerified = false;
let plazaInstallationVerified = false;
let closedDuringInstallation = false;
page.on('pageerror',e=>errors.push(e.message.replace(/token=[^\s&]+/g,'token=[redacted]')));
try {
 await page.goto(entry,{waitUntil:'domcontentloaded'});
 const welcome=page.getByRole('button',{name:'Continue',exact:true});
 const configureLater=page.getByRole('button',{name:'Configure later',exact:true});
 await welcome.or(configureLater).first().waitFor();
 if(await welcome.isVisible()) await welcome.click();
 await configureLater.click();
 if(!await page.getByText(fixture.searchFixtureTitle,{exact:true}).waitFor({timeout:3000}).then(()=>true,()=>false)) {
  const workspace=process.env.DSH_SEARCH_FIXTURE_WORKSPACE ?? 'project';
  await page.getByText(workspace,{exact:true}).click();
 }
 await page.getByText(fixture.searchFixtureTitle,{exact:true}).click();
 const card=page.getByRole('region',{name:'SkillHub 搜索结果'});
 if(!await card.isVisible()) { const process=page.locator('button[data-turn-process="1"]'); await process.waitFor(); if(await process.getAttribute('aria-expanded')==='false') await process.click(); }
 if(!await card.isVisible()) { const tools=page.getByText('Called tools',{exact:true}); await tools.waitFor(); await tools.click(); }
 await card.waitFor();
 const cardControl = await card.evaluate(element => {
  const button = element.querySelector('button');
  return { font: getComputedStyle(element).fontFamily, buttonFont: getComputedStyle(button).fontFamily,
   buttonHeight: button.getBoundingClientRect().height,
   cardHeight: button.closest('.astron-sh-conversation-skill').getBoundingClientRect().height };
 });
 assert.equal(cardControl.font, cardControl.buttonFont);
 assert.ok(cardControl.buttonHeight >= 40);
 assert.ok(cardControl.cardHeight >= 100 && cardControl.cardHeight <= 132);

 if(!(await card.innerText()).includes('document-helper')) throw new Error('Missing search item');
 if (process.env.DSH_PLAZA_INSTALL_PROBE === '1') {
  if (process.env.DSH_INSTALL_PROBE !== '1') throw new Error('Plaza probe requires installation probe mode');
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await selectValue(plaza.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
  await plaza.getByRole('button', { name: '安装到当前项目', exact: true }).click();
  await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
  await revealReceipt(plaza.getByText(/技能文件安装完成。|此版本已安装。/));
  if (installSubmissions !== 1) throw new Error('Expected one plaza installation submission');
  await page.screenshot({ path: `${output}/plaza-install.png` });
  await plaza.getByRole('button', { name: '返回会话', exact: true }).click();
  if (!await card.isVisible()) { const process = page.locator('button[data-turn-process="1"]'); await process.waitFor(); if (await process.getAttribute('aria-expanded') === 'false') await process.click(); }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  await card.waitFor();
  plazaInstallationVerified = true;
 }
 const details=card.getByRole('button',{name:'查看技能详情',exact:true});
 const opening=page.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/openConversationDetail')).catch(error=>error);
 const acknowledgment=page.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/acknowledgeConversationDetail')).catch(error=>error);
 await details.click();
 const drawer=page.getByRole('dialog',{name:'技能详情',exact:true});
 await drawer.getByRole('heading',{name:'Document helper',exact:true}).waitFor();
 assert.equal(await selectedValue(drawer.getByRole('combobox', { name: '安装范围', exact: true })), 'user');
 await selectValue(drawer.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
 const closeButton = drawer.getByRole('button', { name: '关闭技能详情', exact: true });
 assert.equal(await closeButton.evaluate(element => element === document.activeElement), true, 'Opening must focus Close');
 await details.evaluate(element => element.focus());
 assert.equal(await drawer.evaluate(element => element.contains(document.activeElement)), true, 'Background card must be inert');
 const acknowledgmentResponse=await acknowledgment;
 if(acknowledgmentResponse instanceof Error) throw acknowledgmentResponse;
 const openingResponse=await opening;
 if(openingResponse instanceof Error) throw openingResponse;
 const opened=await openingResponse.json();
 const fixedReference=opened.result?.value?.selectionRef;
 if(!fixedReference || fixedReference===openingResponse.request().postDataJSON().payload.args.selectionRef) throw new Error('Detail did not issue a new fixed reference');
 if(acknowledgmentResponse.request().postDataJSON().payload.args.selectionRef!==fixedReference) throw new Error('Client did not acknowledge the displayed fixed reference');
 const confirmed=await acknowledgmentResponse.json();
 if(confirmed.result?.ok!==true || confirmed.result.value!==true) throw new Error('Scoped display acknowledgment failed');
 await drawer.getByRole('tab',{name:'版本',exact:true}).click();
 await drawer.getByText('改进文档提取',{exact:true}).waitFor();
 await drawer.getByRole('tab',{name:'评论',exact:true}).click();
 await drawer.getByText('适合整理长文档',{exact:true}).waitFor();
 await drawer.getByRole('tab',{name:'详情',exact:true}).click();
 await drawer.getByRole('heading',{name:'文档助手',exact:true}).waitFor();
 const tabs = drawer.getByRole('tablist', { name: '技能详情', exact: true });
 await tabs.getByRole('tab', { name: '详情', exact: true }).focus();
 await page.keyboard.press('ArrowRight');
 assert.equal(await tabs.getByRole('tab', { name: '版本', exact: true }).getAttribute('aria-selected'), 'true');
 await page.keyboard.press('End');
 assert.equal(await tabs.getByRole('tab', { name: '评论', exact: true }).getAttribute('aria-selected'), 'true');
 await page.keyboard.press('Home');
 await page.keyboard.press('Tab');
 assert.equal(await drawer.getByRole('tabpanel').evaluate(element => element === document.activeElement), true);
 const tabAssociations = await tabs.getByRole('tab').evaluateAll(elements => elements.every(element => {
  const panel = document.getElementById(element.getAttribute('aria-controls'));
  return panel?.getAttribute('aria-labelledby') === element.id;
 }));
 assert.equal(tabAssociations, true);
 await page.setViewportSize({ width: 390, height: 844 });
 const mobile = await drawer.evaluate(element => ({ width: element.getBoundingClientRect().width,
  contentWidth: element.clientWidth, scrollWidth: element.scrollWidth }));
 assert.ok(mobile.width <= 390 && mobile.scrollWidth <= mobile.contentWidth, 'Mobile drawer must not overflow horizontally');
 await page.screenshot({ path: `${output}/conversation-detail-mobile.png` });
 await page.emulateMedia({ colorScheme: 'dark' });
 await page.waitForTimeout(250);
 const darkControls = await drawer.evaluate(element => {
  const button = element.querySelector('button');
  const style = getComputedStyle(element);
  const control = getComputedStyle(button);
  return { colorScheme: style.colorScheme, foreground: style.color, background: style.backgroundColor,
   font: style.fontFamily, buttonFont: control.fontFamily, buttonColor: control.color,
   buttonHeight: button.getBoundingClientRect().height, width: element.clientWidth, scrollWidth: element.scrollWidth };
 });
 assert.equal(darkControls.font, darkControls.buttonFont);
 assert.equal(darkControls.foreground, darkControls.buttonColor);
 assert.ok(darkControls.buttonHeight >= 40);
 assert.ok(darkControls.scrollWidth <= darkControls.width);
 await page.screenshot({ path: `${output}/conversation-detail-dark-mobile.png` });
 await page.emulateMedia({ colorScheme: 'light' });
 await page.setViewportSize({ width: 1440, height: 1000 });
 assert.equal(installSubmissions, plazaInstallationVerified ? 1 : 0, 'Browsing search results and detail must not submit installation');
 if (process.env.DSH_INSTALL_PROBE === '1') {
  if (!plazaInstallationVerified) {
   await drawer.getByRole('button', { name: '安装到当前项目', exact: true }).click();
   await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
  }
  if (process.env.DSH_CLOSE_DURING_INSTALL === '1') {
   await drawer.getByText('正在安装，请稍候…', { exact: true }).waitFor();
   await closeButton.click();
   await drawer.waitFor({ state: 'detached' });
   assert.equal(await details.evaluate(element => element === document.activeElement), true);
   await details.click();
   await selectValue(drawer.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
   await drawer.getByText('正在安装，请稍候…', { exact: true }).waitFor();
   assert.equal(installSubmissions, 1, 'Reopening an active installation must not resubmit');
   await page.screenshot({ path: `${output}/conversation-install-reopened-pending.png` });
   closedDuringInstallation = true;
  }
  await revealReceipt(drawer.getByText(/技能文件安装完成。|此版本已安装。/));
  await drawer.getByText(/^(上次核验时，DSH 已发现|尚未确认 DSH 发现)此操作对应的技能内容。$/).waitFor();
  if (installSubmissions !== 1) throw new Error('Expected one explicit installation submission');
  installationVerified = true;
 }
 await page.screenshot({path:`${output}/conversation-detail.png`});
 await page.keyboard.press('Escape');
 await drawer.waitFor({state:'detached'});
 if(!await details.evaluate(element=>element===document.activeElement)) throw new Error('Drawer did not restore card focus');
 await details.click();
 await drawer.getByRole('heading',{name:'Document helper',exact:true}).waitFor();
 assert.equal(await selectedValue(drawer.getByRole('combobox', { name: '安装范围', exact: true })), 'user');
 await selectValue(drawer.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
 if (installationVerified) {
  await revealReceipt(drawer.getByText(/技能文件安装完成。|此版本已安装。/));
  if (installSubmissions !== 1) throw new Error('Reopening detail replayed installation');
 }
 await drawer.getByRole('button',{name:'关闭技能详情',exact:true}).click();
 await drawer.waitFor({state:'detached'});
 await page.reload({waitUntil:'domcontentloaded'});
 const later=page.getByRole('button',{name:'Configure later',exact:true});
 if(await later.isVisible()) await later.click();
 if(!await card.isVisible()) { const process=page.locator('button[data-turn-process="1"]'); await process.waitFor(); if(await process.getAttribute('aria-expanded')==='false') await process.click(); }
 if(!await card.isVisible()) { const tools=page.getByText('Called tools',{exact:true}); await tools.waitFor(); await tools.click(); }
 await card.waitFor();
 await page.screenshot({path:`${output}/search-card.png`});
 if (installationVerified) {
  const refreshed = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/getOperation'));
  await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
  await selectValue(drawer.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
  await refreshed;
  await revealReceipt(drawer.getByText(/技能文件安装完成。|此版本已安装。/));
  if (installSubmissions !== 1) throw new Error('Browser reload replayed installation');
  await page.screenshot({ path: `${output}/conversation-install-reloaded.png` });
  await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  reloadInstallationVerified = true;
 }
 let staleHistoryRejected=false;
 if(process.env.DSH_STALE_FIXTURE_TITLE) {
  await page.getByText(process.env.DSH_STALE_FIXTURE_TITLE,{exact:true}).click();
  if(!await card.isVisible()) { const process=page.locator('button[data-turn-process="1"]'); await process.waitFor(); if(await process.getAttribute('aria-expanded')==='false') await process.click(); }
  if(!await card.isVisible()) await page.getByText('Called tools',{exact:true}).click();
  await card.getByRole('button',{name:'查看技能详情',exact:true}).click();
  await drawer.getByText('这条搜索记录已失效或不属于当前会话，请重新搜索。',{exact:true}).waitFor();
  await page.screenshot({path:`${output}/conversation-detail-stale.png`});
  staleHistoryRejected=true;
 }
 const report={passed:true,nativeToolView:true,persistedReplay:true,conversationDetail:true,scopedAcknowledgment:true,fixedReferenceAcknowledged:true,detailModules:true,initialFocus:true,backgroundInert:true,keyboardTabs:true,cardControl,tabAssociations,mobile,darkControls,escapeAndClose:true,focusRestored:true,staleHistoryRejected,installationVerified,closedDuringInstallation,reloadInstallationVerified,plazaInstallationVerified,installSubmissions,errors};
 if(errors.length) throw new Error('Browser errors');
 await writeFile(`${output}/search-card-result.json`,JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
} catch(e) {await page.screenshot({path:`${output}/search-card-failure.png`});console.log(JSON.stringify({failure:e.message.replace(/token=[^\s&]+/g,'token=[redacted]').slice(0,1800),errors,body:(await page.locator('body').innerText()).slice(0,2200)}));process.exitCode=1;}
finally {await browser.close();}
