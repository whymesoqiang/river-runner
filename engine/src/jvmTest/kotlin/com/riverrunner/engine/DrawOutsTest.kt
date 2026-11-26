package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.*

class DrawOutsTest {
    /** Slow card-enumeration oracle, independent of the rank lookup and packed suit counters. */
    private fun reference(hole: IntArray, board: List<Int>): Int {
        val cards=board+hole.toList()
        var outs=(0..3).count { suit -> cards.count { Cards.suit(it)==suit }==4 && hole.any { Cards.suit(it)==suit } }*9
        val ranks=cards.map { Cards.rank(it) }.toSet()
        val boardRanks=board.map { Cards.rank(it) }.toSet()
        fun straight(rs:Set<Int>):Boolean = (0..8).any { low -> (low..low+4).all { it in rs } } || listOf(12,0,1,2,3).all { it in rs }
        if(!straight(ranks)) outs+=(0..12).count { it !in ranks && straight(ranks+it) && !straight(boardRanks+it) }.coerceAtMost(2)*4
        return outs
    }
    @Test fun optimizedOutsMatchCardEnumeration() {
        val r=Random(251126)
        repeat(20000) { i ->
            val deck=Deck(r);val hole=intArrayOf(deck.draw(),deck.draw());val board=List(3+i%3) { deck.draw() }
            assertEquals(reference(hole,board),Bot.drawOuts(hole,board),"${hole.toList()} $board")
        }
    }
    @Test fun boardOnlyDrawsDoNotCountAsHoleCardDraws() {
        assertEquals(0,Bot.drawOuts(Cards.parseMany("2c7d"),Cards.parseMany("9hThJhQh").toList()))
        assertEquals(8,Bot.drawOuts(Cards.parseMany("6c7d"),Cards.parseMany("8h9sAc").toList()))
        assertEquals(9,Bot.drawOuts(Cards.parseMany("Ah7h"),Cards.parseMany("2h9hKc").toList()))
    }
}
