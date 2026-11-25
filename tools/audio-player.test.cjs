const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
function setup(){
 const made=[],events={};
 class Audio {constructor(src){this.src=src;this.paused=true;this.ended=false;made.push(this);}play(){this.paused=false;return Promise.resolve();}pause(){this.paused=true;}removeAttribute(){this.src='';}load(){this.released=true;}}
 const window={addEventListener:(k,f)=>events[k]=f},document={hidden:false,addEventListener:(k,f)=>events[k]=f};
 vm.runInNewContext(fs.readFileSync('web/src/commonMain/resources/audio-player.js','utf8'),{window,document,Audio});return{play:window.rrPlayAudio,made,events,document};
}
test('10000 sounds reuse a bounded number of native audio elements',()=>{
 const s=setup();for(let i=0;i<10000;i++){s.play('sound'+i%14,0.5,1);if(i%7===0)s.made.forEach(a=>a.paused=true);}
 assert.ok(s.made.length<=12);
});
test('busy pool drops cosmetic sounds instead of allocating without bound',()=>{const s=setup();for(let i=0;i<100;i++)s.play('a',1,1);assert.equal(s.made.length,12);});
test('backgrounding releases decoders and foreground playback works again',()=>{
 const s=setup();s.play('a',1,1);s.document.hidden=true;s.events.visibilitychange();assert.ok(s.made[0].released);s.play('b',1,1);assert.equal(s.made.length,2);
 s.events.pagehide();assert.ok(s.made.every(a=>a.released));
});
