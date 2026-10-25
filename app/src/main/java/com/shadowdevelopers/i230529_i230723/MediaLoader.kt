package com.shadowdevelopers.i230529_i230723

import android.widget.ImageView
import com.bumptech.glide.Glide

/** Shows a stored media ref (a Cloudinary https URL) in an ImageView, with design-language placeholders. */
object MediaLoader {
    fun load(view: ImageView, ref: String?) {
        if (ref.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageResource(R.drawable.media_placeholder)
            return
        }
        Glide.with(view).load(ref)
            .placeholder(R.drawable.media_placeholder)
            .error(R.drawable.media_error)
            .centerCrop()
            .into(view)
    }

    /** Cloudinary serves a JPEG frame of any uploaded video when the extension is changed to .jpg. */
    fun videoThumbnail(ref: String): String = ref.substringBeforeLast('.') + ".jpg"

    fun loadVideoThumbnail(view: ImageView, ref: String?) = load(view, ref?.takeIf { it.isNotBlank() }?.let(::videoThumbnail))
}
