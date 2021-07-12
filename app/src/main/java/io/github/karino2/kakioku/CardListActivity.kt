package io.github.karino2.kakioku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CardListActivity : ComponentActivity() {
    lateinit var dirUrl : Uri
    val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception("Cant open dir.")
    }

    val cardList by lazy {
        val deckParser = DeckParser(deckDir, contentResolver)
        deckParser.listFiles()
        mutableListOf<CardDataSource>().apply { addAll( deckParser.filterValidCardList() ) }
    }

    val cardIO by lazy {
        CardIO(contentResolver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
        }


        setContent {
            CardList(cardList, cardIO)
        }
    }
}

val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }


@Composable
fun CardList(cards: List<CardDataSource>, cardIO: CardIO) {
    val loaderScope = rememberCoroutineScope()
    LazyColumn {
        items(cards) { cardSource->
            Card(border = BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Black)) {
                Row(modifier=Modifier.height(IntrinsicSize.Min)) {
                    val qImageState = remember { mutableStateOf<Bitmap>(blankBitmap) }
                    val aImageState = remember { mutableStateOf<Bitmap>(blankBitmap) }
                    loaderScope.launch(Dispatchers.IO) {
                        val loadedCard = cardIO.loadCard(cardSource)
                        qImageState.value = loadedCard.question
                        aImageState.value = loadedCard.answer
                    }
                    Box(modifier= Modifier.height(250.dp).weight(1f).padding(5.dp, 10.dp)) {
                        Image(qImageState.value.asImageBitmap(), "Question Image")
                    }
                    Divider(color= androidx.compose.ui.graphics.Color.LightGray, thickness = 2.dp, modifier= Modifier.fillMaxHeight().width(2.dp))
                    Box(modifier= Modifier.height(250.dp).weight(1f).padding(5.dp, 10.dp)) {
                        Image(aImageState.value.asImageBitmap(), "Answer Image")
                    }
                }
            }
        }

    }

}