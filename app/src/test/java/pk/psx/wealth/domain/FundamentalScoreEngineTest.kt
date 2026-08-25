package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FundamentalScoreEngineTest {
    private val engine = FundamentalScoreEngine()

    @Test
    fun `missing data reduces confidence and is not scored as zero`() {
        val result = engine.score(null, mapOf("PE" to BigDecimal("10")))
        assertNotNull(result.score)
        assertEquals(0, BigDecimal("0.25").compareTo(result.confidence))
        assertEquals(80, result.score?.toInt())
        assertTrue(ScoreComponent.QUALITY in result.missingComponents)
    }

    @Test
    fun `sector chooses transparent specialist profile`() {
        val result = engine.score("Commercial Banks", mapOf("PB" to BigDecimal("1.1"), "ROE" to BigDecimal("22")))
        assertEquals("Banks", result.profile)
        assertEquals(2, result.components.size)
        assertTrue(result.components.all { it.evidence.isNotEmpty() })
    }
}
