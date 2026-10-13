(function () {
  'use strict';
  const data = window.ZIPAI_PRESENTATION_DATA;
  const { slideIndex, koreanVoices, Narrator, TimedTour } = window.ZipaiPresentation;
  const slides = data.slides;
  const $ = id => document.getElementById(id);
  const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const jump = (id, label, primary = false) => `<button type="button" data-jump="${escape(id)}"${primary ? ' class="primary"' : ''}>${escape(label)}</button>`;
  const cards = (items, kind = 'cards') => `<div class="${kind}">${items.map(([title, text]) => `<article><h2>${escape(title)}</h2><p>${escape(text)}</p></article>`).join('')}</div>`;
  let index = slideIndex(slides, location.hash);
  let voices = [], selectedVoice = '', enabled = false, muted = false, pendingVoice = false, advanceTimer;
  let audioNotice = "";
  let tour, tourSlide = null, tourInterval, pendingTour = false;
  const currentSlide = () => tourSlide || slides[index];
  const synth = window.speechSynthesis;
  const status = $('voiceStatus');
  function clearAdvance() { clearTimeout(advanceTimer); advanceTimer = undefined; }
  function speechState(state) {
    const labels = {
      idle: '음성 설명은 버튼을 누르면 시작됩니다.', speaking: '현재 장표를 한국어로 설명하고 있습니다.',
      paused: '음성 설명이 일시정지되었습니다.', finished: '현재 장표 설명이 끝났습니다.',
      unsupported: '이 브라우저는 음성 읽기를 지원하지 않습니다. 대본으로 발표할 수 있습니다.',
      'no-voice': '한국어 음성이 없습니다. 음성 설정 또는 Windows 한국어 음성 설치를 확인해 주세요.',
      blocked: '자동 음성 재생이 차단되었습니다. 음성으로 설명 버튼을 다시 눌러 주세요.',
      error: '음성을 재생하지 못했습니다. 다른 한국어 목소리를 선택하거나 다시 재생해 주세요.'
    };
    status.textContent = labels[state] || labels.idle;
    $('pause').disabled = !['speaking', 'paused'].includes(state);
    $('pause').textContent = state === 'paused' ? '계속 재생' : '일시정지';
    $('play').textContent = state === 'speaking' ? '처음부터 다시 듣기' : '음성으로 설명';
    if (['unsupported', 'no-voice', 'blocked', 'error'].includes(state)) { enabled = false; clearAdvance(); if (tour && tour.active) audioNotice = labels[state] + ' 화면은 시간에 맞춰 자동 진행됩니다.'; status.textContent = audioNotice || labels[state]; }
  }
  const narrator = new Narrator(synth, window.SpeechSynthesisUtterance, speechState, () => {
    if (tour && tour.active) { tour.voiceDone(); updateTourControls(); return; }
    if (enabled && !muted && $('autoNext').checked && index < slides.length - 1 && !document.hidden) {
      clearAdvance(); advanceTimer = setTimeout(() => navigate(slides[index + 1].id), 900);
    }
  });
  tour = new TimedTour(data.tour, scene => {
    tourSlide = scene; enabled = true;
    history.replaceState(null, '', '#' + scene.id);
    render(true); updateTourControls();
  }, () => {
    endTour(false); index = slides.findIndex(s => s.id === 'end');
    history.replaceState(null, '', '#end'); render(true);
    status.textContent = '약 5분 요약 시연을 마쳤습니다. 전체 메뉴에서 상세 기능을 선택할 수 있습니다.';
  });
  function updateTourControls() {
    $('tourControls').hidden = !tour.active;
    document.body.classList.toggle('tour-running', tour.active);
    $('follow').disabled = tour.active; $('autoNext').disabled = tour.active;
    if (!tour.active) return;
    const timing = tour.paused ? '일시정지' : `다음 장표까지 ${tour.remainingSeconds()}초`;
    if (audioNotice) status.textContent = audioNotice;
    $('tourStatus').textContent = `5분 요약 · ${tour.position + 1} / ${data.tour.length} · ${timing}`;
    $('tourPause').textContent = tour.paused ? '시연 계속 진행' : '시연 일시정지';
  }
  function endTour(showMenu) {
    tour.stop(); clearInterval(tourInterval); tourInterval = undefined;
    tourSlide = null; pendingTour = false; enabled = false; pendingVoice = false;
    clearAdvance(); narrator.stop(); audioNotice = ''; updateTourControls();
    if (showMenu) {
      index = slides.findIndex(s => s.id === 'demo');
      history.replaceState(null, '', '#demo'); render(true);
    }
  }
  function startTour() {
    if (tour.active) endTour(false);
    clearAdvance(); pendingVoice = false; enabled = false; narrator.stop();
    pendingTour = false; loadVoices();
    audioNotice = ''; // Missing/blocked speech must never block the tour clock.
    pendingTour = false; muted = false;
    $('mute').setAttribute('aria-pressed','false'); $('mute').textContent = '음소거';
    clearInterval(tourInterval); tourInterval = setInterval(updateTourControls, 1000);
    tour.start(); updateTourControls();
  }
  function pauseTour() {
    if (!tour.active || tour.paused) return;
    tour.pause(); if (narrator.active && !narrator.paused) narrator.togglePause();
    updateTourControls();
  }
  function toggleTourPause() {
    if (!tour.active) return;
    if (tour.paused) {
      if (narrator.active && narrator.paused) narrator.togglePause();
      tour.resume(); updateTourControls();
    } else pauseTour();
  }
  function moveSlide(delta) {
    if (tour.active) { tour.move(delta); updateTourControls(); return; }
    if (index + delta >= 0 && index + delta < slides.length) navigate(slides[index + delta].id);
  }
  function stopPresentationVoice() {
    if (tour.active || pendingTour) endTour(true);
    clearAdvance(); pendingVoice = false; enabled = false; narrator.stop();
    if (muted) status.textContent = '음소거 상태입니다.';
  }
  function renderIndex(onlyGroup) {
    const groups = [...new Set(slides.filter(s => s.group).map(s => s.group))];
    return groups.filter(g => !onlyGroup || g === onlyGroup).map(group => `<section><h3>${escape(group)}</h3>${slides.filter(s => s.group === group).map(s => `<button type="button" data-jump="${s.id}"${s.id === slides[index].id ? ' class="active" aria-current="page"' : ''}>${escape(s.title)}</button>`).join('')}</section>`).join('');
  }
  function slideBody(s) {
    if (s.type === 'cover') return `<div class="cover"><h1 id="slideTitle">ZipAI</h1><p class="tagline">집을 고르는 과정에<br>데이터를 연결하다</p><p class="summary">Frontend · Backend · 전체 메뉴 시연</p><div class="actions">${jump('team','발표 시작',true)}<button type="button" data-tour-start>실제 기능 자동 시연 · 약 5분</button></div></div>`;
    let body = '';
    if (s.type === 'troubleshooting') body = `<table class="trouble-table"><thead><tr><th scope="col">문제</th><th scope="col">원인 · 확인 사항</th><th scope="col">해결 방법</th><th scope="col">확인 결과</th></tr></thead><tbody>${s.cases.map(row=>`<tr>${row.map((cell,i)=>i===0?`<th scope="row">${escape(cell)}</th>`:`<td>${escape(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
    if (s.type === 'team') body = `<table class="team-table"><thead><tr><th scope="col">구성원</th><th scope="col">담당 기능 · 역할</th><th scope="col">적용 기술</th></tr></thead><tbody>${s.members.map(([name,role,area,tech])=>`<tr><th scope="row">${escape(name)}${role ? `<small>${escape(role)}</small>` : ''}</th><td>${escape(area)}</td><td>${escape(tech)}</td></tr>`).join('')}</tbody></table><div class="actions">${jump('agenda','발표 목차로',true)}</div>`;
    if (['cards','agenda','guest'].includes(s.type)) body = cards(s.cards);
    if (s.type === 'overview') body = `<div class="overview-list">${s.cards.map(([title,text])=>`<div><h2>${escape(title)}</h2><p>${escape(text)}</p></div>`).join('')}</div>`;
    if (s.type === 'flow') body = `<div class="flow">${s.flow.map(([title,text],i)=>`${i ? '<span class="flow-arrow" aria-hidden="true">→</span>' : ''}<article class="flow-node"><h2>${escape(title)}</h2><p>${escape(text)}</p></article>`).join('')}</div>`;
    if (s.type === 'metrics') body = `<div class="metrics">${s.metrics.map(([value,title,text])=>`<article class="metric"><strong>${escape(value)}</strong><h2>${escape(title)}</h2><p>${escape(text)}</p></article>`).join('')}</div>`;
    if (s.type === 'menu') body = `<div class="menu-cards">${data.hubs.map(([label,id,desc])=>`<button type="button" class="menu-card" data-jump="${id}"><strong>${escape(label)}</strong><span>${escape(desc)}</span></button>`).join('')}</div><div class="actions"><button type="button" class="primary" data-tour-start>실제 기능 자동 시연</button>${jump('allmenus','전체 하위 메뉴')}${jump('guest','체험 모드 안내')}${jump('end','마무리')}</div>`;
    if (s.type === 'index') body = `<div class="menu-index">${renderIndex(s.onlyGroup)}</div>`;
    if (s.type === 'feature') {
      const image = s.image ? `<figure class="screen-evidence"><img src="${s.image}" alt="${escape(s.caption)}"><figcaption>${escape(s.caption)}</figcaption></figure>` : '';
      body = `<div class="feature-layout${s.image ? ' has-image' : ''}">${image}<div><div class="steps">${s.steps.map(step=>`<article class="step"><h2>${escape(step)}</h2></article>`).join('')}</div><div class="implementation"><strong>구현 연결</strong>${escape(s.implementation)}</div></div></div>`;
      const sameGroup = slides.filter(other=>other.group === s.group);
      body += `<div class="submenu" aria-label="${escape(s.group)} 하위 기능">${sameGroup.map(other=>`<button type="button" data-jump="${other.id}"${other.id === s.id ? ' class="active" aria-current="page"' : ''}>${escape(other.title)}</button>`).join('')}</div>`;
      body += `<div class="actions">${jump('demo','시연 메인으로',true)}<a href="${escape(s.url)}" target="_blank" rel="noopener noreferrer" data-live>실제 기능 시연 ↗</a></div><p class="route-label">실제 서비스 경로: ${escape(s.url)}</p>`;
    }
    if (s.callout) body += `<p class="callout">${escape(s.callout)}</p>`;
    if (s.footnote) body += `<p class="footnote">${escape(s.footnote)}</p>`;
    if (s.type === 'agenda') body += `<div class="actions">${jump('frontend','Frontend 구현',true)}${jump('backend','Backend 구현',true)}${jump('demo','전체 메뉴 시연')}</div>`;
    if (s.type === 'guest') body += `<div class="actions"><a class="primary" href="/demo/start?mode=user" target="_blank" rel="noopener noreferrer" data-live>실제 일반 사용자 체험 ↗</a><a href="/demo/start?mode=admin" target="_blank" rel="noopener noreferrer" data-live>실제 관리자 체험 ↗</a>${jump('demo','시연 메인으로')}</div>`;
    if (s.type !== 'feature' && s.type !== 'menu' && s.type !== 'agenda' && s.type !== 'guest') body += `<div class="actions">${jump('demo','시연 메인으로')}${s.id === 'safety-tech' ? jump('safety','위험안전도 기능') : ''}${s.id === 'ml-tech' ? jump('lifestyle','추천 결과 보기') : ''}</div>`;
    return `<p class="eyebrow">${escape(s.chapter)}</p><h1 id="slideTitle">${escape(s.title)}</h1><p class="summary">${escape(s.summary)}</p>${body}`;
  }
  function render(focus = false) {
    clearAdvance(); narrator.stop();
    const s = currentSlide();
    document.title = `${s.title} | ZipAI 발표`;
    $('slide').innerHTML = slideBody(s);
    $('counter').textContent = tour.active ? `${tour.position + 1} / ${data.tour.length}` : `${index + 1} / ${slides.length}`;
    $('progressFill').style.width = `${(tour.active ? (tour.position + 1) / data.tour.length : (index + 1) / slides.length) * 100}%`;
    $('prev').disabled = tour.active ? tour.position === 0 : index === 0; $('next').disabled = tour.active ? tour.position === data.tour.length - 1 : index === slides.length - 1;
    $('scriptText').textContent = s.script;
    $('menuContent').innerHTML = renderIndex();
    requestAnimationFrame(fitSlide);
    if (focus) { $('slide').focus({preventScroll:true}); window.scrollTo({top:0,behavior:'instant'}); }
    if (tour.active || (enabled && !muted && $('follow').checked && !document.hidden)) readCurrent();
    else if (muted) status.textContent = '음소거 상태입니다.';
  }
  function navigate(id) {
    const nextIndex = slides.findIndex(s=>s.id === id);
    if (nextIndex < 0) return;
    if (tour.active || pendingTour) endTour(false);
    if (nextIndex === index) { render(true); return; }
    location.hash = id;
  }
  function readCurrent() {
    clearAdvance();
    if (muted) { status.textContent = '음소거를 해제한 뒤 재생해 주세요.'; return; }
    const voice = voices.find(v=>v.voiceURI === selectedVoice) || voices[0];
    if (!voice && synth) {
      enabled = false; narrator.stop();
      if (tour.active) { pendingVoice = false; audioNotice = '한국어 음성이 없어 화면만 자동 진행합니다. 음성 설정에서 한국어 목소리를 확인해 주세요.'; status.textContent = audioNotice; }
      else { pendingVoice = true; status.textContent = '한국어 목소리를 확인 중입니다. 목록이 없으면 Windows 한국어 음성 설정을 확인해 주세요.'; }
      return;
    }
    pendingVoice = false;
    enabled = narrator.speak(currentSlide().script, voice, Number($('rate').value));
  }
  function loadVoices() {
    voices = synth ? koreanVoices(synth.getVoices()) : [];
    const previous = selectedVoice;
    $('voiceSelect').replaceChildren();
    if (!voices.length) {
      const option = document.createElement('option'); option.textContent = synth ? '한국어 음성 없음 / 불러오는 중' : '음성 읽기 미지원'; $('voiceSelect').append(option);
      if (!synth) { $('play').disabled = true; speechState('unsupported'); }
      return;
    }
    voices.forEach(v=>{const option=document.createElement('option');option.value=v.voiceURI;option.textContent=v.name; $('voiceSelect').append(option);});
    selectedVoice = voices.some(v=>v.voiceURI === previous) ? previous : (voices.find(v=>v.localService) || voices[0]).voiceURI;
    $('voiceSelect').value = selectedVoice;
    if (pendingTour && !document.hidden && voices.length) { pendingTour = false; startTour(); }
    else if ((pendingVoice || (tour.active && audioNotice)) && !document.hidden && !muted && !tour.paused) { audioNotice = ''; readCurrent(); }
  }
  function closeMenu() { $('menuDialog').close(); $('menuOpen').setAttribute('aria-expanded','false'); }
  document.addEventListener('click', event => {
    if (event.target.closest('[data-tour-start]')) { startTour(); return; }
    const target = event.target.closest('[data-jump]');
    if (target) {
      const inMenu = target.closest('#menuDialog'); if (inMenu) closeMenu();
      navigate(target.dataset.jump); return;
    }
    if (event.target.closest('[data-live]')) stopPresentationVoice();
  });
  $('prev').addEventListener('click',()=>moveSlide(-1));
  $('next').addEventListener('click',()=>moveSlide(1));
  $('tourPause').addEventListener('click',toggleTourPause);
  $('tourStop').addEventListener('click',()=>endTour(true));
  $('play').addEventListener('click',readCurrent);
  $('pause').addEventListener('click',()=>{clearAdvance();narrator.togglePause();});
  $('mute').addEventListener('click',()=>{muted=!muted;stopPresentationVoice();$('mute').setAttribute('aria-pressed',String(muted));$('mute').textContent=muted?'음소거 해제':'음소거';});
  $('voiceSelect').addEventListener('change',()=>{selectedVoice=$('voiceSelect').value;stopPresentationVoice();});
  $('rate').addEventListener('change',stopPresentationVoice);
  $('autoNext').addEventListener('change',clearAdvance);
  $('follow').addEventListener('change',()=>{if(!$('follow').checked){enabled=false;clearAdvance();}});
  $('settingsOpen').addEventListener('click',()=>{const open=$('settings').hidden;$('settings').hidden=!open;$('settingsOpen').setAttribute('aria-expanded',String(open));});
  $('settingsClose').addEventListener('click',()=>{$('settings').hidden=true;$('settingsOpen').setAttribute('aria-expanded','false');$('settingsOpen').focus();});
  $('menuOpen').addEventListener('click',()=>{stopPresentationVoice();$('menuDialog').showModal();$('menuOpen').setAttribute('aria-expanded','true');});
  $('menuClose').addEventListener('click',closeMenu);
  $('menuDialog').addEventListener('close',()=>{$('menuOpen').setAttribute('aria-expanded','false');});
  $('fullScreen').addEventListener('click',async()=>{
    try { if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen(); }
    catch (_){status.textContent='전체 화면 전환을 지원하지 않는 브라우저입니다. PC에서는 F11을 사용해 주세요.';}
  });
  document.addEventListener('fullscreenchange',()=>{$('fullScreen').textContent=document.fullscreenElement?'전체 화면 종료':'전체 화면';});
  window.addEventListener('hashchange',()=>{if(tour.active || pendingTour)endTour(false);index=slideIndex(slides,location.hash);render(true);});
  window.addEventListener('pagehide',stopPresentationVoice);
  document.addEventListener('visibilitychange',()=>{if(document.hidden){if(tour.active)pauseTour();else stopPresentationVoice();}});
  document.addEventListener('keydown',event=>{
    if (event.ctrlKey || event.altKey || event.metaKey || event.target.closest('input,select,textarea,[contenteditable=true]') || $('menuDialog').open || !$('settings').hidden) return;
    if(['ArrowRight','PageDown'].includes(event.key)){event.preventDefault();moveSlide(1);}
    if(['ArrowLeft','PageUp'].includes(event.key)){event.preventDefault();moveSlide(-1);}
    if(event.key==='Home'){event.preventDefault();navigate('cover');}
    if(event.key==='End'){event.preventDefault();navigate('end');}
    if(event.code==='Space' && !event.target.closest('button,a')){event.preventDefault();if(tour.active)toggleTourPause();else if(narrator.active)narrator.togglePause();else readCurrent();}
  });
  function fitSlide() {
    const viewport = $('slideViewport');
    const slide = $('slide');
    const width = viewport.clientWidth;
    const height = viewport.clientHeight;
    if (!width || !height) return;
    const scale = window.ZipaiPresentation.fitScale(width, height, Math.max(slide.offsetWidth, slide.scrollWidth), Math.max(slide.offsetHeight, slide.scrollHeight));
    slide.style.setProperty('--slide-scale', String(scale));
  }
  window.addEventListener('resize', () => requestAnimationFrame(fitSlide));
  document.addEventListener('fullscreenchange', () => requestAnimationFrame(fitSlide));
  $('slide').addEventListener('load', () => requestAnimationFrame(fitSlide), true);
  if (typeof ResizeObserver === 'function') {
    const sizeObserver = new ResizeObserver(() => requestAnimationFrame(fitSlide));
    sizeObserver.observe($('slideViewport')); sizeObserver.observe($('slide'));
    sizeObserver.observe(document.querySelector('.presentation-controls'));
  }
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(fitSlide);
  if(synth && typeof synth.addEventListener==='function')synth.addEventListener('voiceschanged',loadVoices);
  render(); loadVoices();
})();
