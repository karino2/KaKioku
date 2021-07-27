package io.github.karino2.kakioku

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CardListActivity : ComponentActivity() {
    private lateinit var dirUrl : Uri
    private val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception("Cant open dir.")
    }

    private fun listValidCard() : List<CardDataSource> {
        val deckParser = DeckParser(deckDir, contentResolver)
        deckParser.listFiles()
        return deckParser.filterValidCardList()
    }

    private val cardList = MutableLiveData<List<CardDataSource>>()

    private val cardIO by lazy {
        CardIO(contentResolver)
    }

    private var requireReload = false

    override fun onStart() {
        super.onStart()

        if(requireReload) {
            reloadCardList()
        }
    }

    private val cardHeightDP by lazy {
        val metrics = DisplayMetrics()
        // display.getRealMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // density:
        // Thus on a 160dpi screen this density value will be 1; on a 120 dpi screen it would be .75; etc.
        //
        // dpi        density
        // 160: 120 = 1:0.75
        // 160: 200 = 1:1.25

        // for example, width is 800px case. what is the dip of width?
        // 800px = 800/density dip

        // realWidth = width
        // realHeight = height*0.8/2

        // cardWidth = width/2
        // cardHeight = height*0.8/4


        (metrics.heightPixels*0.2/metrics.density).dp
    }

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
        }

        reloadCardList()

        setContent {
            Column {
                TopAppBar(title = { Text(deckDir.name!!) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    })
                CardList(cardList, cardIO, cardHeightDP) {card->
                    Intent(this@CardListActivity, EditCardActivity::class.java).also {
                        it.data = dirUrl
                        it.putExtra("ID_KEY", card.id)
                        requireReload = true
                        startActivity(it)
                    }
                }
            }
        }
    }

    private fun reloadCardList() {
        cardList.value = listValidCard()
        requireReload = false
    }
}

val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }

@ExperimentalMaterialApi
@Composable
fun CardList(cards: LiveData<List<CardDataSource>>, cardIO: CardIO, cardHeightDP: Dp, onClick: (card: CardDataSource)->Unit) {
    val cardsState = cards.observeAsState()
    val loaderScope = rememberCoroutineScope()
    LazyColumn {
        cardsState.value?.let { cardList ->
            items(cardList) { cardSource->
                Card(border = BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Black), onClick = { onClick(cardSource) }) {
                    Row(modifier=Modifier.height(IntrinsicSize.Min)) {
                        val qImageState = remember { mutableStateOf<Bitmap>(blankBitmap) }
                        val aImageState = remember { mutableStateOf<Bitmap>(blankBitmap) }
                        loaderScope.launch(Dispatchers.IO) {
                            val loadedCard = cardIO.loadCard(cardSource)
                            qImageState.value = loadedCard.question
                            aImageState.value = loadedCard.answer
                        }
                        Box(modifier= Modifier.height(cardHeightDP).weight(1f).padding(5.dp, 10.dp)) {
                            Image(qImageState.value.asImageBitmap(), "Question Image")
                        }
                        Divider(color= androidx.compose.ui.graphics.Color.LightGray, thickness = 2.dp, modifier= Modifier.fillMaxHeight().width(2.dp))
                        Box(modifier= Modifier.height(cardHeightDP).weight(1f).padding(5.dp, 10.dp)) {
                            Image(aImageState.value.asImageBitmap(), "Answer Image")
                        }
                    }
                }
            }
        }
    }

}