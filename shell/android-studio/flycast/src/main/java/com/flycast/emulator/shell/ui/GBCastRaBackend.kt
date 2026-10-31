package com.flycast.emulator.shell.ui

import com.flycast.emulator.emu.JNIdc
import gb.frame.RaBackend

/**
 * GBFrame adapter backed by Flycast's existing rcheevos client.
 *
 * HTTP, game hashing, achievement evaluation and authentication are owned by Flycast's native
 * RA client. This adapter translates its state into GBFrame's transport-neutral contract.
 */
object GBCastRaBackend : RaBackend {
    private var initialized = false
    private var lastLoggedIn = false
    private var lastUser = ""
    private var lastToken = ""
    private var lastLoginError = ""
    private var lastGameTitle = ""

    override fun init(userAgent: String) {
        initialized = JNIdc.raInit(userAgent)
        lastLoggedIn = false
        lastUser = ""
        lastToken = ""
        lastLoginError = ""
        lastGameTitle = ""
    }

    override fun login(user: String, secret: String, isToken: Boolean) {
        lastLoginError = ""
        if (isToken) JNIdc.raLoginWithToken(user, secret)
        else JNIdc.raLogin(user, secret)
    }

    override fun logout() {
        JNIdc.raLogout()
        lastLoggedIn = false
        lastUser = ""
        lastToken = ""
        lastGameTitle = ""
        lastLoginError = ""
    }

    override fun setHardcore(on: Boolean) {
        JNIdc.raSetHardcore(on)
    }

    override fun pollEvents(): ByteArray {
        if (!initialized) return byteArrayOf()
        val events = mutableListOf<String>()
        events += JNIdc.raOverlayEvents().orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        val loggedIn = JNIdc.raIsLoggedOn()
        val pending = JNIdc.raIsLoginPending()
        val error = JNIdc.raLoginError().orEmpty()
        val user = JNIdc.raCurrentUser().orEmpty().split('\t')
        val username = user.getOrElse(0) { "" }
        val token = user.getOrElse(1) { "" }
        val avatar = user.getOrElse(2) { "" }
        val score = user.getOrElse(3) { "0" }.toIntOrNull() ?: 0
        val softcoreScore = user.getOrElse(4) { "0" }.toIntOrNull() ?: 0

        if (loggedIn && (!lastLoggedIn || username != lastUser || token != lastToken)) {
            events += listOf("login", username, token, score.toString(), avatar, softcoreScore.toString()).joinToString("\t")
        } else if (!loggedIn && lastLoggedIn) {
            events += "loginfail\tLogged out of RetroAchievements"
        } else if (!loggedIn && !pending && error.isNotBlank() && error != lastLoginError) {
            events += "loginfail\t$error"
        }
        lastLoggedIn = loggedIn
        lastUser = if (loggedIn) username else ""
        lastToken = if (loggedIn) token else ""
        lastLoginError = error

        val game = JNIdc.raCurrentGame().orEmpty().split('\t')
        val gameTitle = game.getOrElse(0) { "" }
        val gameImage = game.getOrElse(5) { "" }
        val active = JNIdc.raIsActive() && gameTitle.isNotBlank()
        if (active && gameTitle != lastGameTitle) {
            events += listOf("game", gameTitle, "RetroAchievements game recognized", "0", gameImage).joinToString("\t")
        } else if (!active && lastGameTitle.isNotBlank()) {
            events += "gamefail\tThe game is no longer recognized by RetroAchievements"
        }
        lastGameTitle = if (active) gameTitle else ""

        return if (events.isEmpty()) byteArrayOf()
        else (events.joinToString("\n", postfix = "\n")).toByteArray(Charsets.UTF_8)
    }

    override fun achievements(): ByteArray = JNIdc.raAchievements().orEmpty().toByteArray(Charsets.UTF_8)

    override fun httpResponse(id: Int, status: Int, body: ByteArray?) {
        JNIdc.raHttpResponse(id, status, body)
    }

    /** The current-game screen uses the native game's achievement list; library-wide hashes are not exposed by Flycast here. */
    override fun hashGame(path: String): String? = null

    override fun onGameRecognized() = Unit

    override fun onResetRequested() {
        com.flycast.emulator.shell.GbcastGameUi.restartCurrentGame()
    }
}
