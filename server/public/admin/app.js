'use strict';
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = t => t ? new Date(t).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
const fc = n => new Intl.NumberFormat('fr-FR').format(n) + ' FC';
async function api(path, method = 'GET', body) {
  const r = await fetch('/admin/api' + path, { method, headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'atlas' }, body: body ? JSON.stringify(body) : undefined });
  const j = await r.json().catch(() => ({}));
  if (r.status === 401 && !path.startsWith('/login') && !path.startsWith('/otp')) { showLogin(); throw new Error('non connecté'); }
  if (!r.ok) throw new Error(j.error || 'erreur'); return j;
}
const toast = m => alert(m);

// ---------- connexion ----------
function showLogin() { $('#app').classList.add('hidden'); $('#login').classList.remove('hidden'); }
$('#f1').onsubmit = async e => { e.preventDefault(); $('#lerr').textContent = '';
  try { await api('/login', 'POST', { email: $('#email').value, password: $('#pw').value }); $('#f1').classList.add('hidden'); $('#f2').classList.remove('hidden'); }
  catch (x) { $('#lerr').textContent = x.message; } };
$('#f2').onsubmit = async e => { e.preventDefault(); $('#lerr').textContent = '';
  try { await api('/otp', 'POST', { code: $('#otp').value }); start(); } catch (x) { $('#lerr').textContent = x.message; } };
$('#out').onclick = async () => { await api('/logout', 'POST'); location.reload(); };

// ---------- navigation ----------
const PAGES = [['dash', 'Tableau de bord'], ['users', 'Comptes'], ['posts', 'Publications'], ['groups', 'Groupes'], ['resellers', 'Revendeurs Wi-Fi'], ['orders', 'Paiements'],
  ['certs', 'Certifications'], ['chats', 'Messages Assistant'], ['announce', 'Publier une annonce'], ['broadcast', 'Message à tous'], ['network', 'Compte network'], ['netconf', 'Configuration network']];
let badges = {};
function nav(cur) { $('#nav').innerHTML = '<div style="padding:0 22px 16px;font-weight:700;font-size:18px">Atlas Globe</div>' + PAGES.map(([k, t]) =>
  `<a data-p="${k}" class="${k === cur ? 'on' : ''}" href="#${k}">${t}${badges[k] ? `<span class="b">${badges[k]}</span>` : ''}</a>`).join(''); }
async function start() {
  $('#login').classList.add('hidden'); $('#app').classList.remove('hidden');
  const me = await api('/me'); $('#who').textContent = me.email; window.ME = me;
  const s = await api('/stats'); badges = { orders: s.ordersPending, certs: s.certPending }; route();
}
window.onhashchange = () => route();
async function route() { const k = (location.hash || '#dash').slice(1); const p = PAGES.find(x => x[0] === k) || PAGES[0];
  $('#title').textContent = p[1]; nav(p[0]); $('#view').innerHTML = '<p class="mute">Chargement…</p>';
  try { await views[p[0]](); } catch (e) { $('#view').innerHTML = `<p class="err">${esc(e.message)}</p>`; } }
const view = h => { $('#view').innerHTML = h; };
function modal(h) { $('#box').innerHTML = h; $('#modal').classList.remove('hidden'); }
$('#modal').onclick = e => { if (e.target.id === 'modal') $('#modal').classList.add('hidden'); };
const closeModal = () => $('#modal').classList.add('hidden');
const ask = (q, def = '') => prompt(q, def);

// ---------- pages ----------
const views = {
  async dash() { const s = await api('/stats');
    const c = (l, v) => `<div class="card"><b>${v}</b><span class="mute">${l}</span></div>`;
    view(`<div class="cards">${c('Comptes inscrits', s.users)}${c('Nouveaux (24 h)', s.today)}${c('Comptes bloqués', s.blocked)}${c('Revendeurs Wi-Fi', s.resellers)}${c('Publications', s.posts)}${c('Groupes', s.groups)}
      ${c('Paiements à vérifier', s.ordersPending)}${c('Certifications à vérifier', s.certPending)}${c('Paiements approuvés', fc(s.revenue))}${c('Configurations qui expirent (3 j)', s.acExpiring)}</div>
      <p class="mute" style="margin-top:24px">Clé publique Atlas (à mettre dans <code>AtlasConfig.kt</code> : ASSISTANT_PUB et ADMIN_PUB) :</p><textarea readonly>${esc(window.ME.atlasPub)}</textarea>`); },

  async users() { let q = '', off = 0;
    const draw = async () => { const r = await api(`/users?q=${encodeURIComponent(q)}&offset=${off}&limit=50`);
      view(`<div class="row"><input id="q" placeholder="Rechercher (nom, ID, numéro, e-mail)" value="${esc(q)}"><button class="btn alt" id="go">Rechercher</button><span class="mute">${r.total} compte(s)</span></div>
        <table><tr><th>Nom</th><th>ID Atlas</th><th>Contact</th><th>Origine</th><th>Inscrit</th><th>Dernière activité</th><th>État</th><th></th></tr>
        ${r.users.map(u => `<tr><td>${esc(u.prenom)} ${esc(u.nom)}${u.stub ? ' <span class="tag">non inscrit</span>' : ''}</td><td>${u.id}</td><td>${esc(u.phone)}<br><span class="mute">${esc(u.email)}${u.email_verified ? ' ✓' : ''}</span></td>
        <td>${esc(u.country)}<br><span class="mute">${esc(u.province)} · ${esc(u.city)}</span></td><td>${fmt(u.created_at)}</td><td>${fmt(u.last_seen)}</td>
        <td>${u.blocked ? '<span class="tag r">Bloqué</span>' : ''}${u.reseller === 1 ? '<span class="tag">Revendeur</span>' : ''}${u.reseller === 2 ? '<span class="tag r">Revendeur suspendu</span>' : ''}${u.cert ? `<span class="tag w">Certifié ${['', 'Standard', 'Premium', 'Pro'][u.cert]}</span>` : ''}</td>
        <td>${u.blocked ? `<button class="btn sm alt" data-a="unb" data-id="${u.id}">Débloquer</button>` : `<button class="btn sm red" data-a="blk" data-id="${u.id}">Bloquer</button>`}
        ${u.reseller === 1 ? `<button class="btn sm alt" data-a="rs0" data-id="${u.id}">Suspendre revendeur</button>` : `<button class="btn sm alt" data-a="rs1" data-id="${u.id}">Revendeur</button>`}
        <button class="btn sm alt" data-a="msg" data-id="${u.id}">Écrire</button></td></tr>`).join('')}</table>
        <div class="row" style="margin-top:12px">${off > 0 ? '<button class="btn alt" id="pv">Précédent</button>' : ''}${r.users.length === 50 ? '<button class="btn alt" id="nx">Suivant</button>' : ''}</div>`);
      $('#go').onclick = () => { q = $('#q').value; off = 0; draw(); }; if ($('#pv')) $('#pv').onclick = () => { off -= 50; draw(); }; if ($('#nx')) $('#nx').onclick = () => { off += 50; draw(); };
      $('#view').onclick = async e => { const b = e.target.closest('button[data-a]'); if (!b) return; const id = b.dataset.id, a = b.dataset.a;
        try { if (a === 'blk') { const r = ask('Motif du blocage (visible par l\'utilisateur) :'); if (r === null) return; await api(`/users/${id}/block`, 'POST', { reason: r }); }
          if (a === 'unb') await api(`/users/${id}/unblock`, 'POST');
          if (a === 'rs1') { if (!confirm('Approuver ce compte comme Revendeur Wi-Fi ?')) return; await api(`/users/${id}/reseller`, 'POST', { approved: true }); }
          if (a === 'rs0') { if (!confirm('Suspendre ce revendeur ?')) return; await api(`/users/${id}/reseller`, 'POST', { suspend: true }); }
          if (a === 'msg') { location.hash = '#chats'; sessionStorage.openChat = id; return; }
          draw(); } catch (x) { toast(x.message); } }; };
    draw(); },

  async posts() { const r = await api('/posts?limit=100');
    view(`<table><tr><th>Auteur</th><th>Contenu</th><th>Date</th><th></th></tr>${r.posts.map(p => `<tr><td>${esc(p.author_name)}<br><span class="mute">${p.author}</span></td>
      <td>${p.media ? (p.media.endsWith('.mp4') ? `<video src="/media/${p.media}" controls preload="none" style="max-width:220px;display:block;margin-bottom:6px"></video>` : `<img src="/media/${p.media}" style="max-width:140px;display:block;margin-bottom:6px">`) : (p.kind === 'video' ? '<span class="tag">vidéo en cours d\'envoi</span>' : '')}${esc(p.text)}</td><td>${fmt(p.created_at)}</td>
      <td>${p.deleted ? '<span class="tag r">Supprimée</span>' : `<button class="btn sm red" data-id="${p.id}">Supprimer</button>`}</td></tr>`).join('')}</table>`);
    $('#view').onclick = async e => { const b = e.target.closest('button[data-id]'); if (!b || !confirm('Supprimer cette publication (fausse ou interdite) ?')) return; await api('/posts/' + b.dataset.id, 'DELETE'); views.posts(); }; },

  async groups() { const r = await api('/groups');
    view(`<p class="mute">Le panel voit le nom, le créateur et les membres. Le contenu des messages reste chiffré de bout en bout et illisible.</p>
      <table><tr><th>Groupe</th><th>Créateur</th><th>Membres</th><th>Créé</th><th></th></tr>${r.groups.map(g => `<tr><td>${esc(g.name)}<br><span class="mute">${g.id}</span></td><td>${g.creator}</td><td>${g.members}</td><td>${fmt(g.created_at)}</td>
      <td><button class="btn sm alt" data-id="${g.id}">Voir les membres</button></td></tr>`).join('')}</table>`);
    $('#view').onclick = async e => { const b = e.target.closest('button[data-id]'); if (!b) return; const g = await api('/groups/' + b.dataset.id);
      modal(`<h3>${esc(g.group.name)}</h3><table><tr><th>Nom</th><th>ID</th><th>Numéro</th><th>Pays d'origine</th></tr>${g.members.map(m => `<tr><td>${esc(m.prenom || '')} ${esc(m.nom || '')}${m.blocked ? ' <span class="tag r">Bloqué</span>' : ''}</td><td>${m.id}</td><td>${esc(m.phone || '')}</td><td>${esc(m.country || '—')}<br><span class="mute">${esc(m.province || '')} ${esc(m.city || '')}</span></td></tr>`).join('')}</table><button class="btn alt" onclick="closeModal()">Fermer</button>`); }; },

  async resellers() { const r = await api('/resellers');
    view(`<p class="mute">Pour créer un revendeur : onglet <b>Comptes</b>, bouton « Revendeur » sur le compte. L'espace Revendeur Wi-Fi apparaît alors dans son téléphone.</p>
      <table><tr><th>Revendeur</th><th>ID</th><th>Ville</th><th>Note</th><th>Volume rapporté</th><th>État</th><th></th></tr>${r.resellers.map(u => `<tr><td>${esc(u.prenom)} ${esc(u.nom)}<br><span class="mute">${esc(u.phone)}</span></td><td>${u.id}</td>
      <td>${esc(u.city)}, ${esc(u.country)}</td><td>${u.rating ? u.rating + ' / 5 (' + u.votes + ')' : '—'}</td><td>${(u.bytes / 1e6).toFixed(0)} Mo</td><td>${u.reseller === 1 ? '<span class="tag w">Approuvé</span>' : '<span class="tag r">Suspendu</span>'}</td>
      <td>${u.reseller === 1 ? `<button class="btn sm red" data-a="sus" data-id="${u.id}">Suspendre</button>` : `<button class="btn sm alt" data-a="app" data-id="${u.id}">Réactiver</button>`}</td></tr>`).join('')}</table>`);
    $('#view').onclick = async e => { const b = e.target.closest('button[data-id]'); if (!b) return; await api(`/users/${b.dataset.id}/reseller`, 'POST', b.dataset.a === 'sus' ? { suspend: true } : { approved: true }); views.resellers(); }; },

  async orders() { const r = await api('/orders'); const st = { awaiting: 'À vérifier', approved: 'Approuvé', refused: 'Refusé' };
    view(`<p class="mute">Vérifiez le paiement reçu (Mobile Money) dans <b>Messages Assistant</b>, puis approuvez : le client reçoit son accès et se connecte seul.</p>
      <table><tr><th>Commande</th><th>Client</th><th>Offre</th><th>Montant</th><th>Revendeur</th><th>Date</th><th>État</th><th></th></tr>${r.orders.map(o => `<tr><td>${o.id}</td><td>${esc(o.prenom || '')} ${esc(o.nom || '')}<br><span class="mute">${o.user}</span></td>
      <td>${esc(o.plan_label)}</td><td>${fc(o.amount)}</td><td>${esc(o.reseller_name)}</td><td>${fmt(o.created_at)}${o.decided_at ? '<br><span class="mute">décidé ' + fmt(o.decided_at) + '</span>' : ''}</td>
      <td><span class="tag ${o.status === 'refused' ? 'r' : o.status === 'approved' ? 'w' : ''}">${st[o.status]}</span></td>
      <td>${o.status === 'awaiting' ? `<button class="btn sm" data-a="ok" data-id="${o.id}">Approuver</button><button class="btn sm red" data-a="no" data-id="${o.id}">Refuser</button><button class="btn sm alt" data-a="chat" data-u="${o.user}">Discussion</button>` : ''}</td></tr>`).join('')}</table>`);
    $('#view').onclick = async e => { const b = e.target.closest('button[data-a]'); if (!b) return; try {
      if (b.dataset.a === 'chat') { sessionStorage.openChat = b.dataset.u; location.hash = '#chats'; return; }
      if (b.dataset.a === 'ok' && confirm('Le paiement est bien reçu ? Approuver et envoyer l\'accès ?')) await api(`/orders/${b.dataset.id}/approve`, 'POST');
      if (b.dataset.a === 'no' && confirm('Refuser cette commande ?')) await api(`/orders/${b.dataset.id}/refuse`, 'POST'); views.orders(); } catch (x) { toast(x.message); } }; },

  async certs() { const r = await api('/certs'); const L = ['', 'Standard', 'Premium', 'Pro']; const st = { pending: 'À vérifier', approved: 'Approuvée', rejected: 'Rejetée' };
    view(`<table><tr><th>Utilisateur</th><th>Niveau</th><th>Origine</th><th>Demande</th><th>État</th><th></th></tr>${r.requests.map(c => `<tr><td>${esc(c.prenom || '')} ${esc(c.nom || '')}<br><span class="mute">${c.user} · ${esc(c.phone || '')}</span></td><td>${L[c.level]}</td>
      <td>${esc(c.country || '')} ${esc(c.city || '')}</td><td>${fmt(c.created_at)}</td><td><span class="tag ${c.status === 'rejected' ? 'r' : c.status === 'approved' ? 'w' : ''}">${st[c.status]}</span>${c.reason ? '<br><span class="mute">' + esc(c.reason) + '</span>' : ''}</td>
      <td><button class="btn sm alt" data-id="${c.id}" data-p="${c.status}">${c.status === 'pending' ? 'Vérifier' : 'Voir'}</button></td></tr>`).join('')}</table>`);
    $('#view').onclick = e => { const b = e.target.closest('button[data-id]'); if (!b) return; const id = b.dataset.id, pending = b.dataset.p === 'pending';
      const img = k => `<a href="/admin/api/certs/${id}/${k}" target="_blank"><img src="/admin/api/certs/${id}/${k}" alt="${k}" onerror="this.outerHTML='<span class=mute>supprimée</span>'"></a>`;
      modal(`<h3>Demande de certification n°${id}</h3><div class="imgs"><div>Recto<br>${img('front')}</div><div>Verso<br>${img('back')}</div><div>Selfie<br>${img('selfie')}</div></div>
        <p class="mute">Comparez le visage du selfie avec la pièce, le nom avec le compte, et vérifiez le paiement dans Messages Assistant. Les images sont supprimées 30 jours après votre décision.</p>
        ${pending ? '<button class="btn" id="ap">Approuver</button> <button class="btn red" id="rj">Rejeter avec un motif</button>' : ''} <button class="btn alt" onclick="closeModal()">Fermer</button>`);
      if (pending) { $('#ap').onclick = async () => { await api(`/certs/${id}/approve`, 'POST'); closeModal(); views.certs(); };
        $('#rj').onclick = async () => { const m = ask('Motif du rejet (visible par l\'utilisateur) :'); if (!m) return; try { await api(`/certs/${id}/reject`, 'POST', { reason: m }); closeModal(); views.certs(); } catch (x) { toast(x.message); } }; } }; },

  async chats() { const r = await api('/chats'); let cur = sessionStorage.openChat; sessionStorage.removeItem('openChat');
    const list = () => `<div style="display:flex;gap:18px;flex-wrap:wrap"><div style="flex:1;min-width:260px;max-width:340px">${r.chats.map(c => `<div class="card" style="margin-bottom:8px;cursor:pointer" data-u="${c.user}"><b style="font-size:15px">${esc(c.prenom || '')} ${esc(c.nom || c.user)}</b>
      <span class="mute">${esc((c.preview || '').slice(0, 70))}</span><br><span class="mute">${fmt(c.last)}</span></div>`).join('') || '<p class="mute">Aucun message.</p>'}</div><div id="thread" style="flex:2;min-width:300px"><p class="mute">Choisissez une discussion.</p></div></div>`;
    view(list());
    const open = async u => { const t = await api('/chats/' + encodeURIComponent(u)); const name = t.user ? `${t.user.prenom} ${t.user.nom}` : u;
      $('#thread').innerHTML = `<h3 style="margin-top:0">${esc(name)} <span class="mute" style="font-size:13px">${u}</span></h3><div class="chat" id="cb">${t.messages.map(m => `<div class="m ${m.dir}">${esc(m.text)}<br><small style="opacity:.6">${fmt(m.created_at)}</small></div>`).join('')}</div>
        <textarea id="rt" placeholder="Répondre en tant qu'Atlas Assistant (ex. numéros de paiement)"></textarea><button class="btn" id="sd">Envoyer</button>`;
      $('#cb').scrollTop = 1e9; $('#sd').onclick = async () => { const text = $('#rt').value.trim(); if (!text) return; try { await api('/chats/' + encodeURIComponent(u), 'POST', { text }); open(u); } catch (x) { toast(x.message); } }; };
    $('#view').onclick = e => { const c = e.target.closest('[data-u]'); if (c) open(c.dataset.u); }; if (cur) open(cur); },

  async announce() { const h = await api('/announcements');
    view(`<p class="mute">Les utilisateurs voient l'annonce <b>devant l'application</b> à l'ouverture et peuvent la fermer. Chaque annonce peut avoir un lien WhatsApp, un lien Telegram et un support : seulement si vous les remplissez.</p>
      <input id="t" placeholder="Titre"><textarea id="b" placeholder="Texte de l'annonce"></textarea><input id="wa" placeholder="Lien du groupe WhatsApp (https://chat.whatsapp.com/…) — facultatif">
      <input id="tg" placeholder="Lien Telegram (https://t.me/…) — facultatif"><input id="sp" placeholder="Support (ex. numéro WhatsApp ou e-mail) — facultatif"><button class="btn" id="pb">Publier à tous les comptes</button>
      <h3 style="margin-top:30px">Dernières annonces</h3><table><tr><th>Date</th><th>Titre</th><th>Destinataires</th><th>Liens</th></tr>${h.announcements.map(a => `<tr><td>${fmt(a.created_at)}</td><td>${esc(a.title)}</td><td>${a.audience}</td><td>${a.whatsapp ? 'WhatsApp ' : ''}${a.telegram ? 'Telegram ' : ''}${a.support ? 'Support' : ''}</td></tr>`).join('')}</table>`);
    $('#pb').onclick = async () => { if (!confirm('Publier cette annonce à tous les comptes ?')) return; try { const r = await api('/announce', 'POST', { title: $('#t').value, body: $('#b').value, whatsapp: $('#wa').value, telegram: $('#tg').value, support: $('#sp').value });
      toast('Annonce envoyée à ' + r.sent + ' compte(s).'); views.announce(); } catch (x) { toast(x.message); } }; },

  async broadcast() { const r = await api('/users?limit=200');
    view(`<p class="mute">Le message arrive dans la discussion Atlas Assistant de chaque destinataire, avec une notification (icône Atlas et son).</p>
      <textarea id="mt" placeholder="Votre message"></textarea><div class="row"><label><input type="checkbox" id="all" checked style="width:auto"> Tous les comptes inscrits</label></div>
      <div id="pick" class="hidden" style="max-height:260px;overflow:auto;border:1px solid #222;padding:8px">${r.users.filter(u => !u.stub && !u.blocked).map(u => `<label style="display:block"><input type="checkbox" class="cb" value="${u.id}" style="width:auto"> ${esc(u.prenom)} ${esc(u.nom)} <span class="mute">${u.id} · ${esc(u.country)}</span></label>`).join('')}</div>
      <button class="btn" id="sm">Envoyer</button>`);
    $('#all').onchange = e => $('#pick').classList.toggle('hidden', e.target.checked);
    $('#sm').onclick = async () => { const users = $('#all').checked ? [] : [...document.querySelectorAll('.cb:checked')].map(c => c.value);
      if (!$('#all').checked && !users.length) return toast('Choisissez au moins un destinataire.'); if (!confirm('Envoyer ce message ?')) return;
      try { const x = await api('/broadcast', 'POST', { text: $('#mt').value, users }); toast('Message envoyé à ' + x.sent + ' compte(s).'); $('#mt').value = ''; } catch (e) { toast(e.message); } }; },

  async network() { view('<p class="mute">Compte network : en attente de votre explication. Cette section sera construite dès que vous m\'aurez décrit son rôle.</p>'); },
  async netconf() { view('<p class="mute">Configuration network : en attente de votre explication.</p>'); },
};

api('/me').then(start).catch(showLogin);
