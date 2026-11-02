package com.flycast.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class GbcastBackPolicyTest {
    @Test
    fun nativeSubmenusReturnToNativeSettings() {
        listOf(GbcastGameUi.LOADING, GbcastGameUi.VJOY, GbcastGameUi.VJOY_CMD).forEach { state ->
            assertEquals(
                GbcastBackPolicy.Action.OPEN_NATIVE_SETTINGS,
                GbcastBackPolicy.decide(state, menuVisible = false),
            )
        }
    }

    @Test
    fun commandAndNativePagesReturnToPauseMenu() {
        listOf(
            GbcastGameUi.COMMANDS,
            GbcastGameUi.SETTINGS,
            GbcastGameUi.DISK,
            GbcastGameUi.CHEATS,
            GbcastGameUi.ACHIEVEMENTS,
        ).forEach { state ->
            assertEquals(
                GbcastBackPolicy.Action.RETURN_TO_PAUSE_MENU,
                GbcastBackPolicy.decide(state, menuVisible = true),
            )
        }
    }

    @Test
    fun pauseStateShowsMenuWhenHiddenAndResumesWhenVisible() {
        assertEquals(
            GbcastBackPolicy.Action.SHOW_PAUSE_MENU,
            GbcastBackPolicy.decide(GbcastGameUi.PAUSE, menuVisible = false),
        )
        assertEquals(
            GbcastBackPolicy.Action.RESUME_GAME,
            GbcastBackPolicy.decide(GbcastGameUi.PAUSE, menuVisible = true),
        )
    }

    @Test
    fun mainFinishesAndUnknownStatesUseToggleFallback() {
        assertEquals(
            GbcastBackPolicy.Action.FINISH_ACTIVITY,
            GbcastBackPolicy.decide(GbcastGameUi.MAIN, menuVisible = false),
        )
        assertEquals(
            GbcastBackPolicy.Action.TOGGLE_PAUSE_AND_SHOW,
            GbcastBackPolicy.decide(GbcastGameUi.CLOSED, menuVisible = false),
        )
        assertEquals(
            GbcastBackPolicy.Action.TOGGLE_PAUSE_AND_SHOW,
            GbcastBackPolicy.decide(99, menuVisible = true),
        )
    }
}
