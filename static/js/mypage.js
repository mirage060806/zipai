(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const guestView = document.querySelector('[data-mypage-view="guest"]');
  const memberView = document.querySelector('[data-mypage-view="member"]');
  const userId = document.getElementById('mypageUserId');
  const loginTime = document.getElementById('mypageLoginTime');
  const favoriteCount = document.getElementById('mypageFavoriteCount');
  const favoriteCountText = document.getElementById('mypageFavoriteCountText');
  const unreadCount = document.getElementById('mypageUnreadCount');
  const inquiryCount = document.getElementById('mypageInquiryCount');
  const inquiries = document.getElementById('mypageInquiries');
  const logoutButton = document.getElementById('mypageLogout');
  const diagnosis = document.getElementById('mypageDiagnosis');
  const notifications = document.getElementById('mypageNotifications');
  const readAllNotifications = document.getElementById('mypageReadAllNotifications');

  if (!auth || !guestView || !memberView || !userId || !loginTime || !favoriteCount || !favoriteCountText || !logoutButton) return;
  await auth.ready;

  function formatLoginTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '로그인 시간 정보 없음';
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(date);
  }

  function render() {
    const user = auth.getUser();
    guestView.hidden = Boolean(user);
    memberView.hidden = !user;
    if (!user) return;

    userId.textContent = user.id + '님';
    loginTime.textContent = formatLoginTime(user.loginAt);
    auth.updateLoginButtons();
    loadFavorites();
    loadLatestDiagnosis();
    loadNotifications();
    loadInquiries();
    loadVisits();
    const adminLink = document.getElementById('mypageAdminLink');
    if (adminLink) adminLink.hidden = user.role !== 'admin';
  }

  async function loadFavorites() {
    try {
      const response = await fetch('/api/favorites', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('찜한 매물을 불러오지 못했습니다.');
      const payload = await response.json();
      const savedCount = Array.isArray(payload.ids) ? payload.ids.length : 0;
      favoriteCount.textContent = savedCount;
      favoriteCountText.textContent = savedCount + '개 매물';
    } catch (error) {
      favoriteCount.textContent = '0';
      favoriteCountText.textContent = '찜한 매물';
    }
  }

  async function loadNotifications() {
    if (!notifications) return;
    try {
      const response = await fetch('/api/notifications', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('알림을 불러오지 못했습니다.');
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (unreadCount) unreadCount.textContent = Number(payload.unreadCount || 0);
      if (readAllNotifications) readAllNotifications.hidden = !items.some(function (item) { return !item.read; });
      notifications.replaceChildren();
      if (!items.length) {
        const empty = document.createElement('div');
        empty.className = 'mypage-notification-empty';
        empty.innerHTML = '<i class="fa-regular fa-bell" aria-hidden="true"></i><div><strong>새 알림이 없습니다</strong><p>새로운 소식이 도착하면 이곳에서 확인할 수 있습니다.</p></div>';
        notifications.appendChild(empty);
        return;
      }
      items.forEach(function (item) {
        const article = document.createElement('article');
        const icon = document.createElement('i');
        const copy = document.createElement('div');
        article.className = 'mypage-notification-item';
        article.classList.toggle('is-unread', !item.read);
        icon.className = 'fa-solid fa-bell';
        icon.setAttribute('aria-hidden', 'true');
        const title = document.createElement('strong');
        const message = document.createElement('p');
        const meta = document.createElement('small');
        title.textContent = item.title || 'ZipAI 알림';
        message.textContent = item.message || '';
        meta.textContent = formatLoginTime(item.createdAt);
        copy.append(title, message, meta);
        article.append(icon, copy);
        if (item.targetUrl) {
          article.classList.add('is-linked');
          article.tabIndex = 0;
          article.setAttribute('role', 'link');
          article.addEventListener('click', async function () {
            if (!item.read) {
              await fetch('/api/notifications/' + item.id + '/read', {
                method: 'PATCH', credentials: 'same-origin'
              }).catch(function () { /* 이동은 유지 */ });
            }
            window.location.href = item.targetUrl;
          });
          article.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              article.click();
            }
          });
        }
        notifications.appendChild(article);
      });
    } catch (error) {
      notifications.innerHTML = '<p class="mypage-notification-error">알림을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>';
    }
  }

  if (readAllNotifications) {
    readAllNotifications.addEventListener('click', async function () {
      readAllNotifications.disabled = true;
      try {
        const response = await fetch('/api/notifications/read-all', {
          method: 'PATCH', credentials: 'same-origin'
        });
        if (!response.ok) throw new Error('알림을 처리하지 못했습니다.');
        await loadNotifications();
        auth.updateLoginButtons();
      } finally {
        readAllNotifications.disabled = false;
      }
    });
  }

  async function loadInquiries() {
    if (!inquiries) return;
    const labels = { received: '접수', in_progress: '확인 중', answered: '답변 완료' };
    try {
      const response = await fetch('/api/inquiries', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('문의내역을 불러오지 못했습니다.');
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (inquiryCount) inquiryCount.textContent = items.length;
      inquiries.replaceChildren();
      if (!items.length) {
        inquiries.innerHTML = '<p>접수한 문의가 없습니다. 고객센터에서 궁금한 내용을 문의할 수 있습니다.</p>';
        return;
      }
      items.slice(0, 3).forEach(function (item) {
        const article = document.createElement('article');
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const status = document.createElement('span');
        article.className = 'mypage-inquiry-item';
        title.textContent = item.title || '제목 없는 문의';
        meta.textContent = (item.category || '기타') + ' · ' + formatLoginTime(item.updatedAt || item.createdAt);
        status.className = 'mypage-inquiry-status';
        status.dataset.status = item.status || '';
        status.textContent = labels[item.status] || item.status || '상태 확인';
        copy.append(title, meta);
        article.append(copy, status);
        inquiries.appendChild(article);
      });
    } catch (error) {
      inquiries.innerHTML = '<p>문의내역을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>';
    }
  }

  async function loadLatestDiagnosis() {
    if (!diagnosis) return;
    try {
      const response = await fetch('/api/fraud-diagnoses/latest', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('진단 결과를 불러오지 못했습니다.');
      const payload = await response.json();
      if (!payload.available || !payload.diagnosis) {
        diagnosis.innerHTML = '<p>저장된 진단 결과가 없습니다.</p><a href="/defense/checklist">안전진단 시작하기</a>';
        return;
      }
      const item = payload.diagnosis;
      const labels = { safe: '비교적 안전', caution: '주의 필요', danger: '위험 신호' };
      diagnosis.innerHTML = '<div class="mypage-diagnosis-score" data-level="' + item.riskLevel + '"><strong>'
        + item.finalScore + '</strong><span>점</span></div><div><strong>' + (labels[item.riskLevel] || '진단 완료')
        + '</strong><p>안전 ' + item.safeCount + ' · 주의 ' + item.cautionCount + ' · 위험 ' + item.dangerCount
        + (item.jeonseRatio == null ? '' : ' · 전세가율 ' + Number(item.jeonseRatio).toFixed(1).replace('.0', '') + '%')
        + '</p><small>' + formatLoginTime(item.updatedAt) + '</small></div><a href="/defense/result">결과 보기</a>';
    } catch (error) {
      diagnosis.innerHTML = '<p>최근 진단 결과를 불러오지 못했습니다.</p>';
    }
  }

  logoutButton.addEventListener('click', async function () {
    await auth.logout();
    window.location.href = auth.resolvePage('login.html');
  });


  const visitMessage = document.getElementById('mypageVisitMessage');
  const visitRefresh = document.getElementById('mypageRefreshVisits');
  async function visitApi(path, options) {
    const response = await fetch(path, { credentials: 'same-origin', ...(options || {}) });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) {
      if (response.status === 401) await auth.requireMember();
      throw new Error(payload.message || '방문 요청을 처리하지 못했습니다.');
    }
    return payload;
  }
  function renderVisits(targetId, items, received) {
    const target = document.getElementById(targetId);
    if (!target) return;
    target.replaceChildren();
    if (!items.length) {
      const empty = document.createElement('p');
      empty.textContent = received ? '받은 방문 요청이 없습니다.' : '신청한 방문 일정이 없습니다.';
      target.appendChild(empty); return;
    }
    const labels = { pending: '승인 대기', approved: '예약 확정', rejected: '요청 거절', reschedule_requested: '일정 변경 요청', completed: '방문 완료', no_show: '미방문', cancelled_by_user: '신청자 취소', cancelled_by_admin: '관리자 취소' };
    items.forEach(function (item) {
      const article = document.createElement('article');
      article.className = 'mypage-visit-item';
      const title = document.createElement('strong');
      const details = document.createElement('p');
      const status = document.createElement('span');
      title.textContent = item.title;
      details.textContent = item.date + ' · ' + item.time + (received ? ' · 연락처 ' + item.phone : '');
      status.textContent = labels[item.status] || '상태 확인';
      article.append(title, details, status);
      if (received && item.question) {
        const question = document.createElement('p'); question.textContent = item.question; article.appendChild(question);
      }
      if (received && item.manageable && item.status === 'pending') {
        const actions = document.createElement('div'); actions.className = 'mypage-visit-actions';
        ['approve', 'reject'].forEach(function (action) {
          const button = document.createElement('button'); button.type = 'button';
          button.textContent = action === 'approve' ? '승인' : '거절';
          button.addEventListener('click', async function () {
            actions.querySelectorAll('button').forEach(function (b) { b.disabled = true; });
            try {
              await visitApi('/api/visits/' + item.id + '/' + action, { method: 'PATCH' });
              visitMessage.textContent = action === 'approve' ? '방문 요청을 승인했습니다.' : '방문 요청을 거절했습니다.';
              await loadVisits();
            } catch (error) { visitMessage.textContent = error.message; }
            finally { actions.querySelectorAll('button').forEach(function (b) { b.disabled = false; }); }
          });
          actions.appendChild(button);
        });
        article.appendChild(actions);
      }
      target.appendChild(article);
    });
  }
  async function loadVisits() {
    if (!document.getElementById('mypageSentVisits')) return;
    try {
      const data = await visitApi('/api/visits/activity');
      renderVisits('mypageSentVisits', data.sent || [], false);
      renderVisits('mypageReceivedVisits', data.received || [], true);
    } catch (error) {
      ['mypageSentVisits', 'mypageReceivedVisits'].forEach(function (id) {
        document.getElementById(id).textContent = '내역을 불러오지 못했습니다. 새로고침으로 다시 확인하세요.';
      });
      visitMessage.textContent = error.message;
    }
  }
  if (visitRefresh) visitRefresh.addEventListener('click', loadVisits);

  render();
})();
