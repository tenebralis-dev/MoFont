package com.je.fontsmanager.samsung.ui

import com.je.fontsmanager.samsung.R

enum class PreviewStyle( // note on enums, see https://developer.android.com/topic/performance/reduce-apk-size#remove-enums
    val labelRes: Int,
    val weight: Int,
    val italic: Boolean,
    val prefersBoldTf: Boolean
) {
    Regular(R.string.label_regular_variant, 400, false, false),
    Italic(R.string.label_italic_variant, 400, true, false),
    Medium(R.string.label_medium_variant, 500, false, false),
    MediumItalic(R.string.label_medium_italic_variant, 500, true, false),
    Bold(R.string.label_bold_style, 700, false, true),
    BoldItalic(R.string.label_bold_italic_variant, 700, true, true)
}
