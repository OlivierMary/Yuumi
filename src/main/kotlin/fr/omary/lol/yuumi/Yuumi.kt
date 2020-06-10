package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import fr.omary.lol.yuumi.models.ItemDatas2
import fr.omary.lol.yuumi.models.PositionDatas
import generated.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalDateTime.*

private var lastPick: Pair<Int, LocalDateTime> = -1 to now().minusDays(1)
private val perksByIdChamp: MutableMap<Int, List<Pair<Int, LolPerksPerkPageResource>>> = mutableMapOf()
private val championsByIdChamp: MutableMap<Int, LolChampionsCollectionsChampion> = mutableMapOf()
private val itemsSetsByIdChamp: MutableMap<Int, List<Triple<String?, LolItemSetsItemSet, Int>>> = mutableMapOf()
private val summonerSpellsByChamp: MutableMap<Int, List<Triple<String?, List<Int>?, Int>>> = mutableMapOf()

private var uniqueMaps = mutableListOf<LolMapsMaps>()
private lateinit var lolItemSetsItemSets: LolItemSetsItemSets
private lateinit var summoner: LolSummonerSummoner
private var gameMode: String? = null
private const val TOKEN = "GENERATED"
private const val FILL_POSITION = "FILL"
private const val ARAM_POSITION = "aram"
private const val ARAM_GAME_MODE = "ARAM"

suspend fun initStaticVariables() {
    summoner = waitToGetSummoner()!!
    val maps = getMaps()
    val uniqueMapsId = maps.await().map(LolMapsMaps::id).distinct()
    uniqueMaps = uniqueMapsId.map { id -> maps.await().first { m -> m.id == id } }.toMutableList()
    lolItemSetsItemSets = getItemsSets(summoner.summonerId).await()
}

fun getChampion(champId: Int) = championsByIdChamp[champId]

private suspend fun waitToGetSummoner(): LolSummonerSummoner? {
    var tempSummoner: LolSummonerSummoner?
    do {
        tempSummoner = getCurrentSummoner().await()
        delay(1000)
    } while (tempSummoner == null)
    return tempSummoner
}

private suspend fun refreshAssignerPosition() : String {
    val me = getTeam().await()?.first { x -> x.summonerId == summoner.summonerId }
    var assignedPosition = me?.assignedPosition
    if (assignedPosition == null || assignedPosition == "") {
        assignedPosition = if (gameMode == ARAM_GAME_MODE) {
            ARAM_POSITION
        } else {
            FILL_POSITION
        }
    }
    return assignedPosition
}

suspend fun validateChampion(champId: Int) {
    if (lastPick.first == champId && now().isBefore(lastPick.second.plusSeconds(30))) {
        return
    }
    lastPick = champId to now()
    startLoading("Send [${championsByIdChamp[champId]?.name}]")
    gameMode = getCurrentGameMode().await()
    sendChampionPostion(champId, refreshAssignerPosition())
    stopLoading()
}

suspend fun sendChampionPostion(champId: Int, position: String) {
    checkProcessedOrWait(champId)
    setPerks(champId, position)
    setItemsSets(champId, position)
    setSummonerSpells(champId, position)
    sendSystemNotification(
        "Champ set ${championsByIdChamp[champId]?.name} - $position - $gameMode : Sent",
        "INFO"
    )
}

private suspend fun checkProcessedOrWait(champId: Int) {
    // In case of select too fast if champ not already processed
    if (!championsByIdChamp.containsKey(champId) || championsByIdChamp[champId] == null) {
        processChampion(champId)
    } else {
        println("${championsByIdChamp[champId]?.name} already processed")
    }
    var timeout = 0
    while ((!perksByIdChamp.containsKey(champId) || !itemsSetsByIdChamp.containsKey(champId)) && timeout < 20) {
        println("Waiting process...")
        delay(500)
        timeout++
    }
}

private suspend fun setPerks(champId: Int, position: String) {
    if (perksByIdChamp.containsKey(champId) && perksByIdChamp[champId] != null) {
        resetGeneratedExistingPerks()
        setCurrentPerk(champId, position)
        commitPerks(champId)
    } else {
        println("Perks : no datas")
    }
}


private suspend fun resetGeneratedExistingPerks() =
    getPages().await()?.filter { p -> p.name.startsWith(TOKEN) }?.forEach { deletePages(it) }

private fun setCurrentPerk(champId: Int, position: String) {
    if (gameMode == ARAM_GAME_MODE) {
        perksByIdChamp[champId]!!.first { p ->
            p.second.name.contains(ARAM_POSITION) && p.second.name.contains(championsByIdChamp[champId]!!.name)
        }.second.current = true
    } else {
        if (position == FILL_POSITION) {
            perksByIdChamp[champId]!!.filterNot { it.second.name.contains(ARAM_POSITION) }
                .maxBy { it.first }?.second?.current = true
        } else {
            perksByIdChamp[champId]!!.forEach { p ->
                run {
                    p.second.current = position.let {
                        p.second.name.contains(it) && p.second.name.contains(championsByIdChamp[champId]!!.name)
                    }
                }
            }
        }
    }
}

private suspend fun commitPerks(champId: Int) {
    perksByIdChamp[champId]!!.sortedBy { it.second.current != null && it.second.current }
        .map { it to sendPage(it.second).await() }
        .filter { !it.second && it.first.second.current != null && it.first.second.current }
        .forEach {
            run {
                sendSystemNotification(
                    "Champ set ${championsByIdChamp[champId]?.name} : Not Enough runes pages",
                    "WARNING"
                )
                resetGeneratedExistingPerks()
                sendPage(it.first.second).await()
            }
        }
}

private suspend fun setItemsSets(champId: Int, position: String) {
    if (itemsSetsByIdChamp.containsKey(champId) && itemsSetsByIdChamp[champId] != null) {
        resetAndPopulateItemsSets(champId, position)
        sendItems(summoner.summonerId, lolItemSetsItemSets).await()
    }
}

private fun resetAndPopulateItemsSets(champId: Int, position: String) {
    lolItemSetsItemSets.itemSets?.removeIf { it.title.startsWith(TOKEN) }
    val items = if (position == FILL_POSITION) {
        itemsSetsByIdChamp[champId]!!.sortedBy { it.third }.map { it.second }
    } else {
        itemsSetsByIdChamp[champId]!!.sortedBy { it.first == position }.map { it.second }
    }
    lolItemSetsItemSets.itemSets?.addAll(items)
}

private suspend fun setSummonerSpells(champId: Int, position: String) {
    if (summonerSpellsByChamp.containsKey(champId) && summonerSpellsByChamp[champId] != null) {
        sendSummonerSpells(prepareSummonerSpells(champId, position)).await()
    }
}

private fun prepareSummonerSpells(champId: Int, position: String) = JsonObject().apply {
    val summonerSpells: List<Int>? = if (position == FILL_POSITION) {
        summonerSpellsByChamp[champId]?.filter { it.first != ARAM_POSITION }?.maxBy { it.third }?.second
    } else {
        summonerSpellsByChamp[champId]?.first { it.first == position }?.second
    }
    this["spell1Id"] = summonerSpells?.get(0)
    this["spell2Id"] = summonerSpells?.get(1)
}

suspend fun processChampion(champId: Int) {
    if (champId < 1) {
        return
    }
    startLoading("Process [$champId]...")

    var champion = championsByIdChamp[champId]
    if (champion == null) {
        champion = getChampion(summoner.summonerId, champId).await()
    }
    if (champion == null) {
        return // maybe client closed before process
    }
    championsByIdChamp[champId] = champion

    startLoading("Process [ ${champion.id} -> ${champion.name}]...")
    println("${champion.name} process...")

    val rankedDatas = getUggRankedOverviewDatas(champion)
    val aramDatas = getUggAramOverviewDatas(champion)

    val newItemsList = mutableListOf<Triple<String?, LolItemSetsItemSet, Int>>()
    val newPerks = mutableListOf<Pair<Int, LolPerksPerkPageResource>>()
    val newSummonerSpells = mutableListOf<Triple<String?, List<Int>?, Int>>()


    for (uniqueMap in uniqueMaps) {
        when (uniqueMap.mapStringId) {
            "SR" -> {
                for (role in rankedDatas.await()?.world?.platPlus?.allRoles()!!) {
                    newItemsList.add(generateItemsSet(champion, uniqueMap, role))
                    newPerks.add(generatePerk(role, champion, uniqueMap))
                    newSummonerSpells.add(generateSummoner(role))
                }
            }
            "HA" -> {
                newItemsList.add(generateItemsSet(champion, uniqueMap, aramDatas.await()?.world?.aram?.aram()))
                newPerks.add(generatePerk(aramDatas.await()?.world?.aram?.aram(), champion, uniqueMap))
                newSummonerSpells.add(generateSummoner(aramDatas.await()?.world?.aram?.aram()))
            }
        }
    }
    itemsSetsByIdChamp[champId] = newItemsList
    perksByIdChamp[champId] = newPerks
    summonerSpellsByChamp[champId] = newSummonerSpells
    println("${champion.name} processed")
    refreshChampTray()
    stopLoading()
}

fun refreshChampTray() {
    refreshChampionList(championsByIdChamp.values.sortedBy { it.name }.map { it.id to it.name })
}

private fun generateItemsSet(champion: LolChampionsCollectionsChampion?, uniqueMap: LolMapsMaps, role: PositionDatas?) =
    Triple(role?.name, LolItemSetsItemSet().apply {
        title = "$TOKEN ${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
        associatedMaps = listOf(uniqueMap.id.toInt())
        associatedChampions = listOf(champion?.id)
        map = uniqueMap.mapStringId
        blocks = listOf(
            generateSkillsBlock(role),
            generateItemsBlock("Starter Items", role?.startItems?.itemsId),
            generateItemsBlock("Mains Items", role?.mainsItems?.itemsId),
            generateSecondariesBlock("Fourth Items", role?.secondaryItems?.fourth),
            generateSecondariesBlock("Fifth Items", role?.secondaryItems?.fith),
            generateSecondariesBlock("Sixth Items", role?.secondaryItems?.sixth)
        )
    }, role?.winTotal!!.total)

private fun generateSecondariesBlock(title: String, itemsIn: List<ItemDatas2>?) =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it.itemId) }?.toList().orEmpty()
    }

private fun generateItem(idItem: Int) = LolItemSetsItemSetItem().apply {
    id = "$idItem"
    count = 1
}

private fun generateItemsBlock(title: String, itemsIn: List<Int>?) =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it) }?.toList().orEmpty()
    }

private fun generateSkillsBlock(role: PositionDatas?) = LolItemSetsItemSetBlock().apply {
    type = "Skills ${role?.skillsOrder?.skillPriority} : ${role?.skillsOrder?.skillOrder}"
    items = listOf(3364, 3340, 3363, 2055, 1001).map { generateItem(it) }.toList()
}

private fun generateSummoner(role: PositionDatas?) =
    Triple(role?.name, role?.summonerSpells!!.summonerSpells, role.winTotal!!.total)

private fun generatePerk(role: PositionDatas?, champion: LolChampionsCollectionsChampion?, uniqueMap: LolMapsMaps) =
    role?.winTotal!!.total to LolPerksPerkPageResource().apply {
        primaryStyleId = role.runes?.masteryA
        subStyleId = role.runes?.masteryB
        selectedPerkIds = role.runes?.additionnalMastery
        name = "$TOKEN ${champion?.name} ${uniqueMap.mapStringId} ${role.name}"
    }



