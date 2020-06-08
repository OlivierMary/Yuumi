package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import fr.omary.lol.yuumi.models.ItemDatas2
import fr.omary.lol.yuumi.models.PositionDatas
import generated.*
import java.lang.Thread.sleep

private val perksByIdChamp: MutableMap<Int, List<LolPerksPerkPageResource>> = mutableMapOf()
private val championsByIdChamp: MutableMap<Int, LolChampionsCollectionsChampion> = mutableMapOf()
private val itemsSetsByIdChamp: MutableMap<Int, List<LolItemSetsItemSet>> = mutableMapOf()
private val summonerSpellsByChamp: MutableMap<Int, List<Pair<String?, List<Int>?>>> = mutableMapOf()

private var uniqueMaps = mutableListOf<LolMapsMaps>()
private lateinit var lolItemSetsItemSets: LolItemSetsItemSets
private lateinit var summoner: LolSummonerSummoner
private var gameMode: String? = null
private var assignedPosition: String? = null

fun initStaticVariables() {
    summoner = waitToGetSummoner()!!
    println("Summoner : ${summoner.displayName}")
    val maps = getMaps()
    val uniqueMapsId = maps.map(LolMapsMaps::id).distinct()
    uniqueMaps = uniqueMapsId.map { id -> maps.first { m -> m.id == id } }.toMutableList()
    for (uniqueMap in uniqueMaps) {
        println("Map : [${uniqueMap.gameMode}, ${uniqueMap.mapStringId}, ${uniqueMap.gameModeName}] ")
    }
    lolItemSetsItemSets = getItemsSets(summoner.summonerId)
}

fun getChampion(champId: Int) = championsByIdChamp[champId]

private fun waitToGetSummoner(): LolSummonerSummoner? {
    var tempSummoner: LolSummonerSummoner?
    do {
        tempSummoner = getCurrentSummoner()
        sleep(1000)
    } while (tempSummoner == null)
    return tempSummoner
}

private fun refreshAssignerPosition() {
    val me = getTeam()?.first { x -> x.summonerId == summoner.summonerId }
    assignedPosition = me?.assignedPosition
    if (assignedPosition == null || assignedPosition == "") {
        assignedPosition = "ARAM"
    }
    println("Assigned position : $assignedPosition")
}

fun validateChampion(champId: Int) {
    startLoading("Send [${championsByIdChamp[champId]?.name}]")
    checkProcessedOrWait(champId)
    setPerks(champId)
    setItemsSets(champId)
    setSummonerSpells(champId)
    sendSystemNotification(
        "Champ set ${championsByIdChamp[champId]?.name} - $assignedPosition - $gameMode : Sent",
        "INFO"
    )
    stopLoading()
}

private fun checkProcessedOrWait(champId: Int) {
    gameMode = getCurrentGameMode()
    if (assignedPosition == null || assignedPosition == "") {
        refreshAssignerPosition()
    }
    // In case of select too fast if champ not already processed
    if (!championsByIdChamp.containsKey(champId) || championsByIdChamp[champId] == null) {
        processChampion(champId)
    } else {
        println("${championsByIdChamp[champId]?.name} already processed")
    }
    var timeout = 0
    while ((!perksByIdChamp.containsKey(champId) || !itemsSetsByIdChamp.containsKey(champId)) && timeout < 20) {
        println("Waiting process...")
        sleep(500)
        timeout++
    }
}

private fun setPerks(champId: Int) {
    if (perksByIdChamp.containsKey(champId) && perksByIdChamp[champId] != null) {
        resetGeneratedExistingPerks()
        setCurrentPerk(champId)
        commitPerks(champId)
    } else {
        println("Perks : no datas")
    }
}

private fun resetGeneratedExistingPerks() {
    val existingPerks = getPages()
    existingPerks.filter { p -> p.name.startsWith("GENERATED") }
        .forEach { deletePages(it) }
}

private fun setCurrentPerk(champId: Int) {
    if (gameMode == "ARAM") {
        perksByIdChamp[champId]!!.first { p ->
            p.name.contains(gameMode!!) && p.name.contains("GENERATED") && p.name.contains(
                championsByIdChamp[champId]!!.name
            )
        }.current = true
    } else {
        perksByIdChamp[champId]!!.forEach { p ->
            run {
                p.current = assignedPosition?.let {
                    p.name.contains(it) && p.name.contains("GENERATED") && p.name.contains(championsByIdChamp[champId]!!.name)
                }
            }
        }
    }
}

private fun commitPerks(champId: Int) {
    perksByIdChamp[champId]!!.sortedBy { it.current }.map { it to sendPage(it) }
        .filter { it.first.current != null && it.first.current }.forEach {
            run {
                sendSystemNotification(
                    "Champ set ${championsByIdChamp[champId]?.name} : Not Enough runes pages",
                    "WARNING"
                )
                resetGeneratedExistingPerks()
                sendPage(it.first)
            }
        }
}

private fun setItemsSets(champId: Int) {
    if (itemsSetsByIdChamp.containsKey(champId) && itemsSetsByIdChamp[champId] != null) {
        resetAndPopulateItemsSets(champId)
        sendItems(summoner.summonerId, lolItemSetsItemSets)
    } else {
        println("Items Set : no datas")
    }
}

private fun resetAndPopulateItemsSets(champId: Int) {
    lolItemSetsItemSets.itemSets?.clear()
    lolItemSetsItemSets.itemSets?.addAll(itemsSetsByIdChamp[champId]!!)
}

private fun setSummonerSpells(champId: Int) {
    if (summonerSpellsByChamp.containsKey(champId) && summonerSpellsByChamp[champId] != null) {
        val spells = prepareSummonerSpells(champId)
        sendSummonerSpells(spells)
    } else {
        println("Summoner Spells : no datas")
    }
}

private fun prepareSummonerSpells(champId: Int): JsonObject = JsonObject().apply {
    if (assignedPosition != null && assignedPosition != "") {
        val summonerSpells =
            summonerSpellsByChamp[champId]?.first { p -> p.first == assignedPosition }?.second
        this["spell1Id"] = summonerSpells?.get(0)
        this["spell2Id"] = summonerSpells?.get(1)
    }
}

fun processChampion(champId: Int) {
    startLoading("Process [$champId]...")
    if (champId < 1) {
        return
    }

    var champion = championsByIdChamp[champId]
    if (champion == null) {
        champion = getChampion(summoner.summonerId, champId)
    }
    if (champion == null) {
        return // maybe client closed before process
    }
    championsByIdChamp[champId] = champion

    startLoading("Process [ ${champion.id} -> ${champion.name}]...")
    println("${champion.name} process...")

    val rankedDatas = getUggRankedOverviewDatas(champion)
    val aramDatas = getUggAramOverviewDatas(champion)

    val newItemsList = mutableListOf<LolItemSetsItemSet>()
    val newPerks = mutableListOf<LolPerksPerkPageResource>()
    val newSummonerSpells = mutableListOf<Pair<String?, List<Int>?>>()


    for (uniqueMap in uniqueMaps) {
        when (uniqueMap.mapStringId) {
            "SR" -> {
                for (role in rankedDatas?.world?.platPlus?.allRoles()!!) {
                    newItemsList.add(generateItemsSet(champion, uniqueMap, role))
                    newPerks.add(generatePerk(role, champion, uniqueMap))
                    newSummonerSpells.add(generateSummoner(role))
                }
            }
            "HA" -> {
                newItemsList.add(generateItemsSet(champion, uniqueMap, aramDatas?.world?.aram?.aram()))
                newPerks.add(generatePerk(aramDatas?.world?.aram?.aram(), champion, uniqueMap))
                newSummonerSpells.add(generateSummoner(aramDatas?.world?.aram?.aram()))
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

private fun generateItemsSet(
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps, role: PositionDatas?
): LolItemSetsItemSet = LolItemSetsItemSet().apply {
    title = "${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
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
}

private fun generateSecondariesBlock(title: String, itemsIn: List<ItemDatas2>?): LolItemSetsItemSetBlock =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it.itemId) }?.toList().orEmpty()
    }

private fun generateItem(idItem: Int): LolItemSetsItemSetItem = LolItemSetsItemSetItem().apply {
    id = "$idItem"
    count = 1
}

private fun generateItemsBlock(title: String, itemsIn: List<Int>?): LolItemSetsItemSetBlock =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it) }?.toList().orEmpty()
    }

private fun generateSkillsBlock(role: PositionDatas?): LolItemSetsItemSetBlock = LolItemSetsItemSetBlock().apply {
    type = "Skills ${role?.skillsOrder?.skillPriority} : ${role?.skillsOrder?.skillOrder}"
    items = listOf(3364, 3340, 3363, 2055).map { generateItem(it) }.toList()
}

private fun generateSummoner(role: PositionDatas?) = role?.name to role?.summonerSpells?.summonerSpells

private fun generatePerk(
    role: PositionDatas?,
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps
): LolPerksPerkPageResource = LolPerksPerkPageResource().apply {
    primaryStyleId = role?.runes?.masteryA
    subStyleId = role?.runes?.masteryB
    selectedPerkIds = role?.runes?.additionnalMastery
    name = "GENERATED ${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
}



