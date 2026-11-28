// Le serveur est un "nœud" du réseau : il est Atlas Assistant. Les téléphones lui remettent les paquets chiffrés qui lui sont destinés
// (par n'importe quel téléphone ayant Internet), et il dépose dans la boîte de chaque utilisateur des paquets chiffrés pour lui.
const crypto = require('crypto');
const bus = require('./bus');
const db = require('./db'), ac = require('./atlascrypto'), { ASSISTANT_ID } = require('./config'), tariffs = require('./tariffs');

const b64 = b => Buffer.from(b).toString('base64');
const uid = () => crypto.randomUUID();
const getUser = id => db.prepare('SELECT * FROM users WHERE id=?').get(id);

function packetFor(user, type, plainObj) {
  const u = typeof user === 'string' ? getUser(user) : user; if (!u) return null;
  const plain = Buffer.from(typeof plainObj === 'string' ? plainObj : JSON.stringify(plainObj));
  return { i: uid(), ty: type, s: ASSISTANT_ID, d: u.id, l: 8, t: Date.now(), sp: ac.PUB, b: b64(ac.seal(ac.shared(u.pub), plain)) };
}
function queue(userId, packet) { if (packet) { db.prepare('INSERT INTO outbox(user,packet,created_at) VALUES(?,?,?)').run(userId, JSON.stringify(packet), Date.now()); bus.emit('wake:' + userId); } }

/** Message texte d'Atlas Assistant vers un utilisateur (notification + son dans l'application). */
function sendText(userId, text) {
  const u = getUser(userId); if (!u) return false;
  const id = uid();
  queue(userId, packetFor(u, 'msg', { id, c: userId, f: ASSISTANT_ID, k: 'text', tx: text, d: 0, t: Date.now() }));
  db.prepare('INSERT INTO messages(id,user,dir,text,created_at) VALUES(?,?,?,?,?)').run(id, userId, 'out', text, Date.now());
  return true;
}
/** Message système chiffré (grant, annonce, certification, blocage, revendeur…). */
function sendControl(userId, obj) { const u = getUser(userId); if (!u) return false; queue(userId, packetFor(u, 'ctl', obj)); return true; }

function ack(user, id) { queue(user.id, { i: uid(), ty: 'ack', s: ASSISTANT_ID, d: user.id, l: 8, t: Date.now(), sp: '', b: b64(id) }); }

function parseOrder(user, text) {
  const r = text.match(/^RECHARGE (\d+)/);
  if (r) { const amt = +r[1]; if (amt > 0) db.prepare(`INSERT INTO orders(id,user,plan_id,plan_label,amount,reseller_name,status,created_at) VALUES(?,?,?,?,?,?, 'awaiting', ?)`)
    .run('RCH-' + uid().slice(0, 8).toUpperCase(), user.id, 'WALLET_RECHARGE', 'Recharge portefeuille', amt, '', Date.now()); return; }
  const m = text.match(/^COMMANDE (CMD-[A-Z0-9]+)/); if (!m) return;
  const plan = tariffs.find((text.match(/\[([a-z0-9]+)\]/) || [])[1]); if (!plan) return;
  const rn = (text.match(/Revendeur : (.*)/) || [])[1] || '';
  db.prepare(`INSERT OR IGNORE INTO orders(id,user,plan_id,plan_label,amount,reseller_name,status,created_at) VALUES(?,?,?,?,?,?, 'awaiting', ?)`)
    .run(m[1], user.id, plan.id, plan.label, plan.fc, rn, Date.now());
}

/** Traite les paquets remis par un téléphone connecté. */
function processIncoming(packets) {
  let n = 0;
  for (const p of packets || []) {
    try {
      if (!p || p.d !== ASSISTANT_ID || !p.i || !p.s) continue;
      if (db.prepare('SELECT 1 FROM seen_packets WHERE id=?').get(p.i)) continue;
      let user = getUser(p.s);
      if (!user) { if (!p.sp || !ac.validPub(p.sp)) continue;                          // première rencontre : clé retenue
        db.prepare('INSERT INTO users(id,pub,created_at,last_seen,stub) VALUES(?,?,?,?,1)').run(p.s, p.sp, Date.now(), Date.now()); user = getUser(p.s); }
      else if (p.sp && p.sp !== user.pub) continue;                                    // clé différente de celle enregistrée : refusé
      if (user.blocked) continue;
      const plain = ac.open(ac.shared(user.pub), Buffer.from(p.b, 'base64')); if (!plain) continue;
      db.prepare('INSERT OR IGNORE INTO seen_packets(id,t) VALUES(?,?)').run(p.i, Date.now());
      const o = JSON.parse(plain.toString('utf8'));
      if (p.ty === 'msg') {
        if (o.f !== p.s) continue;
        db.prepare('INSERT OR IGNORE INTO messages(id,user,dir,text,created_at) VALUES(?,?,?,?,?)').run(o.id, user.id, 'in', o.tx || (o.k === 'voice' ? '[note vocale]' : '[image]'), Date.now());
        parseOrder(user, o.tx || ''); ack(user, p.i); n++;
      } else if (p.ty === 'ctl') {
        if (o.type === 'rate') db.prepare('INSERT OR REPLACE INTO ratings(order_id,reseller,stars,created_at) VALUES(?,?,?,?)').run(String(o.order), String(o.reseller), +o.stars | 0, Date.now());
        if (o.type === 'usage' && Array.isArray(o.list)) for (const r of o.list)
          db.prepare('INSERT INTO usage_reports(reseller,tid,bytes,used_ms,created_at) VALUES(?,?,?,?,?)').run(user.id, String(r.tid), +r.bytes || 0, +r.usedMs || 0, Date.now());
        if (o.type === 'wallet_buy') {
          const plan = tariffs.find(o.planId);
          if (!plan) { sendText(user.id, 'Offre inconnue.'); }
          else if ((user.balance || 0) < plan.fc) { sendText(user.id, `Solde insuffisant. Il vous manque ${plan.fc - (user.balance || 0)} FC.`); }
          else {
            db.prepare('UPDATE users SET balance=balance-? WHERE id=?').run(plan.fc, user.id);
            const tid = 'TK-' + crypto.randomBytes(6).toString('hex').toUpperCase();
            const token = ac.makeToken({ tid, u: user.id, p: plan.id, dur: tariffs.durMs(plan), mb: plan.mb });
            const oid = 'CMD-' + uid().slice(0, 8).toUpperCase();
            db.prepare(`INSERT INTO orders(id,user,plan_id,plan_label,amount,reseller_name,status,created_at,decided_at,tid) VALUES(?,?,?,?,?,?, 'approved', ?, ?, ?)`)
              .run(oid, user.id, plan.id, plan.label, plan.fc, String(o.resellerName || ''), Date.now(), Date.now(), tid);
            sendControl(user.id, { type: 'grant', orderId: oid, token });
          }
        }
      }
    } catch (e) { /* paquet invalide : ignoré */ }
  }
  return n;
}

function takeOutbox(userId, max = 200) {
  const rows = db.prepare('SELECT id,packet FROM outbox WHERE user=? ORDER BY id LIMIT ?').all(userId, max);
  if (rows.length) db.prepare(`DELETE FROM outbox WHERE id IN (${rows.map(() => '?').join(',')})`).run(...rows.map(r => r.id));
  return rows.map(r => JSON.parse(r.packet));
}

module.exports = { sendText, sendControl, processIncoming, takeOutbox, getUser };
