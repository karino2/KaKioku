package io.github.karino2.kakioku

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import io.github.karino2.kakioku.AddCard
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
                    TopAppBar(title = { Text("title") })
                    AddCard(colors, { qbmp = it }, { abmp = it }, {
                        saveCard(qbmp!!, abmp!!)
                    })
                }
            }
        }
    }

    fun createId() = Date().time

    private fun saveCard(qbmp: Bitmap, abmp: Bitmap) {
        val id = createId()
        val qname = "${id}_Q.png"
        val aname = "${id}_A.png"
        val dname = "${id}_D.txt"
        val qfile = deckDir.createFile("image/png", qname) ?: throw Exception("Can't create file $qname")
        savePng(qbmp, qfile)

        val afile = deckDir.createFile("image/png", aname) ?: throw Exception("Can't create file $aname")
        savePng(abmp, afile)

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

    private fun savePng(bitmap: Bitmap, pngFile: DocumentFile) {
        contentResolver.openOutputStream(pngFile.uri, "wt").use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, it)
        }
    }
}

@Composable
fun ClearableCanvas(penColor: Color, label: String, clearCount: Int, onClear: ()->Unit, onUpdateBmp: (bmp: Bitmap)->Unit) {
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
                    DrawingCanvas(context, null).apply {
                        setOnUpdateListener(onUpdateBmp)
                        setStrokeColor(penColor.toArgb())
                    }
                },
                update = {
                    it.clearCanvas(clearCount)
                }
            )

        }
    }

}

@Composable
fun AddCard(colors: CardColors, onQuestionBmpUpdate: (bmp: Bitmap)->Unit, onAnswerBmpUpdate: (bmp:Bitmap)->Unit, onSave: ()->Unit) {
    Column(modifier= Modifier.fillMaxHeight()) {
        val clearQCount = remember { mutableStateOf(0) }
        val clearACount = remember { mutableStateOf(0) }
        Box(modifier= Modifier.weight(1f)) {
            ClearableCanvas(colors.qColor, "Question", clearQCount.value, { clearQCount.value += 1 }, onQuestionBmpUpdate)
        }
        Divider(color= Color.Blue, thickness = 2.dp)
        Box(modifier= Modifier.weight(1f)) {
            ClearableCanvas(colors.aColor, "Answer", clearACount.value, { clearACount.value += 1 }, onAnswerBmpUpdate)
        }
        BottomNavigation {
            BottomNavigationItem(
                icon = {},
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