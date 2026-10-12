'use strict';

const { database } = require('./database');
const { currentUser } = require('./auth');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// ---------- 매물 사진 ----------
const PHOTO_DIR = path.join(__dirname, 'uploads', 'property-photos');
const PHOTO_URL_PREFIX = '/uploads/property-photos/';
const PHOTO_MAX_BYTES = 5 * 1024 * 1024;   // 사진 1장당 5MB
const PHOTO_MAX_PER_PROPERTY = 20;
const PHOTO_TYPES = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };
const PHOTO_MIME_BY_EXT = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

fs.mkdirSync(PHOTO_DIR, { recursive: true });
database.exec(`CREATE TABLE IF NOT EXISTS property_photos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  property_id INTEGER NOT NULL,
  file_name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
)`);
database.exec('CREATE INDEX IF NOT EXISTS idx_property_photos_property ON property_photos (property_id, sort_order)');

function photosOf(propertyId) {
  return database.prepare('SELECT id, file_name, sort_order FROM property_photos WHERE property_id = ? ORDER BY sort_order, id')
    .all(propertyId)
    .map((photo) => ({ id: photo.id, url: PHOTO_URL_PREFIX + photo.file_name, order: photo.sort_order }));
}

function photoSignatureOk(buffer, ext) {
  if (ext === 'jpg') return buffer[0] === 0xff && buffer[1] === 0xd8;
  if (ext === 'png') return buffer.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
  if (ext === 'webp') return buffer.slice(0, 4).toString() === 'RIFF' && buffer.slice(8, 12).toString() === 'WEBP';
  return false;
}

function httpError(message, status) {
  return Object.assign(new Error(message), { status });
}

function json(response, status, payload) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(payload));
}

function readJson(request, limit = 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) return reject(Object.assign(new Error('요청이 너무 큽니다.'), { status: 413 }));
      chunks.push(chunk);
    });
    request.on('end', () => {
      try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); }
      catch (error) { reject(Object.assign(new Error('JSON 형식이 올바르지 않습니다.'), { status: 400 })); }
    });
    request.on('error', reject);
  });
}

function requireUser(request, response) {
  const user = currentUser(request);
  if (!user) json(response, 401, { message: '로그인이 필요합니다.' });
  return user;
}

function propertyPayload(row) {
  const payload = JSON.parse(row.payload_json);
  const photos = photosOf(row.id);
  return {
    ...payload,
    id: row.id, ownerId: row.owner_id, status: row.status, createdAt: row.created_at,
    photos, photoCount: photos.length, thumbnail: photos.length ? photos[0].url : null
  };
}

async function handlePropertyRoute(request, response, pathname) {
  if (pathname.startsWith(PHOTO_URL_PREFIX) && request.method === 'GET') {
    const name = path.basename(pathname);
    const ext = name.split('.').pop();
    const file = path.join(PHOTO_DIR, name);
    if (!PHOTO_MIME_BY_EXT[ext] || !file.startsWith(PHOTO_DIR) || !fs.existsSync(file)) {
      json(response, 404, { message: '사진을 찾을 수 없습니다.' });
      return true;
    }
    response.writeHead(200, { 'Content-Type': PHOTO_MIME_BY_EXT[ext], 'Cache-Control': 'public, max-age=86400', 'X-Content-Type-Options': 'nosniff' });
    fs.createReadStream(file).pipe(response);
    return true;
  }
  if (!pathname.startsWith('/api/properties') && pathname !== '/api/favorites') return false;
  try {
    // GET /api/properties/:id/photos  (누구나 조회)
    // POST /api/properties/:id/photos (소유자만 업로드)
    // DELETE /api/properties/:id/photos/:photoId (소유자만 삭제)
    const photoRoute = pathname.match(/^\/api\/properties\/(\d+)\/photos(?:\/(\d+))?$/);
    if (photoRoute) {
      const propertyId = Number(photoRoute[1]);
      const photoId = photoRoute[2] ? Number(photoRoute[2]) : null;
      const property = database.prepare('SELECT id, owner_id FROM properties WHERE id = ?').get(propertyId);
      if (!property) { json(response, 404, { message: '매물을 찾을 수 없습니다.' }); return true; }

      if (request.method === 'GET' && !photoId) {
        const photos = photosOf(propertyId);
        json(response, 200, { items: photos, count: photos.length });
        return true;
      }

      const user = requireUser(request, response);
      if (!user) return true;
      if (Number(property.owner_id) !== Number(user.userId)) {
        json(response, 403, { message: '본인이 등록한 매물만 수정할 수 있습니다.' });
        return true;
      }

      if (request.method === 'POST' && !photoId) {
        // body: { images: [{ type: 'image/jpeg', data: '<base64>' }, ...] }
        const body = await readJson(request, 12 * 1024 * 1024);
        const images = Array.isArray(body.images) ? body.images : [];
        const existing = database.prepare('SELECT COUNT(*) AS n FROM property_photos WHERE property_id = ?').get(propertyId).n;
        if (!images.length) throw httpError('업로드할 사진이 없습니다.', 400);
        if (existing + images.length > PHOTO_MAX_PER_PROPERTY) throw httpError(`사진은 매물당 최대 ${PHOTO_MAX_PER_PROPERTY}장까지 등록할 수 있습니다.`, 400);

        const saved = [];
        const insert = database.prepare('INSERT INTO property_photos (property_id, file_name, sort_order, created_at) VALUES (?, ?, ?, ?)');
        try {
          images.forEach((image, index) => {
            const ext = PHOTO_TYPES[String(image.type || '')];
            if (!ext) throw httpError('JPG, PNG, WEBP 형식만 올라갑니다.', 400);
            const buffer = Buffer.from(String(image.data || '').replace(/^data:[^,]+,/, ''), 'base64');
            if (!buffer.length || buffer.length > PHOTO_MAX_BYTES) throw httpError('사진은 5MB 이하만 올릴 수 있습니다.', 413);
            if (!photoSignatureOk(buffer, ext)) throw httpError('올바른 이미지 파일이 아닙니다.', 400);
            const fileName = `${propertyId}-${crypto.randomBytes(8).toString('hex')}.${ext}`;
            fs.writeFileSync(path.join(PHOTO_DIR, fileName), buffer);
            saved.push(fileName);
            insert.run(propertyId, fileName, existing + index, new Date().toISOString());
          });
        } catch (error) {
          saved.forEach((name) => { try { fs.unlinkSync(path.join(PHOTO_DIR, name)); } catch (_) {} });
          database.prepare('DELETE FROM property_photos WHERE property_id = ? AND file_name IN (' + (saved.map(() => '?').join(',') || "''") + ')').run(propertyId, ...saved);
          throw error;
        }
        json(response, 201, { items: photosOf(propertyId) });
        return true;
      }

      if (request.method === 'DELETE' && photoId) {
        const photo = database.prepare('SELECT file_name FROM property_photos WHERE id = ? AND property_id = ?').get(photoId, propertyId);
        if (!photo) { json(response, 404, { message: '사진을 찾을 수 없습니다.' }); return true; }
        database.prepare('DELETE FROM property_photos WHERE id = ?').run(photoId);
        try { fs.unlinkSync(path.join(PHOTO_DIR, photo.file_name)); } catch (_) {}
        json(response, 200, { items: photosOf(propertyId) });
        return true;
      }
      json(response, 405, { message: '지원하지 않는 요청입니다.' });
      return true;
    }

    if (pathname === '/api/properties/mine' && request.method === 'GET') {
      const user = requireUser(request, response);
      if (!user) return true;
      const rows = database.prepare('SELECT * FROM properties WHERE owner_id = ? ORDER BY id DESC').all(user.userId);
      json(response, 200, { items: rows.map(propertyPayload) });
      return true;
    }
    if (pathname === '/api/properties' && request.method === 'POST') {
      const user = requireUser(request, response);
      if (!user) return true;
      const payload = await readJson(request);
      if (!String(payload.title || '').trim() || !String(payload.address || '').startsWith('경기도 ')) {
        json(response, 400, { message: '경기도 매물 제목과 주소를 확인해 주세요.' });
        return true;
      }
      const now = new Date().toISOString();
      const result = database.prepare('INSERT INTO properties (owner_id, payload_json, created_at, updated_at) VALUES (?, ?, ?, ?)')
        .run(user.userId, JSON.stringify(payload), now, now);
      const row = database.prepare('SELECT * FROM properties WHERE id = ?').get(Number(result.lastInsertRowid));
      json(response, 201, { item: propertyPayload(row) });
      return true;
    }
    if (pathname === '/api/favorites' && request.method === 'GET') {
      const user = requireUser(request, response);
      if (!user) return true;
      const items = database.prepare('SELECT property_id FROM favorites WHERE user_id = ? ORDER BY created_at').all(user.userId);
      json(response, 200, { ids: items.map((item) => Number(item.property_id)) });
      return true;
    }
    if (pathname === '/api/favorites' && request.method === 'PUT') {
      const user = requireUser(request, response);
      if (!user) return true;
      const payload = await readJson(request);
      const ids = Array.from(new Set((Array.isArray(payload.ids) ? payload.ids : []).map(String))).slice(0, 500);
      database.exec('BEGIN');
      try {
        database.prepare('DELETE FROM favorites WHERE user_id = ?').run(user.userId);
        const insert = database.prepare('INSERT INTO favorites (user_id, property_id, created_at) VALUES (?, ?, ?)');
        const now = new Date().toISOString();
        ids.forEach((id) => insert.run(user.userId, id, now));
        database.exec('COMMIT');
      } catch (error) {
        database.exec('ROLLBACK');
        throw error;
      }
      json(response, 200, { ids: ids.map(Number) });
      return true;
    }
    json(response, 405, { message: '지원하지 않는 요청입니다.' });
    return true;
  } catch (error) {
    json(response, error.status || 500, { message: error.status ? error.message : '매물 데이터를 처리하지 못했습니다.' });
    return true;
  }
}

module.exports = { handlePropertyRoute };
