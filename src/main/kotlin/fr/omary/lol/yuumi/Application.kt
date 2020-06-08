package fr.omary.lol.yuumi

import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.time.LocalDateTime
import javax.swing.*
import kotlin.system.exitProcess


// Source Example : https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/uiswing/examples/misc/TrayIconDemoProject/src/misc/TrayIconDemo.java

private var sendNotifications: Boolean = true
private val connected = createImage("images/Yuumi.png", "tray icon")
private val waiting = createImage("images/waitingConnect.png", "tray icon")
private val loading = createImage("images/loading.gif", "tray icon")
private val trayIcon = TrayIcon(waiting)
private val messages: MutableList<Pair<LocalDateTime, String>> = mutableListOf()
private var champMenu = Menu("Send Synchronized Champion")
private var champMenuAtoG = Menu("[A-G]")
private var champMenuHtoM = Menu("[H-M]")
private var champMenuNtoS = Menu("[N-S]")
private var champMenuTtoZ = Menu("[T-Z]")
private const val defaultToolTip = "Yuumi"
private const val waitingMessage = "Yuumi : Waiting LoL client to connect"


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
    UIManager.put("swing.boldMetal", false)
    SwingUtilities.invokeLater { createAndShowGUI() }
    startYuumi()
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
    val optionNotifications = CheckboxMenuItem("Enable Notifications")
    optionNotifications.state = true
    val exitItem = MenuItem("Exit")

    popupMenu.add(aboutItem)
    popupMenu.addSeparator()
    popupMenu.add(optionNotifications)
    popupMenu.add(history)
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
    trayIcon.addActionListener {
        showAbout()
    }
    aboutItem.addActionListener {
        showAbout()
    }
    history.addActionListener {
        showHistory()
    }

    optionNotifications.addItemListener { e ->
        sendNotifications = e.stateChange == ItemEvent.SELECTED
    }

    exitItem.addActionListener {
        stopYuumi()
        tray.remove(trayIcon)
        exitProcess(0)
    }
}


private fun showAbout() {
    JOptionPane.showMessageDialog(
        null,
        defaultToolTip
    )
}

private fun showHistory() {
    JOptionPane.showMessageDialog(
        null,
        "History: \n${messages.joinToString("\n")}"
    )
}

fun createImage(path: String, description: String?): Image = ImageIcon(Thread.currentThread().contextClassLoader.getResource(path), description).image

fun sendSystemNotification(message: String, level: String) {
    messages.add(Pair(LocalDateTime.now(), message))
    trayIcon.displayMessage(
        defaultToolTip,
        message, TrayIcon.MessageType.valueOf(level.toUpperCase())
    )
}

fun waitingConnect() {
    trayIcon.image = waiting
    trayIcon.toolTip = waitingMessage
}

fun connected(){
    stopLoading()
}

fun startLoading(actionMessage: String = "Processing...") {
    trayIcon.image = loading
    trayIcon.toolTip = actionMessage
}

fun stopLoading() {
    trayIcon.image = connected
    trayIcon.toolTip = defaultToolTip
}

fun refreshChampionList(championsNames: List<Pair<Int, String>>) {
    champMenuAtoG.removeAll()
    champMenuHtoM.removeAll()
    champMenuNtoS.removeAll()
    champMenuTtoZ.removeAll()
    championsNames.forEach { when (it.second.first()){
        in 'A'..'G' -> champMenuAtoG.add(generateItemMenu(it))
        in 'H'..'M' -> champMenuHtoM.add(generateItemMenu(it))
        in 'N'..'S' -> champMenuNtoS.add(generateItemMenu(it))
        in 'T'..'Z' -> champMenuTtoZ.add(generateItemMenu(it))
    } }
}

fun generateItemMenu(champPair: Pair<Int, String>): MenuItem {
    val menuItem = MenuItem(champPair.second)
    val listener = ActionListener {
        validateChampion(champPair.first)
    }
    if (champPair.first == 0) {
        menuItem.isEnabled = false
    }
    menuItem.addActionListener(listener)
    return menuItem
}
