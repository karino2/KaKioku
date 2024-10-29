package io.github.karino2.kakioku

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
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

    private var qbmp : Bitmap? = null
    private var abmp : Bitmap? = null

    private lateinit var dirUrl : Uri
    private val deckDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception(getString(R.string.label_cant_open_dir))
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

    private val cardIO by lazy { CardIO(contentResolver) }
    private fun createId() = Date().time

    private fun saveCard(qbmp: Bitmap, abmp: Bitmap) {
        val id = createId()
        val qname = "${id}_Q.png"
        val aname = "${id}_A.png"
        val dname = "${id}_D.txt"

        cardIO.savePng(deckDir, qbmp, qname)
        cardIO.savePng(deckDir, abmp, aname)

        val dfile = deckDir.createFile("text/plain", dname) ?: throw Exception(getString(R.string.label_cant_open_file) + " $dname")
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
        val canUndoState = remember { mutableStateOf(false) }
        val canRedoState = remember { mutableStateOf(false) }
        val undoCount = remember { mutableStateOf(0) }
        val redoCount = remember { mutableStateOf(0) }

        Row(verticalAlignment= Alignment.CenterVertically) {
            Text(label, fontSize=30.sp)
            Spacer(modifier= Modifier.width(10.dp))
            Button(onClick = onClear) {
                Text(stringResource(R.string.label_clear))
            }
            Spacer(modifier= Modifier.width(10.dp))
            IconButton(onClick = { undoCount.value = undoCount.value+1 }, enabled = canUndoState.value) {
                Icon(Icons.Filled.Undo, contentDescription = "undo")
            }
            IconButton(onClick = { redoCount.value = redoCount.value+1 }, enabled = canRedoState.value) {
                Icon(Icons.Filled.Redo, contentDescription = "redo")
            }
        }
        BoxWithConstraints() {
            AndroidView(modifier = Modifier
                .size(maxWidth, maxHeight),
                factory = {context ->
                    DrawingCanvas(context, backgroundBmp, foregroundBmp).apply {
                        setOnUpdateListener(onUpdateBmp)
                        setStrokeColor(penColor.toArgb())
                        setOnUndoStateListener { undo, redo ->
                            canUndoState.value = undo
                            canRedoState.value = redo
                        }
                        clipToOutline = true
                    }
                },
                update = {
                    it.clearCanvas(clearCount)
                    backgroundBmp?.let { bg ->
                        it.maybeNewBackground(bg)
                    }
                    it.undo(undoCount.value)
                    it.redo(redoCount.value)
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
            ClearableCanvas(colors.qColor, stringResource(R.string.label_question), clearQCount.value, { clearQCount.value += 1 }, onQuestionBmpUpdate, foregroundBmp = questionFg)
        }
        Divider(color= Color.Blue, thickness = 2.dp)
        Box(modifier= Modifier.weight(1f)) {
            ClearableCanvas(colors.aColor, stringResource(R.string.label_answer), clearACount.value, { clearACount.value += 1 }, onAnswerBmpUpdate, foregroundBmp = answerFg)
        }
        BottomNavigation {
            BottomNavigationItem(
                icon = { Icon(Icons.Filled.Save, contentDescription = "Save")},
                label = { Text(stringResource(R.string.label_save)) },
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