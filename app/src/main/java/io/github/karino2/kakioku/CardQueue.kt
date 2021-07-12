package io.github.karino2.kakioku

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*

data class CardData(val question: Bitmap, val answer: Bitmap, val level: Int) {
    fun nextLevelNormal() = when(level) {
        0->2
        1->3
        else -> minOf(8, level+1)
    }
    fun nextLevelHard() = maxOf(3, level-1)
    fun nextLevelRetry() = 1
}

data class CardDataSource(val id: String, val question: DocumentFile, val answer: DocumentFile,
                          val data: DocumentFile, val date: Date, val level: Int) {
    companion object {
        /*
        * level
        * 0: initial.
        * 1: fail, retry
        * 2: initial next,
        * 3: normal next
        * 4: ....
        *
        * initial path
        * 0->2->3->4...
        *
        * fail retry path
        * 1->3->4
         */

        fun levelToIntervalMin(level: Int) = when(level) {
            0 -> 0
            1 -> 0
            2 -> 10
            3 -> (60*24)
            4 -> (60*24)*2
            5 -> (60*24)* 7
            6 -> (60*24)* 14
            7 -> (60*24)* 20
            else -> (60*24)* 30
        }
    }

    val isFire : Boolean
        get() {
            if (level == 0 || level == 1)
                return true

            val now = Date().time
            val diffMs = now - date.time

            // future. strange situation.
            // level is not 0 and answered in the future.
            // I regard this case as already answered.
            if (diffMs < 0)
                return false

            val diffMin = diffMs / (1000*60)
            val nextInterval = levelToIntervalMin(level)
            return diffMin > nextInterval
        }

    fun copyWithLevel(level: Int) = copy(level=level, date = Date())
}

class CardIO(val resolver: ContentResolver) {
    private fun loadBitmap(file: DocumentFile) : Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor)
        }
    }

    fun loadCard(src: CardDataSource) : CardData {
        val qbmp = loadBitmap(src.question)
        val abmp = loadBitmap(src.answer)
        return CardData(qbmp, abmp, src.level)
    }

    fun updateData(newCardData: CardDataSource) {
        val level = newCardData.level
        val date = Date().time
        resolver.openOutputStream(newCardData.data.uri, "wt").use {
            BufferedWriter(OutputStreamWriter(it)).use { bw ->
                bw.write("$level,$date")
            }
        }
    }
}

class DeckParser(val dir: DocumentFile, val resolver: ContentResolver) {
    data class TmpCardDataSource(val id: String, val question: DocumentFile? = null, val answer: DocumentFile? = null, val data: DocumentFile? = null,
                                 val date: Date? = null, val level: Int = 0) {
        val isValid : Boolean
            get() = question != null && answer != null && data!= null && date != null
    }

    val cardDict = mutableMapOf<String, TmpCardDataSource>()

    fun splitFileName(name: String) : Pair<String, String>? {
        val sep = name.lastIndexOf("_")
        if (sep == -1)
            return null
        return Pair(name.substring(0, sep), name.substring(sep+1))
    }

    fun parseData(dfile: DocumentFile) : Pair<Int, Date> {
        resolver.openInputStream(dfile.uri).use {
            BufferedReader(InputStreamReader(it)).use { br->
                val arr = br.readLine().split(",")
                return Pair(arr[0].toInt(), Date(arr[1].toLong()))
            }
        }
    }

    fun listFiles() {
        dir.listFiles().forEach { file->
            splitFileName(file.name!!)?.let { (id, suffix)->
                when(suffix) {
                    "Q.png" -> {
                        cardDict[id] = cardDict[id]?.copy(question = file) ?: TmpCardDataSource(id, question=file)
                    }
                    "A.png" -> {
                        cardDict[id] = cardDict[id]?.copy(answer = file) ?: TmpCardDataSource(id, answer=file)
                    }
                    "D.txt" -> {
                        val (level, date) = parseData(file)
                        cardDict[id] = cardDict[id]?.copy(data=file, level = level, date=date) ?: TmpCardDataSource(id, data=file, level = level, date=date)
                    }
                    else -> {}
                }
            }
        }
    }

    fun filterValidCardList() = cardDict.values.filter { it.isValid }.map { CardDataSource(it.id, it.question!!, it.answer!!, it.data!!, it.date!!, it.level) }

}

class CardQueue(val cards: List<CardDataSource>) {
    val target = mutableListOf<CardDataSource>()
    val rest = mutableListOf<CardDataSource>().apply{ addAll(cards) }

    fun setup() {
        target.addAll( rest.filter { it.isFire } )
        rest.removeAll{ it.isFire}
        target.shuffle()
    }

    val isEnd : Boolean
        get() = target.isEmpty()

    fun pop() : CardDataSource {
        val result = target[0]
        target.removeAt(0)
        return result
    }

    fun pushRest(card: CardDataSource) = rest.add(card)
}