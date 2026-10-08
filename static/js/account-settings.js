(async function () {
  'use strict';
  const auth = window.ZipaiAuth;
  const section = document.getElementById('accountSettings');
  if (!auth || !section) return;
  await auth.ready;
  const user = auth.getUser();
  if (!user) return;
  const profile = document.getElementById('accountProfileForm');
  const username = document.getElementById('accountUsernameForm');
  const message = document.getElementById('accountSettingsMessage');
  const dialog = document.getElementById('accountSettingsDialog');
  const openButton = document.getElementById('accountSettingsOpen');
  const closeButton = document.getElementById('accountSettingsClose');
  function fillProfile() {
    const current = auth.getUser();
    if (!current) return;
    profile.elements.email.value = current.email || '';
    profile.elements.phone.value = current.phone || '';
    username.elements.userId.value = current.id || '';
  }
  fillProfile();
  if (dialog && openButton && closeButton) {
    openButton.addEventListener('click', function () {
      fillProfile(); message.textContent = '';
      if (!dialog.open) dialog.showModal();
      document.body.classList.add('account-dialog-open');
      profile.elements.email.focus({ preventScroll: true });
    });
    closeButton.addEventListener('click', function () { dialog.close(); });
    dialog.addEventListener('click', function (event) {
      const rect = dialog.getBoundingClientRect();
      if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close();
    });
    dialog.addEventListener('close', function () {
      document.body.classList.remove('account-dialog-open');
      section.querySelectorAll('input[type="password"]').forEach(function (input) { input.value = ''; });
      const withdrawal = section.querySelector('.account-withdraw-section');
      if (withdrawal) withdrawal.open = false;
      openButton.focus({ preventScroll: true });
    });
  }
  section.querySelectorAll('form[data-account-action]').forEach(function (form) {
    form.addEventListener('submit', async function (event) {
      event.preventDefault();
      const action = form.dataset.accountAction;
      const body = Object.fromEntries(new FormData(form));
      if (action === 'password' && body.newPassword !== body.confirmPassword) {
        message.textContent = '새 비밀번호와 확인 값이 다릅니다.';
        return;
      }
      if (action === 'withdraw' && !window.confirm('회원탈퇴를 진행할까요? 탈퇴 후 현재 계정으로 로그인할 수 없습니다.')) return;
      const button = form.querySelector('button[type="submit"]');
      button.disabled = true;
      message.textContent = '처리 중입니다.';
      try {
        const response = await fetch('/api/account' + (action === 'withdraw' ? '' : '/' + action), {
          method: action === 'withdraw' ? 'DELETE' : 'PUT', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
        });
        const result = await response.json().catch(function () { return {}; });
        if (!response.ok) throw new Error(result.message || '계정 변경에 실패했습니다.');
        form.querySelectorAll('input[type="password"]').forEach(function (input) { input.value = ''; });
        if (action === 'withdraw') {
          await auth.logout();
          window.location.href = '/member/login';
          return;
        }
        await auth.refreshUser();
        auth.updateLoginButtons();
        fillProfile();
        message.textContent = '변경이 완료되었습니다.';
        const heading = document.getElementById('mypageUserId');
        if (heading) heading.textContent = auth.getUser().id;
      } catch (error) { message.textContent = error.message; }
      finally { button.disabled = false; }
    });
  });
})();
