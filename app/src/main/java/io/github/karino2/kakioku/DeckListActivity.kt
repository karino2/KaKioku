package io.github.karino2.kakioku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource

class DeckListActivity : ComponentActivity() {
    private var _url : Uri? = null

    private val getRootDirUrl = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->
        // if cancel, null coming.
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            writeLastUri(it)
            openRootDir(it)
        }
    }

    private val files = MutableLiveData(emptyList<DocumentFile>())
    private val cardStats = MutableLiveData(emptyList<String>())

    private fun openRootDir(url: Uri) {
        _url = url
        reloadDeckList(url)
    }

    private fun reloadDeckList(url: Uri) {
        val newFiles = listFiles(url)

        cardStats.value = newFiles.map { "N/N" }
        files.value = newFiles
        startLoadCardStats()
    }

    private fun listFiles(url: Uri): List<DocumentFile> {
        val rootDir = DocumentFile.fromTreeUri(this, url) ?: throw Exception(getString(R.string.label_cant_open_dir))
        if (!rootDir.isDirectory)
            throw Exception(getString(R.string.label_not_directory))
        return rootDir.listFiles()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
    }

    override fun onStart() {
        super.onStart()

        // return from other activity, etc.
        if(true == files.value?.isNotEmpty()) {
            reloadDeckList(_url!!)
        }
    }

    private fun startLoadCardStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val newStats = files.value!!.map {
                loadFireCount(it)
            }
            withContext(Dispatchers.Main) {
                cardStats.value = newStats
            }
        }
    }

    private fun showMessage(msg: String) = DeckParser.showMessage(this, msg)

    private fun addNewDeck(newDeckName: String) {
        val rootDir = _url?.let { DocumentFile.fromTreeUri(this, it) } ?: throw Exception(getString(R.string.label_cant_open_dir))
        try {
            rootDir.createDirectory(newDeckName)
            openRootDir(_url!!)
        } catch(_: Exception) {
            showMessage(getString(R.string.label_cant_create_deck_directory) + " ($newDeckName).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            Column {
                val showDialog = rememberSaveable { mutableStateOf(false) }
                TopAppBar(title={Text(getString(R.string.label_deck_list))}, actions = {
                    IconButton(onClick={ showDialog.value = true }) {
                        Icon(Icons.Filled.Add, "New Deck")
                    }
                    IconButton(onClick={ getRootDirUrl.launch(null) }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                })
                if (showDialog.value) {
                    NewDeckPopup(onNewDeck = { addNewDeck(it) }, onDismiss= { showDialog.value = false })
                }
                DeckList(files, cardStats,
                    { dir ->
                        Intent(this@DeckListActivity, QAActivity::class.java).also {
                            it.data = dir.uri
                            startActivity(it)
                        }
                    },
                    { dir ->
                        Intent(this@DeckListActivity, AddCardActivity::class.java).also {
                            it.data = dir.uri
                            startActivity(it)
                        }
                    },
                    { dir ->
                        Intent(this@DeckListActivity, CardListActivity::class.java).also {
                            it.data = dir.uri
                            startActivity(it)
                        }
                    }
                )
            }
        }

        try {
            lastUri?.let {
                return openRootDir(it)
            }
        } catch(_: Exception) {
            showMessage(getString(R.string.label_cant_open_dir_please_reopen))
        }
        getRootDirUrl.launch(null)
    }

    private val lastUri: Uri?
        get() = DeckParser.lastUriStr(this)?.let { Uri.parse(it) }

    private fun writeLastUri(uri: Uri) = DeckParser.writeLastUriStr(this, uri.toString())

    private fun loadFireCount(deckDir: DocumentFile) : String {
        val deckParser = DeckParser(deckDir, contentResolver)
        deckParser.listFiles()
        val cardList = deckParser.filterValidCardList()
        val total = cardList.size
        val fireNum = cardList.filter{ it.isFire }.size
        return "$fireNum/$total"
    }
}

@Composable
fun NewDeckPopup(onNewDeck : (deckName: String)->Unit, onDismiss: ()->Unit) {
    var textState by remember { mutableStateOf("") }
    val requester = FocusRequester()
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                TextField(value = textState, onValueChange={textState = it}, modifier= Modifier
                    .fillMaxWidth()
                    .focusRequester(requester),
                placeholder = { Text(stringResource(R.string.label_new_deck_name))})
                DisposableEffect(Unit) {
                    requester.requestFocus()
                    onDispose {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick= {
                onDismiss()
                if(textState != "") {
                    onNewDeck(textState)
                }
            }) {
                Text(stringResource(R.string.label_create))
            }
        },
        dismissButton = {
            TextButton(onClick= onDismiss) {
                Text(stringResource(android.R.string.cancel))
                Spacer(modifier = Modifier.width(5.dp))
            }
        }

    )
}

@Composable
fun Deck(deckDir: DocumentFile, cardStats: String,  onOpenDeck : ()->Unit, onAddCards: () -> Unit, onCardList: () -> Unit) {
    val expanded = remember { mutableStateOf(false) }
    Card(border= BorderStroke(2.dp, Color.Black)) {
        Row(modifier= Modifier
            .clickable(onClick = onOpenDeck)
            .padding(5.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(deckDir.name!!, fontSize = 20.sp, modifier=Modifier.weight(8f))

            Row(modifier=Modifier.weight(2.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(cardStats, fontSize = 20.sp)
                Box {
                    IconButton(onClick= {expanded.value = true}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Deck menu")
                    }
                    DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                        DropdownMenuItem(onClick = {
                            expanded.value = false
                            onAddCards()
                        }) {
                            Text(stringResource(R.string.label_add_cards))
                        }
                        DropdownMenuItem(onClick = {
                            expanded.value = false
                            onCardList()
                        }) {
                            Text(stringResource(R.string.label_view_card_list))
                        }
                    }
                }

            }

        }
    }

}

@Composable
fun DeckList(deckDirs: LiveData<List<DocumentFile>>, cardStats: LiveData<List<String>>, gotoDeck : (dir: DocumentFile)->Unit, gotoAddCards : (dir: DocumentFile)->Unit, gotoCardList: (dir:DocumentFile)->Unit) {
    val deckListState = deckDirs.observeAsState(emptyList())
    val cardStatsState = cardStats.observeAsState(emptyList())

    Column(modifier= Modifier
        .padding(10.dp)
        .verticalScroll(rememberScrollState())) {
        deckListState.value.forEachIndexed { index, dir->
            Deck(dir, cardStatsState.value[index], { gotoDeck(dir) }, { gotoAddCards(dir) }, { gotoCardList(dir) })
        }
    }
}