package io.github.karino2.kakioku.ui.theme

import androidx.compose.ui.graphics.Color

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)



data class CardColors(val qColor : Color, val aColor: Color, val writingColor: Color)

// Pen color of question, answer, writing.
fun normalColors() = CardColors(Color(0xFF3333FF), Color(0xFFFF3333), Color(0xFF222222))

data class ActionColors(val retry: Color, val hard: Color, val normal: Color)
fun normalActionColors() = ActionColors(retry = Color(0xFFCC0000), hard = Color(0xFF333333), normal = Color(0xFF00AA00))