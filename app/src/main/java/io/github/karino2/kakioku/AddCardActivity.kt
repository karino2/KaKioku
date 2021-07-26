package io.github.karino2.kakioku

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import io.github.karino2.kakioku.ui.theme.CardColors
import io.github.karino2.kakioku.ui.theme.KaKiokuTheme
import io.github.karino2.kakioku.ui.theme.normalColors
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.*

class AddCardActivity : ComponentActivity() {

    var qbmp : Bitmap? = null
    var abmp : Bitmap? = null

    lateinit var dirUrl : Uri
    val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception("Cant open dir.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
        }


        val colors = normalColors()
        setContent {
            KaKiokuTheme {
                Column {
                    TopAppBar(title = { Text(deckDir.name!!) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        })
                    AddCard(colors, { qbmp = it }, { abmp = it }, {
                        saveCard(qbmp!!, abmp!!)
                    })
                }
            }
        }
    }

    val cardIO by lazy { CardIO(contentResolver) }
    fun createId() = Date().time

    private fun saveCard(qbmp: Bitmap, abmp: Bitmap) {
        val id = createId()
        val qname = "${id}_Q.png"
        val aname = "${id}_A.png"
        val dname = "${id}_D.txt"

        cardIO.savePng(deckDir, qbmp, qname)
        cardIO.savePng(deckDir, abmp, aname)

        val dfile = deckDir.createFile("text/plain", dname) ?: throw Exception("Can't create file $dname")
        createDataFile(id, dfile)

    }

    private fun createDataFile(id: Long, dfile: DocumentFile) {
        contentResolver.openOutputStream(dfile.uri, "wt").use {
            BufferedWriter(OutputStreamWriter(it)).use { bw ->
                bw.write("0,$id")
            }
        }
    }
}

@Composable
fun ClearableCanvas(penColor: Color, label: String, clearCount: Int, onClear: ()->Unit, onUpdateBmp: (bmp: Bitmap)->Unit, foregroundBmp: Bitmap? = null, backgroundBmp: Bitmap? = null) {
    Column {
        Row(verticalAlignment= Alignment.CenterVertically) {
            Text(label, fontSize=30.sp)
            Spacer(modifier= Modifier.width(10.dp))
            Button(onClick = onClear) {
                Text("Clear")
            }
        }
        BoxWithConstraints() {
            AndroidView(modifier = Modifier
                .size(maxWidth, maxHeight),
                factory = {context ->
                    DrawingCanvas(context, backgroundBmp, foregroundBmp).apply {
                        setOnUpdateListener(onUpdateBmp)
                        setStrokeColor(penColor.toArgb())
                    }
                },
                update = {
                    it.clearCanvas(clearCount)
                    backgroundBmp?.let { bg ->
                        it.maybeNewBackground(bg)
                    }
                }
            )

        }
    }

}

@Composable
fun AddCard(colors: CardColors, onQuestionBmpUpdate: (bmp: Bitmap)->Unit, onAnswerBmpUpdate: (bmp:Bitmap)->Unit, onSave: ()->Unit) {
    EditCard(colors, null, null, onQuestionBmpUpdate, onAnswerBmpUpdate, onSave)
}

@Composable
fun EditCard(colors: CardColors, questionFg: Bitmap?, answerFg: Bitmap?, onQuestionBmpUpdate: (bmp: Bitmap)->Unit, onAnswerBmpUpdate: (bmp:Bitmap)->Unit, onSave: ()->Unit) {
    Column(modifier= Modifier.fillMaxHeight()) {
        val clearQCount = remember { mutableStateOf(0) }
        val clearACount = remember { mutableStateOf(0) }
        Box(modifier= Modifier.weight(1f)) {
            ClearableCanvas(colors.qColor, "Question", clearQCount.value, { clearQCount.value += 1 }, onQuestionBmpUpdate, foregroundBmp = questionFg)
        }
        Divider(color= Color.Blue, thickness = 2.dp)
        Box(modifier= Modifier.weight(1f)) {
            ClearableCanvas(colors.aColor, "Answer", clearACount.value, { clearACount.value += 1 }, onAnswerBmpUpdate, foregroundBmp = answerFg)
        }
        BottomNavigation {
            BottomNavigationItem(
                icon = { Icon(Icons.Filled.Save, contentDescription = "Save")},
                label = { Text("Save") },
                onClick = {
                    onSave()
                    clearQCount.value += 1
                    clearACount.value += 1
                },
                selected = false
            )
        }
    }
}