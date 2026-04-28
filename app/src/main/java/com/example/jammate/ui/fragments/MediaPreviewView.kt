package com.example.jammate.ui.fragments

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.bumptech.glide.Glide
import com.example.jammate.databinding.ViewMediaPreviewBinding

class MediaPreviewView : FrameLayout {

    private lateinit var binding: ViewMediaPreviewBinding
    private var currentUri: Uri? = null
    private var currentType: String? = null

    var onRemove: (() -> Unit)? = null

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        initView(context)
    }

    private fun initView(context: Context) {
        binding = ViewMediaPreviewBinding.inflate(LayoutInflater.from(context), this, true)

        binding.mediaPreviewBTNRemove.setOnClickListener {
            clear()
            onRemove?.invoke()
        }
    }

    fun setMedia(uri: Uri, mediaType: String) {
        currentUri = uri
        currentType = mediaType

        visibility = VISIBLE

        binding.mediaPreviewIMGPlay.visibility =
            if (mediaType == "video") VISIBLE else GONE

        Glide.with(binding.mediaPreviewIMG)
            .load(uri)
            .centerCrop()
            .into(binding.mediaPreviewIMG)

        requestLayout()
        invalidate()
    }

    fun clear() {
        currentUri = null
        currentType = null
        binding.mediaPreviewIMGPlay.visibility = GONE
        binding.mediaPreviewIMG.setImageDrawable(null)
        visibility = GONE
    }

}
