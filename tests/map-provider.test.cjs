const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

test('property and safety maps load Leaflet without a Naver Maps credential', () => {
  const home = read('index.html');
  const safety = read('templates/safe/safety.html');
  const safetyMap = read('static/js/safety-map.js');

  for (const html of [home, safety]) {
    assert.match(html, /leaflet@1\.9\.4\/dist\/leaflet\.css/);
    assert.match(html, /leaflet@1\.9\.4\/dist\/leaflet\.js/);
    assert.doesNotMatch(html, /naver-map-(?:adapter|loader)|map-config\.js|oapi\.map\.naver\.com/);
  }

  assert.match(home, /static\/js\/home\.js/);
  assert.match(safetyMap, /tile\.openstreetmap\.org/);
  assert.match(safetyMap, /OpenStreetMap contributors/);
  assert.match(safetyMap, /window\.L\.marker/);
  assert.match(safetyMap, /window\.L\.circle/);
  assert.match(safetyMap, /window\.L\.popup/);
  assert.doesNotMatch(safetyMap, /naver\.maps|__zipaiNaverMap/);
  assert.equal(fs.existsSync(path.join(root, 'static/js/map-config.js')), false);
});
