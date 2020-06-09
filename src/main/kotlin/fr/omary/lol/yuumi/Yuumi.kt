package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import fr.omary.lol.yuumi.models.ItemDatas2
import fr.omary.lol.yuumi.models.PositionDatas
import generated.*
import java.lang.Thread.sleep

private val perksByIdChamp: MutableMap<Int, List<Pair<Int, LolPerksPerkPageResource>>> = mutableMapOf()
private val championsByIdChamp: MutableMap<Int, LolChampionsCollectionsChampion> = mutableMapOf()
private val itemsSetsByIdChamp: MutableMap<Int, List<Triple<String?, LolItemSetsItemSet, Int>>> = mutableMapOf()
private val summonerSpellsByChamp: MutableMap<Int, List<Triple<String?, List<Int>?, Int>>> = mutableMapOf()

private var uniqueMaps = mutableListOf<LolMapsMaps>()
private lateinit var lolItemSetsItemSets: LolItemSetsItemSets
private lateinit var summoner: LolSummonerSummoner
private var gameMode: String? = null
private var assignedPosition: String? = null
private const val TOKEN = "GENERATED"

fun initStaticVariables() {
    summoner = waitToGetSummoner()!!
    val maps = getMaps()
    val uniqueMapsId = maps.map(LolMapsMaps::id).distinct()
    uniqueMaps = uniqueMapsId.map { id -> maps.first { m -> m.id == id } }.toMutableList()
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
        assignedPosition = if (gameMode == "ARAM") {
            "ARAM"
        } else {
            "FILL"
        }
    }
}

fun validateChampion(champId: Int) {
    startLoading("Send [${championsByIdChamp[champId]?.name}]")
    gameMode = getCurrentGameMode()
    refreshAssignerPosition()
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


private fun resetGeneratedExistingPerks() =
    getPages()?.filter { p -> p.name.startsWith(TOKEN) }?.forEach { deletePages(it) }

private fun setCurrentPerk(champId: Int) {
    if (gameMode == "ARAM") {
        perksByIdChamp[champId]!!.first { p ->
            p.second.name.contains(gameMode!!) && p.second.name.contains(championsByIdChamp[champId]!!.name)
        }.second.current = true
    } else {
        if (assignedPosition == "FILL") {
            perksByIdChamp[champId]!!.filterNot { it.second.name.contains("ARAM") }
                .maxBy { it.first }?.second?.current = true
        } else {
            perksByIdChamp[champId]!!.forEach { p ->
                run {
                    p.second.current = assignedPosition?.let {
                        p.second.name.contains(it) && p.second.name.contains(championsByIdChamp[champId]!!.name)
                    }
                }
            }
        }
    }
}

private fun commitPerks(champId: Int) {
    perksByIdChamp[champId]!!.sortedBy { it.second.current != null && it.second.current }
        .map { it to sendPage(it.second) }
        .filter { !it.second && it.first.second.current != null && it.first.second.current }
        .forEach {
            run {
                sendSystemNotification(
                    "Champ set ${championsByIdChamp[champId]?.name} : Not Enough runes pages",
                    "WARNING"
                )
                resetGeneratedExistingPerks()
                sendPage(it.first.second)
            }
        }
}

private fun setItemsSets(champId: Int) {
    if (itemsSetsByIdChamp.containsKey(champId) && itemsSetsByIdChamp[champId] != null) {
        resetAndPopulateItemsSets(champId)
        sendItems(summoner.summonerId, lolItemSetsItemSets)
    }
}

private fun resetAndPopulateItemsSets(champId: Int) {
    lolItemSetsItemSets.itemSets?.removeIf { it.title.startsWith(TOKEN) }
    val items = if (assignedPosition == "FILL") {
        itemsSetsByIdChamp[champId]!!.sortedBy { it.third }.map { it.second }
    } else {
        itemsSetsByIdChamp[champId]!!.sortedBy { it.first == assignedPosition }.map { it.second }
    }
    lolItemSetsItemSets.itemSets?.addAll(items)
}

private fun setSummonerSpells(champId: Int) {
    if (summonerSpellsByChamp.containsKey(champId) && summonerSpellsByChamp[champId] != null) {
        sendSummonerSpells(prepareSummonerSpells(champId))
    }
}

private fun prepareSummonerSpells(champId: Int) = JsonObject().apply {
    if (assignedPosition != null && assignedPosition != "") {
        val summonerSpells: List<Int>? = if (assignedPosition == "FILL") {
            summonerSpellsByChamp[champId]?.filter { it.first != "ARAM" }?.maxBy { it.third }?.second
        } else {
            summonerSpellsByChamp[champId]?.first { it.first == assignedPosition }?.second
        }
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

    val newItemsList = mutableListOf<Triple<String?, LolItemSetsItemSet, Int>>()
    val newPerks = mutableListOf<Pair<Int, LolPerksPerkPageResource>>()
    val newSummonerSpells = mutableListOf<Triple<String?, List<Int>?, Int>>()


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



