const {JSDOM}=require(process.env.ZIPAI_TEST_JSDOM || 'jsdom');
const fs=require('fs'),assert=require('node:assert/strict');
const code=fs.readFileSync(require('node:path').join(__dirname,'../static/js/presentation-live.js'),'utf8');
function harness(){
 const dom=new JSDOM('<button data-tour-start>start</button>',{url:'https://zipai.example/presentation',runScripts:'outside-only',pretendToBeVisual:true});const w=dom.window;
 let now=0,id=0;const jobs=new Map(),spoken=[];w.setTimeout=(f,ms)=>{jobs.set(++id,{f,at:now+ms});return id};w.clearTimeout=i=>jobs.delete(i);w.setInterval=(f,ms)=>{jobs.set(++id,{f,at:now+ms,repeat:ms});return id};w.clearInterval=w.clearTimeout;
 w.SpeechSynthesisUtterance=function(text){this.text=text};w.speechSynthesis={getVoices:()=>[{lang:'ko-KR'}],resume(){},pause(){},cancel(){},speak(u){spoken.push(u)}};
 w.HTMLElement.prototype.getClientRects=()=>[{}];w.HTMLElement.prototype.scrollIntoView=()=>{};
 w.eval(code);const frame=w.document.getElementById('liveFrame');let d=null;
 Object.defineProperty(frame,'contentDocument',{get:()=>d});Object.defineProperty(frame,'src',{set:url=>{if(url==='about:blank'){d=null;return;}const path=url.startsWith('/demo/start')?'/':url==='/demo/end'?'/member/login':url;
 const page=new JSDOM('<html><head></head><body><form id="loginForm"></form><main><article class="property-card">매물</article><div id="detailContent"></div><aside id="propertyDetail"></aside></main></body></html>',{url:'https://zipai.example'+path});d=page.window.document;Object.defineProperty(d,'readyState',{get:()=> 'complete'});page.window.HTMLElement.prototype.getClientRects=()=>[{}];page.window.HTMLElement.prototype.scrollIntoView=()=>{};
 d.querySelector('.property-card').onclick=()=>{};
 }});
 async function advance(ms){const end=now+ms;for(let loops=0;loops<10000;loops++){await Promise.resolve();await Promise.resolve();let next=[...jobs].sort((a,b)=>a[1].at-b[1].at)[0];if(!next||next[1].at>end){now=end;for(let i=0;i<12;i++)await Promise.resolve();return;}now=next[1].at;const [key,j]=next;j.repeat?j.at+=j.repeat:jobs.delete(key);j.f();}throw Error('timer loop');}
 return{w,spoken,advance,get d(){return d},click:s=>w.document.querySelector(s).click()};
}
(async()=>{
 const h=harness();h.click('[data-tour-start]');assert.equal(h.spoken.length,1);assert.equal(h.d,null,'explanation must precede navigation');h.spoken[0].onend();await h.advance(200);assert.ok(h.d);assert.equal(h.spoken.length,2);
 await h.advance(1000);h.spoken[1].onend();await h.advance(6000);assert.match(h.w.document.getElementById('liveTitle').textContent,/매물/,'voice alone must not advance');h.d.getElementById('detailContent').textContent='실제 상세';await h.advance(3500);await h.advance(3500);assert.match(h.w.document.getElementById('liveTitle').textContent,/사기방지/);
 h.click('#liveExit');await h.advance(100000);assert.equal(h.w.document.getElementById('liveTour').hidden,true,'cancel must hide tour');
 const e=harness();e.click('[data-tour-start]');e.spoken[0].onerror();await e.advance(12000);await e.advance(12000);assert.equal(e.w.document.getElementById('liveRetry').hidden,true);assert.ok(e.d,'voice failure must still open actual page');assert.match(e.w.document.getElementById('liveCaption').textContent,/음성/);
 const t=harness();t.click('[data-tour-start]');await t.advance(30000);await t.advance(1000);assert.equal(t.w.document.getElementById('liveRetry').hidden,true,'hung voice must use silent continuation');assert.ok(t.d,'hung callback must not freeze page progression');
 const final=harness();final.click('[data-tour-start]');final.spoken[0].onerror();await final.advance(12000);await final.advance(12000);
 for(let i=0;i<11;i++){final.click('#liveSkip');await final.advance(200);}
 await final.advance(500);assert.equal(final.d.URL,'https://zipai.example/member/login');
 assert.equal(final.w.document.getElementById('liveStage').hidden,false);
 assert.match(final.spoken.at(-1).text,/SNS.*OAuth/,'final scene retries speech after earlier failure');
 final.spoken.at(-1).onend();await final.advance(500);
 assert.match(final.spoken.at(-1).text,/실제 계정 인증/);
 final.spoken.at(-1).onend();await final.advance(5000);await final.advance(5000);
 assert.match(final.w.document.getElementById('liveCaption').textContent,/자동 기능 시연 완료/);
 console.log('PASS explanation → live page; speech + real result gate; stop cancels; speech failure and hung callback continue silently');
})().catch(e=>{console.error(e);process.exitCode=1});
