(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const guestView = document.querySelector('[data-mypage-view="guest"]');
  const memberView = document.querySelector('[data-mypage-view="member"]');
  const userId = document.getElementById('mypageUserId');
  const loginTime = document.getElementById('mypageLoginTime');
  const favoriteCount = document.getElementById('mypageFavoriteCount');
  const favoriteCountText = document.getElementById('mypageFavoriteCountText');
  const logoutButton = document.getElementById('mypageLogout');

  if (!auth || !guestView || !memberView || !userId || !loginTime || !favoriteCount || !favoriteCountText || !logoutButton) return;
  await auth.ready;

  function getFavoriteCount() {
    try {
      const favorites = JSON.parse(localStorage.getItem('zipdosoFavorites') || '[]');
      return Array.isArray(favorites) ? favorites.length : 0;
    } catch (error) {
      return 0;
    }
  }

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
    const savedCount = getFavoriteCount();
    favoriteCount.textContent = savedCount;
    favoriteCountText.textContent = savedCount + '개 매물';
    auth.updateLoginButtons();
  }

  logoutButton.addEventListener('click', async function () {
    await auth.logout();
    window.location.href = auth.resolvePage('login.html');
  });

  render();
})();
