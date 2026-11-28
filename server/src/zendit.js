// Intégration Zendit (eSIM) : catalogue en lecture seule pour l'instant (aucun achat réel).
const cfg = require('./config');
const BASE = 'https://api.zendit.io/v1';

async function listOffers(country, limit = 100, offset = 0) {
  if (!cfg.ZENDIT_API_KEY) throw new Error('Clé Zendit manquante dans .env');
  const url = `${BASE}/esim/offers?_limit=${limit}&_offset=${offset}` + (country ? `&country=${encodeURIComponent(country)}` : '');
  const r = await fetch(url, { headers: { Authorization: `Bearer ${cfg.ZENDIT_API_KEY}`, Accept: 'application/json' } });
  const j = await r.json();
  if (!r.ok) throw new Error(j?.message || j?.description || ('Zendit a refusé : ' + r.status));
  return j;
}

module.exports = { listOffers };
