import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';
const { DSH_PROBE_URL_FILE: entryFile, DSH_PROBE_OUTPUT: output, DSH_CHANGE_RESULT: resultFile }=process.env;
assert(entryFile&&output&&resultFile);const fixture=JSON.parse(await readFile(resultFile,'utf8'));assert(fixture.passed&&fixture.realHost);const entry=(await readFile(entryFile,'utf8')).trim();
const browser=await chromium.launch({headless:true,...(process.env.DSH_PROBE_CHROMIUM?{executablePath:process.env.DSH_PROBE_CHROMIUM}:{})});
const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',error=>errors.push(error.message));let stage='boot';
try{
 await page.goto(entry,{waitUntil:'domcontentloaded'});
 for(const name of [/^(Continue|继续)$/,/^(Configure later|稍后配置)$/]){const button=page.getByRole('button',{name});await page.addLocatorHandler(button,()=>button.click());}
 const gettingStarted=page.getByRole('dialog',{name:/^(Get started|开始使用)$/});await page.addLocatorHandler(gettingStarted,()=>gettingStarted.getByRole('button',{name:/^(Close|关闭)$/}).click());
 await page.getByRole('button',{name:'技能中心',exact:true}).click();await page.getByRole('tab',{name:'已安装',exact:true}).click();
 await page.getByRole('button',{name:/查看 .*@change-demo\/document-helper 详情/}).click();
 stage='early-local-summary';await page.getByRole('button',{name:/^本地修改 · \d+$/}).waitFor();assert.equal(await page.getByRole('heading',{name:'版本变化',exact:true}).count(),0);
 await page.screenshot({path:join(output,'real-detail-local.png'),fullPage:true});
 await page.getByRole('button',{name:/^本地修改 · \d+$/}).click();await page.getByRole('heading',{name:'本地修改',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'备份修改并继续'}).count(),0);
 stage='file-diff';await page.locator('.astron-sh-changes summary').first().click();await page.locator('.changes-code').waitFor();assert((await page.locator('.changes-code').innerText()).includes('人工验收'));
 await page.screenshot({path:join(output,'real-local-diff.png'),fullPage:true});
 stage='reset-cancel';await page.getByRole('button',{name:/^撤销 .* 的修改$/}).click();await page.getByRole('button',{name:'取消',exact:true}).click();assert((await readFile(join(fixture.directory,'SKILL.md'),'utf8')).includes('人工验收'));
 stage='reset-confirm';await page.getByRole('button',{name:/^撤销 .* 的修改$/}).click();await page.getByRole('button',{name:'确认撤销',exact:true}).click();await page.getByText('没有本地修改。',{exact:true}).waitFor();assert(!(await readFile(join(fixture.directory,'SKILL.md'),'utf8')).includes('人工验收'));
 stage='narrow';await page.setViewportSize({width:390,height:900});await page.screenshot({path:join(output,'real-narrow.png'),fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth)<=390);
 assert.deepEqual(errors,[]);await writeFile(join(output,'browser-result.json'),JSON.stringify({passed:true,realHost:true,modelCalled:false,earlyLocalSummary:true,fileDiff:true,resetCancel:true,resetConfirmed:true,versionUnchanged:JSON.parse(await readFile(join(fixture.directory,'.skillhub/metadata.json'),'utf8')).version==='1.3.0',narrow:true,browser:browser.version()},null,2));
} catch(error){await page.screenshot({path:join(output,'browser-failure.png'),fullPage:true}).catch(()=>{});await writeFile(join(output,'browser-result.json'),JSON.stringify({passed:false,stage,error:String(error.message).replace(/token=[^\s&]+/g,'token=[redacted]')},null,2));throw error;}
finally{await browser.close();}
