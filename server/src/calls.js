// Appels vocaux : le serveur fait la signalisation (sonnerie, réponse, fin) et relaie l'audio en UDP entre les deux téléphones.
const crypto = require('crypto'), dgram = require('dgram');
const bus = require('./bus'), db = require('./db');
const RELAY_PORT = +process.env.RELAY_PORT || 40000;
const calls = new Map(), byToken = new Map(), events = new Map(), busy = new Map();
const now = () => Date.now();

function push(user, ev) { const a = events.get(user) || []; a.push(ev); events.set(user, a); bus.emit('wake:' + user); }
const hasEvents = u => (events.get(u) || []).length > 0;
function take(u) { const a = events.get(u) || []; events.delete(u); return a; }

function cleanup(c) { calls.delete(c.id); busy.delete(c.from); busy.delete(c.to); for (const t of Object.values(c.tokens || {})) byToken.delete(t); }
function finish(c, reason, notify) { if (!calls.has(c.id)) return; cleanup(c);
  for (const u of [c.from, c.to]) if (u !== notify) push(u, { type: 'ended', id: c.id, reason }); }

function start(from, to) {
  const u = db.prepare('SELECT id,blocked FROM users WHERE id=? AND stub=0').get(to);
  if (!u || u.blocked) return { error: 'unreachable' };
  if (busy.has(from) || busy.has(to)) return { error: 'busy' };
  const c = { id: crypto.randomUUID(), from, to, state: 'ringing', created: now(), tokens: {}, last: now() };
  calls.set(c.id, c); busy.set(from, c.id); busy.set(to, c.id);
  push(to, { type: 'incoming', id: c.id, from });
  setTimeout(() => { const k = calls.get(c.id); if (k && k.state === 'ringing') finish(k, 'timeout'); }, 45000);
  return { id: c.id };
}
function accept(id, user) {
  const c = calls.get(id); if (!c || c.to !== user || c.state !== 'ringing') return { error: 'gone' };
  c.state = 'active'; c.last = now();
  const tf = crypto.randomBytes(8).toString('hex'), tt = crypto.randomBytes(8).toString('hex');
  c.tokens = { from: tf, to: tt }; byToken.set(tf, { id, role: 'from' }); byToken.set(tt, { id, role: 'to' });
  push(c.from, { type: 'accepted', id, token: tf, port: RELAY_PORT }); push(c.to, { type: 'accepted', id, token: tt, port: RELAY_PORT });
  return { ok: true };
}
function reject(id, user) { const c = calls.get(id); if (!c || c.to !== user) return { error: 'gone' }; finish(c, 'rejected', user); return { ok: true }; }
function end(id, user) { const c = calls.get(id); if (!c || (c.from !== user && c.to !== user)) return { ok: true }; finish(c, 'ended', user); return { ok: true }; }

// Relais UDP : [type 1 octet][jeton 8 octets][numéro 2 octets][audio]  ->  [type][numéro][audio]
const sock = dgram.createSocket('udp4');
sock.on('message', (msg, r) => {
  if (msg.length < 9 || msg.length > 700) return;
  const e = byToken.get(msg.subarray(1, 9).toString('hex')); if (!e) return;
  const c = calls.get(e.id); if (!c) return;
  c['addr_' + e.role] = { a: r.address, p: r.port }; c.last = now();
  if (msg[0] !== 2) return;                                          // 1 = bonjour (garde la route NAT ouverte)
  const dst = c['addr_' + (e.role === 'from' ? 'to' : 'from')];
  if (dst) sock.send(Buffer.concat([msg.subarray(0, 1), msg.subarray(9)]), dst.p, dst.a);
});
sock.on('error', e => console.error('relais UDP :', e.message));
sock.bind(RELAY_PORT, () => console.log('Relais audio UDP sur le port', RELAY_PORT));
setInterval(() => { for (const c of [...calls.values()]) if (c.state === 'active' && (now() - c.last > 25000 || now() - c.created > 3 * 3600000)) finish(c, 'lost'); }, 5000);

module.exports = { start, accept, reject, end, take, hasEvents, RELAY_PORT };
