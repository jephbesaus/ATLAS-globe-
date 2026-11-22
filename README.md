# Atlas Globe — projet complet

## Contenu
- `app/` : application Android (Kotlin, Jetpack Compose). Ouvrir le dossier racine dans **Android Studio** (Koala ou plus récent, JDK 17). Min SDK 26.
- `server/` : serveur Node.js, panel admin, relais des appels. Installation sur votre VPS : `server/DEPLOY.md`.

## Avant de compiler
1. Installez le serveur (`server/DEPLOY.md`) : il affiche la **clé publique Atlas**.
2. `connect/AtlasConfig.kt` : collez la clé dans `ASSISTANT_PUB` et `ADMIN_PUB`.
3. `data/Api.kt` : `SERVER_URL = "https://votre-domaine"`.
4. Android Studio : Sync Gradle, puis Build > Build APK.

## Fonctions
Messagerie mesh chiffrée, groupes (admins, verrouillage, suspension, bannissement), appels vocaux (Internet), accueil (publications, stories, likes synchronisés),
marché Wi-Fi (revendeurs approuvés, jetons signés, VPN local), Atlas Connect (VPN par token, configuration donnée depuis le panel), QR de contact,
certification avec vérification d'identité, annonces, kiswahili, panel admin complet.

## Atlas Connect : formats pris en charge
Liens et JSON des protocoles **VLESS, VMess (AEAD), Trojan, Shadowsocks (AEAD)**, transports **TCP et WebSocket**, avec ou sans TLS, URL d'abonnement.
Non pris en charge (le panel le signale clairement) : Reality/XTLS, gRPC, Hysteria, TUIC, WireGuard, OpenVPN, et les fichiers chiffrés d'autres applications (.hc, .npv4, .dark…).

## À tester (3 téléphones Android + 1 VPS)
1. Inscription, accueil, messagerie mesh (le 3e téléphone au milieu = relais), groupes.
2. Appel vocal entre deux téléphones avec Internet.
3. Panel admin : créer un client Atlas Connect, coller une configuration, la donner au client, tester le bouton power.
4. Wi-Fi : revendeur approuvé + client. **Mesurez la vitesse en premier.**

## Pas encore fait
Transfert rapide de gros fichiers entre téléphones (Wi-Fi Direct / Nearby), appels vidéo, traduction du texte de la page À propos en kiswahili.

## Sans ordinateur : compiler avec GitHub
Poussez le dossier sur GitHub depuis Termux ; le fichier `.github/workflows/android.yml` construit l'APK tout seul
(onglet Actions > dernière exécution > Artifacts > AtlasGlobe-APK). En cas d'erreur, l'onglet Summary affiche les erreurs à copier.
