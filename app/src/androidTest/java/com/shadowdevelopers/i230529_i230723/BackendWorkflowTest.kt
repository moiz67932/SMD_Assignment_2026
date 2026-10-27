package com.shadowdevelopers.i230529_i230723

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two end-to-end tests against the real Firebase project (see README "Espresso tests").
 * They need a seeded account that has finished Edit profile and has at least one friend.
 * Credentials are passed as instrumentation arguments, never stored in the repo:
 *   testEmail, testPassword, friendName (the friend's display name exactly as shown in the app)
 */
@RunWith(AndroidJUnit4::class)
class BackendWorkflowTest {

    @After
    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /** Log in, publish a text post, and see it at the top of the Home feed (written to and read back from RTDB). */
    @Test
    fun loginThenCreatedPostAppearsInHomeFeed() {
        signIn()
        val postText = "Espresso post ${System.currentTimeMillis()}"
        onView(withId(R.id.createPostButton)).perform(click())
        waitFor(withId(R.id.postTextInput))
        onView(withId(R.id.postTextInput)).perform(typeText(postText))
        closeSoftKeyboard()
        onView(withId(R.id.publishPostButton)).perform(click())
        // Create post closes after the write succeeds; Home's live query then shows the post.
        waitFor(allOf(withId(R.id.postText), withText(postText)))
    }

    /** Log in, message the seeded friend, see the message in the conversation and as the Chats preview. */
    @Test
    fun sendingChatMessageShowsInConversationAndChatsList() {
        signIn()
        val friendName = argument("friendName")
        val message = "Espresso hello ${System.currentTimeMillis()}"
        onView(withId(R.id.chatsButton)).perform(click())
        waitFor(withId(R.id.chatsNewButton))
        onView(withId(R.id.chatsNewButton)).perform(click())
        waitForDialogItem(friendName)
        onView(withText(friendName)).inRoot(isDialog()).perform(click())
        waitFor(withId(R.id.messageInput))
        onView(withId(R.id.messageInput)).perform(clearText(), typeText(message))
        closeSoftKeyboard()
        onView(withId(R.id.sendMessageButton)).perform(click())
        waitFor(allOf(withId(R.id.messageText), withText(message)))
        pressBack()
        // Chats row preview reads "You: <message> · now".
        waitFor(allOf(withId(R.id.chatLastMessage), withSubstring(message)))
    }

    private fun signIn() {
        FirebaseAuth.getInstance().signOut()
        ActivityScenario.launch(LoginActivity::class.java)
        onView(withId(R.id.emailInput)).perform(clearText(), typeText(argument("testEmail")))
        onView(withId(R.id.passwordInput)).perform(clearText(), typeText(argument("testPassword")))
        closeSoftKeyboard()
        onView(withId(R.id.loginButton)).perform(click())
        // The seeded account has a complete profile, so login lands on Home.
        waitFor(withId(R.id.feedRecyclerView))
    }

    private fun argument(name: String): String =
        InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Pass -Pandroid.testInstrumentationRunnerArguments.$name=… (see README)")

    /** Polls until a view matching [matcher] is on screen (Firebase answers asynchronously), up to [timeoutMs]. */
    private fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 20_000) {
        val end = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < end) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (error: NoMatchingViewException) {
                last = error
            } catch (error: AssertionError) {
                last = error
            }
            Thread.sleep(250)
        }
        throw AssertionError("Timed out after ${timeoutMs / 1000}s waiting for: $matcher", last)
    }

    private fun waitForDialogItem(text: String, timeoutMs: Long = 20_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            try {
                onView(withText(text)).inRoot(isDialog()).check(matches(isDisplayed()))
                return
            } catch (_: NoMatchingRootException) {
            } catch (_: NoMatchingViewException) {
            }
            Thread.sleep(250)
        }
        throw AssertionError("Timed out waiting for \"$text\" in the New message list")
    }
}
