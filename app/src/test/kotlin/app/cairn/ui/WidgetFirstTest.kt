package app.cairn.ui

import app.cairn.widget.Screen
import app.cairn.widget.WidgetKind
import app.cairn.widget.WidgetSize
import app.cairn.widget.WidgetStyle
import app.cairn.widget.WidgetVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFirstTest {
    @Test fun everyCardMapsToAWidgetWithAStyleOfThatKind() {
        CardId.entries.forEach { c ->
            val l = CardWidgetMap.link(c)
            assertTrue("$c", l.style == null || l.style.kind == l.kind)
        }
        assertEquals(WidgetKind.Today, CardWidgetMap.link(CardId.TodayHeader).kind)
        assertEquals(WidgetKind.LastActivity, CardWidgetMap.link(CardId.TodayActivity).kind)
        assertEquals(WidgetKind.HeartRate, CardWidgetMap.link(CardId.HeartRate).kind)
        assertEquals(WidgetKind.HeartRate, CardWidgetMap.link(CardId.RestingHr).kind)
        assertEquals(WidgetKind.Stress, CardWidgetMap.link(CardId.Stress).kind)
        assertEquals(WidgetKind.Sleep, CardWidgetMap.link(CardId.Sleep).kind)
        assertEquals(WidgetKind.Hrv, CardWidgetMap.link(CardId.Hrv).kind)
        assertEquals(WidgetKind.TrainingLoad, CardWidgetMap.link(CardId.WeeklyLoad).kind)
        assertEquals(WidgetStyle.LoadDial, CardWidgetMap.link(CardId.TrainingStatus).style)
        assertEquals(WidgetKind.Recovery, CardWidgetMap.link(CardId.Recovery).kind)
        assertEquals(WidgetKind.Fitness, CardWidgetMap.link(CardId.RunningFitness).kind)
        assertEquals(WidgetKind.WeeklyMileage, CardWidgetMap.link(CardId.ActivitiesMonth).kind)
        assertEquals(WidgetKind.LastActivity, CardWidgetMap.link(CardId.ActivityRow).kind)
        assertEquals(WidgetKind.TrainingLoad, CardWidgetMap.link(CardId.FitTrainingStatus).kind)
        assertEquals(WidgetStyle.FitnessGrid, CardWidgetMap.link(CardId.FitRacePredictions).style)
        assertEquals(WidgetKind.Fitness, CardWidgetMap.link(CardId.FitVo2).kind)
    }

    @Test fun bottomNavStartsOnWidgetsAndKeepsTheOtherThreeTabs() {
        assertEquals(listOf(Routes.WIDGETS, Routes.TODAY, Routes.ACTIVITIES, Routes.FITNESS), Routes.tabs)
        assertEquals(Routes.WIDGETS, Routes.START)
        assertFalse(Routes.SETTINGS in Routes.tabs)
        assertEquals(Routes.WIDGETS, Routes.forScreen(null))
        assertEquals(Routes.WIDGETS, Routes.forScreen(Screen.Home.name))
        assertEquals(Routes.TODAY, Routes.forScreen(Screen.Today.name))
        assertEquals(Routes.FITNESS, Routes.forScreen(Screen.Fitness.name))
    }

    @Test fun deepTargetsArePushedOnTopOfTheHome() {
        assertNull(Routes.deepTarget(null))
        assertNull(Routes.deepTarget(Screen.Home.name))
        assertEquals(Routes.ACCOUNT, Routes.deepTarget(Screen.Account.name))
        assertEquals(Routes.ACTIVITIES, Routes.deepTarget(Screen.Activities.name))
    }

    @Test fun bannerActionPerErrorKind() {
        assertEquals(BannerAction.SignIn, SyncProblems.actionFor(ProblemKind.NotSignedIn))
        assertEquals(BannerAction.SignIn, SyncProblems.actionFor(ProblemKind.NeedsSignIn))
        listOf(ProblemKind.Network, ProblemKind.Server, ProblemKind.Partial).forEach { assertEquals(BannerAction.Retry, SyncProblems.actionFor(it)) }
        listOf(ProblemKind.Offline, ProblemKind.CapReached, ProblemKind.Other).forEach { assertEquals(BannerAction.None, SyncProblems.actionFor(it)) }
        assertEquals("Sign in again", SyncProblems.label(BannerAction.SignIn, ProblemKind.NeedsSignIn))
        assertNull(SyncProblems.label(BannerAction.None, ProblemKind.Offline))
        assertTrue(SyncProblems.startsSignIn(ProblemKind.NotSignedIn))
        assertFalse(SyncProblems.startsSignIn(ProblemKind.NeedsSignIn))
    }

    @Test fun problemKindFromRunOutcomeAndGate() {
        assertEquals(ProblemKind.NeedsSignIn, SyncProblems.fromOutcome("NEEDS_SIGN_IN", null))
        assertEquals(ProblemKind.Network, SyncProblems.fromOutcome("NETWORK_ERROR", null))
        assertEquals(ProblemKind.Server, SyncProblems.fromOutcome("FAILED", null))
        assertEquals(ProblemKind.CapReached, SyncProblems.fromOutcome("SKIPPED", "daily budget reached"))
        assertEquals(ProblemKind.NotSignedIn, SyncProblems.fromGate(false, true, "x"))
        assertEquals(ProblemKind.Offline, SyncProblems.fromGate(true, false, "x"))
        assertEquals(ProblemKind.CapReached, SyncProblems.fromGate(true, true, "Cairn reached its daily limit of 50"))
    }

    @Test fun postSignInDecision() {
        assertTrue(PostSignIn.goHome(wasSignedIn = false, nowSignedIn = true))
        assertFalse(PostSignIn.goHome(wasSignedIn = true, nowSignedIn = true)) // opening the account screen while signed in stays put
        assertFalse(PostSignIn.goHome(wasSignedIn = false, nowSignedIn = false))
        assertFalse(PostSignIn.goHome(wasSignedIn = true, nowSignedIn = false))
        assertTrue(PostSignIn.shouldConfirm(0L, 1_000L))
        assertFalse(PostSignIn.shouldConfirm(1_000L, 2_000L)) // the redirect and the in-app flip report one sign-in once
        assertTrue(PostSignIn.shouldConfirm(1_000L, 1_000L + PostSignIn.CONFIRM_WINDOW_MS + 1))
    }

    @Test fun everyReceiverMapsBackToItsKindAndSize() {
        WidgetKind.entries.forEach { k -> WidgetSize.entries.forEach { s ->
            val name = WidgetVariants.receiverName(k, s)
            assertEquals(k, WidgetVariants.kindOf(name)); assertEquals(s, WidgetVariants.sizeOf(name))
        } }
    }
}
