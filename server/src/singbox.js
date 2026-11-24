// Convertit un élément analysé par acparse en « sortie » sing-box (le moteur embarqué dans l'application Atlas).
// Rien n'est inventé : chaque champ vient de la configuration collée dans le panel.
const FP_OK = ['chrome', 'firefox', 'safari', 'ios', 'android', 'edge', '360', 'qq', 'random', 'randomized'];

function toOutbound(it) {
  if (!it || !it.host || !it.port) return null;
  const o = { server: it.host, server_port: +it.port };
  switch (it.proto) {
    case 'vless': o.type = 'vless'; o.uuid = it.id; if (it.flow) o.flow = it.flow; break;
    case 'vmess': o.type = 'vmess'; o.uuid = it.id; o.security = it.scy || 'auto'; o.alter_id = 0; break;
    case 'trojan': o.type = 'trojan'; o.password = it.pass; break;
    case 'ss': o.type = 'shadowsocks'; o.method = it.method; o.password = it.pass; break;
    default: return null;
  }
  const sec = it.security || 'none';
  if (sec === 'tls' || sec === 'reality' || it.proto === 'trojan') {
    const sni = it.sni || it.hostHeader || (/^[0-9.]+$|:/.test(it.host) ? '' : it.host);
    const tls = { enabled: true };
    if (sni) tls.server_name = sni;
    if (it.insecure) tls.insecure = true;
    if (it.alpn && it.alpn.length) tls.alpn = it.alpn;
    const fp = FP_OK.includes(String(it.fp || '').toLowerCase()) ? String(it.fp).toLowerCase() : '';
    if (sec === 'reality') {
      if (!it.pbk) return null;
      tls.utls = { enabled: true, fingerprint: fp || 'chrome' };
      tls.reality = { enabled: true, public_key: it.pbk, short_id: it.sid || '' }; tls.utls = { enabled: true, fingerprint: it.fp || 'chrome' };
    } else if (fp) tls.utls = { enabled: true, fingerprint: fp };
    o.tls = tls;
  }
  const path = it.path || '/';
  if (it.net === 'ws') {
    o.transport = { type: 'ws', path };
    if (it.hostHeader) o.transport.headers = { Host: it.hostHeader };
  } else if (it.net === 'grpc') {
    o.transport = { type: 'grpc', service_name: it.serviceName || '' };
  } else if (it.net === 'httpupgrade') {
    o.transport = { type: 'httpupgrade', path };
    if (it.hostHeader) o.transport.host = it.hostHeader;
  } else if (it.net === 'h2' || it.net === 'http') {
    o.transport = { type: 'http', path };
    if (it.hostHeader) o.transport.host = String(it.hostHeader).split(',');
  }
  return o;
}
module.exports = { toOutbound };
