package com.riverrunner.engine

import com.riverrunner.TextFiles
import com.riverrunner.data.HistoryStore
import kotlin.test.*
import kotlin.random.Random

class HistoryRetentionTest {
    private class Files : TextFiles {
        val data = mutableMapOf<String,String>()
        override fun exists(name: String) = name in data
        override fun read(name: String) = data[name]
        override fun write(name: String, text: String) { data[name] = text }
        override fun append(name: String, text: String) { data[name] = (data[name] ?: "") + text }
        override fun delete(name: String) { data.remove(name) }
        override fun rename(from: String, to: String) { data.remove(from)?.let { data[to] = it } }
    }
    private fun hand(): HandRecord {
        val e=HandEngine(HandSetup(50,100,0,0,listOf(SeatSetup(0,"H",10000),SeatSetup(1,"B",10000))),RandomCardSource(Random(8)))
        e.apply(Move.FOLD)
        return HandRecord.from(e,GameMode.CASH,1,0,2,emptyMap())
    }
    private fun checkSaved(store: HistoryStore, files: Files) {
        assertEquals(store.all().joinToString("") { it.encode()+"\n" },files.read("hands"))
        assertEquals(store.all().map { it.encode() },HistoryStore(files,"hands").all().map { it.encode() })
    }
    @Test fun longHistoryPreservesFavoritesAndCachedEncoding() {
        val files=Files();var store=HistoryStore(files,"hands");val base=hand()
        repeat(2500) { n ->
            store.add(base.copy(id=n.toLong(),favorite=n%100==0))
            if(n%250==249) checkSaved(store,files)
        }
        assertEquals(1000,store.all().count { !it.favorite })
        assertEquals(25,store.all().count { it.favorite })
        store.setFavorite(0,false);checkSaved(store,files)
        store.add(base.copy(id=2500));assertNull(store.all().find { it.id==0L })
        store.toggleFavorite(100);checkSaved(store,files)
        store.setFavorite(100,true);checkSaved(store,files)
        store.setFavorite(100,true);checkSaved(store,files)
        store=HistoryStore(files,"hands")
        store.add(base.copy(id=2501));checkSaved(store,files)
        store.clearNonFavorites();checkSaved(store,files)
        assertEquals(24,store.count());assertTrue(store.all().all { it.favorite })
    }
    @Test fun legacyInvalidLinesAndDuplicateIdsDoNotCorruptCachedRows() {
        val files=Files();val a=hand().copy(id=1);val b=a.copy(handNumber=2)
        files.write("hands",a.encode()+"\ninvalid\n"+b.encode()+"\n")
        val store=HistoryStore(files,"hands");assertEquals(2,store.count())
        store.toggleFavorite(1);checkSaved(store,files)
        assertTrue(store.all()[0].favorite);assertFalse(store.all()[1].favorite)
    }
}
