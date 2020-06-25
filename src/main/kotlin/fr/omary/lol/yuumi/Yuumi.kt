package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import fr.omary.lol.yuumi.models.Champion
import fr.omary.lol.yuumi.models.ItemDatas2
import fr.omary.lol.yuumi.models.LoLMap
import fr.omary.lol.yuumi.models.PositionDatas
import generated.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalDateTime.*

private var lastPick: Pair<Champion, LocalDateTime> = Champion(-1, "Nobody") to now().minusDays(1)
private val perksByIdChamp: MutableMap<Champion, List<Pair<Int, LolPerksPerkPageResource>>> = mutableMapOf()
private val itemsSetsByIdChamp: MutableMap<Champion, List<Triple<String?, LolItemSetsItemSet, Int>>> = mutableMapOf()
private val summonerSpellsByChamp: MutableMap<Champion, List<Triple<String?, List<Int>?, Int>>> = mutableMapOf()

private lateinit var lolItemSetsItemSets: LolItemSetsItemSets
private lateinit var summoner: LolSummonerSummoner
private const val TOKEN = "GENERATED"
private const val FILL_POSITION = "FILL"
private const val ARAM_POSITION = "aram"
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

private suspend fun refreshAssignerPosition(gameMode: String?): String {
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
    position: String?,
    gameMode: String?
) {
    val finalPosition = position ?: refreshAssignerPosition(gameMode)
    val perksOk = setPerks(champ, finalPosition, gameMode)
    setItemsSets(champ, finalPosition)
    setSummonerSpells(champ, finalPosition)
    if (perksOk) {
        sendSystemNotification(
            "Champ set ${champ.name} - $finalPosition : Sent",
            "INFO"
        )
    }
}

private suspend fun setPerks(champ: Champion, position: String, gameMode: String?): Boolean {
    if (perksByIdChamp.containsKey(champ) && perksByIdChamp[champ] != null) {
        resetGeneratedExistingPerks()
        setCurrentPerk(champ, position, gameMode)
        commitPerks(champ)
    } else {
        sendSystemNotification(
            "Champ set ${champ.name} - Not enough datas",
            "ERROR"
        )
    }
    return perksByIdChamp.containsKey(champ) && perksByIdChamp[champ] != null
}


private suspend fun resetGeneratedExistingPerks() =
    getPages().await()?.filter { p -> p.name.endsWith(TOKEN) }?.forEach { deletePage(it).await() }

private fun setCurrentPerk(champ: Champion, position: String, gameMode: String?) {
    if (gameMode == ARAM_GAME_MODE) {
        perksByIdChamp[champ]!!.first { p ->
            p.second.name.contains(ARAM_POSITION)
        }.second.current = true
    } else {
        if (position == FILL_POSITION) {
            perksByIdChamp[champ]!!.filterNot { it.second.name.contains(ARAM_POSITION) }
                .maxBy { it.first }?.second?.current = true
        } else {
            perksByIdChamp[champ]!!.forEach { p ->
                run {
                    p.second.current = position.let {
                        p.second.name.contains(it)
                    }
                }
            }
        }
    }
}

private suspend fun commitPerks(champ: Champion) {
    perksByIdChamp[champ]!!.sortedBy { it.second.current != null && it.second.current }
        .map { it to sendPage(it.second).await() }
        .filter { !it.second && it.first.second.current != null && it.first.second.current }
        .forEach {
            run {
                sendSystemNotification(
                    "Champ set ${champ.name} : Not Enough runes pages",
                    "WARNING"
                )
                resetGeneratedExistingPerks()
                sendPage(it.first.second).await()
            }
        }
}

private suspend fun setItemsSets(champ: Champion, position: String) {
    lolItemSetsItemSets = getItemsSets(summoner.summonerId).await() // Refresh if user change others items
    if (itemsSetsByIdChamp.containsKey(champ) && itemsSetsByIdChamp[champ] != null) {
        resetAndPopulateItemsSets(champ, position)
        sendItems(summoner.summonerId, lolItemSetsItemSets).await()
    }
}

private fun resetAndPopulateItemsSets(champ: Champion, position: String) {
    lolItemSetsItemSets.itemSets?.removeIf { it.title.endsWith(TOKEN) }
    val items = if (position == FILL_POSITION) {
        itemsSetsByIdChamp[champ]!!.sortedByDescending { it.third }.map { it.second }
    } else {
        itemsSetsByIdChamp[champ]!!.sortedByDescending { it.first == position }.map { it.second }
    }
    var index = 0
    val itemsRenamed = items.map {
        LolItemSetsItemSet().apply {
            title = "${index++} - ${it.title}"
            associatedChampions = it.associatedChampions
            associatedMaps = it.associatedMaps
            blocks = it.blocks
            map = it.map
            mode = it.mode
            preferredItemSlots = it.preferredItemSlots
            sortrank = it.sortrank
            startedFrom
            type = it.type

        }
    }.toList()

    lolItemSetsItemSets.itemSets?.addAll(itemsRenamed)
}

private suspend fun setSummonerSpells(champ: Champion, position: String) {
    if (summonerSpellsByChamp.containsKey(champ) && summonerSpellsByChamp[champ] != null) {
        sendSummonerSpells(prepareSummonerSpells(champ, position)).await()
    }
}

private fun prepareSummonerSpells(champ: Champion, position: String) = JsonObject().apply {
    val summonerSpells: List<Int>? = if (position == FILL_POSITION) {
        summonerSpellsByChamp[champ]?.filter { it.first != ARAM_POSITION }?.maxBy { it.third }?.second
    } else {
        summonerSpellsByChamp[champ]?.first { it.first == position }?.second
    }
    this["spell1Id"] = summonerSpells?.get(0)
    this["spell2Id"] = summonerSpells?.get(1)
}


suspend fun processChampion(champ: Champion) {

    startLoading("Process [ ${champ.id} -> ${champ.name}]...")
    println("${champ.name} process...")

    val rankedDatas = getUggRankedOverviewDatas(champ).await()
    val aramDatas = getUggAramOverviewDatas(champ).await()

    val newItemsList = mutableListOf<Triple<String?, LolItemSetsItemSet, Int>>()
    val newPerks = mutableListOf<Pair<Int, LolPerksPerkPageResource>>()
    val newSummonerSpells = mutableListOf<Triple<String?, List<Int>?, Int>>()


    for (map in getMapsList()) {
        when (map.name) {
            SR_MAP -> {
                for (role in rankedDatas?.world?.platPlus?.allRoles()!!) {
                    newItemsList.add(generateItemsSet(champ, map, role))
                    newPerks.add(generatePerk(role, champ, map))
                    newSummonerSpells.add(generateSummoner(role))
                }
            }
            ARAM_MAP -> {
                newItemsList.add(generateItemsSet(champ, map, aramDatas?.world?.aram?.aram()))
                newPerks.add(generatePerk(aramDatas?.world?.aram?.aram(), champ, map))
                newSummonerSpells.add(generateSummoner(aramDatas?.world?.aram?.aram()))
            }
        }
    }
    itemsSetsByIdChamp[champ] = newItemsList
    perksByIdChamp[champ] = newPerks
    summonerSpellsByChamp[champ] = newSummonerSpells
    println("${champ.name} processed")
    ready()
}

private fun generateItemsSet(champion: Champion, map: LoLMap, role: PositionDatas?) =
    Triple(role?.name, LolItemSetsItemSet().apply {
        title = "${champion.name} ${map.name} ${role?.name} $TOKEN"
        associatedMaps = listOf(map.id)
        associatedChampions = listOf(champion.id)
        this.map = map.name
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

private fun generatePerk(role: PositionDatas?, champion: Champion, map: LoLMap) =
    role?.winTotal!!.total to LolPerksPerkPageResource().apply {
        primaryStyleId = role.runes?.masteryA
        subStyleId = role.runes?.masteryB
        selectedPerkIds = role.runes?.additionnalMastery
        name = "${champion.name} ${map.name} ${role.name} $TOKEN"
    }



