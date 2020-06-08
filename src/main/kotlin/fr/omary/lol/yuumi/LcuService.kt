package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import com.stirante.lolclient.ClientApi
import com.stirante.lolclient.ClientConnectionListener
import com.stirante.lolclient.ClientWebSocket
import generated.*

private val api = ClientApi()
private var socket: ClientWebSocket? = null

fun startYuumi() {
    println("Waiting for client connect.")
    ClientApi.setDisableEndpointWarnings(true)
    api.addClientConnectionListener(object : ClientConnectionListener {
        override fun onClientDisconnected() {
            println("Client disconnected")
            socket?.close()
        }

        override fun onClientConnected() {
            println("Client connected")
            try {
                openSocket()
                if (socket == null || !socket!!.isOpen) {
                    return
                }
                initStaticVariables()
                notifConnected()

                socket?.setSocketListener(object : ClientWebSocket.SocketListener {
                    override fun onEvent(event: ClientWebSocket.Event) {
                        handleValidateChampion(event)
                        handleChooseChampion(event)
                    }

                    override fun onClose(code: Int, reason: String) {
                        println("Socket closed, reason: $reason")
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    })
}

fun stopYuumi() {
    sendLcuNotif("Disconnected", "Disconnected to Yuumi, See you later", 1)
    socket?.close()
    api.stop()
}

fun notifConnected() {
    sendLcuNotif("Connected", "Connected to Yuumi, Enjoy", 11)
}

fun sendLcuNotif(title: String, detail: String, idImage: Int) {
    val notif = PlayerNotificationsPlayerNotificationResource()
    notif.titleKey = "pre_translated_title"
    notif.detailKey = "pre_translated_details"
    val data = JsonObject()
    data["title"] = title
    data["details"] = detail
    notif.data = data
    notif.iconUrl = "https://ddragon.leagueoflegends.com/cdn/10.11.1/img/champion/Yuumi.png"
    notif.backgroundUrl = "https://ddragon.leagueoflegends.com/cdn/img/champion/splash/Yuumi_$idImage.jpg"
    api.executePost("/player-notifications/v1/notifications", notif)
}

fun getCurrentSummoner() = api.executeGet("/lol-summoner/v1/current-summoner", LolSummonerSummoner::class.java)
fun sendSummonerSpells(spells: JsonObject): Boolean = api.executePatch(
    "/lol-lobby-team-builder/champ-select/v1/session/my-selection",
    spells
)

fun getMaps() = api.executeGet("/lol-maps/v2/maps", Array<LolMapsMaps>::class.java)

fun getItemsSets(summonerId: Long): LolItemSetsItemSets = api.executeGet(
    "/lol-item-sets/v1/item-sets/${summonerId}/sets",
    LolItemSetsItemSets::class.java
)

fun getChampion(summonerId: Long, champId: Int): LolChampionsCollectionsChampion? = api.executeGet(
    "/lol-champions/v1/inventories/${summonerId}/champions/${champId}",
    LolChampionsCollectionsChampion::class.java
)


fun sendItems(summonerId: Long?, items: LolItemSetsItemSets): Boolean = api.executePut(
    "/lol-item-sets/v1/item-sets/$summonerId/sets", items
)

fun getCurrentGameMode() = api.executeGet("/lol-lobby/v2/lobby", LolLobbyLobbyDto::class.java)?.gameConfig?.gameMode

fun getTeam(): MutableList<LolLobbyTeamBuilderChampSelectPlayerSelection>? = api.executeGet(
    "/lol-lobby-team-builder/champ-select/v1/session",
    LolLobbyTeamBuilderChampSelectSession::class.java
)?.myTeam

fun deletePages(page: LolPerksPerkPageResource) = api.executeDelete("/lol-perks/v1/pages/${page.id}")
fun getPages() = api.executeGet("/lol-perks/v1/pages", Array<LolPerksPerkPageResource>::class.java)
fun sendPage(perk: LolPerksPerkPageResource) = api.executePost("/lol-perks/v1/pages", perk)

private fun openSocket() {
    var maxTry = 20
    do {
        println("Socket not connected will (re)try...")
        Thread.sleep(1000)
        try {
            if (socket != null) {
                socket?.close()
            }
            socket = api.openWebSocket()
        } catch (e: Exception) {
            // nothing
        }
        maxTry--
    } while (socket == null || !socket?.isOpen!! || maxTry < 0)
    println("Socket connected")
}


private fun handleValidateChampion(event: ClientWebSocket.Event) {
    if (event.eventType != "Delete" && event.uri == "/lol-champ-select/v1/current-champion") {
        validateChampion(event.data as Int)
    }
}

private fun handleChooseChampion(event: ClientWebSocket.Event) {
    if (event.eventType != "Delete" && event.uri.startsWith("/lol-champ-select/v1/grid-champions/")) {
        if (getChampion((event.data as LolChampSelectChampGridChampion).id) == null) {
            processChampion((event.data as LolChampSelectChampGridChampion).id)
        } else {
            println("${getChampion((event.data as LolChampSelectChampGridChampion).id)?.name} already processed")
        }
    }
}


