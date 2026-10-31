package com.example.beeing.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class TagChipTextTest {

    @Test
    fun aTrailingEmojiMovesToTheFront() = assertEquals("😇 FeelGood", tagChipText("FeelGood😇"))

    @Test
    fun aTrailingEmojiAfterASpaceMovesToo() = assertEquals("🚶 Stroll", tagChipText("Stroll 🚶"))

    @Test
    fun aLeadingEmojiStaysWhereItIs() = assertEquals("🎨 Hobbies", tagChipText("🎨 Hobbies"))

    @Test
    fun anEmojiTouchingTheFirstLetterIsLeftAlone() = assertEquals("🚽Toilet", tagChipText("🚽Toilet"))

    @Test
    fun aPlainNameIsUnchanged() = assertEquals("Office Day", tagChipText("Office Day"))
}
