const fs = require('fs'), path = require('path');
// Chargement minimal de .env (sans dépendance)
try { fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8').split('\n').forEach(l => {
  const m = l.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/); if (m && !(m[1] in process.env)) process.env[m[1]] = m[2]; }); } catch (e) {}
const DATA = path.join(__dirname, '..', 'data');
fs.mkdirSync(path.join(DATA, 'ids'), { recursive: true, mode: 0o700 });
fs.mkdirSync(path.join(DATA, 'media'), { recursive: true });
module.exports = {
  DATA, PORT: +process.env.PORT || 8080, PROD: process.env.NODE_ENV === 'production',
  ADMIN_EMAIL: (process.env.ADMIN_EMAIL || 'jephbesaus07@gmail.com').toLowerCase(),
  ADMIN_PASSWORD: process.env.ADMIN_PASSWORD || '',
  JWT_SECRET: process.env.JWT_SECRET || require('crypto').randomBytes(32).toString('hex'),
  BREVO_API_KEY: process.env.BREVO_API_KEY || '', MAIL_FROM: process.env.MAIL_FROM || 'no-reply@atlasglobe.app',
  MAIL_FROM_NAME: process.env.MAIL_FROM_NAME || 'Atlas Globe',
  ASSISTANT_ID: 'ATL-ASST-0001',
};
