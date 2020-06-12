package fr.omary.lol.yuumi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.io.File
import java.lang.System.getenv
import java.time.LocalDateTime
import javax.swing.*
import kotlin.system.exitProcess


// Source Example : https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/uiswing/examples/misc/TrayIconDemoProject/src/misc/TrayIconDemo.java

private var sendNotifications: Boolean = true
private val connected = createImage("images/Yuumi.png", "tray icon")
private val waiting = createImage("images/waitingConnect.png", "tray icon")
private val loading = createImage("images/loading.gif", "tray icon")
private val automaticDisabled = createImage("images/automaticDisabled.png", "tray icon")
private val trayIcon = TrayIcon(waiting.image)
private val messages: MutableList<Pair<LocalDateTime, String>> = mutableListOf()
private var champMenu = Menu("Send Synchronized Champion")
private var champMenuAtoG = Menu("[A-G]")
private var champMenuHtoM = Menu("[H-M]")
private var champMenuNtoS = Menu("[N-S]")
private var champMenuTtoZ = Menu("[T-Z]")
private const val yuumi = "Yuumi"
private const val defaultToolTip = "$yuumi is ready"
private const val waitingMessage = "Yuumi : Waiting LoL client to connect"
private const val lastSyncMessage = "Refresh - Last Sync : "
private val tmpDir = getenv("TMP")
private val yuumiTempDir = "${tmpDir}/yuumi"
val rankedDirectory = File("$yuumiTempDir/ranked")
val aramDirectory = File("$yuumiTempDir/aram")
val lastSync: File? = File("$yuumiTempDir/lastSync")
var lastSyncDate: String = "Never"
var sync = MenuItem("$lastSyncMessage $lastSyncDate")
var automatic: Boolean = true
var currentState = connected


fun main() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (ex: UnsupportedLookAndFeelException) {
        ex.printStackTrace()
    } catch (ex: IllegalAccessException) {
        ex.printStackTrace()
    } catch (ex: InstantiationException) {
        ex.printStackTrace()
    } catch (ex: ClassNotFoundException) {
        ex.printStackTrace()
    }

    createTempsDirs()

    UIManager.put("swing.boldMetal", false)
    SwingUtilities.invokeLater { createAndShowGUI() }
    startYuumi()
}

private fun refreshLastSyncDate(){
    lastSyncDate = if (lastSync?.exists()!!) {
        lastSync.readText()
    } else {
        "Never"
    }
    sync.label = "$lastSyncMessage $lastSyncDate"
}

private fun createTempsDirs() {
    rankedDirectory.mkdirs()
    aramDirectory.mkdirs()
}


private fun createAndShowGUI() {
    if (!SystemTray.isSupported()) {
        println("SystemTray is not supported")
        return
    }
    val popupMenu = PopupMenu()

    val tray = SystemTray.getSystemTray()

    val aboutItem = MenuItem("About")
    val history = MenuItem("History")
    refreshLastSyncDate()

    val optionNotifications = CheckboxMenuItem("Enable Notifications")
    optionNotifications.state = true
    val automaticSelection = CheckboxMenuItem("Automatic selection")
    automaticSelection.state = true
    val exitItem = MenuItem("Exit")

    popupMenu.add(aboutItem)
    popupMenu.addSeparator()
    popupMenu.add(optionNotifications)
    popupMenu.add(automaticSelection)
    popupMenu.add(history)
    popupMenu.add(sync)
    popupMenu.addSeparator()
    popupMenu.add(champMenu)
    popupMenu.addSeparator()
    popupMenu.add(exitItem)

    champMenu.add(champMenuAtoG)
    champMenu.add(champMenuHtoM)
    champMenu.add(champMenuNtoS)
    champMenu.add(champMenuTtoZ)

    // Initial champ = empty
    champMenuAtoG.add(generateItemMenu(Pair(0, "Empty for now")))
    champMenuHtoM.add(generateItemMenu(Pair(0, "Empty for now")))
    champMenuNtoS.add(generateItemMenu(Pair(0, "Empty for now")))
    champMenuTtoZ.add(generateItemMenu(Pair(0, "Empty for now")))

    trayIcon.popupMenu = popupMenu
    trayIcon.isImageAutoSize = true
    trayIcon.toolTip = waitingMessage
    try {
        tray.add(trayIcon)
    } catch (e: AWTException) {
        println("TrayIcon could not be added.")
        throw e
    }
    aboutItem.addActionListener {
        showYuumiDialog(
            """
                        Yuumi's informations:
                        Version : [$VERSION]
                        Commit : [$GIT_SHA]
                        Date Commit : [$GIT_DATE]
                        Date Build : [$BUILD_DATE]
                        Dirty : [$DIRTY]
                    """.trimIndent(),
            "About Yuumi"
        )
    }
    history.addActionListener {
        showHistory()
    }

    sync.addActionListener {

        syncAllChampDatas()
    }

    optionNotifications.addItemListener { e ->
        sendNotifications = e.stateChange == ItemEvent.SELECTED
    }
    automaticSelection.addItemListener { e ->
        automatic = e.stateChange == ItemEvent.SELECTED
        setAutomaticIcon()
    }

    exitItem.addActionListener {
        stopYuumi()
        tray.remove(trayIcon)
        exitProcess(0)
    }
}

fun syncAllChampDatas() {
    rankedDirectory.list().forEach { File("$rankedDirectory/$it").delete() }
    aramDirectory.list().forEach { File("$aramDirectory/$it").delete() }
    getChampListId().forEach { GlobalScope.async { processChampion(it) } }
    lastSync?.writeText(LocalDateTime.now().toString())
}


private fun showYuumiDialog(message: String, title: String) {
    JOptionPane.showMessageDialog(
        null,
        message,
        title,
        0,
        connected
    )
}

private fun showHistory() {
    showYuumiDialog(
        "History: \n${messages.joinToString("\n")}",
        "Yuumi History"
    )
}

fun createImage(path: String, description: String?): ImageIcon =
    ImageIcon(Thread.currentThread().contextClassLoader.getResource(path), description)

fun sendSystemNotification(message: String, level: String) {
    if (sendNotifications) {
        messages.add(Pair(LocalDateTime.now(), message))
        trayIcon.displayMessage(
            yuumi,
            message, TrayIcon.MessageType.valueOf(level.toUpperCase())
        )
    }
}

fun waitingConnect() {
    trayIcon.image = waiting.image
    trayIcon.toolTip = waitingMessage
}

fun setAutomaticIcon() {
    currentState = if (automatic) {
        connected
    } else {
        automaticDisabled
    }
    trayIcon.image = currentState.image
    trayIcon.toolTip = if (automatic) {
        defaultToolTip
    } else {
        "Automatic disabled"
    }
}

fun connected() {
    stopLoading()
}

fun startLoading(actionMessage: String = "Processing...") {
    trayIcon.image = loading.image
    trayIcon.toolTip = actionMessage
}

fun stopLoading() {
    trayIcon.image = currentState.image
    trayIcon.toolTip = defaultToolTip
}

fun refreshChampionList(championsNames: List<Pair<Int, String>>) {
    champMenuAtoG.removeAll()
    champMenuHtoM.removeAll()
    champMenuNtoS.removeAll()
    champMenuTtoZ.removeAll()
    championsNames.forEach {
        when (it.second.first()) {
            in 'A'..'G' -> champMenuAtoG.add(generateItemMenu(it))
            in 'H'..'M' -> champMenuHtoM.add(generateItemMenu(it))
            in 'N'..'S' -> champMenuNtoS.add(generateItemMenu(it))
            in 'T'..'Z' -> champMenuTtoZ.add(generateItemMenu(it))
        }
    }
}

fun generateChampPosition(champId: Int, position: String): MenuItem {
    val menuItem = MenuItem(position)
    val listener = ActionListener {
        GlobalScope.launch {
            sendChampionPostion(champId, position.toLowerCase())
        }
    }
    menuItem.addActionListener(listener)
    return menuItem
}


fun generateItemMenu(champPair: Pair<Int, String>): MenuItem {
    val menuChamp = Menu(champPair.second)
    listOf("Top", "Jungle", "Middle", "Bottom", "Utility", "ARAM").forEach {
        menuChamp.add(generateChampPosition(champPair.first, it))
    }
    if (champPair.first == 0) {
        menuChamp.isEnabled = false
    }
    return menuChamp
}
