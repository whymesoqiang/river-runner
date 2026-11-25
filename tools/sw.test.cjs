const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');
function setup({modern=true,fail=false,windows=1,runtime='js'}={}){
 const base='https://example.test/river-runner/',events={},data=new Map();let skipped=0,claimed=0;
 const key=x=>typeof x==='string'?x:x.url||String(x);
 const caches={keys:async()=>[...data.keys()],delete:async n=>data.delete(n),open:async n=>{
  if(!data.has(n))data.set(n,new Map());const d=data.get(n);
  return{match:async u=>d.get(key(u)),put:async(u,r)=>d.set(key(u),r),keys:async()=>[...d.keys()]};
 }};
 data.set('rr-old',new Map(modern?[[base+'release.json',new Response('{}')]]:[]));data.set('other-app',new Map([['keep',new Response('saved')]]));
 const self={registration:{scope:base},location:{href:base+'sw.js'+(runtime?'?runtime='+runtime:''),origin:'https://example.test'},addEventListener:(k,f)=>events[k]=f,skipWaiting:async()=>skipped++,clients:{claim:async()=>claimed++,matchAll:async()=>Array.from({length:windows},()=>({url:base}))}};
 vm.runInNewContext(fs.readFileSync('web/src/commonMain/resources/sw.js','utf8').replace('__RR_BUILD__','new').replace('__RR_VERSION__','1.2.3'),{self,caches,URL,Response,fetch:async u=>{
  if(String(u).endsWith('release.json'))return new Response(JSON.stringify({build:'new',assets:{js:['index.html','app.js'],wasm:['index.html','wasm-app.js']}}));
  return new Response('asset',{status:fail&&String(u).endsWith('app.js')?503:200});
 }});
 async function run(type,extra={}){let pending;events[type]({...extra,waitUntil:p=>pending=p});await pending;}
 return{run,events,data,base,get skipped(){return skipped;},get claimed(){return claimed;}};
}
test('modern updates fully cache assets then wait for explicit activation',async()=>{const s=setup();await s.run('install');assert.equal(s.skipped,0);assert.ok(s.data.get('rr-new-js').has(s.base));assert.ok(s.data.has('rr-old'));});
test('failed asset download leaves previous application and unrelated cache intact',async()=>{const s=setup({fail:true});await assert.rejects(s.run('install'));assert.equal(s.skipped,0);assert.ok(!s.data.has('rr-new-js'));assert.ok(s.data.has('rr-old'));assert.ok(s.data.has('other-app'));});
test('legacy migration activates once because old pages have no update controls',async()=>{const s=setup({modern:false});await s.run('install');assert.equal(s.skipped,1);});
test('another open training window blocks activation',async()=>{const s=setup({windows:2});let result;await s.run('message',{data:{type:'activate'},ports:[{postMessage:m=>result=m}]});assert.equal(result,'blocked');assert.equal(s.skipped,0);});
test('explicit single-window update activates and never touches save storage',async()=>{const s=setup();await s.run('install');await s.run('message',{data:{type:'activate'},ports:[{postMessage:()=>{}}]});assert.equal(s.skipped,1);await s.run('activate');assert.ok(s.data.has('other-app'));assert.ok(s.data.has('rr-old'));assert.equal(s.claimed,1);});
test('release checks bypass stale application cache',()=>{const s=setup();let intercepted=false;s.events.fetch({request:{method:'GET',url:s.base+'release.json?t=123'},respondWith:()=>intercepted=true});assert.equal(intercepted,false);});

test('legacy registration without runtime caches both compatible entries',async()=>{const s=setup({modern:false,runtime:null});await s.run('install');const c=s.data.get('rr-new-both');assert.ok(c.has(s.base+'app.js'));assert.ok(c.has(s.base+'wasm-app.js'));assert.equal(s.skipped,1);});
