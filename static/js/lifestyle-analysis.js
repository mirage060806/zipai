(async function () {
  'use strict';

  const rooms = [
    { id: 'room-1', title: '야탑역 도보 7분 원룸', area: '성남시 분당구 야탑동', price: '1,000 / 55만 원', time: '평일 19시 이후' },
    { id: 'room-2', title: '서현역 채광 좋은 투룸', area: '성남시 분당구 서현동', price: '2,000 / 68만 원', time: '주말 10시~17시' },
    { id: 'room-3', title: '모란역 풀옵션 오피스텔', area: '성남시 중원구 성남동', price: '500 / 62만 원', time: '평일·주말 협의' },
    { id: 'room-4', title: '정자역 반려동물 가능 원룸', area: '성남시 분당구 정자동', price: '1,500 / 60만 원', time: '토요일 오후' }
  ];
  const visitKey = 'zipaiRoomVisits';
  const offerKey = 'zipaiRoomOffers';
  let visitCache = [];
  let offerCache = [];
  const roomList = document.getElementById('availableRooms');
  const visitForm = document.getElementById('visitForm');
  const visitDate = visitForm.elements.date;
  const visitTime = document.getElementById('visitTime');
  const offerForm = document.getElementById('offerForm');
  const toast = document.getElementById('roomToast');
  const hero = document.getElementById('roomHero');
  const heroBackground = hero.querySelector('.room-hero-background');
  const stageControls = Array.from(document.querySelectorAll('.journey-row[data-stage]'));
  const stageImages = {
    select: '../../static/images/room-connect/room-select.png',
    reserve: '../../static/images/room-connect/visit-reservation.png',
    schedule: '../../static/images/room-connect/schedule-confirmation.png',
    contract: '../../static/images/room-connect/safe-contract.png'
  };
  let activeStage = 'select';
  let backgroundTimer = 0;

  Object.keys(stageImages).forEach(function (stage) {
    const image = new Image();
    image.src = stageImages[stage];
  });

  function showStage(stage, commit) {
    if (!stageImages[stage]) return;
    if (commit) activeStage = stage;
    window.clearTimeout(backgroundTimer);
    heroBackground.classList.add('is-switching');
    backgroundTimer = window.setTimeout(function () {
      heroBackground.style.backgroundImage = 'url("' + stageImages[stage] + '")';
      hero.dataset.stage = stage;
      heroBackground.classList.remove('is-switching');
    }, 120);
    stageControls.forEach(function (control) {
      const active = control.dataset.stage === stage;
      control.classList.toggle('is-active', active);
      if (active) control.setAttribute('aria-current', 'step');
      else control.removeAttribute('aria-current');
    });
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char];
    });
  }

  function read(key) {
    if (key === visitKey) return visitCache;
    if (key === offerKey) return offerCache;
    try {
      const value = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (error) {
      return [];
    }
  }

  function save(key, value) {
    if (key === visitKey) visitCache = value;
    if (key === offerKey) offerCache = value;
  }

  async function api(path, options) {
    const response = await fetch(path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '서버 요청을 처리하지 못했습니다.');
    return payload;
  }

  async function loadServerActivity() {
    const results = await Promise.all([api('/api/visits'), api('/api/room-offers')]);
    visitCache = results[0].items || [];
    offerCache = results[1].items || [];
  }

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    window.setTimeout(function () { toast.classList.remove('is-visible'); }, 2400);
  }

  function renderRooms() {
    roomList.innerHTML = rooms.map(function (room) {
      return '<button class="visit-room-card" type="button" data-room="' + room.id + '">' +
        '<span class="visit-room-photo"><i class="fa-solid fa-house"></i></span>' +
        '<span class="visit-room-copy"><span>방문 가능</span><h3>' + escapeHtml(room.title) + '</h3><p>' + escapeHtml(room.area) + '</p>' +
        '<span class="room-meta"><strong>' + escapeHtml(room.price) + '</strong><small>' + escapeHtml(room.time) + '</small></span></span></button>';
    }).join('');
  }

  function approvedSlot(roomId, date, time, ignoredId) {
    return read(visitKey).some(function (visit) {
      return visit.status === 'approved' && visit.roomId === roomId && visit.date === date && visit.time === time && visit.id !== ignoredId;
    });
  }

  function updateTimeOptions() {
    const roomId = document.getElementById('visitRoom').value;
    const date = visitDate.value;
    const previous = visitTime.value;
    if (!roomId || !date) {
      visitTime.innerHTML = '<option value="">날짜와 방을 먼저 선택해 주세요</option>';
      visitTime.disabled = true;
      return;
    }
    visitTime.disabled = false;
    visitTime.innerHTML = '<option value="">시간 선택</option>' + Array.from({ length: 24 }, function (_, hour) {
      const time = String(hour).padStart(2, '0') + ':00';
      const closed = approvedSlot(roomId, date, time);
      return '<option value="' + time + '"' + (closed ? ' disabled' : '') + '>' + time + (closed ? ' (마감)' : '') + '</option>';
    }).join('');
    if (previous && !approvedSlot(roomId, date, previous)) visitTime.value = previous;
  }

  function selectRoom(id) {
    const room = rooms.find(function (item) { return item.id === id; });
    if (!room) return;
    document.getElementById('visitRoom').value = room.id;
    document.getElementById('selectedRoomName').value = room.title;
    document.querySelectorAll('[data-room]').forEach(function (button) {
      button.classList.toggle('is-selected', button.dataset.room === room.id);
    });
    updateTimeOptions();
  }

  function renderActivity() {
    const visits = read(visitKey);
    const offers = read(offerKey);
    document.getElementById('visitCount').textContent = visits.length + '건';
    document.getElementById('offerCount').textContent = offers.length + '건';
    document.getElementById('visitList').innerHTML = visits.length ? visits.map(function (item) {
      const status = item.status || 'pending';
      const statusText = status === 'approved' ? '예약 확정' : status === 'rejected' ? '요청 거절' : '승인 대기';
      const actions = status === 'pending'
        ? '<div class="approval-actions"><button type="button" data-action="approve" data-visit-id="' + item.id + '">승인</button><button type="button" data-action="reject" data-visit-id="' + item.id + '">거절</button></div>'
        : '';
      return '<div class="activity-item"><div><strong>' + escapeHtml(item.title) + '</strong><small>' + escapeHtml(item.date) + ' · ' + escapeHtml(item.time) + ' · ' + escapeHtml(item.phone) + '</small>' + actions + '</div><span class="activity-status is-' + status + '">' + statusText + '</span></div>';
    }).join('') : '<div class="activity-empty">신청한 방문 일정이 없습니다.</div>';
    document.getElementById('offerList').innerHTML = offers.length ? offers.map(function (item) {
      return '<div class="activity-item"><div><strong>' + escapeHtml(item.title) + '</strong><small>입주 가능 ' + escapeHtml(item.moveIn) + ' · ' + escapeHtml(item.agreement) + '</small></div><span class="activity-status">연결 준비</span></div>';
    }).join('') : '<div class="activity-empty">내놓은 방이 없습니다.</div>';
  }

  roomList.addEventListener('click', function (event) {
    const card = event.target.closest('[data-room]');
    if (!card) return;
    selectRoom(card.dataset.room);
    visitForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
  visitDate.addEventListener('change', updateTimeOptions);

  visitForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    const data = new FormData(visitForm);
    const room = rooms.find(function (item) { return item.id === data.get('room'); });
    if (!room) {
      showToast('방문할 방을 먼저 선택해 주세요.');
      return;
    }
    if (!data.get('time') || approvedSlot(room.id, data.get('date'), data.get('time'))) {
      showToast('이미 마감된 시간이므로 다른 시간을 선택해 주세요.');
      updateTimeOptions();
      return;
    }
    try {
      const payload = await api('/api/visits', { method: 'POST', body: JSON.stringify({ roomId: room.id, title: room.title, date: data.get('date'), time: data.get('time'), phone: data.get('phone'), question: data.get('question') }) });
      visitCache.unshift(payload.item);
    } catch (error) {
      showToast(error.message);
      return;
    }
    visitForm.reset();
    document.getElementById('selectedRoomName').value = '방을 먼저 선택해 주세요';
    document.querySelectorAll('[data-room]').forEach(function (button) { button.classList.remove('is-selected'); });
    updateTimeOptions();
    renderActivity();
    showToast('방문 승인 요청이 접수되었습니다.');
  });

  document.getElementById('visitList').addEventListener('click', async function (event) {
    const button = event.target.closest('[data-visit-id]');
    if (!button) return;
    const id = Number(button.dataset.visitId);
    const visits = read(visitKey);
    const visit = visits.find(function (item) { return item.id === id; });
    if (!visit || (visit.status && visit.status !== 'pending')) return;
    try {
      const action = button.dataset.action === 'approve' ? 'approve' : 'reject';
      const payload = await api('/api/visits/' + visit.id + '/' + action, { method: 'PATCH', body: '{}' });
      Object.assign(visit, payload.item);
      showToast(action === 'approve' ? '방문 요청을 승인했습니다. 예약이 확정되었습니다.' : '방문 요청을 거절했습니다.');
    } catch (error) {
      showToast(error.message);
      return;
    }
    renderActivity();
    updateTimeOptions();
  });

  offerForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    const data = new FormData(offerForm);
    const offer = {
      title: data.get('title'), district: data.get('district'),
      deposit: data.get('deposit'), monthly: data.get('monthly'), maintenance: data.get('maintenance'),
      contractEnd: data.get('contractEnd'), moveIn: data.get('moveIn'),
      availableTime: data.get('availableTime'), agreement: data.get('agreement'), description: data.get('description')
    };
    try {
      const payload = await api('/api/room-offers', { method: 'POST', body: JSON.stringify(offer) });
      offerCache.unshift(payload.item);
    } catch (error) {
      showToast(error.message);
      return;
    }
    offerForm.reset();
    renderActivity();
    showToast('퇴실 예정 방이 등록되었습니다.');
    document.getElementById('activitySection').scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  document.querySelectorAll('[data-scroll]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.getElementById(button.dataset.scroll).scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  stageControls.forEach(function (control) {
    control.addEventListener('mouseenter', function () { showStage(control.dataset.stage, false); });
    control.addEventListener('focus', function () { showStage(control.dataset.stage, false); });
    control.addEventListener('mouseleave', function () { showStage(activeStage, false); });
    control.addEventListener('blur', function () { showStage(activeStage, false); });
    if (control.tagName === 'BUTTON') {
      control.addEventListener('click', function () {
        showStage(control.dataset.stage, true);
        const target = document.getElementById(control.dataset.target);
        if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });
    }
  });

  if ('IntersectionObserver' in window) {
    const sectionStages = [
      { element: document.getElementById('availableRooms'), stage: 'select' },
      { element: document.getElementById('visitForm'), stage: 'reserve' },
      { element: document.getElementById('activitySection'), stage: 'schedule' }
    ];
    const stageObserver = new IntersectionObserver(function (entries) {
      const visible = entries.filter(function (entry) { return entry.isIntersecting; }).sort(function (a, b) { return b.intersectionRatio - a.intersectionRatio; })[0];
      if (!visible) return;
      const match = sectionStages.find(function (item) { return item.element === visible.target; });
      if (match) {
        activeStage = match.stage;
        stageControls.forEach(function (control) {
          const active = control.dataset.stage === activeStage;
          control.classList.toggle('is-active', active);
          if (active) control.setAttribute('aria-current', 'step');
          else control.removeAttribute('aria-current');
        });
      }
    }, { threshold: [0.35, 0.6] });
    sectionStages.forEach(function (item) { if (item.element) stageObserver.observe(item.element); });
  }

  renderRooms();
  if (window.ZipaiAuth) await window.ZipaiAuth.ready;
  try {
    await loadServerActivity();
  } catch (error) {
    showToast(error.message);
  }
  renderActivity();
  updateTimeOptions();
})();
