package com.flycast.emulator.shell

/**
 * Pure decision policy for Android Back while the Flycast game UI is active.
 *
 * Use GbcastGameUi's state constants as the single source of truth; native pause/resume
 * remains owned by GbcastGameUi.
 */
internal object GbcastBackPolicy {
    enum class Action {
        OPEN_NATIVE_SETTINGS,
        RETURN_TO_PAUSE_MENU,
        SHOW_PAUSE_MENU,
        RESUME_GAME,
        FINISH_ACTIVITY,
        TOGGLE_PAUSE_AND_SHOW,
    }

    fun decide(state: Int, menuVisible: Boolean): Action = when (state) {
        GbcastGameUi.LOADING, GbcastGameUi.VJOY, GbcastGameUi.VJOY_CMD ->
            Action.OPEN_NATIVE_SETTINGS
        GbcastGameUi.COMMANDS, GbcastGameUi.SETTINGS, GbcastGameUi.DISK,
        GbcastGameUi.CHEATS, GbcastGameUi.ACHIEVEMENTS ->
            Action.RETURN_TO_PAUSE_MENU
        GbcastGameUi.PAUSE -> if (menuVisible) Action.RESUME_GAME else Action.SHOW_PAUSE_MENU
        GbcastGameUi.MAIN -> Action.FINISH_ACTIVITY
        else -> Action.TOGGLE_PAUSE_AND_SHOW
    }
}
