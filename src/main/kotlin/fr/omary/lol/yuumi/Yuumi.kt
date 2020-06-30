package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import fr.omary.lol.yuumi.models.*
import generated.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalDateTime.now

private var lastPick: Pair<Champion, LocalDateTime> = Champion(-1, "Nobody", "Nobody") to now().minusDays(1)
private var championsDatas: MutableList<ChampionPositionData> = mutableListOf()

private lateinit var lolItemSetsItemSets: LolItemSetsItemSets
private lateinit var summoner: LolSummonerSummoner
private const val TOKEN = "GENERATED"
private const val ARAM_GAME_MODE = "ARAM"

suspend fun initStaticVariables() {
    summoner = waitToGetSummoner()!!
}

private suspend fun waitToGetSummoner(): LolSummonerSummoner? {
    var tempSummoner: LolSummonerSummoner?
    do {
        tempSummoner = getCurrentSummoner().await()
        delay(1000)
    } while (tempSummoner == null)
    return tempSummoner
}

private suspend fun refreshAssignerPosition(champ: Champion, gameMode: String?): YPosition {
    val me = getTeam().await()?.first { x -> x.summonerId == summoner.summonerId }
    val assignedPosition = me?.assignedPosition
    if (assignedPosition == null || assignedPosition.isBlank()) {
        return if (gameMode == ARAM_GAME_MODE) {
            YPosition.ARAM
        } else {
            championsDatas.filter { it.champion.id == champ.id && it.rank == Rank.ALL && it.zone == Zone.ALL }
                .sortedByDescending { it.totalGames }.map { it.position }.first()
        }
    }
    return YPosition.values().first { p -> p.lolkey == assignedPosition }
}

suspend fun validateChampion(champ: Champion) {
    if (lastPick.first == champ && now().isBefore(lastPick.second.plusSeconds(30))) {
        return
    }
    lastPick = champ to now()
    startLoading("Send [${champ.name}]")
    sendChampionPostion(champ, null, getCurrentGameMode().await())
    ready()
}

suspend fun sendChampionPostion(
    champ: Champion,
    position: YPosition?,
    gameMode: String?
) {
    val finalPosition = if (position == null || position == YPosition.FILL) {
        refreshAssignerPosition(champ, gameMode)
    } else {
        position
    }
    val finalRank = if (finalPosition == YPosition.ARAM) {
        Rank.ALL
    } else {
        currentRank
    }
    val champDatas =
        championsDatas.firstOrNull {
            it.champion.id == champ.id &&
                    it.zone == currentZone &&
                    it.rank == finalRank &&
                    (it.position == finalPosition)
        }
    if (champDatas == null) {
        sendSystemNotification(
            "Champ ${champ.name} - ${finalPosition.title} - Not Enough Datas for Zone ${currentZone.title} / Rank ${currentRank.title}",
            "WARNING"
        )
        return
    }
    setPerks(champDatas)
    setItemsSets(champDatas)
    setSummonerSpells(champDatas)
    if (position == null || position == finalPosition) {
        sendSystemNotification(
            "Champ set ${champ.name} - ${finalPosition.title} : Sent",
            "INFO"
        )
    } else {
        sendSystemNotification(
            "Champ set ${champ.name} - ${position.title} -> ${finalPosition.title} : Sent",
            "INFO"
        )
    }
}

private suspend fun setPerks(champ: ChampionPositionData) {
    resetGeneratedExistingPerks()
    commitPerks(champ)
}


private suspend fun resetGeneratedExistingPerks() =
    getPages().await()?.filter { p -> p.name.endsWith(TOKEN) }?.forEach { deletePage(it).await() }


private suspend fun commitPerks(champ: ChampionPositionData) {
    if (!sendPage(generatePerk(champ)).await()) {
        run {
            sendSystemNotification(
                "Champ set ${champ.champion.name} : Not Enough runes pages",
                "WARNING"
            )
        }
    }
}

private suspend fun setItemsSets(champ: ChampionPositionData) {
    lolItemSetsItemSets = getItemsSets(summoner.summonerId).await() // Refresh if user change others items
    resetAndPopulateItemsSets(champ)
    sendItems(summoner.summonerId, lolItemSetsItemSets).await()
}

private fun resetAndPopulateItemsSets(champ: ChampionPositionData) {
    lolItemSetsItemSets.itemSets?.removeIf { it.title.endsWith(TOKEN) }
    lolItemSetsItemSets.itemSets?.add(generateItemsSet(champ))
}

private suspend fun setSummonerSpells(champ: ChampionPositionData) {
    sendSummonerSpells(prepareSummonerSpells(champ)).await()
}

private fun prepareSummonerSpells(champ: ChampionPositionData) = JsonObject().apply {
    this["spell1Id"] = champ.summonerSpells?.get(0)
    this["spell2Id"] = champ.summonerSpells?.get(1)
}


suspend fun processChampion(champ: Champion) {

    startLoading("Process [ ${champ.id} -> ${champ.name}]...")
    println("${champ.name} process...")

    championsDatas.addAll(getUggDatas(champ).await())

    println("${champ.name} processed")
    ready()
}

private fun generateItemsSet(champDatas: ChampionPositionData) = LolItemSetsItemSet().apply {
    title = "${champDatas.champion.name} ${champDatas.position.title} $TOKEN"
    associatedChampions = listOf(champDatas.champion.id)
    if (champDatas.position == YPosition.ARAM) {
        associatedMaps = listOf(ARAM.id)
        this.map = ARAM.name
    } else {
        associatedMaps = listOf(SR.id)
        this.map = SR.name
    }
    blocks = listOf(
        generateSkillsBlock(champDatas.skills),
        champDatas.startItems?.let { generateItemsBlock("Starter Items", it) },
        champDatas.mainsItems?.let { generateItemsBlock("Mains Items", it) },
        champDatas.fourthItems?.let { generateSecondariesBlock("Fourth Items", it) },
        champDatas.fithItems?.let { generateSecondariesBlock("Fifth Items", it) },
        champDatas.sixthItems?.let { generateSecondariesBlock("Sixth Items", it) }
    )
}

private fun generateSecondariesBlock(title: String, itemsIn: List<Int>) =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn.map { generateItem(it) }.toList()
    }

private fun generateItem(idItem: Int) = LolItemSetsItemSetItem().apply {
    id = "$idItem"
    count = 1
}

private fun generateItemsBlock(title: String, itemsIn: List<Int>) =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn.map { generateItem(it) }.toList()
    }

private fun generateSkillsBlock(skills: Skills) = LolItemSetsItemSetBlock().apply {
    type = "Skills ${skills.priority} : ${skills.fullOrder}"
    items = listOf(3364, 3340, 3363, 2055, 1001).map { generateItem(it) }.toList()
}

private fun generatePerk(champ: ChampionPositionData) = LolPerksPerkPageResource().apply {
    primaryStyleId = champ.rune.masteryA
    subStyleId = champ.rune.masteryB
    selectedPerkIds = champ.rune.additionnalMastery
    name = "${champ.champion.name} ${champ.position.title} $TOKEN"
}



