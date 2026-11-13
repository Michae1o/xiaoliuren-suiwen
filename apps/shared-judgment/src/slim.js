/* slim.js —— 只给**小程序速断**用的最小出口。
   为什么要它：`@xlr/shared-judgment` 的 index.js 把 corpus（200KB）、mcp-tools 也一起 re-export，
   打成一个 CJS 就是 498KB —— 小程序主包上限 2MB，扛不住。这里只放出速断真正用到的四样。
   ⛔ 别拿它替换 index.js：桌面/安卓/MCP 仍从 index.js 取。 */
export { G, SHENG, KE, CATS, ZWX, autoCat } from './judgment.js';
export { ASKS } from './asks.js';
