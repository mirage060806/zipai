(async function () {
  'use strict';
  const links = Array.from(document.querySelectorAll('.admin-quick-nav a'));
  function select(hash) {
    const selected = links.find(link => link.getAttribute('href') === hash) || links[0];
    links.forEach(link => {
      if (link === selected) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }
  select(location.hash);
  links.forEach(link => link.addEventListener('click', function () { select(link.getAttribute('href')); }));
  window.addEventListener('hashchange', function () { select(location.hash); });
  if (!window.ZipaiAuth) return;
  await window.ZipaiAuth.ready;
  const user = window.ZipaiAuth.getUser();
  if (!user || !user.demo) return;
  const role = document.querySelector('.admin-role');
  if (role) role.textContent = '관리자 체험';
  const account = document.getElementById('adminUserId');
  if (account) account.textContent = '체험 계정';
  const logout = document.getElementById('adminLogout');
  if (logout) logout.textContent = '체험 종료';
})();
