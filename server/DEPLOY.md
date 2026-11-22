# Installer le serveur sur votre VPS (Ubuntu, France)

```bash
# 1. Node.js 20
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs unzip

# 2. Le projet
unzip AtlasGlobe.zip && cd AtlasGlobe/server
npm install
cp .env.example .env && nano .env      # ADMIN_PASSWORD, JWT_SECRET (long), BREVO_API_KEY, MAIL_FROM

# 3. Lancement permanent
sudo tee /etc/systemd/system/atlas.service >/dev/null <<'UNIT'
[Unit]
Description=Atlas Globe serveur
After=network.target
[Service]
WorkingDirectory=/home/VOTRE_UTILISATEUR/AtlasGlobe/server
ExecStart=/usr/bin/node src/index.js
Restart=always
User=VOTRE_UTILISATEUR
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl enable --now atlas
journalctl -u atlas -n 30        # affiche la CLÉ PUBLIQUE Atlas à copier dans l'application

# 4. HTTPS automatique avec Caddy (votre domaine doit pointer vers l'IPv4 du VPS)
sudo apt-get install -y caddy
echo 'api.votredomaine.com { reverse_proxy localhost:8080 }' | sudo tee /etc/caddy/Caddyfile
sudo systemctl restart caddy

# 5. Pare-feu : HTTPS et relais des appels
sudo ufw allow 80,443/tcp && sudo ufw allow 40000/udp
```

- Panel admin : `https://api.votredomaine.com/admin/`
- Dans l'application : `Api.kt` -> `SERVER_URL = "https://api.votredomaine.com"` ; `AtlasConfig.kt` -> collez la clé publique dans `ASSISTANT_PUB` et `ADMIN_PUB`.
- Sauvegardez `server/data/` (base de données et clé Atlas). Perdre `atlas_key.json` invalide tous les accès Wi-Fi émis.
