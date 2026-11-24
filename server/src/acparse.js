// Analyse de configurations VPN, indépendante du format : liens (vless, vmess, trojan, ss…), JSON Xray/V2Ray/sing-box,
// URL d'abonnement. Les formats chiffrés/propriétaires d'autres applications ne sont pas lisibles : ils sont signalés.
const net = require('net'), tls = require('tls'), dns = require('dns').promises;

const b64d = s => { s = String(s).trim().replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '='; return Buffer.from(s, 'base64').toString('utf8'); };
const dec = s => { try { return decodeURIComponent(s); } catch (e) { return s; } };
const SS_OK = ['aes-128-gcm', 'aes-256-gcm', 'chacha20-ietf-poly1305', 'chacha20-poly1305'];

function finish(it) {
  it.port = +it.port || 0; it.net = (it.net || 'tcp').toLowerCase(); it.security = (it.security || 'none').toLowerCase();
  if (it.proto === 'trojan' && it.security === 'none') it.security = 'tls';
  const why = [];
  if (it.proto === 'dnstt-payload' || it.proto === 'http-inject') why.push('tunnel par payload : module dédié requis (à venir)'); else if (!['vless', 'vmess', 'trojan', 'ss'].includes(it.proto)) why.push(`protocole ${it.proto} non pris en charge par le moteur`);
  if (!it.host || !it.port) why.push('adresse ou port manquant');
  if (it.net === 'websocket') it.net = 'ws';
  if (it.net === 'http') it.net = 'h2';
  if (!['tcp', 'ws', 'grpc', 'httpupgrade', 'h2'].includes(it.net)) why.push(`transport ${it.net} non pris en charge`);
  if (it.security === 'xtls') why.push('XTLS ancien non pris en charge (utilisez Reality ou TLS)');
  if (it.security === 'reality' && !it.pbk) why.push('clé publique Reality (pbk) manquante');
  if (it.flow && (it.proto !== 'vless' || it.flow !== 'xtls-rprx-vision')) why.push('flow ' + it.flow + ' non pris en charge');
  if (it.proto === 'ss' && !SS_OK.includes(it.method)) why.push(`méthode ${it.method} non prise en charge`);
  if (it.proto === 'vmess' && Number(it.aid || 0) !== 0) why.push('VMess ancien (alterId ≠ 0) non pris en charge');
  if (['vless', 'vmess'].includes(it.proto) && !/^[0-9a-f-]{32,36}$/i.test(it.id || '')) why.push('UUID invalide');
  it.supported = why.length === 0; it.why = why.join(' ; '); return it;
}

function fromLink(line) {
  line = line.trim(); const m = line.match(/^([a-z0-9+.-]+):\/\//i); if (!m) return null; const sch = m[1].toLowerCase();
  try {
    if (sch === 'vmess') { const j = JSON.parse(b64d(line.slice(8)));
      return finish({ proto: 'vmess', name: j.ps || '', host: j.add, port: j.port, id: j.id, aid: +j.aid || 0, scy: j.scy || 'auto', net: j.net || 'tcp',
        path: j.path || '/', hostHeader: j.host || '', security: j.tls === 'tls' ? 'tls' : 'none', sni: j.sni || j.host || '', insecure: false, alpn: j.alpn ? String(j.alpn).split(',') : [] }); }
    if (sch === 'ss') { let rest = line.slice(5), name = ''; const h = rest.indexOf('#'); if (h >= 0) { name = dec(rest.slice(h + 1)); rest = rest.slice(0, h); }
      rest = rest.split('?')[0]; let method, pass, host, port;
      if (rest.includes('@')) { const [u, hp] = rest.split('@'); const cred = u.includes(':') ? dec(u) : b64d(u); [method, pass] = [cred.slice(0, cred.indexOf(':')), cred.slice(cred.indexOf(':') + 1)];
        [host, port] = [hp.slice(0, hp.lastIndexOf(':')), hp.slice(hp.lastIndexOf(':') + 1)]; }
      else { const d = b64d(rest); const at = d.lastIndexOf('@'); const cred = d.slice(0, at), hp = d.slice(at + 1);
        [method, pass] = [cred.slice(0, cred.indexOf(':')), cred.slice(cred.indexOf(':') + 1)]; [host, port] = [hp.slice(0, hp.lastIndexOf(':')), hp.slice(hp.lastIndexOf(':') + 1)]; }
      return finish({ proto: 'ss', name, host: host.replace(/^\[|\]$/g, ''), port, method: (method || '').toLowerCase(), pass }); }
    const u = new URL(line); const q = u.searchParams; const base = { name: dec(u.hash.replace(/^#/, '')), host: u.hostname.replace(/^\[|\]$/g, ''), port: u.port || 443,
      net: q.get('type') || 'tcp', path: dec(q.get('path') || '/'), hostHeader: q.get('host') || '', security: q.get('security') || 'none', sni: q.get('sni') || q.get('peer') || '',
      insecure: ['1', 'true'].includes(q.get('allowInsecure') || q.get('insecure') || ''), alpn: q.get('alpn') ? dec(q.get('alpn')).split(',') : [], flow: q.get('flow') || '',
      fp: q.get('fp') || '', pbk: q.get('pbk') || '', sid: q.get('sid') || '', serviceName: dec(q.get('serviceName') || '') };
    if (sch === 'vless') return finish({ ...base, proto: 'vless', id: dec(u.username) });
    if (sch === 'trojan') return finish({ ...base, proto: 'trojan', pass: dec(u.username) });
    if (['hysteria2', 'hy2', 'hysteria', 'tuic', 'wireguard', 'socks', 'http', 'https'].includes(sch) && sch !== 'http' && sch !== 'https')
      return finish({ ...base, proto: sch === 'hy2' ? 'hysteria2' : sch });
  } catch (e) { return { proto: sch, name: '', host: '', port: 0, supported: false, why: 'lien illisible' }; }
  return null;
}

function fromJson(o) {
  const out = []; const arr = Array.isArray(o) ? o : (o.outbounds || o.outbound ? [].concat(o.outbounds || o.outbound) : [o]);
  const dnsList = ((o.dns && (o.dns.servers || o.dns)) || []).map(d => typeof d === 'string' ? d : (d.address || d.server || '')).filter(Boolean);
  for (const b of arr) {
    if (!b || typeof b !== 'object') continue;
    const p = (b.protocol || b.type || '').toLowerCase(); const ss = b.streamSettings || {};
    let it = null;
    if (b.protocol) {                                              // Xray / V2Ray
      const st = ss.security || 'none', rl = ss.realitySettings || {}, tl = st === 'reality' ? rl : (ss.tlsSettings || {}), ws = ss.wsSettings || {}, hu = ss.httpupgradeSettings || {}, gr = ss.grpcSettings || {};
      const common = { name: b.tag || '', net: ss.network || 'tcp', security: st, sni: tl.serverName || '', insecure: !!tl.allowInsecure, alpn: tl.alpn || [],
        path: ws.path || hu.path || '/', hostHeader: (ws.headers && (ws.headers.Host || ws.headers.host)) || ws.host || hu.host || '',
        fp: tl.fingerprint || '', pbk: rl.publicKey || '', sid: rl.shortId || '', serviceName: gr.serviceName || '' };
      const s = b.settings || {};
      if (p === 'vless' || p === 'vmess') { const v = (s.vnext || [])[0] || {}; const u = (v.users || [])[0] || {};
        it = { ...common, proto: p, host: v.address, port: v.port, id: u.id, flow: u.flow || '', aid: u.alterId || 0, scy: u.security || 'auto' }; }
      else if (p === 'trojan') { const v = (s.servers || [])[0] || {}; it = { ...common, proto: 'trojan', host: v.address, port: v.port, pass: v.password }; }
      else if (p === 'shadowsocks') { const v = (s.servers || [])[0] || {}; it = { ...common, proto: 'ss', host: v.address, port: v.port, method: (v.method || '').toLowerCase(), pass: v.password }; }
    } else if (b.type && b.server) {                              // sing-box
      const tl = b.tls || {}, tr = b.transport || {};
      it = { name: b.tag || '', proto: p === 'shadowsocks' ? 'ss' : p, host: b.server, port: b.server_port, id: b.uuid, pass: b.password, method: (b.method || '').toLowerCase(),
        net: tr.type || 'tcp', path: tr.path || '/', hostHeader: (tr.headers && tr.headers.Host) || '', security: tl.reality && tl.reality.enabled ? 'reality' : (tl.enabled ? 'tls' : 'none'),
        sni: tl.server_name || '', insecure: !!tl.insecure, flow: b.flow || '', aid: b.alter_id || 0, scy: b.security || 'auto',
        fp: (tl.utls && tl.utls.fingerprint) || '', pbk: (tl.reality && tl.reality.public_key) || '', sid: (tl.reality && tl.reality.short_id) || '', serviceName: tr.service_name || '' };
    }
    if (it) { it.dns = dnsList[0] || ''; out.push(finish(it)); }
  }
  return out;
}

function detectExpire(obj) { // champs d'expiration courants dans les fichiers JSON
  const keys = ['expire', 'expiry', 'expires', 'expired', 'exp', 'expireTime', 'expire_at', 'expiration', 'valid_until', 'validUntil'];
  const walk = o => { if (!o || typeof o !== 'object') return 0; for (const k of Object.keys(o)) { if (keys.includes(k)) { const v = o[k];
      const n = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(v); if (n && n > 1e11) return n; } const r = walk(o[k]); if (r) return r; } return 0; };
  return walk(obj);
}

async function safeFetch(url) {
  const u = new URL(url); const addrs = await dns.lookup(u.hostname, { all: true });
  const bad = a => /^(10\.|127\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|0\.|::1|fc|fd|fe80)/i.test(a.address);
  if (addrs.some(bad)) throw new Error('adresse privée refusée');
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), 12000);
  try { const r = await fetch(url, { signal: ctl.signal, headers: { 'user-agent': 'v2rayN/6.0 Atlas' } });
    const text = (await r.text()).slice(0, 2_000_000); let expire = 0; const ui = r.headers.get('subscription-userinfo');
    if (ui) { const m = ui.match(/expire=(\d+)/); if (m && +m[1]) expire = +m[1] * 1000; } return { text, expire, status: r.status }; } finally { clearTimeout(t); }
}

/** input : texte, ou { binary:true } pour un fichier non texte. Renvoie { format, items[], expire, notes[] }. */
async function analyze(input, filename = '', isBinary = false) {
  const notes = []; let expire = 0; let items = []; let format = 'texte';
  if (isBinary) return { format: 'binaire', items: [], expire: 0, notes: ['Fichier binaire ou chiffré (format propriétaire d\'une autre application) : illisible. Exportez la configuration en lien (vless://, vmess://, trojan://, ss://) ou en JSON.'] };
  let text = String(input || '').trim();
  if (!text) return { format: 'vide', items: [], expire: 0, notes: ['Rien à analyser'] };
  if ((text.match(/[\u0000-\u0008\u000e-\u001f]/g) || []).length > 5) return { format: 'binaire', items: [], expire: 0, notes: ['Contenu illisible (chiffré ou propriétaire). Exportez en lien ou JSON.'] };
  if (/^https?:\/\/\S+$/i.test(text)) { format = 'abonnement'; try { const r = await safeFetch(text); text = r.text.trim(); expire = r.expire; if (r.status >= 400) notes.push('Le serveur d\'abonnement a répondu ' + r.status); }
    catch (e) { return { format, items: [], expire: 0, notes: ['URL inaccessible : ' + e.message] }; } }
  if (/^[\[{]/.test(text)) { try { const j = JSON.parse(text); format = 'JSON'; items = fromJson(j); expire = expire || detectExpire(j); } catch (e) { notes.push('JSON invalide'); } }
  else if (/^\[Interface\]/m.test(text)) { format = 'WireGuard'; items = [{ proto: 'wireguard', name: filename, host: (text.match(/Endpoint\s*=\s*([^:\s]+)/) || [])[1] || '', port: +(text.match(/Endpoint\s*=\s*[^:]+:(\d+)/) || [])[1] || 0, supported: false, why: 'WireGuard : moteur non inclus' }]; }
  else if (/^\s*(client|remote\s+\S+\s+\d+)/m.test(text) && /<ca>|dev tun/i.test(text)) { format = 'OpenVPN'; items = [{ proto: 'openvpn', name: filename, host: (text.match(/^remote\s+(\S+)\s+(\d+)/m) || [])[1] || '', port: +(text.match(/^remote\s+(\S+)\s+(\d+)/m) || [])[2] || 0, supported: false, why: 'OpenVPN : moteur non inclus' }]; }
  else if (/^proxies:/m.test(text)) { format = 'Clash (YAML)'; notes.push('Format Clash : exportez les nœuds en liens ou en JSON.'); }
  else {
    let body = text; if (!/:\/\//.test(body) && /^[A-Za-z0-9+/=_\s-]+$/.test(body)) { try { const d = b64d(body.replace(/\s/g, '')); if (/:\/\//.test(d)) { body = d; if (format === 'texte') format = 'abonnement'; } } catch (e) {} }
    const links = body.split(/\r?\n/).map(s => s.trim()).filter(Boolean); if (format === 'texte') format = links.length > 1 ? 'liste de liens' : 'lien';
    for (const l of links) { const it = fromLink(l); if (it) items.push(it); }
  }
  items.forEach(it => { if (!expire) { const m = (it.name || '').match(/(\d{4}-\d{2}-\d{2})/); if (m) { const t = Date.parse(m[1]); if (t > 1e11) expire = t; } } });
  if (!items.length && !notes.length) {
    const flat = text.replace(/\s/g, '');
    notes.push(flat.length > 80 && /^[A-Za-z0-9+/=_-]+$/.test(flat) && !/:\/\//.test(text)
      ? 'Ce texte ressemble à un fichier chiffré d\'une autre application : seule cette application peut le lire. Demandez à son propriétaire une configuration en lien (vless://, vmess://, trojan://, ss://) ou en JSON.'
      : 'Aucune configuration reconnue.');
  }
  return { format, items: items.slice(0, 100), expire, notes };
}

/** Test de joignabilité depuis le serveur : connexion TCP, puis poignée de main TLS avec le SNI. */
function testItem(it) {
  return new Promise(resolve => {
    if (!it.host || !it.port) return resolve({ ok: false, note: 'adresse manquante' });
    const t0 = Date.now(); let done = false; const fin = r => { if (!done) { done = true; resolve(r); } };
    const s = net.connect({ host: it.host, port: it.port, timeout: 6000 }, () => {
      if (it.security !== 'tls') { s.destroy(); return fin({ ok: true, ms: Date.now() - t0, note: 'port joignable' }); }
      const c = tls.connect({ socket: s, servername: it.sni || it.hostHeader || it.host, rejectUnauthorized: false }, () => { c.destroy(); fin({ ok: true, ms: Date.now() - t0, note: 'TLS établi' }); });
      c.on('error', e => fin({ ok: false, note: 'TLS : ' + e.message }));
    });
    s.on('timeout', () => { s.destroy(); fin({ ok: false, note: 'délai dépassé' }); }); s.on('error', e => fin({ ok: false, note: e.code || e.message }));
  });
}

function fromProprietary(o) {
  const out = [];
  const elc = o.encryptedLockedConfig && o.encryptedLockedConfig.EncryptedLockedConfig;
  if (elc && elc.V2RayConfig && elc.V2RayConfig.EncryptedConfig) {
    const inj = elc.InjectConfig || {};
    const items = fromJson(elc.V2RayConfig.EncryptedConfig);
    items.forEach(it => {
      if (inj.EncryptedMode === 'PROXY' && inj.EncryptedProxyHost) { it.injectHost = inj.EncryptedProxyHost; it.injectPort = +inj.EncryptedProxyPort || 0; }
      if (inj.EncryptedPayload) it.payload = inj.EncryptedPayload;
    });
    out.push(...items);
  }
  if (o.Fserver && o.FpubKey) {
    const [host, port] = String(o.Fserver).split(':');
    out.push(finish({ proto: 'dnstt-payload', name: o.Location || '', host, port: +port || 443, dnsttHost: o.FTunnelDomain || '', dnsttPub: o.FpubKey, payload: o.Payload || '', cdn: o.CDNTargetADDR || '' }));
  }
  if (o.payload && o.remoteProxy) {
    const [phost, pport] = String(o.remoteProxy).split(':');
    out.push(finish({ proto: 'http-inject', name: 'HTTP Injector', host: phost, port: +pport || 80, sni: o.sniHostname || '', payload: o.payload, user: o.user || '', pass: o.password || '' }));
  }
  return out;
}

module.exports = { analyze, testItem, fromProprietary };
