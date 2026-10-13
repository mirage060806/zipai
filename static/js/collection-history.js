(async function () {
  'use strict';
  const auth = window.ZipaiAuth;
  const root = document.getElementById('adminCollectionSection');
  if (!auth || !root) return;
  await auth.ready;
  if (!auth.getUser() || auth.getUser().role !== 'admin') return;
  const labels = { market: '아파트 실거래', property: '매물 수집', lh: 'LH 공고', finance: '금융 정책', lifestyle: '생활 데이터' };
  const statusLabels = { completed: '완료', partial: '부분 실패', failed: '실패', running: '실행 중', unknown: '상태 확인 필요', cancelled: '사용자 중단' };
  let category = 'market'; let page = 0; let sequence = 0;
  const list = document.getElementById('collectionHistoryList');
  const message = document.getElementById('collectionHistoryMessage');
  function element(tag, text) { const node = document.createElement(tag); node.textContent = text; return node; }
  function count(item, key) { return item[key] == null ? '집계 없음' : String(item[key]); }
  function date(value) { if (!value) return '미기록'; if (String(value).endsWith('Z')) return new Date(value).toLocaleString('ko-KR', {timeZone:'Asia/Seoul'}) + ' (한국 시간)'; return String(value).replace('T', ' ').replace(/\.\d+$/, '') + ' (기존 서버 시각)'; }
  async function load() {
    const current = ++sequence;
    message.textContent = '수집·반영 이력을 확인하고 있습니다.';
    list.replaceChildren();
    try {
      const response = await fetch('/api/admin/collection-history?category=' + encodeURIComponent(category) + '&page=' + page, { credentials: 'same-origin' });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || '이력 조회에 실패했습니다.');
      if (current !== sequence) return;
      const items = payload.items || [];
      document.getElementById('collectionHistoryPage').textContent = String(page + 1);
      document.getElementById('collectionHistoryPrev').disabled = page === 0;
      document.getElementById('collectionHistoryNext').disabled = items.length < 20;
      message.textContent = labels[category] + ' · ' + (payload.categories.find(function (item) { return item.id === category; }) || {}).tracking;
      if (!items.length) list.append(element('p', '이 페이지에 기록된 이력이 없습니다. 수집·반영 완료를 의미하지 않습니다.'));
      items.forEach(function (item) {
        const card = element('article', ''); card.className = 'collection-history-card';
        card.append(element('h3', item.source || labels[category]), element('p', statusLabels[item.status] || '상태 확인 필요'));
        if (item.recordType === 'execution') {
          const triggers = {manual:'수동',scheduled:'예약',unknown:'방식 미지정'};
          const operations = {crawl:'수집 실행',upload_only:'저장 자료 반영',geocode_only:'좌표 보완',listing_import:'매물 파일 반영',build_import:'CSV 정규화·반영'};
          const stages = {preflight:'사전 점검',collect:'자료 수집',geocode:'좌표 조회',upload:'DB 반영',done:'종료'};
          card.append(element('p', 'PC 전체 실행 · ' + (triggers[item.triggerType] || '방식 미지정') + ' · ' + (operations[item.operation] || '') + ' · ' + (stages[item.stage] || '')));
          if (item.dealYmd) card.append(element('p', '거래월 ' + item.dealYmd + ' · 대상 지역 ' + count(item,'regionCount') + '개'));
          card.append(element('p', '마지막 서버 수신: ' + date(item.lastSeenAt)));
          if (item.status === 'running') card.append(element('p', '진행 중 기록입니다. 마지막 수신이 오래됐다면 PC 종료·통신 단절 여부를 확인하세요.'));
        } else if (category === 'market' || category === 'property') card.append(element('p', '배치별 서버 반영 기록 · 전체 실행 결과와 별개이며 합산하지 않습니다.'));
        card.append(element('p', '시작: ' + date(item.startedAt) + ' / 종료: ' + date(item.finishedAt)));
        let summary;
        if (item.recordType === 'execution' && ['lh','finance','lifestyle'].includes(category)) summary = '처리 ' + count(item, 'collected') + (category === 'finance' ? '개 정책' : category === 'lh' ? '개 공고' : '개 지역') + ' · 단계별 실패 ' + count(item, 'errors') + ' · 신규/수정별 건수: 집계 없음';
        else if (item.recordType === 'execution' && item.operation === 'listing_import') summary = '반영 완료 파일 ' + count(item, 'collected') + ' · 실패 파일 ' + count(item, 'errors') + ' · 매물별 건수는 서버 반영 기록 참조';
        else if (category === 'finance') summary = '수신 ' + count(item, 'collected') + ' · 최초 기준 등록 ' + count(item, 'baselined') + ' · 변경 없음 ' + count(item, 'unchanged') + ' · 검토 후보 ' + count(item, 'detected') + ' · 대상 정책 없음 ' + count(item, 'missingPolicies');
        else if (category === 'lh') summary = '공고 ' + count(item, 'noticeCount') + ' · 규칙 ' + count(item, 'ruleCount') + ' · 신규/수정별 반영 건수: 집계 없음';
        else summary = (item.recordType === 'execution' ? '수집/처리 대상 ' : '수신 ')  + count(item, 'collected') + ' · 신규 ' + count(item, 'inserted') + ' · 수정 ' + count(item, 'updated') + ' · 실패 ' + count(item, 'errors');
        card.append(element('p', summary));
        if (item.recordType === 'execution' && ['market','property'].includes(category) && item.operation !== 'listing_import') card.append(element('p', '제외 ' + count(item,'skipped') + ' · 수집 실패 지역 ' + count(item,'failedRegions') + ' · 기존 완료 배치 재사용 ' + count(item,'reusedBatches') + '개'));
        if (item.status === 'failed' || item.status === 'partial') card.append(element('p', '수집·반영 로그를 확인해 주세요. 이 화면에는 API 키가 포함될 수 있는 원문 오류를 표시하지 않습니다.'));
        list.append(card);
      });
    } catch (error) { if (current === sequence) message.textContent = error.message; }
  }
  const tabs = document.getElementById('collectionHistoryTabs');
  Object.entries(labels).forEach(function (entry) {
    const button = element('button', entry[1]); button.type = 'button'; button.dataset.category = entry[0];
    button.setAttribute('aria-pressed', String(category === entry[0]));
    button.addEventListener('click', function () { category = entry[0]; page = 0; tabs.querySelectorAll('button').forEach(function (tab) { tab.setAttribute('aria-pressed', String(tab.dataset.category === category)); }); load(); });
    tabs.append(button);
  });
  document.getElementById('collectionHistoryRefresh').addEventListener('click', load);
  document.getElementById('collectionHistoryPrev').addEventListener('click', function () { if (page > 0) { page--; load(); } });
  document.getElementById('collectionHistoryNext').addEventListener('click', function () { page++; load(); });
  load();
})();
