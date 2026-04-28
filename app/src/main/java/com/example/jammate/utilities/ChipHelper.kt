package com.example.jammate.utilities

import android.content.Context
import android.view.View
import com.example.jammate.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

object ChipHelper {

    fun buildChips(context: Context, group: ChipGroup, items: List<String>) {
        group.removeAllViews()
        items.forEach { text ->
            val chip = Chip(context).apply {
                this.text = text
                isCheckable = true
                isCheckedIconVisible = false

                setChipBackgroundColorResource(R.color.chip_bg_selector)
                setChipStrokeColorResource(R.color.chip_stroke_selector)
                chipStrokeWidth = context.resources.displayMetrics.density * 1f
                setTextColor(context.resources.getColorStateList(R.color.chip_text_selector, null))
            }
            group.addView(chip)
        }
    }

    fun getCheckedChipTexts(group: ChipGroup): List<String> {
        val checked = ArrayList<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) checked.add(chip.text.toString())
        }
        return checked
    }

    fun getSingleSelectedChipText(group: ChipGroup): String? {
        val checkedId = group.checkedChipId
        if (checkedId == View.NO_ID) return null
        return group.findViewById<Chip>(checkedId)?.text?.toString()
    }

}