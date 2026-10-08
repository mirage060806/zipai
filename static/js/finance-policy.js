(function () {
  'use strict';

  const list = document.getElementById('financePolicyList');
  const category = document.getElementById('financeCategory');
  const target = document.getElementById('financeTarget');
  if (!list || !category || !target) return;

  const mobile = window.matchMedia('(max-width: 700px)');
  let shown = 5;
  let requestNumber = 0;
  const more = document.createElement('button');
  more.type = 'button'; more.className = 'finance-more'; more.hidden = true;
  more.setAttribute('aria-controls', 'financePolicyList');
  list.after(more);
  function updateVisible() {
    const cards = Array.from(list.querySelectorAll('.finance-policy-card'));
    cards.forEach(function (card, index) {
      card.hidden = mobile.matches && index >= shown;
      const button = card.querySelector('.finance-details-toggle');
      const detail = card.querySelector('.finance-policy-detail');
      if (detail && button) detail.hidden = mobile.matches && button.getAttribute('aria-expanded') !== 'true';
    });
    const remaining = cards.length - shown;
    more.hidden = !mobile.matches || remaining <= 0;
    more.textContent = '정책 더 보기 (' + Math.min(5, Math.max(0, remaining)) + '개 · 남은 ' + Math.max(0, remaining) + '개)';
  }
  more.addEventListener('click', function () { shown += 5; updateVisible(); });
  mobile.addEventListener('change', updateVisible);
  list.addEventListener('click', function (event) {
    const button = event.target.closest('.finance-details-toggle');
    if (!button) return;
    const expanded = button.getAttribute('aria-expanded') !== 'true';
    button.setAttribute('aria-expanded', String(expanded));
    button.textContent = expanded ? '상세 접기' : '상세 보기';
    button.closest('.finance-policy-card').classList.toggle('is-expanded', expanded);
    updateVisible();
  });

  const query = new URLSearchParams(window.location.search);
  const requestedCategory = query.get('category');
  const requestedTarget = query.get('targetType');
  if (requestedCategory && Array.from(category.options).some(function (option) { return option.value === requestedCategory; })) {
    category.value = requestedCategory;
  }
  if (requestedTarget && Array.from(target.options).some(function (option) { return option.value === requestedTarget; })) {
    target.value = requestedTarget;
  }

  const categoryLabels = {
    purchase: '주택 구입',
    jeonse: '전세자금',
    monthly: '월세지원'
  };
  const targetLabels = {
    general: '일반',
    youth: '청년',
    newlywed: '신혼부부'
  };
  const categoryIcons = {
    purchase: 'fa-house-circle-check',
    jeonse: 'fa-vault',
    monthly: 'fa-receipt'
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function render(policies) {
    shown = 5;
    more.hidden = true;
    if (!policies.length) {
      list.innerHTML = '<div class="finance-policy-empty panel"><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><strong>조건에 맞는 정책이 없어요</strong><p>지원 유형이나 신청 대상을 변경해 보세요.</p></div>';
      return;
    }
    list.innerHTML = policies.map(function (policy, index) {
      const icon = categoryIcons[policy.category] || 'fa-landmark';
      const categoryLabel = categoryLabels[policy.category] || policy.category || '주거지원';
      const targetLabel = targetLabels[policy.targetType] || policy.targetType || '전체';
      const rate = policy.rateInfo || '공식 공고 확인';
      const limit = policy.limitInfo || '공식 공고 확인';
      const description = policy.description || '세부 신청 조건은 공식 기관에서 확인하세요.';
      const checkedAt = policy.sourceCheckedAt ? new Date(policy.sourceCheckedAt).toLocaleDateString('ko-KR') : '';
      const sourceLink = policy.sourceUrl
        ? '<a class="finance-policy-source" href="' + escapeHtml(policy.sourceUrl) + '" target="_blank" rel="noopener noreferrer">' + escapeHtml(policy.sourceName || '공식 출처') + ' 원문 확인</a>'
        : '<span class="finance-policy-source is-muted">공식 출처 등록 전</span>';
      const review = policy.updateStatus === 'review' ? '<span class="finance-policy-review">변경 검토 중</span>' : '';
      return '<article class="finance-policy-card panel">' +
        '<div class="finance-policy-icon"><i class="fa-solid ' + icon + '" aria-hidden="true"></i></div>' +
        '<div><p class="eyebrow">' + escapeHtml(categoryLabel) + ' · ' + escapeHtml(targetLabel) + '</p>' +
        '<h3>' + escapeHtml(policy.name) + '</h3>' +
        '<ul class="finance-policy-key"><li><strong>금리</strong> <span>' + escapeHtml(rate) + '</span></li><li><strong>최대한도</strong> <span>' + escapeHtml(limit) + '</span></li></ul>' +
        '<button type="button" class="finance-details-toggle" aria-expanded="false" aria-controls="financePolicyDetail-' + index + '">상세 보기</button>' +
        '<div class="finance-policy-detail" id="financePolicyDetail-' + index + '">' +
        '<p class="finance-policy-description">' + escapeHtml(description) + '</p>' +
        '<ul class="finance-policy-full-terms"><li><strong>금리</strong> ' + escapeHtml(rate) + '</li><li><strong>최대한도</strong> ' + escapeHtml(limit) + '</li></ul>' +
        '<div class="finance-policy-source-row">' + sourceLink + (checkedAt ? '<small>최종 확인 ' + escapeHtml(checkedAt) + '</small>' : '') + review + '</div></div></div></article>';
    }).join('');
    updateVisible();
  }

  async function loadPolicies() {
    const currentRequest = ++requestNumber;
    more.hidden = true;
    const params = new URLSearchParams();
    if (category.value) params.set('category', category.value);
    if (target.value) params.set('targetType', target.value);
    list.setAttribute('aria-busy', 'true');
    try {
      const response = await fetch('/api/finance/policies?' + params.toString(), { credentials: 'same-origin' });
      if (!response.ok) throw new Error('정책을 불러오지 못했습니다.');
      const data = await response.json();
      if (currentRequest !== requestNumber) return;
      render(Array.isArray(data) ? data : (data.items || []));
    } catch (error) {
      if (currentRequest !== requestNumber) return;
      more.hidden = true;
      list.innerHTML = '<div class="finance-policy-empty is-error panel"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i><strong>정책 정보를 불러오지 못했어요</strong><p>잠시 후 다시 시도해 주세요.</p></div>';
    } finally {
      if (currentRequest === requestNumber) list.removeAttribute('aria-busy');
    }
  }

  category.addEventListener('change', loadPolicies);
  target.addEventListener('change', loadPolicies);
  loadPolicies();
})();
