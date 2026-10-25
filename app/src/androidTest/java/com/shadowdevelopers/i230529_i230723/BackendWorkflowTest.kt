package com.shadowdevelopers.i230529_i230723

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.core.app.ActivityScenario
import org.hamcrest.Matcher
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendWorkflowTest {
    @Test
    fun loginLoadsFirebasePostAndItsCommentsThenReturnsHome() {
        signIn()
        val postText = requiredArgument("postText")
        onView(isRoot()).perform(waitForText(postText))
        onView(withText(postText)).check(matches(isDisplayed()))
        onView(withId(R.id.commentsButton)).perform(click())
        onView(withText("Comments")).check(matches(isDisplayed()))
        onView(isRoot()).perform(waitForText(requiredArgument("commentText")))
        pressBack()
        onView(withId(R.id.feedRecyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun logoutClearsAuthenticatedBackStack() {
        signIn()
        onView(withId(R.id.menuTab)).perform(click())
        onView(withId(R.id.logoutButton)).perform(click())
        onView(withId(R.id.loginButton)).check(matches(isDisplayed()))
        pressBack()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        assertFalse(resumed.any { it is HomeActivity || it is MenuActivity })
    }

    private fun signIn() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        ActivityScenario.launch(LoginActivity::class.java)
        onView(withId(R.id.emailInput)).perform(clearText(), typeText(requiredArgument("testEmail")))
        onView(withId(R.id.passwordInput)).perform(clearText(), typeText(requiredArgument("testPassword")))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        onView(withId(R.id.loginButton)).perform(click())
        onView(isRoot()).perform(waitForId(R.id.menuTab))
    }

    private fun requiredArgument(name: String): String {
        return InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Provide instrumentation argument '$name'")
    }

    private fun waitForId(id: Int) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription() = "Wait for view $id"
        override fun perform(uiController: UiController, view: View) {
            val end = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < end) {
                if (view.rootView.findViewById<View>(id)?.visibility == View.VISIBLE) return
                uiController.loopMainThreadForAtLeast(100)
            }
            assertTrue("Timed out waiting for view $id", false)
        }
    }

    private fun waitForText(value: String) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription() = "Wait for text to appear"
        override fun perform(uiController: UiController, view: View) {
            val end = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < end) {
                val matches = ArrayList<View>()
                view.rootView.findViewsWithText(matches, value, View.FIND_VIEWS_WITH_TEXT)
                if (matches.isNotEmpty()) return
                uiController.loopMainThreadForAtLeast(100)
            }
            assertTrue("Timed out waiting for Firebase post", false)
        }
    }
}
