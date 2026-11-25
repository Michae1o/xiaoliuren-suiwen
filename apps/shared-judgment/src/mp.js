/* mp.js —— 小程序专用入口。
   与 index.js 同源（同样从这 7 个文件出口），但**不含** corpus.js 与 mcp-tools.js：
     · corpus（查典语料）在 bundle 里占 446KB，小程序的 yi.js 一个导出都没用到它；
     · 打全量入口会把主包顶过微信 1.5M 校验（实测「主包大小 未通过」）。
   小程序实际用到的 12 个导出全在这 7 个文件里：
     SYS / Tidy / autoCat / buildBlocks / buildContext /
     castByClock / castByDate / castByNums / numsFilled / plainAll / zhi
   ⛔ 桌面端、网页版、安卓、MCP 仍走 index.js，不要换成这个。 */
export * from './judgment.js';
export * from './cast.js';
export * from './plain-text.js';
export * from './asks.js';
export * from './tidy.js';
export * from './prompt.js';
export * from './followup.js';  /* 接着问：四端共用这一份规矩与拼装 */
