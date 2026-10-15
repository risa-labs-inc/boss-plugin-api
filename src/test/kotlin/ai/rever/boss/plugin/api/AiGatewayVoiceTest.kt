package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `quota is optional on results and defaults to unknown on the interface`() {
        assertEquals(null, AiTranscription("hi").quota)
        assertEquals(null, AiSpeechChunk.Completed(2).quota)
        val q = AiVoiceQuota(null, null, 90, "2026-11-01T00:00:00Z")
        assertEquals(90, AiTranscription("hi", 3, q).quota?.remainingSeconds)
        assertEquals("2026-11-01T00:00:00Z", AiSpeechChunk.Completed(2, q).quota?.resetsAt)
        val voice = object : AiGatewayVoiceAPI {
            override suspend fun transcribe(request: AiTranscriptionRequest): Result<AiTranscription> = Result.success(AiTranscription(""))
            override fun speak(request: AiSpeechRequest): Flow<AiSpeechChunk> = emptyFlow()
        }
        assertEquals(null, runBlocking { voice.quota(emptyMap()) }.getOrThrow())
    }
}
