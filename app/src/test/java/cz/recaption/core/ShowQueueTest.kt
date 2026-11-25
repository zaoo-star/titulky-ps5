package cz.recaption.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowQueueTest {
    private fun show(v: String) = ShowQueue.Action.Show(v)

    @Test fun bezFrontyJakoDriv() {
        val q = ShowQueue<String>(0)
        assertEquals(show("a"), q.submit("a", 0))
        assertEquals(show("b"), q.submit("b", 100))
    }

    @Test fun rychleRepliky() {
        val q = ShowQueue<String>(1500)
        assertEquals(show("a"), q.submit("a", 0))
        // b a c přijdou dřív, než jde a přečíst → čekají
        assertEquals(ShowQueue.Action.None, q.submit("b", 400))
        assertEquals(ShowQueue.Action.None, q.submit("c", 900))
        assertEquals(1100L, q.dueIn(400))
        assertEquals(ShowQueue.Action.None, q.poll(1000))
        assertEquals(show("b"), q.poll(1500))
        assertEquals(ShowQueue.Action.None, q.poll(2000))
        assertEquals(show("c"), q.poll(3000))
        // anglický titulek zmizel, „nechat déle“ 1 s → schová se až po nejkratší době i prodloužení
        q.requestHide(3200)
        assertEquals(ShowQueue.Action.None, q.poll(3300))
        assertEquals(ShowQueue.Action.Hide, q.poll(4500))
        assertEquals(-1L, q.dueIn(4600))
    }

    @Test fun mezeraFrontuDozene() {
        val q = ShowQueue<String>(1500)
        q.submit("a", 0)
        q.submit("b", 200)
        q.requestHide(500)       // a už ve hře zmizel, b čeká – schovat se nesmí, dokud se neukáže b
        assertEquals(show("b"), q.poll(1500))
        assertEquals(ShowQueue.Action.None, q.poll(2000))
        // b zůstane aspoň 1,5 s a pak se schová i bez další žádosti (anglický titulek už zmizel)
        assertEquals(1000L, q.dueIn(2000))
        assertEquals(ShowQueue.Action.Hide, q.poll(3000))
        assertEquals(show("c"), q.submit("c", 5000))
    }

    @Test fun nejviceTriCekajiciAStareSeZahodi() {
        val q = ShowQueue<String>(2000, maxWaiting = 3, maxLagMs = 5000)
        q.submit("a", 0)
        listOf("b", "c", "d", "e").forEachIndexed { i, s -> q.submit(s, 100L + i) }
        assertEquals(3, q.waitingCount)              // b zahozeno
        assertEquals(show("c"), q.poll(2000))
        // d i e čekají přes 5 s → d zahozeno, e (poslední) zůstává
        assertEquals(show("e"), q.poll(9000))
    }
}
