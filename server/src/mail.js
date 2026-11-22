const cfg = require('./config');
/** Envoi d'e-mail par Brevo. Sans clé : en développement le message est affiché dans la console. */
async function send(to, subject, text) {
  if (!cfg.BREVO_API_KEY) { if (!cfg.PROD) console.log(`[MAIL à ${to}] ${subject}\n${text}\n`); return cfg.PROD ? false : true; }
  try {
    const r = await fetch('https://api.brevo.com/v3/smtp/email', { method: 'POST',
      headers: { 'api-key': cfg.BREVO_API_KEY, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ sender: { name: cfg.MAIL_FROM_NAME, email: cfg.MAIL_FROM }, to: [{ email: to }], subject,
        htmlContent: `<div style="font-family:sans-serif;font-size:16px">${text.replace(/\n/g, '<br>')}</div>` }) });
    return r.ok;
  } catch (e) { return false; }
}
module.exports = { send };
