package dev.zephyr.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeNamesTest {
    @Test fun multiplierIsReadInEitherSpelling() {
        assertEquals(1.0, multiplierOf("[vless]香港|移动|x1")!!, 0.0)
        assertEquals(0.5, multiplierOf("[vless]日本|软银|1G竞技场|x0.5")!!, 0.0)
        assertEquals(25.0, multiplierOf("[ss]香港|砖线1|x25")!!, 0.0)
        assertEquals(1.0, multiplierOf("🇳🇱【荷兰】线路1 | 1x")!!, 0.0)
        assertEquals(2.0, multiplierOf("✨ [顶级][2X]龙泉 美国 05 · VLESS")!!, 0.0)
        assertEquals(2.0, multiplierOf("香港 01 ×2")!!, 0.0)
        assertEquals(1.5, multiplierOf("日本 倍率：1.5")!!, 0.0)
        assertEquals(0.28, multiplierOf("🇸🇬新加坡 04 直连 | 0.28")!!, 0.0)
        assertNull(multiplierOf("香港 | 01"))
    }

    @Test fun protocolNamesAreNotMultipliers() {
        assertNull(multiplierOf("[Hy2]美国|家宽|Cox"))
        assertNull(multiplierOf("香港 10 Xray"))
        assertNull(multiplierOf("[vless]香港|三网1"))
        assertEquals(6.0, multiplierOf("[Hy2]美国|家宽|DMIT1|移动|联通|x6")!!, 0.0)
    }

    @Test fun twoLetterCodesOnlyMatchWholeWords() {
        assertEquals("KR", regionOf("韩国 Plus 01").code)
        assertEquals("RU", regionOf("Russia 01").code)
        assertEquals("DE", regionOf("德国 Business").code)
        assertEquals("ES", regionOf("🇪🇸 Spain").code)
        assertEquals("TR", regionOf("TR-Reality").code)
        assertEquals("HK", regionOf("miku hk").code)
    }

    @Test fun flagsNameEveryCountry() {
        assertEquals("MY", regionOf("🇲🇾马来 增强 | 0.4").code)
        assertEquals("NL", regionOf("🇳🇱【荷兰】线路1 | 1x").code)
        assertEquals("TH", regionOf("🇹🇭泰国·曼谷|原生-2").code)
        assertEquals("UK", regionOf("🇬🇧英国 9929 | 0.25").code)
        val vietnam = regionOf("🇻🇳 胡志明 01")
        assertEquals("VN", vietnam.code)
        assertTrue(vietnam.name.isNotBlank())
    }

    @Test fun providerNoticesAreRecognised() {
        listOf(
            "剩余流量：491.1 GB", "套餐到期：2026-09-30", "距离下次重置剩余：10 天",
            "官网 example.top", "🔔 状态：✅ _ 余额：7.5元", "⌛ Subscription expired",
            "🛠️有超时请重启网络", "新域名：https://traffic.example.us.ci",
        ).forEach { assertTrue(it, isInfoNode(it)) }
        listOf("账户欠费后可用 0.2", "[vless]香港|移动|x1", "233boy-reality", "BO-Comteco1").forEach {
            assertFalse(it, isInfoNode(it))
        }
    }

    @Test fun everySearchWordMustAppear() {
        assertTrue(matchesQuery("[vless]香港|家宽|x1", "香港 家宽"))
        assertFalse(matchesQuery("[vless]香港|移动|x1", "香港 家宽"))
        assertTrue(matchesQuery("[vless]日本|软银|x0.5", "0.5x"))
        assertTrue(matchesQuery("🇳🇱【荷兰】线路1 | 1x", "x1"))
        assertFalse(matchesQuery("[ss]香港|砖线1|x25", "x2"))
        assertTrue(matchesQuery("anything", "  "))
    }
}
