package app.cairn.appdrop

import app.cairn.appdrop.AppDropLogic.Event
import app.cairn.ui.UpdateBannerMapper
import app.cairn.ui.UpdateBannerUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDropLogicTest {
    private val H12 = 12L * 3600 * 1000
    private fun info(v: String = "1.5.0", code: Long? = null, dl: String? = "https://x/d") =
        UpdateInfo(v, code, "Notes", null, "https://store", dl, 100L)

    private fun resolve(i: UpdateInfo?, installed: String = "1.4.0", skipped: String? = null, dismissedUntil: Long = 0, now: Long = 1000, force: Boolean = false, checked: Boolean = true) =
        AppDropLogic.resolve(i, installed, null, null, skipped, dismissedUntil, now, force, checked)

    @Test fun versionCompare() {
        assertTrue(AppDropLogic.compareVersions("1.10.0", "1.9.9")!! > 0)
        assertEquals(0, AppDropLogic.compareVersions("v1.5", "1.5.0")!!)
        assertTrue(AppDropLogic.compareVersions("1.5.0-beta", "1.5.0")!! < 0)
        assertEquals(0, AppDropLogic.compareVersions("1.5.0-beta", "1.5.0-beta")!!)
        assertNull(AppDropLogic.compareVersions("abc", "1.0"))
    }

    @Test fun isNewerFallsBackToVersionCode() {
        assertTrue(AppDropLogic.isNewer("1.4.0", 4, "1.5.0", null)!!)
        assertFalse(AppDropLogic.isNewer("1.5.0", 5, "1.5.0", 5)!!)
        assertTrue(AppDropLogic.isNewer("dev", 4, "nightly", 9)!!)
        assertNull(AppDropLogic.isNewer("dev", null, "nightly", 9))
    }

    @Test fun throttle() {
        assertFalse(AppDropLogic.shouldCheck(H12 - 1, 1, false, H12 + 1))
        assertTrue(AppDropLogic.shouldCheck(H12 + 1, 1, false, H12))
        assertTrue(AppDropLogic.shouldCheck(10, 5, true, H12))
        assertTrue(AppDropLogic.shouldCheck(10, 0, false, H12))
        assertTrue(AppDropLogic.shouldCheck(5, 10, false, H12)) // clock went backwards
    }

    @Test fun availableSkippedDismissedAndCachedOffline() {
        assertTrue(resolve(info()) is UpdateState.Available)                       // Available
        assertEquals(UpdateState.Idle, resolve(info(), skipped = "1.5.0"))          // skipped this version
        assertTrue(resolve(info("1.6.0"), skipped = "1.5.0") is UpdateState.Available) // newer than the skipped one
        assertEquals(UpdateState.Idle, resolve(info(), dismissedUntil = 2000, now = 1000)) // dismissed for now
        assertTrue(resolve(info(), dismissedUntil = 2000, now = 3000) is UpdateState.Available)
        assertTrue(resolve(info(), skipped = "1.5.0", dismissedUntil = 9999, force = true) is UpdateState.Available) // manual check ignores both
        // cached while offline: still newer than installed -> shows; once installed catches up -> gone
        assertTrue(resolve(info()) is UpdateState.Available)
        assertEquals(UpdateState.UpToDate, resolve(info(), installed = "1.5.0"))
        assertEquals(UpdateState.Idle, resolve(null, checked = false))
        assertEquals(UpdateState.UpToDate, resolve(null))
    }

    @Test fun reducer() {
        val a = UpdateState.Available(info())
        assertEquals(UpdateState.Checking, AppDropLogic.reduce(UpdateState.Idle, Event.CheckStarted))
        assertEquals(a, AppDropLogic.reduce(a, Event.CheckStarted)) // no flicker while an update is known
        assertEquals(UpdateState.Idle, AppDropLogic.reduce(a, Event.Skipped("1.5.0")))
        assertEquals(a, AppDropLogic.reduce(a, Event.Skipped("1.4.9")))
        assertEquals(UpdateState.Idle, AppDropLogic.reduce(a, Event.Dismissed))
        assertEquals(UpdateState.Downloading(100), AppDropLogic.reduce(a, Event.Progress(150)))
        val d = UpdateState.Downloading(40)
        assertEquals(d, AppDropLogic.reduce(d, Event.Resolved(UpdateState.UpToDate))) // a refresh never interrupts a download
        assertEquals(UpdateState.ReadyToInstall, AppDropLogic.reduce(d, Event.Downloaded))
        assertEquals(UpdateState.Error("x", a.info), AppDropLogic.reduce(d, Event.Failed("x", a.info)))
    }

    @Test fun summaryIsOneLine() {
        assertEquals("Faster widgets", AppDropLogic.summarize("- **Faster** widgets\n- b"))
        assertEquals("What's new", AppDropLogic.summarize("## What's new\n- a"))
        assertEquals("Widget-first home, sticky editor, update notices", AppDropLogic.summarize("Widget-first home, sticky editor, update notices"))
        val long = AppDropLogic.summarize("word ".repeat(100))
        assertTrue(long.endsWith("word…") && long.length <= 200)
    }

    @Test fun shortSummaryIsNeverCutToAWord() {
        assertEquals("New widgets and a calmer sign-in", AppDropLogic.summarize("New widgets and a calmer sign-in"))
        assertEquals("New widgets and a calmer sign-in", (UpdateBannerMapper.map(UpdateState.Available(info().copy(notesSummary = AppDropLogic.summarize("New widgets and a calmer sign-in")))) as UpdateBannerUi.Offer).summary)
    }

    @Test fun bannerMapping() {
        val a = UpdateBannerMapper.map(UpdateState.Available(info()))
        assertEquals("Cairn 1.5.0 available", (a as UpdateBannerUi.Offer).title)
        assertTrue(a.canInstallInApp)
        assertFalse((UpdateBannerMapper.map(UpdateState.Available(info(dl = null))) as UpdateBannerUi.Offer).canInstallInApp)
        assertNull(UpdateBannerMapper.map(UpdateState.Idle))
        assertNull(UpdateBannerMapper.map(UpdateState.UpToDate))
        assertNull(UpdateBannerMapper.map(UpdateState.Error("offline", null)))
        assertEquals("offline", (UpdateBannerMapper.map(UpdateState.Error("offline", info())) as UpdateBannerUi.Offer).error)
        assertEquals(UpdateBannerUi.Progress(30), UpdateBannerMapper.map(UpdateState.Downloading(30)))
        assertEquals(UpdateBannerUi.Ready, UpdateBannerMapper.map(UpdateState.ReadyToInstall))
    }
}
