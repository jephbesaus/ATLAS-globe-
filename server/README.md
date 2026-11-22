# Serveur et panel admin Atlas Globe

1. Installez Node.js 18 ou plus récent.
2. `cd server && npm install`
3. Copiez `.env.example` en `.env` et remplissez : `ADMIN_PASSWORD`, `JWT_SECRET` (long et aléatoire), `BREVO_API_KEY` (e-mails).
4. `npm start`. Au premier démarrage, le serveur crée sa clé Atlas (`data/atlas_key.json`, à sauvegarder et à ne jamais publier)
   et affiche la **clé publique** : copiez-la dans `AtlasConfig.kt` (`ASSISTANT_PUB` et `ADMIN_PUB`), puis `SERVER_URL` dans `Api.kt`.
5. Mettez le serveur derrière **HTTPS** (Nginx ou Caddy). Panel : `https://votre-domaine/admin/`.

Connexion au panel : e-mail `jephbesaus07@gmail.com` + mot de passe + code à 6 chiffres envoyé par e-mail. Aucun autre compte n'est accepté.
