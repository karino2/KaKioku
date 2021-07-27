package io.github.karino2.kakioku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import io.github.karino2.kakioku.ui.theme.KaKiokuTheme
import io.github.karino2.kakioku.ui.theme.normalActionColors
import io.github.karino2.kakioku.ui.theme.normalColors


class QAActivity : ComponentActivity() {
    private lateinit var dirUrl : Uri
    private val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception(getString(R.string.label_cant_open_dir))
    }

    private val cardQueue by lazy {
        val deckParser = DeckParser(deckDir, contentResolver)
        deckParser.listFiles()
        CardQueue(deckParser.filterValidCardList()).apply { setup() }
    }

    private val cardIO by lazy { CardIO(contentResolver) }

    private val targetCard = MutableLiveData<CardData>()
    private lateinit var targetCardSource: CardDataSource

    private fun setCardDataSource(cardSrc: CardDataSource) {
        targetCardSource = cardSrc
        targetCard.value = cardIO.loadCard(cardSrc)
    }

    private var requireReload = false
    override fun onStart() {
        super.onStart()
        if(requireReload) {
            requireReload = false
            targetCard.value = cardIO.loadCard(targetCardSource)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
        }

        if (cardQueue.isEnd) {
            Toast.makeText(this, getString(R.string.label_all_cards_done), Toast.LENGTH_LONG).show()
            finish()
        }
        else {
            setCardDataSource(cardQueue.pop())
        }

        setContent {
            val cardState = targetCard.observeAsState()
            cardState.value?.let {
                Content(normalColors().writingColor,  it, deckDir.name!!, onResult = { nextLevel ->
                    val updated = targetCardSource.copyWithLevel(nextLevel)
                    cardIO.updateData(updated)
                    cardQueue.pushRest(updated)

                    if (!cardQueue.isEnd)
                        setCardDataSource(cardQueue.pop())
                    else {
                        cardQueue.setup()
                        if (!cardQueue.isEnd)
                            setCardDataSource(cardQueue.pop())
                        else
                            finish()  // all done
                    }
                }, gotoEdit= {
                    Intent(this, EditCardActivity::class.java).also { intent->
                        intent.data = dirUrl
                        intent.putExtra("ID_KEY", targetCardSource.id)
                        requireReload = true
                        startActivity(intent)
                    }
                }, onBack= { finish() })
            }
        }
    }
}

@Composable
fun RowScope.BottomButton(modifier: Modifier=Modifier, content: @Composable (BoxScope.() -> Unit)) {
    Box(modifier= modifier
        .weight(1f)
        .fillMaxHeight()
        .border(1.dp, Color.White), contentAlignment = Alignment.Center, content = content)
}

@Composable
fun Content(penColor: Color, cardData: CardData, deckName: String, onResult: (nextLevel: Int)->Unit, gotoEdit: ()->Unit, onBack: ()->Unit) {
    KaKiokuTheme {
        Column(modifier= Modifier.fillMaxHeight()) {
            TopAppBar(title = { Text(deckName) },
                actions = {
                    IconButton(onClick={gotoEdit()}) {
                        Icon(Icons.Filled.Edit, "Edit Card")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                })

            val clearCount = remember { mutableStateOf(0) }
            val peeping = remember {mutableStateOf(false) }
            val isAnswered = remember { mutableStateOf(false) }

            Box(modifier=Modifier.weight(1f)) {
                ClearableCanvas(penColor, stringResource(R.string.label_question), clearCount.value, { clearCount.value += 1 }, {}, backgroundBmp = cardData.question)
            }
            Divider(color= Color.Blue, thickness = 2.dp)
            Box(modifier=Modifier.weight(1f)) {
                Column {
                    Text(stringResource(R.string.label_answer), fontSize=30.sp)
                    BoxWithConstraints() {
                        if (peeping.value || isAnswered.value) {
                            Image(
                                bitmap=cardData.answer.asImageBitmap(),
                                modifier=Modifier.size(maxWidth, maxHeight),
                                contentDescription = null
                            )
                        }
                    }
                }
            }

            BottomNavigation {
                if (!isAnswered.value) {
                    BottomButton(modifier= Modifier
                        .selectable(
                            selected = peeping.value,
                            onClick = { peeping.value = !peeping.value }
                        )
                        .background(color = if (peeping.value) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.primary)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Peep")
                            Text(stringResource(R.string.label_peep))
                        }
                    }
                    BottomButton(modifier=Modifier.clickable { isAnswered.value = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Filled.Done, contentDescription = "Answer")
                            Text(stringResource(R.string.label_goto_answer))
                        }
                    }
                } else {
                    val onNext = fun(nextLevel:Int) {
                        onResult(nextLevel)
                        isAnswered.value = false
                    }
                    val (retryColor, hardColor, normalColor) = normalActionColors()

                    // initial or retry or initial next. Every case has the same hard-normal next level.
                    if (cardData.level == 0 ||
                        cardData.level == 1 ||
                            cardData.level == 2) {
                        val normalLevel = cardData.nextLevelNormal()
                        val normalIntervalMin = CardDataSource.levelToIntervalMin(normalLevel)
                        val nextLabel = if(cardData.level==0) "$normalIntervalMin min" else "1 day"
                        val retryLevel = if(cardData.level == 2) 0 else cardData.level

                        BottomButton(modifier= Modifier
                            .background(color = retryColor)
                            .clickable { onNext(retryLevel) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.label_retry))
                            }
                        }
                        BottomButton(modifier= Modifier
                            .background(color = normalColor)
                            .clickable { onNext(normalLevel) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(nextLabel)
                                Text(stringResource(R.string.label_normal))
                            }
                        }

                    } else {
                        val retryLevel = cardData.nextLevelRetry()
                        val hardLevel = cardData.nextLevelHard()
                        val normalLevel = cardData.nextLevelNormal()
                        val hardIntervalDays = CardDataSource.levelToIntervalMin(hardLevel)/(60*24)
                        val normalIntervalDays = CardDataSource.levelToIntervalMin(normalLevel)/(60*24)
                        val labelDays = stringResource(R.string.label_days)



                        BottomButton(modifier= Modifier
                            .background(color = retryColor)
                            .clickable { onNext(retryLevel) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.label_retry))
                            }
                        }
                        BottomButton(modifier= Modifier
                            .background(color = hardColor)
                            .clickable { onNext(hardLevel) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$hardIntervalDays$labelDays")
                                Text(stringResource(R.string.label_hard))
                            }
                        }
                        BottomButton(modifier= Modifier
                            .background(color = normalColor)
                            .clickable { onNext(normalLevel) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$normalIntervalDays$labelDays")
                                Text(stringResource(R.string.label_normal))
                            }
                        }

                    }
                }
            }
        }
    }
}