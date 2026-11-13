package ma.aminehanout.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerTest {
    private fun ryal(s: String) = parseAmount(s, true)!!

    @Test fun conversion_is_exact() {
        assertEquals(5000L, ryal("1000"))            // 1000 ryal = 50 DH = 5000 centimes
        assertEquals(5L, ryal("1"))                  // 1 ryal = 0.05 DH
        assertEquals(100L, parseAmount("1", false))  // 1 DH
        assertEquals(1L, ryal("0.2"))
        assertEquals(30L, parseAmount("0.2", false)!! + parseAmount("0.1", false)!!) // no 0.30000000000000004
        assertNull(parseAmount("abc", true))
        assertNull(parseAmount("0", true))
        assertNull(parseAmount("-5", true))
        assertEquals(5000L, ryal("١٠٠٠"))            // Arabic-Indic digits
        assertEquals(5000L, ryal("1000,0"))          // comma decimal separator
    }

    @Test fun display_shows_both_units() {
        assertEquals("1,000 ريال (50 درهم)", fmtBoth(5000, true))
        assertEquals("50 درهم (1,000 ريال)", fmtBoth(5000, false))
        assertEquals("160 ريال (8 درهم)", fmtBoth(ryal("160"), true))
        assertEquals("12.6 ريال (0.63 درهم)", fmtBoth(63, true))
        assertEquals("0 ريال (0 درهم)", fmtBoth(0, true))
    }

    @Test fun scenario_from_spec() {
        val cid = 1L
        val txns = mutableListOf(Txn(1, cid, ryal("1000"), true))
        assertEquals(ryal("1000"), balanceOf(txns))
        // purchase with two items: 160 + 180 = 340
        val items = listOf(ryal("160"), ryal("180"))
        val purchase = items.sum()
        assertEquals(ryal("340"), purchase)
        txns.add(Txn(2, cid, purchase, true))
        assertEquals(ryal("1340"), balanceOf(txns))
        // payment of 500
        txns.add(Txn(3, cid, ryal("500"), false))
        assertEquals(ryal("840"), balanceOf(txns))
        assertEquals("840 ريال (42 درهم)", fmtBoth(balanceOf(txns), true))
    }

    @Test fun example_from_spec_1540() {
        val txns = listOf(Txn(1, 1, ryal("1000"), true), Txn(2, 1, ryal("160") + ryal("180") + ryal("200"), true))
        assertEquals(ryal("540"), txns[1].amount)
        assertEquals(ryal("1540"), balanceOf(txns))
    }

    @Test fun backup_roundtrip_with_items() {
        val c = listOf(Customer(1, "أحمد", "0612345678", 111))
        val t = listOf(Txn(10, 1, 1700, true, "n", 222), Txn(11, 1, 2500, false, "", 333))
        val i = listOf(TxnItem(1, 10, "حليب", 800), TxnItem(2, 10, "", 900))
        val b = fromJson(toJson(c, t, i))
        assertEquals(c, b.customers); assertEquals(t, b.txns); assertEquals(i, b.items)
    }

    @Test fun old_backup_format_still_restores() {
        val old = """{"customers":[{"id":1,"name":"محمد","phone":"","createdAt":5}],"txns":[{"id":1,"customerId":1,"amount":35000,"isDebt":true,"note":"x","date":6}]}"""
        val b = fromJson(old)
        assertEquals(1, b.customers.size)
        assertEquals(35000L, b.txns[0].amount)   // amounts keep their centime meaning
        assertEquals(0, b.items.size)
        assertEquals(35000L, balanceOf(b.txns))
    }
}
