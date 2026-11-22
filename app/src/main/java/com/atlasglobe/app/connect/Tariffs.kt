package com.atlasglobe.app.connect

/** kind : "time" (durée) ou "data" (volume). Les prix sont en francs congolais et seront modifiables depuis le panel admin. */
data class Plan(val id: String, val kind: String, val label: String, val priceFc: Int,
                val minutes: Int = 0, val megabytes: Int = 0, val note: String)

object Tariffs {
    private const val OVER = "Au-delà de votre limite, la connexion peut ralentir ou se couper un moment."
    val time = listOf(
        Plan("t30", "time", "30 minutes", 200, minutes = 30, note = "YouTube, TikTok, Instagram, Facebook. Vous ne serez pas coupé avant 30 minutes. $OVER"),
        Plan("t120", "time", "2 heures", 500, minutes = 120, note = "Vidéos, discussions et réseaux sociaux. Limite de téléchargement : 1 Go. $OVER"),
        Plan("t480", "time", "8 heures", 1000, minutes = 480, note = "Une demi-journée de travail ou d'études. Évitez les gros téléchargements."),
        Plan("t840", "time", "14 heures", 1500, minutes = 840, note = "Du matin au soir, pour discuter, étudier et regarder des vidéos."),
        Plan("t1440", "time", "24 heures", 3000, minutes = 1440, note = "Une journée complète. Restez à portée de votre revendeur."),
        Plan("t2880", "time", "48 heures", 4000, minutes = 2880, note = "Deux jours. Pensez à renouveler avant la fin pour ne pas être coupé."),
        Plan("t7200", "time", "5 jours", 10000, minutes = 7200, note = "Réservé aux voisins proches : le téléphone du revendeur doit rester allumé et à portée."),
        Plan("t10080", "time", "7 jours", 11000, minutes = 10080, note = "Réservé aux voisins proches : le téléphone du revendeur doit rester allumé et à portée."),
        Plan("t28800", "time", "20 jours", 20000, minutes = 28800, note = "Pour la maison ou la boutique voisine du revendeur. Une consommation élevée peut ralentir la connexion."),
        Plan("t43200", "time", "30 jours", 25000, minutes = 43200, note = "Un mois complet pour un voisin proche. Une consommation élevée peut ralentir la connexion.")
    )
    val data = listOf(
        Plan("d500", "data", "500 Mo", 500, megabytes = 500, note = "Messages et réseaux légers."),
        Plan("d1000", "data", "1 Go", 1000, megabytes = 1000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d2000", "data", "2 Go", 1500, megabytes = 2000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d3500", "data", "3,5 Go", 2000, megabytes = 3500, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d4000", "data", "4 Go", 2500, megabytes = 4000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d8000", "data", "8 Go", 5000, megabytes = 8000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d10000", "data", "10 Go", 8000, megabytes = 10000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d15000", "data", "15 Go", 12000, megabytes = 15000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d20000", "data", "20 Go", 18000, megabytes = 20000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d30000", "data", "30 Go", 20000, megabytes = 30000, note = "Plus vous consommez, plus le forfait se termine vite."),
        Plan("d50000", "data", "50 Go", 30000, megabytes = 50000, note = "Plus vous consommez, plus le forfait se termine vite.")
    )
    fun find(id: String) = (time + data).firstOrNull { it.id == id }
    fun fc(n: Int) = "%,d FC".format(n).replace(',', ' ')
}
