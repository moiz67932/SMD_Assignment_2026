package com.shadowdevelopers.i230529_i230723

import android.annotation.SuppressLint
import android.app.Activity
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseException

/** Small helpers that make forms behave the way people expect (keyboard actions, busy buttons, clear errors). */
object Forms {
    /** The eye icon at the end of a password field shows / hides the password (and keeps the cursor where it was). */
    @SuppressLint("ClickableViewAccessibility")
    fun passwordToggle(field: EditText) {
        field.setOnTouchListener { view, event ->
            val icon = field.compoundDrawablesRelative[2] ?: return@setOnTouchListener false
            val iconStart = field.width - field.paddingEnd - icon.bounds.width() - field.compoundDrawablePadding
            if (event.action == MotionEvent.ACTION_UP && event.x >= iconStart) {
                val cursor = field.selectionEnd
                val hidden = field.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD
                field.inputType = InputType.TYPE_CLASS_TEXT or if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                field.setSelection(cursor.coerceIn(0, field.text.length))
                icon.alpha = if (hidden) 255 else 140
                view.performClick()
                return@setOnTouchListener true
            }
            false
        }
        field.compoundDrawablesRelative[2]?.alpha = 140
    }

    /** Runs [action] when the keyboard's Done / Send / Search / Go key is pressed in this field. */
    fun onKeyboardAction(field: EditText, action: () -> Unit) {
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                action()
                true
            } else false
        }
    }

    fun hideKeyboard(activity: Activity) {
        val view = activity.currentFocus ?: return
        (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * While a request is running the button is disabled and says what is happening ("Logging in…"),
     * so the user knows the tap worked and does not tap again. busy = false restores the original label.
     */
    fun setBusy(button: View, busy: Boolean, busyLabel: String? = null) {
        button.isEnabled = !busy
        button.alpha = if (busy) 0.7f else 1f
        if (button !is TextView) return
        if (busy && busyLabel != null) {
            if (button.getTag(R.id.formOriginalLabel) == null) button.setTag(R.id.formOriginalLabel, button.text)
            button.text = busyLabel
        } else if (!busy) {
            // Always put the normal label back ("Create account"), whether it worked or failed.
            (button.getTag(R.id.formOriginalLabel) as? CharSequence)?.let { button.text = it }
            button.setTag(R.id.formOriginalLabel, null)
        }
    }

    /** Shows the problem right on the field (and moves the cursor there) instead of a generic toast. */
    fun error(field: TextView, message: String): Boolean {
        field.error = message
        field.requestFocus()
        return false
    }

    /** Same, for errors delivered to a database listener (onCancelled). */
    fun message(error: com.google.firebase.database.DatabaseError): String =
        message(error.toException(), "Could not load this right now. Check your connection.")

    /** Plain-language versions of Firebase errors. */
    fun message(error: Exception?, fallback: String): String = when (error) {
        is FirebaseAuthUserCollisionException -> "An account with this email already exists. Log in instead."
        is FirebaseAuthWeakPasswordException -> "That password is too weak. Use at least 6 characters."
        is FirebaseAuthInvalidUserException -> "No account was found with this email."
        is FirebaseAuthInvalidCredentialsException -> "The email or password is not correct."
        is FirebaseTooManyRequestsException -> "Too many attempts. Wait a minute and try again."
        is FirebaseNetworkException -> "No internet connection. Check your connection and try again."
        is com.shadowdevelopers.i230529_i230723.data.ApiClient.ApiException -> when (error.status) {
            401 -> if (error.code == "invalid_credentials") "The email or password is not correct." else "Your session ended. Please log in again."
            else -> error.message?.takeIf { it.isNotBlank() } ?: fallback
        }
        is java.io.IOException -> "Can't reach the Kinnect server. Check your Wi-Fi and that the backend is running."
        is DatabaseException -> if (error.message.orEmpty().contains("permission", ignoreCase = true))
            "The server refused to save this (database rules). Make sure the latest rules are published." else fallback
        null -> fallback
        else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
