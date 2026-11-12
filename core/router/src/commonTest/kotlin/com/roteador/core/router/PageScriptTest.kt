package com.roteador.core.router

import com.roteador.core.router.html.HtmlText
import com.roteador.core.router.html.PageScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageScriptTest {
    private val page = """
        <script>
            var sessionKey='123';
            var pppUptime = parseInt('456');
            var ssid_id = '0';
        var wlSsid = htmlDecode('Casa &amp; Cia');
        var wlBand = '1';
            var ssid_id = '1';
        var wlSsid = htmlDecode('Casa-5G');
        var wlBand = "2";
            var note = 'it\'s';
            var lanHostList = [['1','tv','0','192.168.0.5'],['0','','0','192.168.0.6']];
        </script>
    """.trimIndent()

    @Test
    fun readsAllValueForms() {
        val script = PageScript(page)
        assertEquals("123", script["sessionKey"])
        assertEquals("456", script["pppUptime"])
        assertEquals("Casa & Cia", script["wlSsid"])
        assertEquals("it's", script["note"])
        assertNull(script["missing"])
        assertEquals(listOf("1", "2"), script.all("wlBand"))
    }

    @Test
    fun splitsRepeatedVariablesIntoBlocks() {
        val blocks = PageScript(page).blocks("ssid_id")
        assertEquals(2, blocks.size)
        assertEquals("Casa-5G", blocks[1]["wlSsid"])
    }

    @Test
    fun parsesStringRows() {
        val rows = PageScript.stringRows(page, "lanHostList")
        assertEquals(2, rows.size)
        assertEquals(listOf("1", "tv", "0", "192.168.0.5"), rows[0])
        assertEquals("", rows[1][1])
    }

    @Test
    fun readsLabelValuePairs() {
        val html = "<td><strong>Modelo:</strong></td><td>RTF8225VW-SV</td>"
        assertEquals("RTF8225VW-SV", HtmlText.valueAfterLabel(html, "Modelo:"))
        assertNull(HtmlText.valueAfterLabel(html, "Fabricante:"))
    }
}
