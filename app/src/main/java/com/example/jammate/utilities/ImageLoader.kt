package com.example.jammate.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.jammate.R
import java.lang.ref.WeakReference

class ImageLoader private constructor(context: Context) {
    private val contextRef = WeakReference(context)

    companion object {
        @Volatile
        private var instance: ImageLoader? = null
        fun init(context: Context): ImageLoader {
            return instance ?: synchronized(this) {
                instance
                    ?: ImageLoader(context).also { instance = it }
            }
        }

        fun getInstance(): ImageLoader {
            return instance ?: throw IllegalStateException(
                "ImageLoader must be initialized by calling init(context) before use."
            )
        }

        // Shortcut for easy access
        fun load(source: Any?, imageView: ImageView, placeHolder: Int = R.raw.loading) {
            getInstance().loadImage(source, imageView, placeHolder)
        }
    }

    @SuppressLint("ResourceType")
    fun loadImage(
        source: Any?,
        imageView: ImageView,
        placeHolder: Int = R.raw.loading
    ) {
        val context = contextRef.get() ?: return
        Glide.with(context)
            .load(source)
            .centerCrop()
            .placeholder(placeHolder)
            .error(R.drawable.unavailable_photo)
            .into(imageView)
    }
}