package com.shadowdevelopers.i230529_i230723

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Every avatar in the design is a teal circle with initials (a TextView). When the user has a
 * profile photo, the photo is drawn as that circle's background and the initials are hidden.
 * If there is no photo (or it fails to load), the initials circle stays, so no layout changes are needed.
 */
object AvatarBinder {
    fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.take(2).joinToString("")

    /** cornerRadiusDp = null draws a circle; a number draws a rounded square (e.g. friend tiles). */
    fun bind(view: TextView, name: String, photoRef: String?, cornerRadiusDp: Float? = null) {
        // Remember the original circle drawable once, so recycled rows can go back to it.
        if (view.getTag(R.id.avatarOriginalBackground) == null) view.setTag(R.id.avatarOriginalBackground, view.background ?: Unit)
        val original = view.getTag(R.id.avatarOriginalBackground) as? Drawable
        Glide.with(view).clear(view)
        view.background = original
        view.text = initials(name)
        if (photoRef.isNullOrBlank()) return
        val request = Glide.with(view).asBitmap().load(photoRef)
        (if (cornerRadiusDp == null) request.circleCrop() else request.centerCrop()).into(object : CustomViewTarget<TextView, Bitmap>(view) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                getView().background = RoundedBitmapDrawableFactory.create(getView().resources, resource).apply {
                    if (cornerRadiusDp == null) isCircular = true
                    else cornerRadius = cornerRadiusDp * getView().resources.displayMetrics.density
                }
                getView().text = ""
            }

            override fun onLoadFailed(errorDrawable: Drawable?) = restore()
            override fun onResourceCleared(placeholder: Drawable?) = restore()

            private fun restore() {
                getView().background = original
                getView().text = initials(name)
            }
        })
    }
}
