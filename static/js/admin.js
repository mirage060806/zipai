(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const deniedView = document.querySelector('[data-admin-view="denied"]');
  const adminView = document.querySelector('[data-admin-view="dashboard"]');
  const switchButton = document.getElementById('adminSwitchAccount');
  const logoutButton = document.getElementById('adminLogout');
  if (!auth || !deniedView || !adminView || !switchButton || !logoutButton) return;

  await auth.ready;
  const user = auth.getUser();
  const isAdmin = Boolean(user && user.role === 'admin');
  deniedView.hidden = isAdmin;
  adminView.hidden = !isAdmin;
  switchButton.addEventListener('click', logout);
  logoutButton.addEventListener('click', logout);
  async function logout() {
    await auth.logout();
    window.location.href = auth.resolvePage('login.html');
  }
  if (!isAdmin) return;

  const searchInput = document.getElementById('adminSearch');
  const categoryFilter = document.getElementById('adminCategoryFilter');
  const statusFilter = document.getElementById('adminStatusFilter');
  const list = document.getElementById('adminInquiryList');
  const empty = document.getElementById('adminEmpty');
  const resultCount = document.getElementById('adminResultCount');
  const detailEmpty = document.getElementById('adminDetailEmpty');
  const detailContent = document.getElementById('adminDetailContent');
  const statusSelect = document.getElementById('adminDetailStatus');
  const replyMessage = document.getElementById('adminReplyMessage');
  const statusLabels = { received: '접수', in_progress: '확인 중', answered: '답변 완료' };
  let selectedId = null;
  let items = [];
  document.getElementById('adminUserId').textContent = user.id;

  async function api(path, options) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: options && options.body ? { 'Content-Type': 'application/json' } : {},
      ...options
    });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '요청을 처리하지 못했습니다.');
    return payload;
  }

  function formatDate(value, withTime) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('ko-KR', withTime ? { dateStyle: 'medium', timeStyle: 'short' } : { dateStyle: 'medium' }).format(date);
  }

  function filteredItems() {
    const query = searchInput.value.trim().toLowerCase();
    return items.filter(function (item) {
      if (categoryFilter.value && item.category !== categoryFilter.value) return false;
      if (statusFilter.value && item.status !== statusFilter.value) return false;
      return !query || [item.title, item.email, item.message, item.username].join(' ').toLowerCase().includes(query);
    });
  }

  function updateStats() {
    document.getElementById('adminTotalCount').textContent = items.length;
    document.getElementById('adminNewCount').textContent = items.filter(function (item) { return item.status === 'received'; }).length;
    document.getElementById('adminProgressCount').textContent = items.filter(function (item) { return item.status === 'in_progress'; }).length;
    document.getElementById('adminCompleteCount').textContent = items.filter(function (item) { return item.status === 'answered'; }).length;
  }

  function renderDetail() {
    const item = items.find(function (entry) { return entry.id === selectedId; });
    detailEmpty.hidden = Boolean(item);
    detailContent.hidden = !item;
    if (!item) return;
    document.getElementById('adminDetailCategory').textContent = item.category;
    document.getElementById('adminDetailDate').textContent = formatDate(item.createdAt, true);
    document.getElementById('adminDetailDate').dateTime = item.createdAt;
    document.getElementById('adminDetailTitle').textContent = item.title;
    document.getElementById('adminDetailEmail').textContent = item.email;
    document.getElementById('adminDetailMessage').textContent = item.message;
    replyMessage.value = item.answer || '';
    statusSelect.value = item.status;
  }

  function render() {
    const visible = filteredItems();
    updateStats();
    list.replaceChildren();
    resultCount.textContent = visible.length + '건';
    empty.hidden = visible.length > 0;
    list.hidden = visible.length === 0;
    visible.forEach(function (item) {
      const button = document.createElement('button');
      const category = document.createElement('span');
      const copy = document.createElement('span');
      const title = document.createElement('strong');
      const meta = document.createElement('small');
      const status = document.createElement('span');
      button.type = 'button';
      button.className = 'admin-inquiry' + (item.id === selectedId ? ' is-active' : '');
      button.dataset.id = item.id;
      category.className = 'admin-inquiry-category';
      copy.className = 'admin-inquiry-copy';
      status.className = 'admin-status';
      category.textContent = item.category;
      title.textContent = item.title;
      meta.textContent = (item.username || item.email) + ' · ' + formatDate(item.createdAt, false);
      status.textContent = statusLabels[item.status] || item.status;
      status.dataset.status = item.status;
      copy.append(title, meta);
      button.append(category, copy, status);
      list.appendChild(button);
    });
    renderDetail();
  }

  async function load() {
    try {
      const payload = await api('/api/admin/inquiries');
      items = payload.items || [];
      render();
    } catch (error) {
      window.alert(error.message);
    }
  }

  async function loadProperties() {
    const target = document.getElementById('adminPropertyList');
    if (!target) return;
    try {
      const payload = await api('/api/admin/properties');
      target.replaceChildren();
      (payload.items || []).forEach(function (item) {
        const row = document.createElement('article');
        const copy = document.createElement('span');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const actions = document.createElement('span');
        row.className = 'admin-inquiry';
        copy.className = 'admin-inquiry-copy';
        title.textContent = item.title || '제목 없는 매물';
        meta.textContent = (item.owner || '-') + ' · ' + item.status;
        copy.append(title, meta);
        ['approved', 'rejected', 'closed'].forEach(function (status) {
          const button = document.createElement('button');
          button.type = 'button';
          button.textContent = status === 'approved' ? '승인' : status === 'rejected' ? '거절' : '종료';
          button.addEventListener('click', async function () {
            await api('/api/admin/properties/' + item.id + '/status', {
              method: 'PATCH', body: JSON.stringify({ status: status })
            });
            await loadProperties();
          });
          actions.appendChild(button);
        });
        row.append(copy, actions);
        target.appendChild(row);
      });
      if (!target.children.length) target.textContent = '검토할 등록 매물이 없습니다.';
    } catch (error) {
      target.textContent = error.message;
    }
  }

  async function loadPosts() {
    const target = document.getElementById('adminPostList');
    if (!target) return;
    try {
      const payload = await api('/api/admin/community/posts');
      target.replaceChildren();
      (payload.items || []).forEach(function (item) {
        const row = document.createElement('article');
        const copy = document.createElement('span');
        const title = document.createElement('strong');
        const meta = document.createElement('small');
        const remove = document.createElement('button');
        row.className = 'admin-inquiry';
        copy.className = 'admin-inquiry-copy';
        title.textContent = item.title;
        meta.textContent = item.username + ' · ' + formatDate(item.created_at, false);
        remove.type = 'button';
        remove.textContent = '삭제';
        remove.addEventListener('click', async function () {
          if (!window.confirm('이 게시글을 삭제할까요?')) return;
          await api('/api/admin/community/posts/' + item.id, { method: 'DELETE' });
          await loadPosts();
        });
        copy.append(title, meta);
        row.append(copy, remove);
        target.appendChild(row);
      });
      if (!target.children.length) target.textContent = '등록된 게시글이 없습니다.';
    } catch (error) {
      target.textContent = error.message;
    }
  }

  async function save(status, answer) {
    if (!selectedId) return;
    try {
      await api('/api/admin/inquiries/' + selectedId + '/answer', {
        method: 'PATCH',
        body: JSON.stringify({ status: status, answer: answer })
      });
      await load();
    } catch (error) {
      window.alert(error.message);
    }
  }

  list.addEventListener('click', function (event) {
    const button = event.target.closest('[data-id]');
    if (!button) return;
    selectedId = Number(button.dataset.id);
    render();
  });
  [searchInput, categoryFilter, statusFilter].forEach(function (control) {
    control.addEventListener(control.tagName === 'INPUT' ? 'input' : 'change', render);
  });
  document.getElementById('adminSaveStatus').addEventListener('click', function () {
    save(statusSelect.value, replyMessage.value.trim());
  });
  document.getElementById('adminSaveReply').addEventListener('click', function () {
    const answer = replyMessage.value.trim();
    if (!answer) {
      replyMessage.focus();
      return;
    }
    save('answered', answer);
  });
  document.getElementById('adminDeleteInquiry').hidden = true;
  await Promise.all([load(), loadProperties(), loadPosts()]);
})();
