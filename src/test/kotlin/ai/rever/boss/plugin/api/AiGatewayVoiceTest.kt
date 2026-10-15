package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiGatewayVoiceTest {
    @Test
    fun `capability constant and defaults`() {
        assertEquals("voice", AiGatewayVoiceAPI.CAPABILITY_VOICE)
        val t = AiTranscriptionRequest(byteArrayOf(1), "wav")
        assertEquals(60_000, t.timeoutMs)
        assertTrue(t.extras.isEmpty())
        assertEquals(60_000, AiSpeechRequest("hi").timeoutMs)
    }

    @Test
    fun `toString omits audio bytes and text`() {
        assertFalse("SECRET" in AiSpeechRequest("SECRET words").toString())
        assertTrue("audio.size=3" in AiTranscriptionRequest(byteArrayOf(1, 2, 3), "mp3").toString())
        assertFalse("SECRET" in AiTranscription("SECRET").toString())
    }

    @Test
    fun `exception carries error and chunks are open`() {
        val e = AiVoiceException(AiVoiceError(AiVoiceError.BUSY, "Try again."))
        assertEquals("Try again.", e.message)
        val chunks: List<AiSpeechChunk> = listOf(AiSpeechChunk.Audio(byteArrayOf()), AiSpeechChunk.Completed(2), AiSpeechChunk.Failed(e.error))
        assertEquals(3, chunks.size)
    }
}
