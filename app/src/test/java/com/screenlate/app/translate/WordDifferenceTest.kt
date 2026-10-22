package com.screenlate.app.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class WordDifferenceTest {
    @Test
    fun countsLikeTheConversionCheck() {
        assertEquals(0, wordDifference("Новые сообщения", "Новые сообщения"))
        assertEquals(1, wordDifference("Проверьте обновления", "Проверка обновления"))
        assertEquals(2, wordDifference("Погода завтра будет солнечной.", "Завтра погода будет солнечной."))
        assertEquals(2, wordDifference("Поделитесь этим постом с друзьями.", "Поделитесь этим постом со своими друзьями."))
    }
}
