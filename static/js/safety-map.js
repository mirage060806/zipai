(function () {
  'use strict';

  const host = document.querySelector('[data-safety-map]');
  if (!host) return;

  const canvas = host.querySelector('.safety-map-canvas');
  const status = host.querySelector('.safety-map-status');
  const filterButtons = Array.from(host.querySelectorAll('[data-map-filter]'));
  const filterTypes = ['cctv', 'police', 'bell', 'light', 'women'];
  const activeFilters = new Set(filterTypes);
  const markerColors = {
    cctv: '#2563eb', police: '#15803d', bell: '#ea580c',
    light: '#ca8a04', women: '#9333ea'
  };
  const state = {
    map: null, circle: null, centerMarker: null, popup: null,
    entries: [], primaryFacilities: [], womenFacilities: [],
    center: null, radiusMeters: 500, ready: false, initialization: null
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function setStatus(title, message, isError) {
    if (!status) return;
    status.hidden = false;
    status.classList.toggle('is-error', Boolean(isError));
    status.innerHTML = '<strong>' + escapeHtml(title) + '</strong><span>' + escapeHtml(message) + '</span>';
  }

  function hideStatus() {
    if (status) status.hidden = true;
  }

  function validCenter(value) {
    if (!value) return null;
    const latitude = Number(value.latitude);
    const longitude = Number(value.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
    return Object.assign({}, value, { latitude: latitude, longitude: longitude });
  }

  function ensureMap() {
    if (state.ready) return Promise.resolve(state.map);
    if (state.initialization) return state.initialization;
    setStatus('지도를 불러오는 중입니다.', '잠시만 기다려 주세요.', false);
    state.initialization = Promise.resolve().then(function () {
      if (!window.L || typeof window.L.map !== 'function') {
        throw new Error('Leaflet is unavailable');
      }
      const canvasRect = canvas.getBoundingClientRect();
      if (canvasRect.width < 1 || canvasRect.height < 1) {
        throw new Error('Map container has no visible size: '
          + Math.round(canvasRect.width) + 'x' + Math.round(canvasRect.height));
      }
      const initial = validCenter(state.center) || { latitude: 37.5665, longitude: 126.9780 };
      state.map = window.L.map(canvas, {
        zoomControl: false,
        minZoom: 7,
        maxZoom: 19,
        scrollWheelZoom: false,
        keyboard: false
      }).setView([initial.latitude, initial.longitude], 14);
      window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
      }).addTo(state.map);
      state.map.on('click', closeFacilityOverlay);
      state.ready = true;
      host.classList.remove('is-map-unavailable');
      if (state.center) renderCurrentState();
      else setStatus('지도 준비', '지역을 검색하면 안전시설 위치가 표시됩니다.', false);
      [0, 250, 600].forEach(function (delay) {
        window.setTimeout(function () {
          if (!state.ready || !state.map) return;
          state.map.invalidateSize();
          if (state.center) state.map.setView([state.center.latitude, state.center.longitude], state.map.getZoom());
        }, delay);
      });
      return state.map;
    }).catch(function (error) {
      state.initialization = null;
      host.classList.add('is-map-unavailable');
      const message = !window.L
        ? '지도 라이브러리를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.'
        : '지도 실행 중 오류가 발생했습니다. 브라우저 콘솔을 확인해 주세요.';
      console.error('[ZipAI Safety Map]', error);
      setStatus('지도를 불러오지 못했습니다.', message, true);
      return null;
    });
    return state.initialization;
  }

  function facilityType(facility, women) {
    if (women) return 'women';
    const type = String(facility.facilityType || '').toUpperCase();
    if (type === 'POLICE_STATION' || type === 'POLICE_BOX') return 'police';
    if (type === 'SAFETY_BELL' || type === 'EMERGENCY_BELL') return 'bell';
    if (type === 'STREET_LIGHT' || type === 'SECURITY_LIGHT') return 'light';
    return 'cctv';
  }

  function typeLabel(facility, type) {
    if (facility.label) return facility.label;
    if (type === 'cctv') return 'CCTV';
    if (type === 'police') return facility.facilityType === 'POLICE_BOX' ? '파출소·지구대' : '경찰서';
    if (type === 'bell') return '안전 비상벨';
    if (type === 'light') return '보안등';
    if (facility.facilityType === 'SAFE_PARCEL_LOCKER') return '안심택배함';
    if (facility.facilityType === 'SAFE_STORE') return '여성안심점포';
    if (facility.facilityType === 'SAFE_ROUTE') return '여성안심길';
    if (facility.facilityType === 'SAFE_HOUSE') return '여성안심지킴이집';
    return '여성안전시설';
  }

  function markerContent(type) {
    const symbols = { cctv: '●', police: '◆', bell: '!', light: '✦', women: '♥' };
    return '<span class="safety-map-marker" style="--marker-color:' + markerColors[type] + '">'
      + '<span>' + symbols[type] + '</span></span>';
  }

  function clearMarkers() {
    closeFacilityOverlay();
    state.entries.forEach(function (entry) { entry.marker.remove(); });
    state.entries = [];
  }

  function createMarker(facility, type) {
    const position = validCenter(facility);
    if (!position) return null;
    const marker = window.L.marker([position.latitude, position.longitude], {
      icon: window.L.divIcon({
        className: '',
        html: markerContent(type),
        iconSize: [36, 42],
        iconAnchor: [18, 42]
      }),
      title: (facility.name || typeLabel(facility, type)) + ' · ' + typeLabel(facility, type),
      keyboard: true
    });
    marker.on('click', function () {
      openFacilityOverlay(marker, facility, type);
    });
    if (activeFilters.has(type)) marker.addTo(state.map);
    return { marker: marker, facility: facility, type: type };
  }

  function rebuildMarkers() {
    if (!state.ready) return;
    clearMarkers();
    state.primaryFacilities.forEach(function (facility) {
      const entry = createMarker(facility, facilityType(facility, false));
      if (entry) state.entries.push(entry);
    });
    state.womenFacilities.forEach(function (facility) {
      const entry = createMarker(facility, facilityType(facility, true));
      if (entry) state.entries.push(entry);
    });
  }

  function updateVisibleMarkers() {
    closeFacilityOverlay();
    state.entries.forEach(function (entry) {
      if (activeFilters.has(entry.type)) entry.marker.addTo(state.map);
      else state.map.removeLayer(entry.marker);
    });
  }

  function renderCenter() {
    if (state.centerMarker) state.map.removeLayer(state.centerMarker);
    state.centerMarker = window.L.marker([state.center.latitude, state.center.longitude], {
      icon: window.L.divIcon({
        className: '',
        html: '<div class="safety-search-center" title="검색 위치"><span></span><strong>검색 위치</strong></div>',
        iconSize: [80, 36],
        iconAnchor: [40, 18]
      }),
      keyboard: false,
      zIndexOffset: 100
    }).addTo(state.map);
  }

  function updateRadiusCircle() {
    if (state.circle) state.map.removeLayer(state.circle);
    state.circle = window.L.circle([state.center.latitude, state.center.longitude], {
      radius: state.radiusMeters,
      weight: 2, color: '#2563eb', opacity: 0.72,
      fillColor: '#60a5fa', fillOpacity: 0.09,
      interactive: false
    }).addTo(state.map);
  }

  function fitBounds() {
    const latDelta = state.radiusMeters / 111320;
    const lngDelta = state.radiusMeters / (111320 * Math.max(0.25, Math.cos(state.center.latitude * Math.PI / 180)));
    const bounds = window.L.latLngBounds(
      [state.center.latitude - latDelta, state.center.longitude - lngDelta],
      [state.center.latitude + latDelta, state.center.longitude + lngDelta]
    );
    state.map.fitBounds(bounds, { paddingTopLeft: [56, 56], paddingBottomRight: [56, 88] });
  }

  function renderCurrentState() {
    if (!state.ready || !state.center) return;
    hideStatus();
    renderCenter();
    updateRadiusCircle();
    rebuildMarkers();
    fitBounds();
  }

  function openFacilityOverlay(marker, facility, type) {
    closeFacilityOverlay();
    const name = facility.name || [facility.brandName, facility.storeName].filter(Boolean).join(' ') || typeLabel(facility, type);
    const source = [facility.sourceName, facility.sourceUpdatedAt || facility.dataYear].filter(Boolean).join(' · ');
    const content = '<article class="safety-facility-card" data-tone="' + type + '"><button type="button" class="safety-overlay-close" aria-label="시설 정보 닫기">×</button>'
      + '<span class="safety-facility-type">' + escapeHtml(typeLabel(facility, type)) + '</span><strong>' + escapeHtml(name) + '</strong>'
      + '<p>' + escapeHtml(facility.address || '주소 정보 없음') + '</p><span>검색 위치에서 ' + escapeHtml(String(facility.distanceMeters == null ? '-' : facility.distanceMeters)) + 'm</span>'
      + (source ? '<small>' + escapeHtml(source) + '</small>' : '') + '</article>';
    state.popup = window.L.popup({
      closeButton: false,
      className: 'safety-map-popup',
      offset: [0, -12]
    }).setLatLng(marker.getLatLng()).setContent(content).openOn(state.map);
    window.setTimeout(function () {
      const close = host.querySelector('.safety-overlay-close');
      if (close) close.addEventListener('click', closeFacilityOverlay, { once: true });
    }, 0);
  }

  function closeFacilityOverlay() {
    if (state.popup && state.map) state.map.closePopup(state.popup);
    state.popup = null;
  }

  function render(payload) {
    state.center = validCenter(payload && payload.center);
    state.radiusMeters = Math.max(100, Number(payload && payload.radiusMeters) || 500);
    state.primaryFacilities = Array.isArray(payload && payload.facilities) ? payload.facilities : [];
    state.womenFacilities = [];
    ensureMap().then(function (map) { if (map && state.center) renderCurrentState(); });
  }

  function appendWomenFacilities(payload) {
    if (payload && validCenter(payload.center)) state.center = validCenter(payload.center);
    if (payload && payload.radiusMeters) state.radiusMeters = Math.max(100, Number(payload.radiusMeters) || 500);
    state.womenFacilities = Array.isArray(payload && payload.facilities) ? payload.facilities : [];
    ensureMap().then(function (map) { if (map && state.center) rebuildMarkers(); });
  }

  function reset() {
    state.primaryFacilities = [];
    state.womenFacilities = [];
    state.center = null;
    clearMarkers();
    if (state.circle && state.map) state.map.removeLayer(state.circle);
    if (state.centerMarker && state.map) state.map.removeLayer(state.centerMarker);
    state.circle = null;
    state.centerMarker = null;
    if (state.ready) setStatus('지도 준비', '지역을 검색하면 안전시설 위치가 표시됩니다.', false);
  }

  function relayout() {
    ensureMap().then(function (map) {
      if (!map) return;
      map.invalidateSize();
      if (state.center) {
        map.setView([state.center.latitude, state.center.longitude], map.getZoom());
        fitBounds();
      }
    });
  }

  function updateFilterButtonState() {
    filterButtons.forEach(function (button) {
      const type = button.dataset.mapFilter;
      const pressed = type === 'all' ? activeFilters.size === filterTypes.length : activeFilters.has(type);
      button.setAttribute('aria-pressed', String(pressed));
    });
  }

  filterButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      const type = button.dataset.mapFilter;
      if (type === 'all') {
        if (activeFilters.size === filterTypes.length) activeFilters.clear();
        else filterTypes.forEach(function (item) { activeFilters.add(item); });
      } else if (activeFilters.has(type)) activeFilters.delete(type);
      else activeFilters.add(type);
      updateFilterButtonState();
      updateVisibleMarkers();
    });
  });

  host.querySelectorAll('[data-map-action]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (!state.ready) return;
      const action = button.dataset.mapAction;
      if (action === 'zoom-in') state.map.setZoom(Math.min(19, state.map.getZoom() + 1));
      if (action === 'zoom-out') state.map.setZoom(Math.max(7, state.map.getZoom() - 1));
      if (action === 'recenter' && state.center) {
        state.map.panTo([state.center.latitude, state.center.longitude]);
        fitBounds();
      }
    });
  });

  const legendToggle = host.querySelector('.map-legend-toggle');
  if (legendToggle) {
    legendToggle.addEventListener('click', function () {
      const expanded = legendToggle.getAttribute('aria-expanded') !== 'true';
      legendToggle.setAttribute('aria-expanded', String(expanded));
      host.classList.toggle('is-legend-open', expanded);
    });
  }

  window.ZipaiSafetyMap = {
    render: render,
    appendWomenFacilities: appendWomenFacilities,
    reset: reset,
    relayout: relayout,
    closeFacilityOverlay: closeFacilityOverlay
  };

  if ('ResizeObserver' in window) {
    let resizeFrame = null;
    const resizeObserver = new ResizeObserver(function () {
      if (!state.ready || !canvas.clientWidth || !canvas.clientHeight) return;
      if (resizeFrame) window.cancelAnimationFrame(resizeFrame);
      resizeFrame = window.requestAnimationFrame(function () {
        state.map.invalidateSize();
        if (state.center) state.map.setView([state.center.latitude, state.center.longitude], state.map.getZoom());
      });
    });
    resizeObserver.observe(canvas);
  }

  if (!host.closest('[hidden]')) ensureMap();
})();
