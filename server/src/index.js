const express = require('express'), cookieParser = require('cookie-parser'), jwt = require('jsonwebtoken'), bcrypt = require('bcryptjs');
const crypto = require('crypto'), fs = require('fs'), path = require('path');
const bus = require('./bus'), calls = require('./calls'), acparse = require('./acparse');
const cfg = require('./config'), db = require('./db'), ac = require('./atlascrypto'), mesh = require('./mesh'), mail = require('./mail'), tariffs = require('./tariffs');
const zendit = require('./zendit');

const app = express();
app.set('trust proxy', 1);
app.use(express.json({ limit: '14mb', verify: (req, res, buf) => { req.rawBody = buf; } }));
app.use(cookieParser());
app.use((req, res, next) => { res.set({ 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer',
  'Content-Security-Policy': "default-src 'self'; img-src 'self' blob: data:; style-src 'self' 'unsafe-inline'" }); next(); });

const now = () => Date.now();
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const hashCode = c => sha('atlas|' + c);
const rand6 = () => String(crypto.randomInt(0, 1000000)).padStart(6, '0');

// =====================================================================================================
//  PARTIE TÉLÉPHONES  (requêtes signées par la clé de l'appareil)
// =====================================================================================================
/** Signature : ECDSA( id|ts|METHODE|chemin|sha256(corps) ) avec la clé d'identité du téléphone. */
function deviceAuth(req, res, next) {
  const id = req.get('X-Atlas-Id'), ts = +req.get('X-Atlas-Ts'), sig = req.get('X-Atlas-Sig');
  if (!id || !ts || !sig || Math.abs(now() - ts) > 10 * 60000) return res.status(401).json({ error: 'auth' });
  const u = db.prepare('SELECT * FROM users WHERE id=?').get(id);
  const pub = u ? u.pub : (req.path === '/v1/register' ? (req.body || {}).pub : null);
  if (!pub) return res.status(401).json({ error: 'auth' });
  const data = Buffer.from(`${id}|${ts}|${req.method}|${req.path}|${sha(req.rawBody || '')}`);
  if (!ac.verify(pub, data, Buffer.from(sig, 'base64'))) return res.status(401).json({ error: 'auth' });
  if (u && u.blocked && req.path !== '/v1/sync') return res.status(403).json({ error: 'blocked', reason: u.block_reason });
  req.deviceId = id; req.user = u; next();
}

app.post('/v1/register', deviceAuth, (req, res) => {
  const b = req.body, id = req.deviceId;
  if (!/^ATL-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(id) || !ac.validPub(b.pub)) return res.status(400).json({ error: 'bad' });
  const u = db.prepare('SELECT * FROM users WHERE id=?').get(id);
  if (u && u.pub !== b.pub) return res.status(409).json({ error: 'key' });
  const f = k => String(b[k] || '').slice(0, 80);
  if (!u) db.prepare('INSERT INTO users(id,pub,nom,prenom,phone,continent,country,province,city,bio,created_at,last_seen) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)')
    .run(id, b.pub, f('nom'), f('prenom'), f('phone'), f('continent'), f('country'), f('province'), f('city'), f('bio'), now(), now());
  else db.prepare('UPDATE users SET nom=?,prenom=?,phone=?,continent=?,country=?,province=?,city=?,bio=?,stub=0,last_seen=? WHERE id=?')
    .run(f('nom'), f('prenom'), f('phone'), f('continent'), f('country'), f('province'), f('city'), f('bio'), now(), id);
  res.json({ ok: true, assistantPub: ac.PUB });
});

/** Le téléphone remet les paquets destinés à Atlas Assistant et reçoit ceux qui l'attendent (messages, jetons, annonces…). */
app.post('/v1/sync', deviceAuth, (req, res) => {
  if (!req.user) return res.status(404).json({ error: 'register' });
  if (!req.user.blocked) mesh.processIncoming((req.body || {}).packets);
  db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(), req.deviceId);
  res.json({ packets: mesh.takeOutbox(req.deviceId), t: now() });
});

app.post('/v1/verify/request', deviceAuth, async (req, res) => {
  const { email, phone } = req.body || {};
  if (!req.user || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email || '')) return res.status(400).json({ error: 'bad' });
  const code = rand6();
  db.prepare('INSERT OR REPLACE INTO email_codes(user,email,phone,code_hash,expires,attempts) VALUES(?,?,?,?,?,0)').run(req.deviceId, email, String(phone || ''), hashCode(code), now() + 10 * 60000);
  const ok = await mail.send(email, 'Votre code Atlas Globe', `Votre code de confirmation est : ${code}\nIl est valable 10 minutes.\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message.`);
  res.status(ok ? 200 : 502).json({ ok });
});
app.post('/v1/verify/confirm', deviceAuth, (req, res) => {
  const r = db.prepare('SELECT * FROM email_codes WHERE user=?').get(req.deviceId);
  if (!r || r.expires < now() || r.attempts >= 5) return res.json({ ok: false });
  db.prepare('UPDATE email_codes SET attempts=attempts+1 WHERE user=?').run(req.deviceId);
  if (hashCode(String((req.body || {}).code || '')) !== r.code_hash) return res.json({ ok: false });
  db.prepare('UPDATE users SET email=?, phone=?, email_verified=1 WHERE id=?').run(r.email, r.phone, req.deviceId);
  db.prepare('DELETE FROM email_codes WHERE user=?').run(req.deviceId);
  res.json({ ok: true });
});

app.post('/v1/posts', deviceAuth, (req, res) => {
  const b = req.body || {}; const u = req.user; if (!u) return res.status(404).json({ error: 'register' });
  if (!b.id || !['text', 'image', 'video'].includes(b.kind)) return res.status(400).json({ error: 'bad' });
  let media = null;
  if (b.media) { const buf = Buffer.from(b.media, 'base64'); if (buf.length > 400000) return res.status(413).json({ error: 'big' });
    media = sha(b.id).slice(0, 24) + '.jpg'; fs.writeFileSync(path.join(cfg.DATA, 'media', media), buf); }
  db.prepare('INSERT OR IGNORE INTO posts(id,author,author_name,kind,text,media,created_at) VALUES(?,?,?,?,?,?,?)')
    .run(String(b.id), u.id, `${u.prenom} ${u.nom}`.trim(), b.kind, String(b.text || '').slice(0, 600), media, now());
  res.json({ ok: true });
});
/** Vidéo d'une publication : corps brut (25 Mo max), signé comme les autres requêtes. */
app.post('/v1/video/:id', express.raw({ type: () => true, limit: '30mb', verify: (req, res, buf) => { req.rawBody = buf; } }), deviceAuth, (req, res) => {
  if (!req.user) return res.status(404).json({ error: 'register' });
  const post = db.prepare("SELECT id FROM posts WHERE id=? AND author=? AND kind='video'").get(req.params.id, req.deviceId);
  if (!post) return res.status(404).json({ error: 'post' });
  const buf = req.body; if (!Buffer.isBuffer(buf) || buf.length < 1000 || buf.length > 26000000) return res.status(400).json({ error: 'bad' });
  const f = sha(req.params.id).slice(0, 24) + '.mp4'; fs.writeFileSync(path.join(cfg.DATA, 'media', f), buf);
  db.prepare('UPDATE posts SET media=? WHERE id=?').run(f, post.id); res.json({ ok: true });
});
app.get('/v1/feed', deviceAuth, (req, res) => {
  const rows = db.prepare(`SELECT p.id, p.author, p.author_name, p.kind, p.text, p.media, p.created_at, u.cert,
    (SELECT COUNT(*) FROM likes WHERE post=p.id) likes, EXISTS(SELECT 1 FROM likes WHERE post=p.id AND user=?) liked
    FROM posts p LEFT JOIN users u ON u.id=p.author WHERE p.deleted=0 ORDER BY p.created_at DESC LIMIT 100`).all(req.deviceId);
  res.json({ posts: rows });
});
app.post('/v1/like', deviceAuth, (req, res) => {
  const { post, on } = req.body || {}; if (!req.user || !post) return res.status(400).json({ error: 'bad' });
  if (on) db.prepare('INSERT OR IGNORE INTO likes(post,user) VALUES(?,?)').run(String(post), req.deviceId); else db.prepare('DELETE FROM likes WHERE post=? AND user=?').run(String(post), req.deviceId);
  res.json({ ok: true });
});
app.get('/media/:f', (req, res) => { const f = path.basename(req.params.f); const p = path.join(cfg.DATA, 'media', f);
  fs.existsSync(p) ? res.sendFile(p) : res.sendStatus(404); });

app.post('/v1/groups', deviceAuth, (req, res) => {
  const g = req.body || {}; if (!req.user || !/^GRP-/.test(g.id || '') || !Array.isArray(g.members)) return res.status(400).json({ error: 'bad' });
  const old = db.prepare('SELECT * FROM groups WHERE id=?').get(g.id);
  if (old && old.creator !== req.deviceId && !JSON.parse(old.members).includes(req.deviceId)) return res.status(403).json({ error: 'no' });
  db.prepare('INSERT INTO groups(id,name,creator,members,created_at,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name, members=excluded.members, updated_at=excluded.updated_at')
    .run(g.id, String(g.name || '').slice(0, 60), g.creator || req.deviceId, JSON.stringify(g.members.slice(0, 1100)), g.created || now(), now());
  res.json({ ok: true });
});

/** Demande de certification : pièce d'identité (recto, verso) et selfie. Images stockées hors du dossier public. */
app.post('/v1/identity', deviceAuth, (req, res) => {
  const b = req.body || {}; if (!req.user || ![1, 2, 3].includes(+b.level) || !b.front || !b.back || !b.selfie) return res.status(400).json({ error: 'bad' });
  const pend = db.prepare("SELECT id FROM cert_requests WHERE user=? AND status='pending'").get(req.deviceId);
  if (pend) return res.status(409).json({ error: 'pending' });
  const r = db.prepare('INSERT INTO cert_requests(user,level,created_at) VALUES(?,?,?)').run(req.deviceId, +b.level, now());
  const save = (k, data) => { const buf = Buffer.from(data, 'base64'); if (buf.length > 3_000_000) throw new Error('big');
    const f = `${r.lastInsertRowid}_${k}.jpg`; fs.writeFileSync(path.join(cfg.DATA, 'ids', f), buf, { mode: 0o600 }); return f; };
  try { db.prepare('UPDATE cert_requests SET front=?, back=?, selfie=? WHERE id=?').run(save('front', b.front), save('back', b.back), save('selfie', b.selfie), r.lastInsertRowid); }
  catch (e) { db.prepare('DELETE FROM cert_requests WHERE id=?').run(r.lastInsertRowid); return res.status(413).json({ error: 'big' }); }
  res.json({ ok: true, id: r.lastInsertRowid });
});

// =====================================================================================================
//  PANEL ADMIN  (accès uniquement pour ADMIN_EMAIL : mot de passe + code envoyé par e-mail)
// =====================================================================================================
const fails = new Map();
function limited(ip) { const f = fails.get(ip) || { n: 0, t: now() }; if (now() - f.t > 15 * 60000) { f.n = 0; f.t = now(); } fails.set(ip, f); return f.n >= 10; }
const bump = ip => { const f = fails.get(ip); if (f) f.n++; };

(function initAdmin() {
  const has = db.prepare("SELECT v FROM admin WHERE k='pw'").get();
  if (!has) { if (!cfg.ADMIN_PASSWORD) { console.warn('ADMIN_PASSWORD manquant : définissez-le dans .env pour créer le compte du panel.'); return; }
    db.prepare("INSERT INTO admin(k,v) VALUES('pw',?)").run(bcrypt.hashSync(cfg.ADMIN_PASSWORD, 12)); console.log('Compte admin créé pour', cfg.ADMIN_EMAIL); }
})();

const cookieOpts = { httpOnly: true, sameSite: 'strict', secure: cfg.PROD, path: '/' };
const csrf = (req, res, next) => (req.method === 'GET' || req.get('X-Requested-With') === 'atlas') ? next() : res.status(403).json({ error: 'csrf' });

app.post('/admin/api/login', csrf, async (req, res) => {
  if (limited(req.ip)) return res.status(429).json({ error: 'trop de tentatives' });
  const { email, password } = req.body || {}; const pw = db.prepare("SELECT v FROM admin WHERE k='pw'").get();
  if (!pw || String(email || '').toLowerCase() !== cfg.ADMIN_EMAIL || !bcrypt.compareSync(String(password || ''), pw.v)) { bump(req.ip); return res.status(401).json({ error: 'identifiants incorrects' }); }
  const code = rand6(); const nonce = crypto.randomBytes(8).toString('hex');
  db.prepare("INSERT OR REPLACE INTO admin(k,v) VALUES('otp',?)").run(JSON.stringify({ h: hashCode(code + nonce), exp: now() + 10 * 60000, n: 0, nonce }));
  const ok = await mail.send(cfg.ADMIN_EMAIL, 'Code de connexion Atlas Admin', `Votre code de connexion : ${code}\nValable 10 minutes.`);
  if (!ok) return res.status(502).json({ error: "envoi de l'e-mail impossible (vérifiez BREVO_API_KEY)" });
  res.cookie('atlas_pending', jwt.sign({ nonce, p: 'otp' }, cfg.JWT_SECRET, { expiresIn: '10m' }), cookieOpts); res.json({ step: 'otp' });
});
app.post('/admin/api/otp', csrf, (req, res) => {
  if (limited(req.ip)) return res.status(429).json({ error: 'trop de tentatives' });
  let pend; try { pend = jwt.verify(req.cookies.atlas_pending || '', cfg.JWT_SECRET); } catch (e) { return res.status(401).json({ error: 'session expirée' }); }
  const row = db.prepare("SELECT v FROM admin WHERE k='otp'").get(); const o = row && JSON.parse(row.v);
  if (!o || o.nonce !== pend.nonce || o.exp < now() || o.n >= 5) return res.status(401).json({ error: 'code expiré' });
  o.n++; db.prepare("UPDATE admin SET v=? WHERE k='otp'").run(JSON.stringify(o));
  if (hashCode(String((req.body || {}).code || '') + o.nonce) !== o.h) { bump(req.ip); return res.status(401).json({ error: 'code incorrect' }); }
  db.prepare("DELETE FROM admin WHERE k='otp'").run();
  res.clearCookie('atlas_pending', cookieOpts);
  res.cookie('atlas_admin', jwt.sign({ email: cfg.ADMIN_EMAIL }, cfg.JWT_SECRET, { expiresIn: '8h' }), { ...cookieOpts, maxAge: 8 * 3600 * 1000 });
  res.json({ ok: true });
});
app.post('/admin/api/logout', csrf, (req, res) => { res.clearCookie('atlas_admin', cookieOpts); res.json({ ok: true }); });

const admin = express.Router();
admin.use(csrf, (req, res, next) => {
  try { const t = jwt.verify(req.cookies.atlas_admin || '', cfg.JWT_SECRET); if ((t.email || '').toLowerCase() === cfg.ADMIN_EMAIL) return next(); } catch (e) {}
  res.status(401).json({ error: 'non connecté' });
});
app.use('/admin/api', admin);

const page = (req, def = 50) => ({ limit: Math.min(+req.query.limit || def, 200), offset: Math.max(+req.query.offset || 0, 0) });
const like = q => '%' + String(q || '').replace(/[%_]/g, '') + '%';

admin.get('/me', (req, res) => res.json({ email: cfg.ADMIN_EMAIL, atlasPub: ac.PUB, assistant: cfg.ASSISTANT_ID }));
admin.get('/stats', (req, res) => {
  const c = (sql, ...a) => db.prepare(sql).get(...a).n;
  res.json({ users: c('SELECT COUNT(*) n FROM users WHERE stub=0'), today: c('SELECT COUNT(*) n FROM users WHERE created_at>?', now() - 86400000),
    blocked: c('SELECT COUNT(*) n FROM users WHERE blocked=1'), resellers: c('SELECT COUNT(*) n FROM users WHERE reseller=1'), posts: c('SELECT COUNT(*) n FROM posts WHERE deleted=0'),
    groups: c('SELECT COUNT(*) n FROM groups'), ordersPending: c("SELECT COUNT(*) n FROM orders WHERE status='awaiting'"), certPending: c("SELECT COUNT(*) n FROM cert_requests WHERE status='pending'"), acExpiring: c('SELECT COUNT(*) n FROM ac_configs WHERE expire_at>0 AND expire_at<?', now() + 3 * 86400000),
    revenue: c("SELECT COALESCE(SUM(amount),0) n FROM orders WHERE status='approved'") });
});

// ---- comptes ----
admin.get('/users', (req, res) => { const { limit, offset } = page(req), q = like(req.query.q), cn = req.query.country ? String(req.query.country) : '%';
  const rows = db.prepare(`SELECT id,nom,prenom,phone,email,email_verified,continent,country,province,city,created_at,last_seen,blocked,block_reason,reseller,cert,stub FROM users
    WHERE (nom||' '||prenom||' '||id||' '||phone||' '||email) LIKE ? AND country LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?`).all(q, cn, limit, offset);
  res.json({ users: rows, total: db.prepare('SELECT COUNT(*) n FROM users WHERE (nom||\' \'||prenom||\' \'||id||\' \'||phone||\' \'||email) LIKE ? AND country LIKE ?').get(q, cn).n }); });
admin.post('/users/:id/block', (req, res) => { const reason = String((req.body || {}).reason || '').slice(0, 200);
  db.prepare('UPDATE users SET blocked=1, block_reason=? WHERE id=?').run(reason, req.params.id); mesh.sendControl(req.params.id, { type: 'blocked', on: true, reason }); res.json({ ok: true }); });
admin.post('/users/:id/unblock', (req, res) => { db.prepare("UPDATE users SET blocked=0, block_reason='' WHERE id=?").run(req.params.id); mesh.sendControl(req.params.id, { type: 'blocked', on: false }); res.json({ ok: true }); });

// ---- revendeurs : 0 aucun, 1 approuvé, 2 suspendu ----
admin.get('/resellers', (req, res) => res.json({ resellers: db.prepare(`SELECT u.id,u.nom,u.prenom,u.phone,u.city,u.country,u.reseller,u.last_seen,
  (SELECT ROUND(AVG(stars),1) FROM ratings WHERE reseller=u.id) rating, (SELECT COUNT(*) FROM ratings WHERE reseller=u.id) votes,
  (SELECT COALESCE(SUM(bytes),0) FROM usage_reports WHERE reseller=u.id) bytes FROM users u WHERE u.reseller>0 ORDER BY u.created_at DESC`).all() }));
admin.post('/users/:id/reseller', (req, res) => { const on = !!(req.body || {}).approved; const st = (req.body || {}).suspend ? 2 : (on ? 1 : 0);
  if (!db.prepare('SELECT 1 FROM users WHERE id=?').get(req.params.id)) return res.status(404).json({ error: 'introuvable' });
  db.prepare('UPDATE users SET reseller=? WHERE id=?').run(st, req.params.id); mesh.sendControl(req.params.id, { type: 'reseller', approved: st === 1 }); res.json({ ok: true }); });

// ---- publications ----
admin.get('/posts', (req, res) => { const { limit, offset } = page(req);
  res.json({ posts: db.prepare('SELECT id,author,author_name,kind,text,media,created_at,deleted FROM posts ORDER BY created_at DESC LIMIT ? OFFSET ?').all(limit, offset) }); });
admin.delete('/posts/:id', (req, res) => { db.prepare('UPDATE posts SET deleted=1 WHERE id=?').run(req.params.id); res.json({ ok: true }); });

// ---- groupes (métadonnées seulement : nom, créateur, membres ; jamais le contenu des messages, chiffré de bout en bout) ----
admin.get('/groups', (req, res) => res.json({ groups: db.prepare('SELECT id,name,creator,members,created_at,updated_at FROM groups ORDER BY updated_at DESC LIMIT 200').all()
  .map(g => ({ ...g, members: JSON.parse(g.members).length })) }));
admin.get('/groups/:id', (req, res) => { const g = db.prepare('SELECT * FROM groups WHERE id=?').get(req.params.id); if (!g) return res.status(404).json({ error: 'introuvable' });
  const ids = JSON.parse(g.members);
  const members = ids.map(id => db.prepare('SELECT id,nom,prenom,phone,country,province,city,blocked FROM users WHERE id=?').get(id) || { id }); res.json({ group: { ...g, members: undefined }, members }); });

// ---- paiements ----
admin.get('/zendit/offers', async (req, res) => {
  try { const country = String(req.query.country || 'CD').toUpperCase();
    const j = await zendit.listOffers(country, +req.query._limit || 100, +req.query._offset || 0);
    res.json(j);
  } catch (e) { res.status(500).json({ error: e.message }); }
});
admin.get('/orders', (req, res) => res.json({ orders: db.prepare(`SELECT o.*, u.nom, u.prenom, u.phone FROM orders o LEFT JOIN users u ON u.id=o.user ORDER BY o.created_at DESC LIMIT 300`).all() }));
admin.post('/orders/:id/approve', (req, res) => {
  const o = db.prepare('SELECT * FROM orders WHERE id=?').get(req.params.id); if (!o || o.status !== 'awaiting') return res.status(400).json({ error: 'déjà traitée' });
  if (o.plan_id === 'WALLET_RECHARGE') {
    db.prepare('UPDATE users SET balance=COALESCE(balance,0)+? WHERE id=?').run(o.amount, o.user);
    db.prepare("UPDATE orders SET status='approved', decided_at=? WHERE id=?").run(now(), o.id);
    mesh.sendControl(o.user, { type: 'wallet_credited', orderId: o.id, amount: o.amount });
    return res.json({ ok: true });
  }
  const plan = tariffs.find(o.plan_id); if (!plan) return res.status(400).json({ error: 'offre inconnue' });
  const tid = 'TK-' + crypto.randomBytes(6).toString('hex').toUpperCase();
  const token = ac.makeToken({ tid, u: o.user, p: plan.id, dur: tariffs.durMs(plan), mb: plan.mb });
  db.prepare("UPDATE orders SET status='approved', decided_at=?, tid=? WHERE id=?").run(now(), tid, o.id);
  mesh.sendControl(o.user, { type: 'grant', orderId: o.id, token }); res.json({ ok: true });
});
admin.post('/orders/:id/refuse', (req, res) => { const o = db.prepare('SELECT * FROM orders WHERE id=?').get(req.params.id); if (!o || o.status !== 'awaiting') return res.status(400).json({ error: 'déjà traitée' });
  db.prepare("UPDATE orders SET status='refused', decided_at=? WHERE id=?").run(now(), o.id);
  mesh.sendControl(o.user, { type: o.plan_id === 'WALLET_RECHARGE' ? 'wallet_refused' : 'refused', orderId: o.id }); res.json({ ok: true }); });

// ---- certifications ----
admin.get('/certs', (req, res) => res.json({ requests: db.prepare(`SELECT c.id,c.user,c.level,c.status,c.reason,c.created_at,c.decided_at,u.nom,u.prenom,u.phone,u.country,u.city
  FROM cert_requests c LEFT JOIN users u ON u.id=c.user ORDER BY (c.status='pending') DESC, c.created_at DESC LIMIT 200`).all() }));
admin.get('/certs/:id/:which', (req, res) => { const r = db.prepare('SELECT * FROM cert_requests WHERE id=?').get(req.params.id);
  const f = r && { front: r.front, back: r.back, selfie: r.selfie }[req.params.which]; if (!f) return res.sendStatus(404);
  res.set('Cache-Control', 'no-store'); res.sendFile(path.join(cfg.DATA, 'ids', path.basename(f))); });
admin.post('/certs/:id/approve', (req, res) => { const r = db.prepare("SELECT * FROM cert_requests WHERE id=? AND status='pending'").get(req.params.id); if (!r) return res.status(400).json({ error: 'déjà traitée' });
  db.prepare("UPDATE cert_requests SET status='approved', decided_at=? WHERE id=?").run(now(), r.id); db.prepare('UPDATE users SET cert=? WHERE id=?').run(r.level, r.user);
  mesh.sendControl(r.user, { type: 'cert', level: r.level }); res.json({ ok: true }); });
admin.post('/certs/:id/reject', (req, res) => { const reason = String((req.body || {}).reason || '').trim().slice(0, 300); if (!reason) return res.status(400).json({ error: 'motif obligatoire' });
  const r = db.prepare("SELECT * FROM cert_requests WHERE id=? AND status='pending'").get(req.params.id); if (!r) return res.status(400).json({ error: 'déjà traitée' });
  db.prepare("UPDATE cert_requests SET status='rejected', reason=?, decided_at=? WHERE id=?").run(reason, now(), r.id);
  mesh.sendControl(r.user, { type: 'cert_rejected', level: r.level, reason }); res.json({ ok: true }); });

// ---- annonces et messages à tous ----
const audience = ids => Array.isArray(ids) && ids.length ? ids : db.prepare('SELECT id FROM users WHERE stub=0 AND blocked=0').all().map(r => r.id);
const link = (v, hosts) => { try { const u = new URL(String(v || '')); return u.protocol === 'https:' && hosts.some(h => u.hostname === h || u.hostname.endsWith('.' + h)) ? u.toString() : ''; } catch (e) { return ''; } };
admin.post('/announce', (req, res) => {
  const b = req.body || {}; if (!b.title || !b.body) return res.status(400).json({ error: 'titre et texte obligatoires' });
  const wa = link(b.whatsapp, ['whatsapp.com', 'wa.me']), tg = link(b.telegram, ['t.me', 'telegram.me', 'telegram.org']), sup = b.support ? String(b.support).slice(0, 300) : '';
  const ids = audience(b.users);
  const r = db.prepare('INSERT INTO announcements(title,body,whatsapp,telegram,support,audience,created_at) VALUES(?,?,?,?,?,?,?)').run(String(b.title).slice(0, 120), String(b.body).slice(0, 1500), wa, tg, sup, ids.length, now());
  ids.forEach(id => mesh.sendControl(id, { type: 'announce', id: 'AN-' + r.lastInsertRowid, title: String(b.title).slice(0, 120), body: String(b.body).slice(0, 1500), whatsapp: wa, telegram: tg, support: sup }));
  res.json({ ok: true, sent: ids.length });
});
admin.get('/announcements', (req, res) => res.json({ announcements: db.prepare('SELECT * FROM announcements ORDER BY id DESC LIMIT 50').all() }));
admin.post('/broadcast', (req, res) => { const t = String((req.body || {}).text || '').trim().slice(0, 1000); if (!t) return res.status(400).json({ error: 'message vide' });
  const ids = audience((req.body || {}).users); ids.forEach(id => mesh.sendText(id, t)); res.json({ ok: true, sent: ids.length }); });

// ---- discussion avec les utilisateurs (Atlas Assistant) ----
admin.get('/chats', (req, res) => res.json({ chats: db.prepare(`SELECT m.user, MAX(m.created_at) last, u.nom, u.prenom, u.phone,
  (SELECT text FROM messages WHERE user=m.user ORDER BY created_at DESC LIMIT 1) preview,
  (SELECT dir FROM messages WHERE user=m.user ORDER BY created_at DESC LIMIT 1) lastdir
  FROM messages m LEFT JOIN users u ON u.id=m.user GROUP BY m.user ORDER BY last DESC LIMIT 200`).all() }));
admin.get('/chats/:user', (req, res) => res.json({ user: db.prepare('SELECT id,nom,prenom,phone,country,city FROM users WHERE id=?').get(req.params.user),
  messages: db.prepare('SELECT dir,text,created_at FROM messages WHERE user=? ORDER BY created_at').all(req.params.user) }));
admin.post('/chats/:user', (req, res) => { const t = String((req.body || {}).text || '').trim().slice(0, 1000); if (!t) return res.status(400).json({ error: 'vide' });
  res.json({ ok: mesh.sendText(req.params.user, t) }); });


// ---------- attente longue : les messages, appels et annonces arrivent aussitôt ----------
app.get('/v1/poll', deviceAuth, async (req, res) => {
  if (!req.user) return res.status(404).json({ error: 'register' });
  const id = req.deviceId; let closed = false; res.on('close', () => { closed = true; });
  const ready = () => db.prepare('SELECT 1 FROM outbox WHERE user=? LIMIT 1').get(id) || calls.hasEvents(id);
  if (!ready()) await new Promise(r => { const ev = 'wake:' + id; const t = setTimeout(done, 25000); function done() { clearTimeout(t); bus.removeListener(ev, done); r(); } bus.once(ev, done); res.on('close', done); });
  if (closed) return;
  db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(), id);
  res.json({ packets: mesh.takeOutbox(id), events: calls.take(id), t: now() });
});

// ---------- appels vocaux ----------
const callRoute = (name, fn) => app.post('/v1/call/' + name, deviceAuth, (req, res) => {
  if (!req.user) return res.status(404).json({ error: 'register' }); const r = fn(req.deviceId, req.body || {}); res.status(r.error ? 409 : 200).json(r); });
callRoute('start', (u, b) => calls.start(u, String(b.to || '')));
callRoute('accept', (u, b) => calls.accept(String(b.id || ''), u));
callRoute('reject', (u, b) => calls.reject(String(b.id || ''), u));
callRoute('end', (u, b) => calls.end(String(b.id || ''), u));

// ---------- Atlas Connect (VPN) : côté téléphone ----------
const acFails = new Map();
const acOf = id => db.prepare('SELECT * FROM ac_clients WHERE device=?').get(id);
function acState(c) {
  const hasCfg = !!(c.config_id && db.prepare('SELECT 1 FROM ac_configs WHERE id=?').get(c.config_id));
  return { name: c.name, balance: c.balance, expiresAt: c.expires_at, active: c.expires_at > now(), hasConfig: hasCfg, suspended: c.status !== 'active',
    tariffs: db.prepare('SELECT id,label,price,days FROM ac_tariffs WHERE active=1 ORDER BY price').all() };
}
app.post('/v1/ac/activate', deviceAuth, (req, res) => {
  if (!req.user) return res.status(404).json({ error: 'register' });
  const f = acFails.get(req.deviceId) || { n: 0, t: now() }; if (now() - f.t > 600000) { f.n = 0; f.t = now(); } acFails.set(req.deviceId, f);
  if (f.n >= 6) return res.status(429).json({ error: 'wait' });
  const c = db.prepare('SELECT * FROM ac_clients WHERE lower(token)=lower(?)').get(String((req.body || {}).token || '').trim());
  if (!c) { f.n++; return res.status(404).json({ error: 'invalid' }); }
  if (c.status !== 'active') return res.status(403).json({ error: 'suspended' });
  if (c.device && c.device !== req.deviceId) return res.status(409).json({ error: 'used' });
  db.prepare('UPDATE ac_clients SET device=? WHERE id=?').run(req.deviceId, c.id);
  res.json({ ok: true, state: acState(db.prepare('SELECT * FROM ac_clients WHERE id=?').get(c.id)) });
});
app.post('/v1/ac/state', deviceAuth, (req, res) => { const c = acOf(req.deviceId); if (!c) return res.status(404).json({ error: 'none' }); res.json(acState(c)); });
app.post('/v1/ac/buy', deviceAuth, (req, res) => {
  const c = acOf(req.deviceId); if (!c || c.status !== 'active') return res.status(403).json({ error: 'suspended' });
  const t = db.prepare('SELECT * FROM ac_tariffs WHERE id=? AND active=1').get(+(req.body || {}).tariff); if (!t) return res.status(404).json({ error: 'tariff' });
  if (c.balance < t.price) return res.status(402).json({ error: 'insufficient', balance: c.balance, missing: t.price - c.balance });
  const start = Math.max(now(), c.expires_at);
  db.prepare('UPDATE ac_clients SET balance=balance-?, expires_at=? WHERE id=?').run(t.price, start + t.days * 86400000, c.id);
  db.prepare('INSERT INTO ac_tx(client,kind,amount,note,created_at) VALUES(?,?,?,?,?)').run(c.id, 'buy', -t.price, t.label, now());
  res.json({ ok: true, state: acState(db.prepare('SELECT * FROM ac_clients WHERE id=?').get(c.id)) });
});
/** Configuration remise au téléphone seulement si l'abonnement est actif. Aucune information d'expiration de la source n'est jamais transmise. */
app.post('/v1/ac/config', deviceAuth, (req, res) => {
  const c = acOf(req.deviceId); if (!c || c.status !== 'active' || c.expires_at <= now() || !c.config_id) return res.status(403).json({ error: 'unavailable' });
  const cf = db.prepare('SELECT items FROM ac_configs WHERE id=?').get(c.config_id); if (!cf) return res.status(403).json({ error: 'unavailable' });
  const keep = ['proto', 'host', 'port', 'id', 'pass', 'method', 'aid', 'scy', 'net', 'path', 'hostHeader', 'security', 'sni', 'insecure', 'alpn', 'flow'];
  const items = JSON.parse(cf.items).filter(i => i.supported).slice(0, 8).map(i => { const o = Object.fromEntries(keep.filter(k => i[k] !== undefined).map(k => [k, i[k]])); const sb = require('./singbox').toOutbound(i); if (sb) o.sb = sb; return o; });
  if (!items.length) return res.status(403).json({ error: 'unavailable' });
  res.json({ items });
});

// ---------- Atlas Connect : panel admin ----------
const ALPHA = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; const g4 = () => Array.from(crypto.randomBytes(4), b => ALPHA[b % 32]).join('');
admin.get('/ac/clients', (req, res) => res.json({ clients: db.prepare(`SELECT c.*, (SELECT name FROM ac_configs WHERE id=c.config_id) config_name FROM ac_clients c ORDER BY c.id DESC LIMIT 500`).all() }));
admin.post('/ac/clients', (req, res) => {
  const b = req.body || {}; const name = String(b.name || '').trim().slice(0, 80); if (!name) return res.status(400).json({ error: 'nom obligatoire' });
  const token = `Atlas-${g4()}-${g4()}-${g4()}-${g4()}`;
  const r = db.prepare('INSERT INTO ac_clients(name,country,email,phone,token,created_at) VALUES(?,?,?,?,?,?)').run(name, String(b.country || '').slice(0, 60), String(b.email || '').slice(0, 120), String(b.phone || '').slice(0, 30), token, now());
  res.json({ ok: true, id: r.lastInsertRowid, token });
});
admin.post('/ac/clients/:id/credit', (req, res) => { const n = Math.round(+(req.body || {}).amount); if (!n || Math.abs(n) > 10000000) return res.status(400).json({ error: 'montant invalide' });
  db.prepare('UPDATE ac_clients SET balance=MAX(0,balance+?) WHERE id=?').run(n, req.params.id); db.prepare('INSERT INTO ac_tx(client,kind,amount,note,created_at) VALUES(?,?,?,?,?)').run(req.params.id, 'credit', n, String((req.body || {}).note || ''), now()); res.json({ ok: true }); });
admin.post('/ac/clients/:id/status', (req, res) => { db.prepare('UPDATE ac_clients SET status=? WHERE id=?').run((req.body || {}).suspend ? 'suspended' : 'active', req.params.id); res.json({ ok: true }); });
admin.post('/ac/clients/:id/reset', (req, res) => { db.prepare('UPDATE ac_clients SET device=NULL WHERE id=?').run(req.params.id); res.json({ ok: true }); });
admin.post('/ac/clients/:id/assign', (req, res) => { const cid = (req.body || {}).config; if (cid && !db.prepare('SELECT 1 FROM ac_configs WHERE id=?').get(cid)) return res.status(404).json({ error: 'configuration introuvable' });
  db.prepare('UPDATE ac_clients SET config_id=? WHERE id=?').run(cid || null, req.params.id); res.json({ ok: true }); });
admin.delete('/ac/clients/:id', (req, res) => { db.prepare('DELETE FROM ac_clients WHERE id=?').run(req.params.id); res.json({ ok: true }); });

admin.post('/ac/analyze', async (req, res) => { const b = req.body || {};
  try { res.json(await acparse.analyze(b.binary ? '' : b.text, b.filename || '', !!b.binary)); } catch (e) { res.status(500).json({ error: e.message }); } });
admin.post('/ac/test', async (req, res) => res.json(await acparse.testItem((req.body || {}).item || {})));
admin.get('/ac/configs', (req, res) => res.json({ configs: db.prepare('SELECT id,name,source,items,expire_at,created_at,(SELECT COUNT(*) FROM ac_clients WHERE config_id=ac_configs.id) clients FROM ac_configs ORDER BY id DESC').all()
  .map(c => ({ ...c, items: JSON.parse(c.items) })) }));
admin.post('/ac/configs', (req, res) => { const b = req.body || {}; const name = String(b.name || '').trim().slice(0, 80);
  if (!name || !Array.isArray(b.items) || !b.items.length) return res.status(400).json({ error: 'nom et configuration obligatoires' });
  const r = db.prepare('INSERT INTO ac_configs(name,source,items,expire_at,created_at) VALUES(?,?,?,?,?)').run(name, String(b.source || ''), JSON.stringify(b.items.slice(0, 100)), +b.expire || 0, now()); res.json({ ok: true, id: r.lastInsertRowid }); });
admin.put('/ac/configs/:id', (req, res) => { const b = req.body || {}; db.prepare('UPDATE ac_configs SET name=COALESCE(?,name), expire_at=? WHERE id=?').run(b.name ? String(b.name).slice(0, 80) : null, +b.expire || 0, req.params.id); res.json({ ok: true }); });
admin.delete('/ac/configs/:id', (req, res) => { db.prepare('UPDATE ac_clients SET config_id=NULL WHERE config_id=?').run(req.params.id); db.prepare('DELETE FROM ac_configs WHERE id=?').run(req.params.id); res.json({ ok: true }); });
admin.get('/ac/tariffs', (req, res) => res.json({ tariffs: db.prepare('SELECT * FROM ac_tariffs ORDER BY price').all() }));
admin.post('/ac/tariffs', (req, res) => { const b = req.body || {}; const price = Math.round(+b.price), days = Math.round(+b.days);
  if (!b.label || !(price > 0) || !(days > 0)) return res.status(400).json({ error: 'libellé, prix et durée obligatoires' });
  db.prepare('INSERT INTO ac_tariffs(label,price,days) VALUES(?,?,?)').run(String(b.label).slice(0, 60), price, days); res.json({ ok: true }); });
admin.delete('/ac/tariffs/:id', (req, res) => { db.prepare('DELETE FROM ac_tariffs WHERE id=?').run(req.params.id); res.json({ ok: true }); });

admin.get('/tariffs', (req, res) => res.json({ plans: tariffs.PLANS }));

app.use('/admin', express.static(path.join(__dirname, '..', 'public', 'admin'), { index: 'index.html' }));
app.get('/', (req, res) => res.redirect('/admin/'));

// Nettoyage : les pièces d'identité sont supprimées 30 jours après la décision.
function cleanup() {
  db.prepare("SELECT * FROM cert_requests WHERE status!='pending' AND decided_at < ?").all(now() - 30 * 86400000).forEach(r => {
    [r.front, r.back, r.selfie].forEach(f => { if (f) fs.rmSync(path.join(cfg.DATA, 'ids', path.basename(f)), { force: true }); });
    db.prepare("UPDATE cert_requests SET front=NULL, back=NULL, selfie=NULL WHERE id=?").run(r.id); });
  db.prepare('DELETE FROM seen_packets WHERE t < ?').run(now() - 7 * 86400000);
}
cleanup(); setInterval(cleanup, 6 * 3600 * 1000);

app.listen(cfg.PORT, () => {
  console.log(`Atlas serveur sur le port ${cfg.PORT}`);
  console.log('Clé publique Atlas (à copier dans AtlasConfig.kt : ASSISTANT_PUB et ADMIN_PUB) :\n' + ac.PUB);
});
