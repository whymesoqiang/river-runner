const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
function setup({hang=false,registerHang=false}={}){
 const listeners={},intervals=[];let failing=hang,regFail=registerHang,now=1000;
 const worker={state:'installing',addEventListener(){},postMessage(){}};
 const reg={installing:worker,waiting:null,addEventListener(){},update:async()=>{}};
 const nav={onLine:true,serviceWorker:{register:()=>regFail?new Promise(()=>{}):Promise.resolve(reg),addEventListener:(k,f)=>listeners[k]=f}};
 const win={addEventListener(){}};
 class Channel{constructor(){this.port1={close(){}};this.port2={postMessage:data=>this.port1.onmessage?.({data})}}}
 vm.runInNewContext(fs.readFileSync('web/src/commonMain/resources/updates.js','utf8'),{window:win,navigator:nav,document:{hidden:false,querySelector:()=>({content:'old'}),addEventListener(){}},performance:{getEntriesByType:()=>[]},location:{reload(){}},MessageChannel:Channel,AbortController,Date:{now:()=>now},setTimeout:(f,ms)=>setTimeout(f,Math.min(ms,15)),clearTimeout,setInterval:f=>intervals.push(f),fetch:async(url,opts)=>{
  if(failing)return new Promise((_,reject)=>opts.signal.addEventListener('abort',()=>reject(Error('timeout'))));
  return {ok:true,json:async()=>({build:'new',version:'0.14.11'})};
 }});
 return {api:win.rrUpdates,reg,progress:data=>listeners.message({data}),recover:()=>{failing=false;regFail=false},stale:()=>{now+=101000;intervals.at(-1)()}};
}
test('a stalled release check becomes retryable, and downloaded progress is visible',async()=>{const s=setup({hang:true});await s.api.start();assert.equal(s.api.state(),'timeout|');s.recover();await s.api.check();s.progress({type:'rr-update-progress',build:'new',version:'0.14.11',done:3,total:8});assert.equal(s.api.state(),'installing|0.14.11|3/8');s.stale();assert.equal(s.api.state(),'timeout|0.14.11');s.reg.waiting={postMessage:(q,ports)=>ports[0].postMessage({build:'new',version:'0.14.11'})};await s.api.check();assert.equal(s.api.state(),'ready|0.14.11');});
test('hung service worker registration can be retried',async()=>{const s=setup({registerHang:true});await s.api.start();assert.equal(s.api.state(),'timeout|');s.recover();await s.api.start();assert.match(s.api.state(),/^installing/);});
