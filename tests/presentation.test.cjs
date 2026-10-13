const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const {slideIndex, koreanVoices, Narrator} = require('../static/js/presentation-core.js');
const context = {window:{}};
vm.runInNewContext(fs.readFileSync(path.join(root,'static/js/presentation-data.js'),'utf8'),context);
const data = context.window.ZIPAI_PRESENTATION_DATA;
function mockSpeech() {
  const synth={queue:[],cancels:0,pauses:0,resumes:0,cancel(){this.cancels++;},resume(){this.resumes++;},pause(){this.pauses++;},speak(u){this.queue.push(u);}};
  const states=[];let ended=0;
  const narrator=new Narrator(synth,class {constructor(text){this.text=text;}},s=>states.push(s),()=>ended++);
  return {synth,states,narrator,ended:()=>ended};
}
const voice={lang:'ko-KR',voiceURI:'korean'};
test('59 slides and every primary/submenu have complete narration and valid navigation IDs',()=>{
  assert.equal(data.slides.length,59);
  assert.equal(new Set(data.slides.map(s=>s.id)).size,59);
  for(const slide of data.slides){assert.ok(slide.title);assert.ok(slide.script.length>35);}
  for(const [,id] of data.hubs)assert.ok(data.slides.some(s=>s.id===id));
  const features=data.slides.filter(s=>s.type==='feature');assert.equal(features.length,40);
  assert.ok(features.some(s=>s.id==='safety'));assert.ok(features.some(s=>s.id==='public-safety'));
  assert.ok(features.some(s=>s.id==='account-settings'));assert.ok(features.some(s=>s.id==='admin-audit'));
});
test('saved routes restore and malformed/unknown hashes safely return to cover',()=>{
  assert.equal(data.slides[slideIndex(data.slides,'#public-safety')].id,'public-safety');
  assert.equal(slideIndex(data.slides,'#%E0%A4%A'),0);assert.equal(slideIndex(data.slides,'#unknown'),0);
});
test('all live links refer to existing page controller mappings and evidence assets exist',()=>{
  const directory=path.join(root,'src/main/java/com/onrender/zipai/web');
  const source=fs.readdirSync(directory).filter(n=>n.endsWith('.java')).map(n=>fs.readFileSync(path.join(directory,n),'utf8')).join('\n');
  for(const slide of data.slides.filter(s=>s.url)){
    const [route]=slide.url.split('#');assert.ok(source.includes('"'+route+'"'),'unknown route '+route);
    if(slide.image)assert.ok(fs.existsSync(path.join(root,slide.image.slice(1))));
  }
  const template=fs.readFileSync(path.join(root,'templates/presentation/index.html'),'utf8');
  for(const match of template.matchAll(/(?:src|href)="(\/static\/[^"?]+)/g))assert.ok(fs.existsSync(path.join(root,match[1].slice(1))));
});
test('missing Korean voice never speaks using an English fallback',()=>{
  const m=mockSpeech();assert.equal(m.narrator.speak('안녕하세요.',{lang:'en-US'},1),false);assert.equal(m.synth.queue.length,0);assert.equal(m.states.at(-1),'no-voice');
  assert.equal(koreanVoices([{lang:'en-US'},voice,{lang:'ko_KR'}]).length,2);
});
test('navigation cancellation ignores stale end/error callbacks and cannot advance slides',()=>{
  const m=mockSpeech();m.narrator.speak('첫 번째 문장입니다. 두 번째 문장입니다.',voice,1);
  const stale=m.synth.queue[0];m.narrator.stop();const count=m.synth.queue.length;
  stale.onend();stale.onerror({error:'interrupted'});assert.equal(m.synth.queue.length,count);assert.equal(m.ended(),0);assert.equal(m.states.at(-1),'idle');
});
test('restart plays the new script only; previous utterance cannot finish the new narration',()=>{
  const m=mockSpeech();m.narrator.speak('이전 장표.',voice,1);const stale=m.synth.queue[0];m.narrator.speak('새 장표.',voice,.95);
  stale.onend();assert.equal(m.ended(),0);m.synth.queue.at(-1).onend();assert.equal(m.ended(),1);
});
test('sentence chunks finish once and pause/resume controls the existing utterance',()=>{
  const m=mockSpeech();m.narrator.speak('첫 문장입니다. 마지막 문장입니다.',voice,.95);assert.equal(m.synth.queue.length,1);
  m.narrator.togglePause();assert.equal(m.synth.pauses,1);assert.equal(m.states.at(-1),'paused');
  m.narrator.togglePause();assert.equal(m.states.at(-1),'speaking');
  m.synth.queue[0].onend();assert.equal(m.synth.queue.length,2);assert.equal(m.ended(),0);
  m.synth.queue[1].onend();assert.equal(m.ended(),1);assert.equal(m.narrator.active,false);
});
test('blocked speech reports failure without treating it as completed narration',()=>{
  const m=mockSpeech();m.narrator.speak('음성 설명.',voice,1);m.synth.queue[0].onerror({error:'not-allowed'});
  assert.equal(m.states.at(-1),'blocked');assert.equal(m.ended(),0);assert.equal(m.narrator.active,false);
});
function fakeClock() {
  let now=0,next=0;const pending=new Map();
  return {
    now:()=>now,setTimeout(fn,ms){const id=++next;pending.set(id,{fn,at:now+ms});return id;},clearTimeout(id){pending.delete(id);},
    advance(ms){const until=now+ms;for(;;){const candidate=[...pending.entries()].filter(([,t])=>t.at<=until).sort((a,b)=>a[1].at-b[1].at)[0];if(!candidate)break;pending.delete(candidate[0]);now=candidate[1].at;candidate[1].fn();}now=until;}
  };
}
const {TimedTour}=require('../static/js/presentation-core.js');
test('nine-summary tour budgets total 300 seconds and includes all seven primary menus plus member/admin',()=>{
  assert.equal(data.tour.length,9);assert.equal(data.tour.reduce((n,s)=>n+s.seconds,0),300);
  for(const id of ['tour-property','tour-defense','tour-safety','tour-finance','tour-happy','tour-lifestyle','tour-members'])assert.ok(data.tour.some(s=>s.id===id));
  for(const s of data.tour)assert.ok(s.script.length>60);
});
test('full tour completes at five minutes when narration fits its budgets',()=>{
  const clock=fakeClock(),seen=[];let finish=0;
  const tour=new TimedTour(data.tour,s=>{seen.push(s.id);tour.voiceDone();},()=>finish++,clock);
  tour.start();clock.advance(299999);assert.equal(finish,0);clock.advance(1);assert.equal(finish,1);assert.equal(seen.length,9);assert.equal(tour.active,false);
});
test('missing or hung voice callback cannot freeze timed progression',()=>{
  const clock=fakeClock(),seen=[];
  const tour=new TimedTour([{seconds:1,id:'a'},{seconds:1,id:'b'}],s=>seen.push(s.id),()=>{},clock);
  tour.start();clock.advance(1000);assert.deepEqual(seen,['a','b']);clock.advance(1000);assert.equal(tour.active,false);
});
test('pause holds the countdown even after narration ends and resumes with remaining time',()=>{
  const clock=fakeClock(),seen=[];
  const tour=new TimedTour([{seconds:10,id:'a'},{seconds:10,id:'b'}],s=>seen.push(s.id),()=>{},clock);
  tour.start();clock.advance(4000);tour.pause();tour.voiceDone();clock.advance(50000);assert.deepEqual(seen,['a']);assert.equal(tour.remainingSeconds(),6);
  tour.resume();clock.advance(5999);assert.deepEqual(seen,['a']);clock.advance(1);assert.deepEqual(seen,['a','b']);
});
test('tour stop cancels pending automatic transitions',()=>{
  const clock=fakeClock(),seen=[];let finish=0;
  const tour=new TimedTour([{seconds:1,id:'a'},{seconds:1,id:'b'}],s=>seen.push(s.id),()=>finish++,clock);
  tour.start();tour.voiceDone();tour.stop();clock.advance(10000);assert.deepEqual(seen,['a']);assert.equal(finish,0);
});

const {fitScale}=require('../static/js/presentation-core.js');
test('complete slide content fits desktop, small windows and mobile viewports without cropping',()=>{
  for(const [vw,vh] of [[1920,820],[1366,540],[1280,490],[800,350],[375,450]]){
    for(const [cw,ch] of [[1280,640],[1280,950],[1280,1400]]){
      const scale=fitScale(vw,vh,cw,ch);
      assert.ok(scale>0 && scale<=1);assert.ok(cw*scale<=vw);assert.ok(ch*scale<=vh);
    }
  }
});
test('fit waits safely for unmeasurable DOM and never enlarges beyond design size',()=>{
  assert.equal(fitScale(0,0,1280,640),1);assert.equal(fitScale(1920,1080,1280,640),1);
});

test('team is the second slide and contains all five confirmed responsibilities',()=>{
  assert.equal(data.slides[1].id,'team');
  assert.deepEqual(Array.from(data.slides[1].members,row=>row[0]),['윤태석','유준서','송성근','김해원','이훈경']);
  assert.equal(data.slides[1].members[0][1],'조장');
  assert.ok(data.slides[1].members[0][2].includes('Backend 통합'));
  assert.ok(data.slides[1].members[2][2].includes('Frontend 통합'));
});

test('problem solving, achievements and plans directly precede demo',()=>{
  const i=data.slides.findIndex(s=>s.id==='demo');
  assert.deepEqual(Array.from(data.slides.slice(i-3,i),s=>s.id),['troubleshooting','evidence','roadmap']);
  assert.equal(data.slides[i-3].cases.length,4);
  assert.match(data.slides[i-2].summary,/2026.10.12/);
  assert.match(data.slides[i-1].footnote,/향후 계획/);
});
