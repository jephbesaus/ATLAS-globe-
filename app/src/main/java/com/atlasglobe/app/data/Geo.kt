package com.atlasglobe.app.data

/** Listes intégrées : fonctionnent sans Internet. */
object Geo {
    private fun l(s: String) = s.split(",").map { it.trim() }

    val continents = listOf("Afrique", "Amérique", "Asie", "Europe", "Océanie")

    val countries: Map<String, List<String>> = mapOf(
        "Afrique" to l("Afrique du Sud,Algérie,Angola,Bénin,Botswana,Burkina Faso,Burundi,Cameroun,Cap-Vert,Centrafrique,Comores,Congo (Brazzaville),Côte d'Ivoire,Djibouti,Égypte,Érythrée,Eswatini,Éthiopie,Gabon,Gambie,Ghana,Guinée,Guinée-Bissau,Guinée équatoriale,Kenya,Lesotho,Libéria,Libye,Madagascar,Malawi,Mali,Maroc,Maurice,Mauritanie,Mozambique,Namibie,Niger,Nigéria,Ouganda,RD Congo,Rwanda,Sao Tomé-et-Principe,Sénégal,Seychelles,Sierra Leone,Somalie,Soudan,Soudan du Sud,Tanzanie,Tchad,Togo,Tunisie,Zambie,Zimbabwe"),
        "Amérique" to l("Antigua-et-Barbuda,Argentine,Bahamas,Barbade,Belize,Bolivie,Brésil,Canada,Chili,Colombie,Costa Rica,Cuba,Dominique,Équateur,États-Unis,Grenade,Guatemala,Guyana,Haïti,Honduras,Jamaïque,Mexique,Nicaragua,Panama,Paraguay,Pérou,République dominicaine,Saint-Christophe-et-Niévès,Sainte-Lucie,Saint-Vincent-et-les-Grenadines,Salvador,Suriname,Trinité-et-Tobago,Uruguay,Venezuela"),
        "Asie" to l("Afghanistan,Arabie saoudite,Arménie,Azerbaïdjan,Bahreïn,Bangladesh,Bhoutan,Birmanie,Brunei,Cambodge,Chine,Corée du Nord,Corée du Sud,Émirats arabes unis,Géorgie,Inde,Indonésie,Irak,Iran,Israël,Japon,Jordanie,Kazakhstan,Kirghizistan,Koweït,Laos,Liban,Malaisie,Maldives,Mongolie,Népal,Oman,Ouzbékistan,Pakistan,Palestine,Philippines,Qatar,Singapour,Sri Lanka,Syrie,Tadjikistan,Thaïlande,Timor oriental,Turkménistan,Turquie,Viêt Nam,Yémen"),
        "Europe" to l("Albanie,Allemagne,Andorre,Autriche,Belgique,Biélorussie,Bosnie-Herzégovine,Bulgarie,Chypre,Croatie,Danemark,Espagne,Estonie,Finlande,France,Grèce,Hongrie,Irlande,Islande,Italie,Kosovo,Lettonie,Liechtenstein,Lituanie,Luxembourg,Macédoine du Nord,Malte,Moldavie,Monaco,Monténégro,Norvège,Pays-Bas,Pologne,Portugal,Roumanie,Royaume-Uni,Russie,Saint-Marin,Serbie,Slovaquie,Slovénie,Suède,Suisse,Tchéquie,Ukraine,Vatican"),
        "Océanie" to l("Australie,Fidji,Îles Marshall,Îles Salomon,Kiribati,Micronésie,Nauru,Nouvelle-Zélande,Palaos,Papouasie-Nouvelle-Guinée,Samoa,Tonga,Tuvalu,Vanuatu")
    )

    const val DRC = "RD Congo"

    /** Provinces -> villes principales (RDC). "Autre ville" permet d'écrire librement. */
    val drc: Map<String, List<String>> = linkedMapOf(
        "Bas-Uele" to l("Buta,Aketi,Bondo"),
        "Équateur" to l("Mbandaka,Bikoro,Basankusu"),
        "Haut-Katanga" to l("Lubumbashi,Likasi,Kipushi,Kasumbalesa,Sakania"),
        "Haut-Lomami" to l("Kamina,Kaniama,Malemba-Nkulu"),
        "Haut-Uele" to l("Isiro,Watsa,Dungu,Faradje"),
        "Ituri" to l("Bunia,Mahagi,Aru,Mambasa"),
        "Kasaï" to l("Tshikapa,Ilebo,Luebo"),
        "Kasaï-Central" to l("Kananga,Demba,Dibaya"),
        "Kasaï-Oriental" to l("Mbuji-Mayi,Miabi,Kabeya-Kamwanga"),
        "Kinshasa" to l("Gombe,Limete,Lemba,Ngaliema,Masina,Kimbanseke,Ndjili,Matete,Bandalungwa,Kalamu,Kintambo,Lingwala,Barumbu,Kinshasa (autre commune)"),
        "Kongo-Central" to l("Matadi,Boma,Mbanza-Ngungu,Kisantu,Moanda"),
        "Kwango" to l("Kenge,Popokabaka,Kasongo-Lunda"),
        "Kwilu" to l("Kikwit,Bandundu,Bulungu,Idiofa"),
        "Lomami" to l("Kabinda,Mwene-Ditu,Lubao"),
        "Lualaba" to l("Kolwezi,Dilolo,Mutshatsha"),
        "Mai-Ndombe" to l("Inongo,Nioki,Kiri"),
        "Maniema" to l("Kindu,Kasongo,Kailo,Kibombo"),
        "Mongala" to l("Lisala,Bumba,Bongandanga"),
        "Nord-Kivu" to l("Goma,Butembo,Beni,Rutshuru,Masisi,Walikale"),
        "Nord-Ubangi" to l("Gbadolite,Mobayi-Mbongo,Businga"),
        "Sankuru" to l("Lodja,Lusambo,Kole"),
        "Sud-Kivu" to l("Bukavu,Uvira,Kamituga,Baraka,Fizi,Kalehe,Walungu,Mwenga,Shabunda,Kabare,Idjwi"),
        "Sud-Ubangi" to l("Gemena,Libenge,Zongo"),
        "Tanganyika" to l("Kalemie,Kongolo,Manono,Moba"),
        "Tshopo" to l("Kisangani,Yangambi,Isangi,Opala"),
        "Tshuapa" to l("Boende,Ikela,Befale")
    )
}
