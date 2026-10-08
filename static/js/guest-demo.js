(function () {
  'use strict';
  const mode = new URLSearchParams(window.location.search).get('mode') === 'admin' ? 'admin' : 'user';
  const labels = mode === 'admin'
    ? { members: '회원', properties: '매물 검토', visits: '방문 승인', community: '게시글 관리', inquiries: '문의 답변', finance: '금융 정책', crawl: '항목별 수집 이력', audit: '관리자 변경 이력' }
    : { properties: '매물 등록', favorites: '찜한 매물', visits: '방문 신청', community: '게시글 작성', inquiries: '문의·답변 확인' };
  document.title = 'ZipAI ' + (mode === 'admin' ? '관리자 체험' : '일반 사용자 체험');
  document.getElementById('demoModeTitle').textContent = document.title;
  document.getElementById('demoModeBadge').textContent = mode === 'admin' ? '현재: 관리자 모드' : '현재: 일반 사용자 모드';
  const switchMode = document.getElementById('demoModeSwitch');
  switchMode.href = '/demo?mode=' + (mode === 'admin' ? 'user' : 'admin');
  switchMode.textContent = mode === 'admin' ? '일반 사용자 체험으로 전환' : '관리자 체험으로 전환';
  document.getElementById('demoAccount').hidden = mode === 'admin';
  document.getElementById('demoAccountLink').hidden = mode === 'admin';
  document.getElementById('demoWorkspaceTitle').textContent = mode === 'admin' ? '관리자 기능 체험' : '일반 사용자 기능 체험';
  document.getElementById('demoWorkspaceLink').textContent = mode === 'admin' ? '관리자 기능' : '사용자 기능';
  document.getElementById('demoWorkspaceHelp').textContent = mode === 'admin'
    ? '회원·매물·방문·문의·정책·수집 이력을 관리해 보세요. 일반 사용자 모드에서 작성한 문의를 선택하고 내용을 답변으로 수정한 뒤 답변 완료로 저장하면 일반 사용자 모드에서 확인할 수 있습니다.'
    : '매물 등록·찜하기·방문 신청·게시글·문의를 체험하세요. 관리자 모드의 답변과 승인 결과도 여기에서 확인할 수 있습니다.';

  const statusLabels = { pending: '대기', active: '활성·게시 중', approved: '승인·예약 확정', rejected: '거절', answered: '답변 완료', closed: '종료', completed: '반영 완료', partial: '부분 실패', failed: '실패', cancelled: '취소' };
  const message = document.getElementById('demoMessage');
  const form = document.getElementById('demoItemForm');
  let module = 'properties';
  let state = null;
  async function api(path, body) {
    const response = await fetch('/api/demo' + path + '?mode=' + mode, { credentials: 'same-origin', method: body ? 'POST' : 'GET', headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || '체험 요청에 실패했습니다.');
    return result;
  }
  async function perform(task) {
    document.querySelectorAll('button').forEach(function (button) { button.disabled = true; });
    try { state = await task(); render(); message.textContent = '체험 데이터에 반영했습니다.'; }
    catch (error) { message.textContent = error.message; }
    finally { document.querySelectorAll('button').forEach(function (button) { button.disabled = false; }); }
  }
  function node(tag, text) { const el = document.createElement(tag); el.textContent = text; return el; }
  function syncStatusOptions() {
    const allowed = mode === 'admin' ? null : module === 'visits' ? ['pending', 'cancelled'] : module === 'inquiries' ? ['pending'] : ['pending', 'active', 'closed'];
    Array.from(form.elements.status.options).forEach(function (option) {
      option.hidden = allowed !== null && !allowed.includes(option.value);
      option.disabled = option.hidden;
    });
    if (allowed && !allowed.includes(form.elements.status.value)) form.elements.status.value = allowed[0];
  }
  function render() {
    syncStatusOptions();
    const account = state.account;
    document.getElementById('demoAccountSummary').textContent = state.active ? '체험 회원: ' + account.id + ' · ' + account.email + ' · ' + account.phone : '탈퇴 처리되었습니다. 회원가입 체험으로 새 계정을 만들 수 있습니다.';
    const container = document.getElementById('demoItems'); container.replaceChildren();
    const rows = state.modules[module] || [];
    if (!rows.length) container.append(node('p', '항목이 없습니다. 새로 등록해 보세요.'));
    rows.forEach(function (item) {
      const card = node('article', ''); card.append(node('h3', item.title), node('p', statusLabels[item.status] || item.status), node('p', item.detail));
      if (item.reply) card.append(node('p', '관리자 답변: ' + item.reply));
      const edit = node('button', '정보·상태 수정'); edit.type = 'button';
      edit.addEventListener('click', function () { form.elements.id.value = item.id; form.elements.title.value = item.title; form.elements.status.value = item.status; form.elements.detail.value = mode === 'admin' && module === 'inquiries' && item.reply ? item.reply : item.detail; syncStatusOptions(); form.scrollIntoView({ block: 'center' }); });
      const remove = node('button', '삭제'); remove.type = 'button'; remove.addEventListener('click', function () { if (confirm('이 체험 항목을 삭제할까요?')) perform(function () { return api('/' + module + '/delete', { id: item.id }); }); });
      if (!(mode === 'user' && (module === 'inquiries' || (module === 'visits' && item.status === 'approved')))) card.append(edit);
      card.append(remove); container.append(card);
    });
    document.querySelectorAll('[data-demo-module]').forEach(function (button) { button.setAttribute('aria-pressed', String(button.dataset.demoModule === module)); });
  }
  Object.keys(labels).forEach(function (key) {
    const button = node('button', labels[key]); button.type = 'button'; button.dataset.demoModule = key;
    button.addEventListener('click', function () { module = key; form.reset(); form.elements.id.value = ''; render(); });
    document.getElementById('demoModules').append(button);
  });
  form.addEventListener('submit', function (event) { event.preventDefault(); const body = Object.fromEntries(new FormData(form)); perform(async function () { const result = await api('/' + module + '/' + (body.id ? 'update' : 'create'), body); form.reset(); form.elements.id.value = ''; return result; }); });
  document.getElementById('demoCancelEdit').addEventListener('click', function () { form.reset(); form.elements.id.value = ''; });
  document.getElementById('demoAccountForm').addEventListener('submit', function (event) {
    event.preventDefault(); const accountForm = event.currentTarget; const body = Object.fromEntries(new FormData(accountForm));
    if (body.action === 'withdraw' && !confirm('체험 계정을 탈퇴 처리할까요?')) return;
    perform(async function () { const result = await api('/account/' + body.action, body); accountForm.querySelectorAll('input[type=password]').forEach(function (input) { input.value = ''; }); return result; });
  });
  document.getElementById('demoReset').addEventListener('click', function () { if (confirm('이 방문자의 체험 내용을 초기화할까요?')) perform(function () { return api('/reset', {}); }); });
  perform(function () { return api(''); });
})();
