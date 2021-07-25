package io.github.karino2.kakioku

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.documentfile.provider.DocumentFile
import io.github.karino2.kakioku.ui.theme.KaKiokuTheme
import io.github.karino2.kakioku.ui.theme.normalColors

class EditCardActivity : ComponentActivity() {
    lateinit var qbmp : Bitmap
    lateinit var abmp : Bitmap

    lateinit var dirUrl : Uri
    val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception("Cant open dir.")
    }

    lateinit var cardId: String

    val cardIO by lazy { CardIO(contentResolver) }
    val cardSource by lazy { cardIO.loadCardDataSource(deckDir, cardId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
            cardId = it.getStringExtra("ID_KEY")!!
        }

        val colors = normalColors()
        val card = cardIO.loadCard(cardSource)

        setContent {
            KaKiokuTheme {
                Column {
                    TopAppBar(title = { Text(deckDir.name!!) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        })
                    EditCard(colors, card.question, card.answer, { qbmp = it }, { abmp = it }, {
                        updateCardBmp(qbmp, abmp)
                        finish()
                    })
                }
            }
        }
    }

    private fun updateCardBmp(qbmp: Bitmap, abmp: Bitmap) {
        cardIO.savePngFile(qbmp, cardSource.question)
        cardIO.savePngFile(abmp, cardSource.answer)
    }
}