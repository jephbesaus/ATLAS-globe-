// Même cryptographie que l'application : ECDH P-256 -> HMAC-SHA256("atlas-v1") -> AES-256-GCM ; signatures ECDSA SHA-256 (DER).
const crypto = require('crypto'), fs = require('fs'), path = require('path');
const { DATA } = require('./config');
const KEY_FILE = path.join(DATA, 'atlas_key.json');

function load() {
  if (fs.existsSync(KEY_FILE)) { const j = JSON.parse(fs.readFileSync(KEY_FILE, 'utf8'));
    return { priv: crypto.createPrivateKey({ key: Buffer.from(j.priv, 'base64'), format: 'der', type: 'pkcs8' }), pub: j.pub }; }
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const priv = privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
  const pub = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  fs.writeFileSync(KEY_FILE, JSON.stringify({ priv, pub }), { mode: 0o600 });
  return { priv: privateKey, pub };
}
const K = load();
const pubObj = b64 => crypto.createPublicKey({ key: Buffer.from(b64, 'base64'), format: 'der', type: 'spki' });
const shared = peer => crypto.createHmac('sha256', 'atlas-v1').update(crypto.diffieHellman({ privateKey: K.priv, publicKey: pubObj(peer) })).digest();
function seal(key, plain) { const iv = crypto.randomBytes(12); const c = crypto.createCipheriv('aes-256-gcm', key, iv);
  return Buffer.concat([iv, c.update(plain), c.final(), c.getAuthTag()]); }
function open(key, blob) { try { const d = crypto.createDecipheriv('aes-256-gcm', key, blob.subarray(0, 12));
  d.setAuthTag(blob.subarray(blob.length - 16)); return Buffer.concat([d.update(blob.subarray(12, blob.length - 16)), d.final()]); } catch (e) { return null; } }
const sign = data => crypto.sign('sha256', data, { key: K.priv, dsaEncoding: 'der' });
const verify = (pub, data, sig) => { try { return crypto.verify('sha256', data, { key: pubObj(pub), dsaEncoding: 'der' }, sig); } catch (e) { return false; } };
const validPub = b64 => { try { pubObj(b64); return true; } catch (e) { return false; } };

/** Jeton d'accès Wi-Fi : "payload.signature" (base64), vérifié hors ligne par les revendeurs. */
function makeToken({ tid, u, p, dur, mb }) {
  const payload = Buffer.from(JSON.stringify({ tid, u, p, dur, mb, iat: Date.now() }));
  return payload.toString('base64') + '.' + sign(payload).toString('base64');
}
module.exports = { PUB: K.pub, shared, seal, open, sign, verify, validPub, makeToken };
