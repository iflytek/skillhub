import { selectValue } from './select-field.mjs';
import { chromium } from 'playwright';
import { writeFile } from 'node:fs/promises';
const entry = process.env.DSH_PROBE_URL;
if (!entry) throw new Error('DSH_PROBE_URL must point to the isolated authenticated DSH');
const output = process.env.DSH_PROBE_OUTPUT;
if (!output) throw new Error('DSH_PROBE_OUTPUT must be an existing output directory');
const browser = await chromium.launch({
  ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}),
  headless: true,
});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
const errors=[]; const unexpected=[];
page.on('pageerror',e=>errors.push(e.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request',r=>{if(r.url().includes('example.invalid')) unexpected.push('remote-image');});
try {
 await page.goto(entry,{waitUntil:'domcontentloaded'});
 const welcome=page.getByRole('button',{name:/^(Continue|继续)$/});
 const later=page.getByRole('button',{name:/^(Configure later|稍后配置)$/});
 await welcome.or(later).first().waitFor();
 if(await welcome.isVisible()) await welcome.click();
 await later.click();
 await page.getByText(/^(Plugins|插件)$/).click();
 await page.getByText('@astron-team/skillhub-dsh',{exact:true}).first().click();
 await page.getByRole('form',{name:'SkillHub 配置'}).waitFor();
 await selectValue(page.getByRole('combobox').nth(0), 'auto');
 await selectValue(page.getByRole('combobox').nth(1), 'show');
 await selectValue(page.getByRole('combobox').nth(2), 'auto');
 await page.getByRole('button',{name:'保存配置',exact:true}).click();
 await page.getByText('配置已保存。',{exact:true}).waitFor();
 await page.getByText('技能中心',{exact:true}).click();
 await page.getByRole('button',{name:/document-helper/}).waitFor();
 await page.getByLabel('搜索技能').fill('document');
 await page.getByRole('button',{name:'搜索',exact:true}).click();
 await page.getByRole('button',{name:/document-helper/}).click();
 await page.getByRole('heading',{name:'文档助手',exact:true}).waitFor();
 if(await page.locator('article a[href^="javascript:"]').count()) throw new Error('Unsafe link');
 if(await page.evaluate(()=>window.fixtureUnsafe===true)) throw new Error('Unsafe script executed');
 if(!await page.getByRole('article').innerText().then(v=>v.includes('摘要 & 关键结论'))) throw new Error('Entity decoding failed');
 await page.getByRole('tab',{name:'版本',exact:true}).click();
 await page.getByText('改进文档提取',{exact:true}).waitFor();
 await page.getByRole('tab',{name:'评论',exact:true}).click();
 await page.getByText('适合整理长文档',{exact:true}).waitFor();
 await page.getByRole('tab',{name:'安全审核',exact:true}).click();
 await page.getByText('当前来源的审核信息不支持匿名读取。',{exact:true}).waitFor();
 await page.getByRole('tab',{name:'详情',exact:true}).click();
 await page.screenshot({path:`${output}/catalog-detail.png`});
 await page.getByRole('button',{name:'← 返回技能广场'}).click();
 if(!await page.getByRole('button',{name:/document-helper/}).evaluate(el=>document.activeElement===el)) throw new Error('Focus not restored');
 if(await page.getByLabel('搜索技能').inputValue()!=='document') throw new Error('Search not retained');
 await page.getByLabel('搜索技能').fill('nothing');
 await page.getByRole('button',{name:'搜索',exact:true}).click();
 await page.getByText('没有找到匹配的技能，请尝试其他关键词。').waitFor();
 await page.getByRole('button',{name:'清空',exact:true}).click();
 await page.getByRole('button',{name:/document-helper/}).waitFor();
 await page.setViewportSize({width:760,height:900});
 await page.screenshot({path:`${output}/catalog-list.png`});
 const report={passed:true,search:true,fixedVersionDetail:true,returnRetainsQuery:true,focusRestored:true,emptyAndClear:true,markdownSafe:unexpected.length===0,optionalTabs:true,errors};
 if(errors.length||unexpected.length) throw new Error('Browser errors or unsafe request');
 await writeFile(`${output}/catalog-result.json`,JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
} catch(error) {console.log(JSON.stringify({failure:error.message.replace(/token=[^\s&]+/g, 'token=[redacted]').split('\n')[0],errors})); await page.screenshot({path:`${output}/catalog-failure.png`});process.exitCode=1;}
finally {await browser.close();}
