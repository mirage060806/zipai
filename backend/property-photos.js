/* 매물 사진 연결 모듈 — property.css의 .property-photo / .has-image / .photo-count 와 연동
 *
 * 카드 렌더러에서:  `<div class="property-photo ..." data-property-id="${item.id}"></div>` 로 두면
 * ZipaiPhotos.hydrate(listEl) 이 사진을 불러와 채워 줍니다.
 * 또는 ZipaiPhotos.photoHtml(item, toneIndex) 로 직접 HTML을 만들 수도 있습니다.
 */
(function () {
  'use strict';

  const cache = new Map();          // propertyId -> Promise<photos[]>
  const MAX_SIDE = 1600;            // 업로드 전 리사이즈 기준(px)

  function esc(value) {
    return String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  async function api(url, options) {
    const response = await fetch(url, { credentials: 'same-origin', ...options });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || '요청을 처리하지 못했습니다.');
    return data;
  }

  function fetchPhotos(propertyId) {
    if (!cache.has(propertyId)) {
      cache.set(propertyId, api(`/api/properties/${propertyId}/photos`).then((d) => d.items || []).catch((e) => { cache.delete(propertyId); throw e; }));
    }
    return cache.get(propertyId);
  }

  /* item.photos / item.thumbnail 이 이미 내려온 경우(API 응답) 바로 HTML 생성 */
  function photoHtml(item, tone) {
    const photos = Array.isArray(item.photos) ? item.photos : [];
    const cover = item.thumbnail || (photos[0] && photos[0].url);
    const count = item.photoCount || photos.length;
    const toneClass = `tone-${(Number(tone) || Number(item.id) || 0) % 5 + 1}`;
    if (!cover) return `<div class="property-photo ${toneClass}" data-property-id="${esc(item.id)}"></div>`;
    return `<div class="property-photo has-image ${toneClass}" data-property-id="${esc(item.id)}">` +
      `<img src="${esc(cover)}" alt="${esc(item.title || '매물 사진')}" loading="lazy" decoding="async">` +
      (count > 1 ? `<span class="photo-count">사진 ${count}장</span>` : '') + `</div>`;
  }

  function applyPhotos(box, photos, title) {
    if (!photos.length) return;
    box.classList.add('has-image');
    let img = box.querySelector('img');
    if (!img) { img = document.createElement('img'); img.loading = 'lazy'; img.decoding = 'async'; box.prepend(img); }
    img.src = photos[0].url;
    img.alt = title || '매물 사진';
    img.onerror = () => { img.remove(); box.classList.remove('has-image'); };   // 깨진 이미지면 기본 배경으로
    if (photos.length > 1 && !box.querySelector('.photo-count')) {
      const badge = document.createElement('span');
      badge.className = 'photo-count';
      badge.textContent = `사진 ${photos.length}장`;
      box.append(badge);
    }
  }

  /* container 안에서 아직 사진이 없는 .property-photo[data-property-id] 를 채움 */
  function hydrate(container) {
    const root = container || document;
    const boxes = Array.from(root.querySelectorAll('.property-photo[data-property-id]:not(.has-image):not([data-photo-loaded])'));
    if (!boxes.length) return;
    const load = (box) => {
      box.dataset.photoLoaded = '1';
      const id = Number(box.dataset.propertyId);
      if (!Number.isFinite(id) || id <= 0) return;           // 외부/공공 매물 등 숫자 ID가 아닌 경우 건너뜀
      fetchPhotos(id).then((photos) => applyPhotos(box, photos, box.dataset.title)).catch(() => {});
    };
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => { if (entry.isIntersecting) { observer.unobserve(entry.target); load(entry.target); } });
      }, { rootMargin: '200px' });
      boxes.forEach((box) => observer.observe(box));
    } else {
      boxes.forEach(load);
    }
  }

  /* 파일 → 리사이즈된 base64 */
  function toPayload(file) {
    return new Promise((resolve, reject) => {
      if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return reject(new Error('JPG, PNG, WEBP 사진만 올릴 수 있어요.'));
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('사진을 읽지 못했어요.'));
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('올바른 이미지가 아니에요.'));
        img.onload = () => {
          const scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.round(img.width * scale);
          canvas.height = Math.round(img.height * scale);
          canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
          const type = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
          resolve({ type, data: canvas.toDataURL(type, 0.86) });
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  async function upload(propertyId, files) {
    const images = await Promise.all(Array.from(files).map(toPayload));
    const data = await api(`/api/properties/${propertyId}/photos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ images })
    });
    cache.delete(Number(propertyId));
    return data.items;
  }

  async function remove(propertyId, photoId) {
    const data = await api(`/api/properties/${propertyId}/photos/${photoId}`, { method: 'DELETE' });
    cache.delete(Number(propertyId));
    return data.items;
  }

  /* #propertyList 에 카드가 추가될 때마다 자동으로 사진 채우기 */
  function autoHydrate(selector) {
    const list = document.querySelector(selector || '#propertyList');
    if (!list) return;
    hydrate(list);
    new MutationObserver(() => hydrate(list)).observe(list, { childList: true, subtree: true });
  }

  window.ZipaiPhotos = { photoHtml, hydrate, fetchPhotos, upload, remove, autoHydrate };
  document.addEventListener('DOMContentLoaded', () => autoHydrate('#propertyList'));
})();
