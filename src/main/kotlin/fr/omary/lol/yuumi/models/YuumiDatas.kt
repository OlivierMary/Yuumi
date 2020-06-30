package fr.omary.lol.yuumi.models

data class Champion(val id: Int, val name: String, val key: String)
data class LoLMap(val id: Int, val name: String)
enum class YPosition(val lolkey: String, val title: String, val keyUgg: Int, val iconName: String) {
    JUNGLE("jungle", "Jungle", 1, "Position_Plat-Jungle.png"),
    UTILITY("utility", "Utility", 2, "Position_Plat-Support.png"),
    BOTTOM("bottom", "Bottom", 3, "Position_Plat-Bot.png"),
    TOP("top", "Top", 4, "Position_Plat-Top.png"),
    MIDDLE("middle", "Middle", 5, "Position_Plat-Mid.png"),
    FILL("FILL", "Fill", -1, "Emblem_Platinum.png"),
    ARAM("ARAM", "ARAM", 6, "Emblem_Platinum.png")
}

enum class Zone(val keyUgg: Int, val title: String) {
    ALL(12, "All"),
    NA(1, "North America"),
    EUW(2, "Europe West"),
    KR(3, "Korea"),
    BR(5, "Brazil"),
    EUN(4, "Europe Nordic & East"),
    JP(11, "Japan"),
    LAN(6, "Latin America North"),
    LAS(7, "Latin America South"),
    OCE(8, "Oceania"),
    RU(9, "Russia"),
    TR(10, "Turkey")

}

enum class Rank(val keyUgg: Int, val title: String) {
    PLATINE_PLUS(10, "Platinum+"),
    DIAMANT_PLUS(11, "Diamond+"),
    MAITRE_PLUS(14, "Master+"),
    ALL(8, "All"),
    CHALLENGER(1, "Challenger"),
    GRAND_MAITRE(13, "Grand Master"),
    MAITRE(2, "Master"),
    DIAMANT(3, "Diamond"),
    PLATINE(4, "Platinum"),
    OR(5, "Gold"),
    ARGENT(6, "Silver"),
    BRONZE(7, "Bronze"),
    FER(12, "Iron")
}

data class ChampionPositionData(
    val champion: Champion,
    val position: YPosition,
    val rank: Rank,
    val zone: Zone,
    val rune: Rune,
    val summonerSpells: List<Int>?,
    val startItems: List<Int>?,
    val mainsItems: List<Int>?,
    val fourthItems: List<Int>?,
    val fithItems: List<Int>?,
    val sixthItems: List<Int>?,
    val skills: Skills,
    val totalGames: Int?
)

data class Rune(val masteryA: Int?, val masteryB: Int?, val additionnalMastery: List<Int>?)
data class Skills(val priority: String?, val fullOrder: List<String>?)
