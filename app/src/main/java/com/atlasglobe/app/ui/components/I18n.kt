package com.atlasglobe.app.ui.components

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Langue de l'interface : français (par défaut) ou kiswahili. Les textes non traduits restent en français. */
object Lang {
    var code by mutableStateOf("fr"); private set
    fun init(c: Context) { code = c.applicationContext.getSharedPreferences("atlas_lang", Context.MODE_PRIVATE).getString("code", "fr") ?: "fr" }
    fun set(c: Context, v: String) { code = v; c.applicationContext.getSharedPreferences("atlas_lang", Context.MODE_PRIVATE).edit().putString("code", v).apply() }

    fun tr(s: String): String {
        if (code != "sw") return s
        SW[s]?.let { return it }
        Regex("^il y a (\\d+) min$").find(s)?.let { return "dakika ${it.groupValues[1]} zilizopita" }
        Regex("^il y a (\\d+) h$").find(s)?.let { return "saa ${it.groupValues[1]} zilizopita" }
        Regex("^il y a (\\d+) j$").find(s)?.let { return "siku ${it.groupValues[1]} zilizopita" }
        return s
    }

    private val SW = mapOf(
        "Accueil" to "Nyumbani", "Recherche" to "Tafuta", "Messages" to "Ujumbe", "Paramètres" to "Mipangilio",
        "Bienvenue dans" to "Karibu kwenye", "Nom" to "Jina la ukoo", "Prénom" to "Jina la kwanza", "Code PIN (4 à 6 chiffres)" to "Nambari ya siri PIN (tarakimu 4 hadi 6)",
        "Numéro de téléphone (facultatif)" to "Namba ya simu (si lazima)", "Continuer" to "Endelea", "Activer" to "Washa", "Ouvrir les réglages" to "Fungua mipangilio",
        "Votre continent" to "Bara lako", "Votre pays" to "Nchi yako", "Votre province" to "Mkoa wako", "Votre ville" to "Mji wako", "Rechercher un pays" to "Tafuta nchi",
        "Autre ville…" to "Mji mwingine…", "Votre photo de profil" to "Picha yako ya wasifu", "Touchez pour choisir" to "Gusa ili kuchagua", "Se connecter sans photo" to "Endelea bila picha",
        "Se connecter" to "Unganisha", "Annuler" to "Ghairi", "Supprimer" to "Futa", "Enregistrer" to "Hifadhi", "Fermer" to "Funga", "Valider" to "Thibitisha", "Envoyer" to "Tuma",
        "Répondre" to "Jibu", "Partager" to "Shiriki", "Republier" to "Chapisha tena", "Télécharger" to "Pakua", "Publier" to "Chapisha", "Publication" to "Chapisho", "Vidéo" to "Video",
        "Story" to "Hadithi", "Texte" to "Maandishi", "Suivre" to "Fuata", "Abonné" to "Unamfuata", "Notifications" to "Arifa", "Aucune notification" to "Hakuna arifa",
        "Votre story" to "Hadithi yako", "Enregistrer" to "Hifadhi", "Nouvelle publication" to "Chapisho jipya", "Nouvelle story" to "Hadithi mpya", "Nouveau texte" to "Maandishi mapya",
        "Choisir une photo" to "Chagua picha", "Légende (facultatif)" to "Maelezo (si lazima)", "Écrivez votre message" to "Andika ujumbe wako",
        "Pas de connexion. Vous voyez les publications déjà enregistrées. Les vidéos et les nouvelles publications demandent des mégas." to "Hakuna mtandao. Unaona machapisho yaliyohifadhiwa tayari. Video na machapisho mapya yanahitaji data.",
        "Aucune discussion" to "Hakuna mazungumzo", "Ajoutez un contact Atlas ou créez un groupe pour commencer." to "Ongeza mtu wa Atlas au tengeneza kikundi ili kuanza.",
        "Contacts Atlas" to "Anwani za Atlas", "Contacts" to "Anwani", "Groupe" to "Kikundi", "Message" to "Ujumbe", "Réseau mesh en pause" to "Mtandao wa mesh umesimama",
        "Ajouter un contact" to "Ongeza anwani", "Inviter" to "Alika", "Message supprimé" to "Ujumbe umefutwa", "Photo" to "Picha", "Note vocale" to "Ujumbe wa sauti",
        "Supprimer pour moi" to "Futa kwangu", "Supprimer pour tout le monde" to "Futa kwa kila mtu", "Supprimer le message ?" to "Kufuta ujumbe?", "Bloquer" to "Zuia",
        "Débloquer" to "Ondoa kizuizi", "Signaler" to "Ripoti", "Profil" to "Wasifu", "Infos du groupe" to "Taarifa za kikundi", "Pause" to "Simamisha", "Reprendre" to "Endelea",
        "Vous avez bloqué ce contact" to "Umemzuia mtu huyu", "Vous n'êtes plus dans ce groupe" to "Hauko tena kwenye kikundi hiki",
        "Seuls les administrateurs peuvent écrire dans ce groupe" to "Wasimamizi pekee wanaweza kuandika kwenye kikundi hiki",
        "Appel en cours…" to "Inapiga simu…", "Appel entrant" to "Simu inayoingia", "Refuser" to "Kataa", "Raccrocher" to "Kata simu", "Micro" to "Maikrofoni",
        "Micro coupé" to "Maikrofoni imezimwa", "Haut-parleur" to "Spika", "Connexion…" to "Inaunganisha…", "Appel refusé" to "Simu imekataliwa", "Pas de réponse" to "Hakuna jibu",
        "Connexion perdue" to "Muunganisho umepotea", "Ligne occupée" to "Laini ina shughuli", "Ce contact n'est pas joignable" to "Mtu huyu hapatikani",
        "Vous devez être connecté à Internet pour appeler" to "Lazima uwe na intaneti ili upige simu",
        "Nouveau groupe" to "Kikundi kipya", "Nom du groupe" to "Jina la kikundi", "Créer le groupe" to "Tengeneza kikundi", "Membres" to "Wanachama", "Quitter le groupe" to "Ondoka kwenye kikundi",
        "Ajouter des membres" to "Ongeza wanachama", "Conversation verrouillée" to "Mazungumzo yamefungwa", "Seuls les administrateurs peuvent écrire" to "Wasimamizi pekee wanaweza kuandika",
        "Nommer administrateur" to "Mteue msimamizi", "Suspendre 5 minutes" to "Simamisha dakika 5", "Suspendre 30 minutes" to "Simamisha dakika 30", "Suspendre 1 heure" to "Simamisha saa 1",
        "Suspendre 24 heures" to "Simamisha saa 24", "Retirer du groupe" to "Ondoa kwenye kikundi", "Bannir du groupe" to "Piga marufuku kwenye kikundi", "Admin" to "Msimamizi", "Créateur" to "Muumbaji",
        "Vérifier la disponibilité Internet" to "Angalia upatikanaji wa intaneti", "Trouvez une connexion Internet près de vous" to "Tafuta muunganisho wa intaneti karibu nawe",
        "Connexions disponibles" to "Miunganisho inayopatikana", "Relancer la recherche" to "Tafuta tena", "Aucun revendeur Atlas trouvé près de vous" to "Hakuna muuzaji wa Atlas karibu nawe",
        "Équipe complète : plus de place" to "Timu imejaa: hakuna nafasi", "Mes achats" to "Manunuzi yangu", "Temps" to "Muda", "Forfait" to "Kifurushi", "Paiement approuvé" to "Malipo yamekubaliwa",
        "Temps restant" to "Muda uliobaki", "Félicitations, vous êtes connecté" to "Hongera, umeunganishwa", "Recherche des connexions Atlas autour de vous" to "Tunatafuta miunganisho ya Atlas karibu nawe",
        "Restez à l'endroit où vous êtes" to "Baki mahali ulipo", "Revendeur officiel" to "Muuzaji rasmi",
        "Collez le token que vous a remis Atlas" to "Bandika tokeni ulizopewa na Atlas", "Vérification du token…" to "Tunathibitisha tokeni…",
        "Félicitations, vous êtes connecté dans l'espace Atlas Connect" to "Hongera, umeingia kwenye Atlas Connect", "Token invalide" to "Tokeni si sahihi",
        "Déconnecté" to "Haujaunganishwa", "Connecté" to "Umeunganishwa", "Tarif" to "Bei", "Recharger mon solde" to "Ongeza salio", "Aucun abonnement actif" to "Hakuna usajili unaotumika",
        "Certification du compte" to "Uthibitisho wa akaunti", "Sécurité" to "Usalama", "Confidentialité" to "Faragha", "Discussions" to "Mazungumzo", "Batterie" to "Betri", "Stockage" to "Hifadhi",
        "Contacts bloqués" to "Anwani zilizozuiwa", "Inviter des amis" to "Alika marafiki", "Aide" to "Msaada", "À propos" to "Kuhusu", "Verrouiller maintenant" to "Funga sasa",
        "COMPTE" to "AKAUNTI", "APPLICATION" to "PROGRAMU", "CONNECTIVITÉ" to "MUUNGANISHO", "PLUS" to "ZAIDI", "Espace Revendeur Wi-Fi" to "Eneo la Muuzaji wa Wi-Fi",
        "Mes achats Wi-Fi" to "Manunuzi yangu ya Wi-Fi", "LANGUE" to "LUGHA", "FOND DE LA DISCUSSION" to "MANDHARI YA MAZUNGUMZO", "Utiliser ma photo" to "Tumia picha yangu",
        "Français" to "Kifaransa", "Présence en ligne" to "Hali ya mtandaoni", "Afficher mon numéro" to "Onyesha namba yangu", "Afficher mon e-mail" to "Onyesha barua pepe yangu",
        "Confirmations de lecture" to "Uthibitisho wa kusoma", "Autoriser les appels" to "Ruhusu simu", "Verrouiller Atlas avec le PIN" to "Funga Atlas kwa PIN", "Empreinte digitale" to "Alama ya kidole",
        "Changer le code PIN" to "Badilisha PIN", "Autorisations de l'application" to "Ruhusa za programu", "Entrez votre PIN" to "Weka PIN yako", "PIN incorrect" to "PIN si sahihi",
        "Compte suspendu" to "Akaunti imesimamishwa", "ANNONCE OFFICIELLE" to "TANGAZO RASMI", "Rejoindre le groupe WhatsApp" to "Jiunge na kikundi cha WhatsApp", "Rejoindre sur Telegram" to "Jiunge kwenye Telegram",
        "Appuyez sur Retour pour fermer" to "Bonyeza Rudi ili kufunga", "Qui sommes-nous ?" to "Sisi ni nani?", "Pourquoi Atlas Globe existe" to "Kwa nini Atlas Globe ipo",
        "Comment ça marche" to "Inavyofanya kazi", "Pourquoi nous choisir" to "Kwa nini utuchague", "Être Leader de quartier" to "Kuwa Kiongozi wa mtaa", "Être Revendeur Wi-Fi" to "Kuwa Muuzaji wa Wi-Fi",
        "Le créateur" to "Muumbaji", "Informations" to "Taarifa", "Version" to "Toleo", "Aide" to "Msaada", "Activer" to "Washa", "Gardez Atlas actif" to "Weka Atlas ikiwa hai",
        "à l'instant" to "sasa hivi", "Ouvrir ma connexion" to "Fungua muunganisho wangu", "Fermer ma connexion" to "Funga muunganisho wangu", "Revendeur Wi-Fi" to "Muuzaji wa Wi-Fi",
        "Clients connectés" to "Wateja walioungana", "Aucun client pour le moment." to "Hakuna mteja kwa sasa.", "Batterie" to "Betri", "Température" to "Joto", "Vitesse" to "Kasi",
        "Mon QR" to "QR yangu", "Scanner un QR" to "Changanua QR"
    )
}

@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) = androidx.compose.material3.Text(Lang.tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight,
    overflow, softWrap, maxLines, minLines, onTextLayout, style)
