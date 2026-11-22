'use strict';
// Pages Atlas Connect : comptes clients (tokens), configurations VPN (collées, importées ou par URL), attribution aux clients.
const dleft = t => t ? Math.ceil((t - Date.now()) / 86400000) : null;
const dtag = t => { const d = dleft(t); if (d === null) return '<span class="mute">non renseignée</span>'; if (d < 0) return `<span class="tag r">Expirée il y a ${-d} j</span>`; if (d <= 3) return `<span class="tag r">Expire dans ${d} j</span>`; return `<span class="tag">Expire dans ${d} j</span>`; };
const globe = '<svg viewBox="0 0 100 100" width="64" height="64" style="animation:spin 1.2s linear infinite"><circle cx="50" cy="50" r="46" fill="none" stroke="#fff" stroke-width="2"/><ellipse cx="50" cy="50" rx="20" ry="46" fill="none" stroke="#fff"/><line x1="4" y1="50" x2="96" y2="50" stroke="#fff"/></svg><style>@keyframes spin{to{transform:rotate(360deg)}}</style>';
const copy = async t => { try { await navigator.clipboard.writeText(t); toast('Copié.'); } catch (e) { prompt('Copiez :', t); } };
window.copyText = copy;

views.network = async function () {
  const [c, t] = await Promise.all([api('/ac/clients'), api('/ac/tariffs')]);
  view(`<p class="mute">Un client Atlas Connect reçoit un <b>token</b> (commence par « Atlas »). Il le colle dans l'application, puis utilise le bouton power. Le solde sert à payer les tarifs.</p>
    <div class="card" style="margin-bottom:22px"><b style="font-size:17px">Nouveau client Atlas Connect</b><div class="row" style="margin-top:10px">
      <input id="cn" placeholder="Nom complet"><input id="cc" placeholder="Pays"><input id="ce" placeholder="E-mail"><input id="cp" placeholder="Numéro"><button class="btn" id="cr">Créer le compte</button></div><div id="cres"></div></div>
    <table><tr><th>Client</th><th>Contact</th><th>Token</th><th>Appareil</th><th>Solde</th><th>Abonnement</th><th>Configuration</th><th></th></tr>${c.clients.map(x => `<tr>
      <td>${esc(x.name)}<br><span class="mute">${esc(x.country)}</span> ${x.status !== 'active' ? '<span class="tag r">Suspendu</span>' : ''}</td><td>${esc(x.email)}<br><span class="mute">${esc(x.phone)}</span></td>
      <td><button class="btn sm alt" onclick="copyText('${esc(x.token)}')">Copier</button></td><td>${x.device ? '<span class="tag w">Lié</span>' : '<span class="tag">Non lié</span>'}</td>
      <td>${fc(x.balance)}</td><td>${x.expires_at > Date.now() ? 'jusqu\'au ' + new Date(x.expires_at).toLocaleDateString('fr-FR') : '<span class="mute">aucun</span>'}</td>
      <td>${x.config_name ? esc(x.config_name) : '<span class="tag r">Aucune</span>'}</td>
      <td><button class="btn sm" data-a="cr" data-id="${x.id}">Recharger</button><button class="btn sm alt" data-a="cf" data-id="${x.id}">Configuration</button>
      <button class="btn sm alt" data-a="${x.status === 'active' ? 'su' : 're'}" data-id="${x.id}">${x.status === 'active' ? 'Suspendre' : 'Réactiver'}</button><button class="btn sm alt" data-a="rs" data-id="${x.id}">Réinitialiser l'appareil</button>
      <button class="btn sm red" data-a="dl" data-id="${x.id}">Supprimer</button></td></tr>`).join('')}</table>
    <h3 style="margin-top:34px">Tarifs Atlas Connect</h3><div class="row"><input id="tl" placeholder="Libellé (ex. 30 jours)"><input id="tp" type="number" placeholder="Prix en FC"><input id="td" type="number" placeholder="Durée en jours"><button class="btn alt" id="ta">Ajouter</button></div>
    <table><tr><th>Libellé</th><th>Prix</th><th>Durée</th><th></th></tr>${t.tariffs.map(x => `<tr><td>${esc(x.label)}</td><td>${fc(x.price)}</td><td>${x.days} j</td><td><button class="btn sm red" data-a="dt" data-id="${x.id}">Supprimer</button></td></tr>`).join('') || '<tr><td colspan="4" class="mute">Aucun tarif : ajoutez-en pour que les clients puissent payer.</td></tr>'}</table>`);
  $('#cr').onclick = async () => { $('#cres').innerHTML = `<div style="text-align:center;padding:14px">${globe}<p class="mute">Création du compte…</p></div>`;
    try { const r = await api('/ac/clients', 'POST', { name: $('#cn').value, country: $('#cc').value, email: $('#ce').value, phone: $('#cp').value }); await new Promise(z => setTimeout(z, 900));
      $('#cres').innerHTML = `<p style="margin-top:14px" class="mute">Compte créé. Copiez ce token et donnez-le au client :</p><div class="card" style="font-size:20px;letter-spacing:1px;font-family:monospace">${esc(r.token)}</div><button class="btn" onclick="copyText('${esc(r.token)}')">Copier le token</button> <button class="btn alt" onclick="views.network()">Terminer</button>`; }
    catch (e) { $('#cres').innerHTML = `<p class="err">${esc(e.message)}</p>`; } };
  $('#ta').onclick = async () => { try { await api('/ac/tariffs', 'POST', { label: $('#tl').value, price: $('#tp').value, days: $('#td').value }); views.network(); } catch (e) { toast(e.message); } };
  $('#view').onclick = async e => { const b = e.target.closest('button[data-a]'); if (!b) return; const id = b.dataset.id, a = b.dataset.a;
    try { if (a === 'cr') { const v = ask('Montant à créditer en FC (négatif pour retirer) :'); if (!v) return; await api(`/ac/clients/${id}/credit`, 'POST', { amount: v }); }
      else if (a === 'cf') return assignModal(id); else if (a === 'su' || a === 're') await api(`/ac/clients/${id}/status`, 'POST', { suspend: a === 'su' });
      else if (a === 'rs') { if (!confirm('Délier l\'appareil de ce client ? Il pourra réutiliser son token sur un autre téléphone.')) return; await api(`/ac/clients/${id}/reset`, 'POST'); }
      else if (a === 'dl') { if (!confirm('Supprimer ce client ?')) return; await api('/ac/clients/' + id, 'DELETE'); }
      else if (a === 'dt') await api('/ac/tariffs/' + id, 'DELETE'); views.network(); } catch (x) { toast(x.message); } };
};

async function assignModal(clientId) { const [cf, cl] = await Promise.all([api('/ac/configs'), api('/ac/clients')]); const cli = cl.clients.find(c => c.id == clientId);
  modal(`<h3>Configuration pour ${esc(cli.name)}</h3><div class="row"><select id="sel"><option value="">— Aucune —</option>${cf.configs.map(c => `<option value="${c.id}" ${cli.config_id == c.id ? 'selected' : ''}>${esc(c.name)} (${c.items.filter(i => i.supported).length} nœud(s))</option>`).join('')}</select></div>
    <button class="btn" id="ok">Donner cette configuration</button> <button class="btn alt" onclick="closeModal()">Annuler</button>`);
  $('#ok').onclick = async () => { await api(`/ac/clients/${clientId}/assign`, 'POST', { config: $('#sel').value || null }); closeModal(); views.network(); }; }

views.netconf = async function () {
  const r = await api('/ac/configs'); let analysis = null;
  const detail = it => `<tr><td>${esc(it.proto)}</td><td>${esc(it.host)}:${it.port}</td><td>${esc(it.net || '')}${it.path && it.net === 'ws' ? '<br><span class="mute">' + esc(it.path) + '</span>' : ''}</td><td>${esc(it.security || '')}</td>
    <td>${esc(it.sni || '—')}<br><span class="mute">${esc(it.hostHeader || '')}</span></td><td>${esc(it.dns || '—')}</td><td>${it.supported ? '<span class="tag w">Pris en charge</span>' : `<span class="tag r">Non</span><br><span class="mute">${esc(it.why || '')}</span>`}</td><td>${esc(it.name || '')}</td></tr>`;
  const head = '<tr><th>Protocole</th><th>Serveur</th><th>Transport</th><th>Sécurité</th><th>SNI / Host</th><th>DNS</th><th>Moteur Atlas</th><th>Nom</th></tr>';
  view(`<p class="mute">Collez ici n'importe quelle configuration : <b>liens VLESS, VMess, Trojan, Shadowsocks</b>, URL d'abonnement, JSON Xray / V2Ray / sing-box, ou importez un fichier. L'application des clients n'a aucune configuration en dur : elle utilise exactement ce que vous enregistrez ici.</p>
    <div class="card" style="margin-bottom:24px"><b style="font-size:17px">Importer une configuration</b>
      <div class="row" style="margin-top:10px"><input type="file" id="file" style="max-width:380px"><span class="mute">fichier texte, JSON, liste de liens</span></div>
      <textarea id="txt" placeholder="Ou collez ici : un lien vless://… vmess://… trojan://… ss://…, plusieurs liens (un par ligne), une URL d'abonnement https://…, ou le contenu d'un fichier de configuration"></textarea>
      <button class="btn" id="an">Analyser</button><div id="ares"></div></div>
    <h3>Configurations enregistrées</h3>
    <table><tr><th>Nom</th><th>Nœuds</th><th>Protocoles</th><th>Expiration</th><th>Clients</th><th></th></tr>${r.configs.map(c => `<tr><td>${esc(c.name)}<br><span class="mute">${esc(c.source)} · ${fmt(c.created_at)}</span></td>
      <td>${c.items.filter(i => i.supported).length} / ${c.items.length}</td><td>${[...new Set(c.items.map(i => i.proto))].map(esc).join(', ')}</td><td>${dtag(c.expire_at)}</td><td>${c.clients}</td>
      <td><button class="btn sm alt" data-a="dt" data-id="${c.id}">Détails</button><button class="btn sm" data-a="gv" data-id="${c.id}">Donner à un client</button><button class="btn sm alt" data-a="ex" data-id="${c.id}">Expiration</button><button class="btn sm red" data-a="dl" data-id="${c.id}">Supprimer</button></td></tr>`).join('') || '<tr><td colspan="6" class="mute">Aucune configuration enregistrée.</td></tr>'}</table>`);

  const run = async (text, binary, filename) => { $('#ares').innerHTML = `<div style="text-align:center;padding:14px">${globe}<p class="mute">Analyse en cours…</p></div>`;
    try { analysis = await api('/ac/analyze', 'POST', { text, binary, filename }); const exp = analysis.expire ? new Date(analysis.expire).toISOString().slice(0, 10) : '';
      $('#ares').innerHTML = `<p style="margin-top:14px"><span class="tag w">${esc(analysis.format)}</span> ${analysis.items.length} configuration(s) lue(s) ${analysis.items.some(i => i.supported) ? '' : '<span class="tag r">aucune utilisable</span>'}</p>
        ${analysis.notes.map(n => `<p class="err">${esc(n)}</p>`).join('')}${analysis.items.length ? `<table>${head}${analysis.items.map(detail).join('')}</table>
        <div class="row" style="margin-top:10px"><button class="btn alt sm" id="tst">Vérifier les transports (test de connexion)</button><span id="tres" class="mute"></span></div>
        <div class="row"><input id="nm" placeholder="Nom de la configuration"><label class="mute">Expiration <input id="ex" type="date" value="${exp}" style="width:auto"></label>${analysis.expire ? '<span class="mute">détectée dans le fichier</span>' : '<span class="mute">non détectée : renseignez-la si vous la connaissez</span>'}</div>
        <button class="btn" id="sv" ${analysis.items.some(i => i.supported) ? '' : 'disabled'}>Enregistrer la configuration</button>` : ''}`;
      if ($('#tst')) $('#tst').onclick = async () => { $('#tres').textContent = 'Test en cours…'; const out = []; for (const it of analysis.items.filter(i => i.supported).slice(0, 10)) { const t = await api('/ac/test', 'POST', { item: it }); out.push(`${it.host}:${it.port} → ${t.ok ? 'OK (' + t.ms + ' ms, ' + t.note + ')' : 'échec (' + t.note + ')'}`); } $('#tres').innerHTML = out.map(esc).join('<br>'); };
      if ($('#sv')) $('#sv').onclick = async () => { const name = $('#nm').value.trim(); if (!name) return toast('Donnez un nom à cette configuration.');
        try { await api('/ac/configs', 'POST', { name, items: analysis.items, source: analysis.format, expire: $('#ex').value ? Date.parse($('#ex').value + 'T23:59:59') : 0 }); views.netconf(); } catch (e) { toast(e.message); } };
    } catch (e) { $('#ares').innerHTML = `<p class="err">${esc(e.message)}</p>`; } };
  $('#an').onclick = () => { const v = $('#txt').value.trim(); if (v) run(v, false, ''); else toast('Collez une configuration ou importez un fichier.'); };
  $('#file').onchange = async e => { const f = e.target.files[0]; if (!f) return; const buf = new Uint8Array(await f.arrayBuffer());
    const bin = buf.slice(0, 2000).some(b => b === 0); if (bin) return run('', true, f.name); const txt = new TextDecoder().decode(buf); $('#txt').value = txt.length < 6000 ? txt : txt.slice(0, 6000) + '…'; run(txt, false, f.name); };
  $('#view').onclick = async e => { const b = e.target.closest('button[data-a]'); if (!b) return; const id = b.dataset.id, a = b.dataset.a, cf = r.configs.find(c => c.id == id);
    try { if (a === 'dt') modal(`<h3>${esc(cf.name)}</h3><table>${head}${cf.items.map(detail).join('')}</table><p>${dtag(cf.expire_at)}</p><button class="btn alt" onclick="closeModal()">Fermer</button>`);
      if (a === 'ex') { const v = ask('Date d\'expiration (AAAA-MM-JJ), vide pour effacer :', cf.expire_at ? new Date(cf.expire_at).toISOString().slice(0, 10) : ''); if (v === null) return; await api('/ac/configs/' + id, 'PUT', { expire: v ? Date.parse(v + 'T23:59:59') : 0 }); views.netconf(); }
      if (a === 'dl') { if (!confirm('Supprimer cette configuration ? Les clients qui l\'utilisent seront déconnectés.')) return; await api('/ac/configs/' + id, 'DELETE'); views.netconf(); }
      if (a === 'gv') giveModal(cf); } catch (x) { toast(x.message); } };
};

async function giveModal(cf) { const cl = await api('/ac/clients'); let tab = 0;
  const draw = () => { modal(`<h3>Donner « ${esc(cf.name)} » à un client</h3><div class="row"><button class="btn sm ${tab === 0 ? '' : 'alt'}" id="t0">Client existant</button><button class="btn sm ${tab === 1 ? '' : 'alt'}" id="t1">Nouveau client</button></div>
    ${tab === 0 ? `<input id="q" placeholder="Rechercher par nom, pays, e-mail ou token"><div id="lst" style="max-height:300px;overflow:auto"></div>` :
      `<input id="nn" placeholder="Nom complet"><input id="nc" placeholder="Pays"><input id="ne" placeholder="E-mail"><input id="np" placeholder="Numéro"><button class="btn" id="mk">Créer et donner la configuration</button><div id="mres"></div>`}
    <button class="btn alt" style="margin-top:12px" onclick="closeModal()">Fermer</button>`);
    $('#t0').onclick = () => { tab = 0; draw(); }; $('#t1').onclick = () => { tab = 1; draw(); };
    if (tab === 0) { const list = () => { const q = $('#q').value.toLowerCase(); $('#lst').innerHTML = `<table>${cl.clients.filter(c => (c.name + c.country + c.email + c.token).toLowerCase().includes(q)).map(c => `<tr><td>${esc(c.name)}<br><span class="mute">${esc(c.country)} · ${esc(c.email)}</span></td><td>${c.config_id == cf.id ? '<span class="tag w">Déjà donnée</span>' : `<button class="btn sm" data-c="${c.id}">Donner</button>`}</td></tr>`).join('')}</table>`; };
      $('#q').oninput = list; list(); $('#lst').onclick = async e => { const b = e.target.closest('[data-c]'); if (!b) return; await api(`/ac/clients/${b.dataset.c}/assign`, 'POST', { config: cf.id }); closeModal(); views.netconf(); }; }
    else $('#mk').onclick = async () => { try { const r = await api('/ac/clients', 'POST', { name: $('#nn').value, country: $('#nc').value, email: $('#ne').value, phone: $('#np').value }); await api(`/ac/clients/${r.id}/assign`, 'POST', { config: cf.id });
      $('#mres').innerHTML = `<p class="mute">Client créé. Token à lui donner :</p><div class="card" style="font-family:monospace;font-size:18px">${esc(r.token)}</div><button class="btn" onclick="copyText('${esc(r.token)}')">Copier</button>`; } catch (e) { toast(e.message); } }; };
  draw(); }
