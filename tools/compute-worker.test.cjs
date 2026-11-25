const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function setup(){
 const workers=[],events={};
 class Worker {
  constructor(url){this.url=String(url);this.sent=[];this.dead=false;workers.push(this);}
  postMessage(m){this.sent.push(m);}
  terminate(){this.dead=true;}
  reply(payload){this.onmessage({data:{id:this.sent.at(-1).id,payload}});}
 }
 const window={addEventListener:(k,f)=>events[k]=f};
 vm.runInNewContext(fs.readFileSync('web/src/commonMain/resources/compute-worker.js','utf8'),{
  window,document:{baseURI:'https://example.com/river-runner/'},Worker,URL,Map,
  setTimeout:()=>1,clearTimeout:()=>{}
 });
 return {api:window.rrCompute,workers,events};
}
test('one worker queues calculations and preserves Pages base path',()=>{
 const {api,workers}=setup();api.submit(1,'first');api.submit(2,'second');
 assert.equal(workers.length,1);assert.equal(workers[0].sent.length,1);
 assert.equal(workers[0].url,'https://example.com/river-runner/strategyWorker.js');
 workers[0].reply('answer');assert.equal(api.poll(1),'answer');assert.equal(api.poll(1),null);
 assert.equal(workers[0].sent[1].id,2);
});
test('cancel stops stale running work and removes queued work',()=>{
 const {api,workers}=setup();api.submit(1,'old');api.submit(2,'discard');api.submit(3,'latest');
 api.cancel(2);api.cancel(1);assert.equal(workers[0].dead,true);assert.equal(workers.length,2);
 assert.equal(workers[1].sent[0].id,3);workers[1].reply('latest answer');
 assert.equal(api.poll(3),'latest answer');assert.equal(api.poll(1),null);assert.equal(api.poll(2),null);
});
test('worker error resolves caller and restarts queued work',()=>{
 const {api,workers}=setup();api.submit(1,'bad');api.submit(2,'next');workers[0].onerror();
 assert.ok(JSON.parse(api.poll(1)).error);assert.equal(workers[1].sent[0].id,2);
});
test('back-forward cache pauses and resumes outstanding work',()=>{
 const {api,workers,events}=setup();api.submit(1,'pending');events.pagehide();
 assert.equal(workers[0].dead,true);api.submit(2,'queued');assert.equal(workers.length,1);
 events.pageshow();assert.equal(workers[1].sent[0].id,1);workers[1].reply('resumed');
 assert.equal(api.poll(1),'resumed');assert.equal(workers[1].sent[1].id,2);
});
