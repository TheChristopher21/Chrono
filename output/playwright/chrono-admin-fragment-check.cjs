const fs=require('fs'),vm=require('vm'),path=require('path');
const dir='C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0';
for(const name of fs.readdirSync(dir).filter(n=>/^chrono-admin-0[1-5]-.*\.html$/.test(n))){
 const html=fs.readFileSync(path.join(dir,name),'utf8');
 const scripts=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]);
 scripts.forEach(code=>new vm.Script(code));
 console.log(JSON.stringify({name,bytes:Buffer.byteLength(html),scripts:scripts.length,documentTags:/<(?:html|head|body)\b|<!doctype/i.test(html),networkCalls:/\bfetch\s*\(|XMLHttpRequest|WebSocket\s*\(/.test(html),authoredSvg:/<svg\b/.test(html),submitButtons:(html.match(/type="submit"/g)||[]).length}));
}
