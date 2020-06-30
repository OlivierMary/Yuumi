package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import com.stirante.lolclient.ClientApi
import com.stirante.lolclient.ClientConnectionListener
import com.stirante.lolclient.ClientWebSocket
import generated.*
import kotlinx.coroutines.*

private val api = ClientApi()
private var socket: ClientWebSocket? = null

fun apiIsConnected(): Boolean = api.isConnected

fun startYuumi() {
    println("Waiting for client connect.")
    ClientApi.setDisableEndpointWarnings(true)
    api.addClientConnectionListener(object : ClientConnectionListener {
        override fun onClientDisconnected() {
            println("Client disconnected")
            socket?.close()
            waitingConnect()
        }

        override fun onClientConnected() {
            println("Client connected")
            try {
                openSocket()
                if (socket == null || !socket!!.isOpen) {
                    return
                } else {
                    println("Socket connected")
                }


                socket?.setSocketListener(object : ClientWebSocket.SocketListener {
                    override fun onEvent(event: ClientWebSocket.Event) {
                        GlobalScope.launch {
                            //println(event)
                            handleValidateChampion(event)
                        }
                    }

                    override fun onClose(code: Int, reason: String) {
                        println("Socket closed, reason: $reason")
                        if (api.isConnected) {
                            openSocket()
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runBlocking {
                initStaticVariables()
                notifConnected()
                connect()
            }
            ready()
        }
    })
}

fun stopYuumi() {
    try {
        sendLcuNotif("Disconnected", "Disconnected to Yuumi, See you later", 1)
        socket?.close()
        api.stop()
    } catch (e: Exception) {
        // Nothing
    }
}

fun notifConnected() {
    GlobalScope.launch {
        sendLcuNotif("Connected", "Connected to Yuumi, Enjoy", 11)
    }
}

fun sendLcuNotif(title: String, detail: String, idImage: Int) {
    if (sendNotifications) {
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
}

fun getCurrentSummoner(): Deferred<LolSummonerSummoner> = GlobalScope.async {
    api.executeGet("/lol-summoner/v1/current-summoner", LolSummonerSummoner::class.java)
}

fun sendSummonerSpells(spells: JsonObject): Deferred<Boolean> = GlobalScope.async {
    api.executePatch(
        "/lol-lobby-team-builder/champ-select/v1/session/my-selection",
        spells
    ) || api.executePatch(
        "/lol-champ-select/v1/session/my-selection",
        spells
    )
}

fun getItemsSets(summonerId: Long): Deferred<LolItemSetsItemSets> = GlobalScope.async {
    api.executeGet(
        "/lol-item-sets/v1/item-sets/${summonerId}/sets",
        LolItemSetsItemSets::class.java
    )
}

fun sendItems(summonerId: Long?, items: LolItemSetsItemSets): Deferred<Boolean> = GlobalScope.async {
    api.executePut(
        "/lol-item-sets/v1/item-sets/$summonerId/sets", items
    )
}

fun getCurrentGameMode() =
    GlobalScope.async { api.executeGet("/lol-lobby/v2/lobby", LolLobbyLobbyDto::class.java)?.gameConfig?.gameMode }

fun getTeam(): Deferred<MutableList<LolLobbyTeamBuilderChampSelectPlayerSelection>?> = GlobalScope.async {
    api.executeGet(
        "/lol-lobby-team-builder/champ-select/v1/session",
        LolLobbyTeamBuilderChampSelectSession::class.java
    )?.myTeam
}

fun deletePage(page: LolPerksPerkPageResource) =
    GlobalScope.async { api.executeDelete("/lol-perks/v1/pages/${page.id}") }

fun getPages(): Deferred<Array<LolPerksPerkPageResource>?> = GlobalScope.async {
    api.executeGet("/lol-perks/v1/pages", Array<LolPerksPerkPageResource>::class.java)
}

fun sendPage(perk: LolPerksPerkPageResource): Deferred<Boolean> =
    GlobalScope.async { api.executePost("/lol-perks/v1/pages", perk) }

private fun openSocket() {
    var maxTry = 20
    do {
        println("Socket not connected will (re)try... $maxTry remaining")
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
    } while (maxTry > 0 && (socket == null || !socket?.isOpen!!))
}


private suspend fun handleValidateChampion(event: ClientWebSocket.Event) {
    if (automatic && event.eventType != "Delete" && event.uri == "/lol-champ-select/v1/current-champion") {
        validateChampion(getChampionList().find { it.id == (event.data as Int) }!!)
    }
}


