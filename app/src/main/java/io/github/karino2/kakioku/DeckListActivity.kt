package io.github.karino2.kakioku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DeckListActivity : ComponentActivity() {
    private var _url : Uri? = null

    val getRootDirUrl = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        contentResolver.takePersistableUriPermission(it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        openRootDir(it)
    }

    val files = MutableLiveData(emptyList<DocumentFile>())

    fun openRootDir(url: Uri) {
        _url = url
        val rootDir = DocumentFile.fromTreeUri(this, url) ?: throw Exception("Can't open dir")
        files.value = rootDir.listFiles()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeckList(files, { dir ->
                                Intent(this, QAActivity::class.java).also {
                                    it.data = dir.uri
                                    startActivity(it)
                                }
                            },
                            { dir ->
                                Intent(this, AddCardActivity::class.java).also {
                                    it.data = dir.uri
                                    startActivity(it)
                                }
                            },
                            {
                                dir ->
                                Intent(this, CardListActivity::class.java).also {
                                    it.data = dir.uri
                                    startActivity(it)
                                }
                            }
                        )
        }

        if (_url == null) {
            getRootDirUrl.launch(null)
        }
    }
}

@Composable
fun Deck(deckDir: DocumentFile, onOpenDeck : ()->Unit, onAddCards: () -> Unit, onCardList: () -> Unit) {
    var expanded = remember { mutableStateOf(false) }
    Card(border= BorderStroke(2.dp, Color.Black)) {
        Row(modifier=Modifier.clickable(onClick = onOpenDeck).padding(5.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(deckDir.name!!, fontSize = 20.sp, modifier=Modifier.weight(9f))
            Box(modifier=Modifier.weight(1f)) {
                IconButton(onClick= {expanded.value = true}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Deck menu")
                }
                DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                    DropdownMenuItem(onClick = {
                        expanded.value = false
                        onAddCards()
                    }) {
                        Text("Add Cards")
                    }
                    DropdownMenuItem(onClick = {
                        expanded.value = false
                        onCardList()
                    }) {
                        Text("Card List")
                    }
                }
            }
        }
    }

}

@Composable
fun DeckList(deckDirs: LiveData<List<DocumentFile>>, gotoDeck : (dir: DocumentFile)->Unit, gotoAddCards : (dir: DocumentFile)->Unit, gotoCardList: (dir:DocumentFile)->Unit) {
    val deckListState = deckDirs.observeAsState(emptyList())
    Column(modifier= Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
        deckListState.value.forEach {
            Deck(it, { gotoDeck(it) }, { gotoAddCards(it) }, { gotoCardList(it) })
        }
    }

}