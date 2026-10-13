(function () {
  'use strict';
  // Uses the app's existing UI handlers and real read-only APIs. No fabricated results.
  const chapters = [
    {title:'매물 찾기',url:'/demo/start?mode=user',intro:'일반 사용자 체험으로 실제 매물 화면을 열겠습니다. 자동 시연을 시작하면 현재 계정 대신 체험 세션을 사용합니다. 지도와 등록된 매물을 함께 확인합니다.',steps:[
      {say:'첫 번째 매물을 클릭해서 사진, 가격과 상세 정보를 펼칩니다.',click:'.property-card',ready:d=>d.querySelector('#detailContent')?.textContent.trim(),focus:'#propertyDetail'}]},
    {title:'사기방지 · 전세가율',url:'/defense/calculator',intro:'사기방지 메뉴에는 안전 진단, 체크리스트, 계약 가이드와 전세가율 계산이 있습니다. 실제 계산기에 예시 금액을 입력합니다.',steps:[
      {say:'매매가 오억 원, 보증금 삼억 오천만 원, 선순위 채권 이천만 원을 입력하고 계산 버튼을 누릅니다.',values:{'#salePrice':'50000','#deposit':'35000','#seniorDebt':'2000'},click:'#calculatorForm button[type=submit]',ready:d=>!d.querySelector('#calculatedContent')?.hidden,focus:'#ratioResult'},
      {say:'화면에 표시된 전세가율과 위험도, 계산식을 확인합니다. 이 수치는 계약 판단을 돕는 참고 자료입니다.',focus:'#ratioResult'}]},
    {title:'위험안전도 · 지도',url:'/safe/safety',intro:'위험안전도에서는 지역 안전정보, 범죄 통계와 주변 안전시설을 조회합니다. 실제 지도에서 역삼동 주변을 검색합니다.',steps:[
      {say:'검색어를 입력하고 조회를 실행합니다. 주소 검색과 안전정보 응답, 지도 타일이 준비될 때까지 기다립니다.',values:{'#safety-keyword':'역삼동'},click:'.safety-search-form button[type=submit]',ready:d=>{const h=d.querySelector('[data-safety-map]');return h&&!h.classList.contains('is-map-unavailable')&&Array.from(h.querySelectorAll('img')).some(i=>i.complete&&i.naturalWidth>0)&&!!d.querySelector('.safety-score-content');},focus:'[data-safety-map]'},
      {say:'지도 확대 버튼을 클릭합니다. CCTV, 치안시설과 여성 안전시설을 실제 위치에서 살펴볼 수 있습니다.',click:'[data-map-action="zoom-in"]',focus:'[data-safety-map]'}]},
    {title:'금융지원정책',url:'/board/finance-policy',intro:'금융지원정책에서는 주거 대출의 대상과 조건을 확인합니다. 정책 카드와 대출 계산기는 실제 서비스 화면입니다.',steps:[
      {say:'서버에서 불러온 금융지원정책 카드를 확인합니다. 정책 변경 수집 이력은 관리자 메뉴에서 확인할 수 있습니다.',ready:d=>d.querySelector('#financePolicyList')?.querySelector('article'),focus:'#financePolicyList'}]},
    {title:'대출 이자 계산',url:'/board/trend3',intro:'대출 원금, 금리와 기간을 입력해서 상환 부담을 계산합니다.',steps:[
      {say:'원금 일억 원, 연 금리 삼 점 오 퍼센트, 삼십 년 조건으로 계산 버튼을 클릭합니다.',values:{'#principal':'10000','#rate':'3.5','#term':'30'},click:'#calcForm button[type=submit]',ready:d=>/\d/.test(d.querySelector('#calcResultVal')?.textContent||'')&&!/^0\s*원$/.test(d.querySelector('#calcResultVal')?.textContent.trim()||''),focus:'#calcResultVal'}]},
    {title:'행복주택',url:'/board/happy-housing',intro:'행복주택에서는 수집한 모집 공고와 신청자격 간편진단을 제공합니다. 공고문과 소득, 자산 기준을 함께 확인하는 기능입니다.',steps:[
      {say:'신청 계층을 청년으로 선택합니다. 계층에 따라 안내 문구가 달라지는 실제 입력 화면을 확인합니다.',values:{'#applicantType':'youth'},focus:'#happyEligibilityForm'}]},
    {title:'AI·ML 생활지역 추천',url:'/ai/lifestyle-analysis',intro:'생활지역 추천은 공공 생활 데이터와 사용자의 선호도를 연결합니다. 가중 점수, 코사인 유사도와 케이 엔 엔 결과를 비교합니다.',steps:[
      {say:'추천 분석하기 버튼을 클릭합니다. 서버가 실제 파이썬 추천 결과를 돌려줄 때까지 기다립니다.',click:'#lifestyleRecommendForm button[type=submit]',ready:d=>!!d.querySelector('#lifestyleRecommendationResults .lifestyle-algorithm-block'),focus:'#lifestyleRecommendationResults'},
      {say:'세 알고리즘의 추천 지역과 점수를 비교합니다. 아래에서는 지역의 방문 가능한 방과 방문 요청을 연결합니다. 예약을 확정하는 주체는 매물 등록자입니다.',focus:'#lifestyleRecommendationResults'}]},
    {title:'챗봇 상담',url:'/',intro:'챗봇은 주거 기능의 사용 방법을 안내합니다. 생성형 인공지능이 아니라 질문의 키워드와 규칙을 이용한 일차 상담입니다.',steps:[
      {say:'챗봇을 열고 행복주택 자격진단을 질문하겠습니다.',click:'.zipai-chatbot-launcher',focus:'.zipai-chatbot-panel'},
      {say:'질문을 전송합니다. 실제 상담 응답과 관련 메뉴로 이동하는 링크를 확인합니다.',values:{'.zipai-chatbot-input':'행복주택 자격진단 알려줘'},click:'.zipai-chatbot-send',ready:d=>d.querySelectorAll('.zipai-chatbot-message.bot').length>1,focus:'.zipai-chatbot-panel'}]},
    {title:'게시판 · 고객센터',url:'/board/community',intro:'게시판에서는 사용자 글과 상세 내용을 확인하고, 고객센터에서는 문의와 답변을 관리합니다. 이번 자동 시연은 운영 데이터를 작성하거나 삭제하지 않습니다.',steps:[{say:'게시판의 실제 목록 화면을 확인합니다.',focus:'main'}]},
    {title:'마이페이지 · 계정관리',url:'/member/mypage',intro:'마이페이지에서는 찜한 매물, 방문 요청과 회원 정보를 확인합니다. 개인정보 변경 창은 로그아웃 아래 버튼으로 열 수 있습니다.',steps:[
      {say:'개인정보 변경 버튼을 클릭합니다. 이메일, 전화번호, 아이디와 비밀번호 변경 입력 화면을 보여주며, 실제 정보는 변경하지 않습니다.',click:'#accountSettingsOpen',ready:d=>d.querySelector('#accountSettingsDialog')?.open,focus:'#accountSettingsDialog'}]},
    {title:'관리자 · 수집 이력',url:'/demo/start?mode=admin',intro:'관리자 체험 화면으로 전환합니다. 회원, 문의, 매물과 게시글 관리, 수집 항목별 실행 이력을 확인할 수 있습니다. 체험 화면의 관리 데이터는 운영 권한과 구분됩니다.',steps:[
      {say:'수집 이력 새로고침 버튼을 클릭하고 항목별 상태와 실행 내역을 확인합니다.',click:'#collectionHistoryRefresh',ready:d=>!!d.querySelector('#collectionHistoryList')?.textContent.trim(),focus:'#adminCollectionSection'}]},
    {title:'SNS 로그인',url:'/demo/end',screenFirst:true,retryVoice:true,intro:'ZipAI는 일반 회원 로그인과 SNS 로그인을 제공합니다. 카카오, 네이버, 구글 계정을 연결할 수 있으며, 현재 서버에 설정된 로그인 버튼이 표시됩니다. SNS 로그인은 OAuth 2.0을 이용해 외부 서비스에서 인증하고 ZipAI로 돌아오는 방식입니다.',steps:[
      {say:'이번 시연에서는 실제 계정 인증을 진행하지 않고 로그인 화면과 구성을 소개합니다. 활성화된 SNS 버튼을 누르면 해당 서비스에서 로그인과 정보 제공 동의를 진행할 수 있습니다. 이것으로 ZipAI 기능 시연을 마치겠습니다.',ready:d=>!!d.querySelector('#loginForm'),focus:'#loginForm'}]}
  ];
  const root=document.createElement('section');root.id='liveTour';root.hidden=true;
  root.innerHTML='<header><strong id="liveTitle"></strong><span id="livePosition"></span><button id="livePause">일시정지</button><button id="liveRetry" hidden>현재 장면 재시도</button><button id="liveSkip">다음 장면</button><button id="liveExit">시연 종료</button></header><div id="liveExplanation"><h1></h1><p></p></div><div id="liveStage" hidden><iframe title="ZipAI 실제 기능 자동 시연" id="liveFrame"></iframe></div><p id="liveCaption" role="status"></p>';
  document.body.append(root);
  const q=s=>root.querySelector(s);const synth=window.speechSynthesis;
  let active=false,paused=false,generation=0,position=0,utterance=null,frameDoc=null,speechUnavailable=false;
  const stale=t=>!active||t!==generation;
  const visible=e=>!!e&&!e.hidden&&e.getClientRects().length>0;
  async function waitFor(check,t,timeout=25000){let elapsed=0;while(!stale(t)){if(paused){await new Promise(r=>setTimeout(r,150));continue;}if(check())return;await new Promise(r=>setTimeout(r,150));elapsed+=150;if(elapsed>=timeout)throw new Error('화면 또는 결과가 준비되지 않았습니다. 서버 상태·지도 키·메뉴 설정을 확인하세요.');}throw new Error('cancelled');}
  async function hold(ms,t){let n=0;await waitFor(()=>{n+=150;return n>=ms;},t,ms+1000);}
  function say(text,t){q('#liveCaption').textContent=text;return new Promise((resolve,reject)=>{
    const budget=Math.min(18000,Math.max(2500,text.length*65));
    const voices=synth?.getVoices().filter(v=>/^ko(?:-|_)/i.test(v.lang))||[];
    const silent=()=>{q('#liveCaption').textContent='음성 없이 자동 진행 · '+text;hold(budget,t).then(resolve,reject);};
    if(speechUnavailable||!synth||!voices.length){silent();return;}
    const current=new SpeechSynthesisUtterance(text);utterance=current;
    current.lang='ko-KR';current.voice=voices[0];current.rate=1.05;
    let finished=false,elapsed=0;
    const settle=(error)=>{if(finished)return;finished=true;clearInterval(watch);error?reject(error):resolve();};
    const fallback=()=>{
      if(finished)return;finished=true;clearInterval(watch);
      if(stale(t)){reject(new Error('cancelled'));return;}
      speechUnavailable=true;synth.cancel();
      q('#liveCaption').textContent='음성 재생 불가 · 화면 시연은 계속 진행합니다. '+text;
      hold(Math.max(150,budget-elapsed),t).then(resolve,reject);
    };
    const watch=setInterval(()=>{if(stale(t)){settle(new Error('cancelled'));return;}
      if(!paused)elapsed+=250;
      if(elapsed>Math.max(12000,budget+5000))fallback();
    },250);
    current.onend=()=>settle();current.onerror=fallback;
    try{synth.resume();synth.speak(current);}catch(_){fallback();}
  });}
  function focus(selector,d){const el=d.querySelector(selector);if(!el)throw new Error('실제 화면의 요소를 찾지 못했습니다: '+selector);d.querySelectorAll('.zipai-tour-focus').forEach(e=>e.classList.remove('zipai-tour-focus'));el.classList.add('zipai-tour-focus');el.scrollIntoView({block:'center',behavior:'smooth'});}
  async function openPage(url,t){const frame=q('#liveFrame');const previous=frame.contentDocument;frameDoc=null;frame.src=url;await waitFor(()=>{try{const d=frame.contentDocument;return d&&d!==previous&&d.readyState==='complete'&&new URL(d.URL).origin===location.origin&&d.URL!=='about:blank'&&d.querySelector('body')&&(new URL(d.URL).pathname===url.split('?')[0]||url.startsWith('/demo/start')||(url==='/demo/end'&&new URL(d.URL).pathname==='/member/login'));}catch(_){return false;}},t);
    frameDoc=frame.contentDocument;
    const css=frameDoc.createElement('style');css.textContent='.zipai-tour-focus{outline:4px solid #00b8ff!important;outline-offset:5px!important}html{scroll-behavior:smooth;scrollbar-width:none}::-webkit-scrollbar{display:none}';frameDoc.head.append(css);
  }
  function resize(){const stage=q('#liveStage');const scale=Math.min(stage.clientWidth/1280,stage.clientHeight/800);q('#liveFrame').style.transform='scale('+scale+')';}
  async function runScene(t){const c=chapters[position];q('#liveRetry').hidden=true;q('#liveTitle').textContent=c.title;q('#livePosition').textContent=(position+1)+' / '+chapters.length;
    q('#liveStage').hidden=true;q('#liveExplanation').hidden=false;q('#liveExplanation h1').textContent=c.title;q('#liveExplanation p').textContent=c.intro;
    try{
      if(c.retryVoice)speechUnavailable=false;
      if(c.screenFirst){
        q('#liveExplanation').hidden=true;q('#liveStage').hidden=false;resize();
        await openPage(c.url,t);
        await waitFor(()=>!!frameDoc.querySelector('#loginForm'),t);
        focus('#loginForm',frameDoc);
        await say(c.intro,t);
      }else{
        await say(c.intro,t);if(stale(t))return;
        q('#liveExplanation').hidden=true;q('#liveStage').hidden=false;resize();await openPage(c.url,t);
      }
      if(stale(t))return;
      for(const step of c.steps){await waitFor(()=>!paused,t);if(stale(t))return;
        // Start speech and UI operation together; neither completion can advance alone.
        await Promise.all([say(step.say,t),(async()=>{
          for(const [selector,value] of Object.entries(step.values||{})){await waitFor(()=>visible(frameDoc.querySelector(selector)),t);const el=frameDoc.querySelector(selector);focus(selector,frameDoc);el.value=value;el.dispatchEvent(new frameDoc.defaultView.Event('input',{bubbles:true}));el.dispatchEvent(new frameDoc.defaultView.Event('change',{bubbles:true}));await hold(600,t);}
          if(step.click){await waitFor(()=>{const e=frameDoc.querySelector(step.click);return visible(e)&&!e.disabled;},t);focus(step.click,frameDoc);await hold(700,t);frameDoc.querySelector(step.click).click();}
          if(step.ready)await waitFor(()=>step.ready(frameDoc),t,35000);
          if(step.focus)focus(step.focus,frameDoc);await hold(2000,t);
        })()]);
      }
      await hold(1000,t);if(stale(t))return;
      if(position+1<chapters.length){position++;runScene(t);}else{q('#liveCaption').textContent='자동 기능 시연 완료 · 종료하면 발표 장표로 돌아갑니다. SNS 로그인 화면에서 체험이 종료되었습니다.';q('#liveSkip').disabled=true;}
    }catch(error){if(stale(t))return;cancelSpeech();paused=true;q('#livePause').textContent='계속';q('#liveRetry').hidden=false;q('#liveCaption').textContent=error.message+' 성공한 결과로 표시하지 않았습니다.';}
  }
  function cancelSpeech(){generation++;synth?.cancel();utterance=null;}
  function start(){cancelSpeech();active=true;paused=false;speechUnavailable=false;position=0;q('#liveSkip').disabled=false;q('#livePause').textContent='일시정지';root.hidden=false;runScene(generation);}
  q('#liveExit').onclick=()=>{active=false;cancelSpeech();q('#liveFrame').src='about:blank';root.hidden=true;};
  q('#livePause').onclick=()=>{if(!q('#liveRetry').hidden){q('#liveRetry').click();return;}paused=!paused;q('#livePause').textContent=paused?'계속':'일시정지';if(paused)synth?.pause();else synth?.resume();};
  q('#liveRetry').onclick=()=>{cancelSpeech();paused=false;q('#livePause').textContent='일시정지';runScene(generation);};
  q('#liveSkip').onclick=()=>{if(position+1>=chapters.length)return;cancelSpeech();paused=false;position++;q('#livePause').textContent='일시정지';runScene(generation);};
  document.addEventListener('click',e=>{if(e.target.closest('[data-tour-start]')){e.preventDefault();e.stopImmediatePropagation();document.getElementById('tourStop')?.click();synth?.cancel();start();}},true);
  document.addEventListener('visibilitychange',()=>{if(active&&document.hidden&&!paused){paused=true;synth?.pause();q('#livePause').textContent='계속';}});
  window.addEventListener('resize',resize);window.addEventListener('pagehide',()=>{active=false;cancelSpeech();});
  document.querySelectorAll('[data-tour-start]').forEach(e=>e.textContent='실제 기능 자동 시연 · 약 5분');
  window.ZipaiLiveTour={chapters};
})();
