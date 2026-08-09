package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These cover the path a user reaches by pressing an AI button that then does nothing, so
 * the property that must hold unconditionally is that *explaining* a failure never becomes
 * a second failure.
 */
class AiAvailabilityTest {

    private class FakeGateway(private val model: AiModelInfo?) : AiGatewayAPI {
        override suspend fun complete(request: AiRequest) = Result.success(AiReply("x"))

        override fun stream(request: AiRequest): Flow<AiChunk> = emptyFlow()

        override suspend fun runAgent(
            request: AiRequest,
            tools: List<AiToolSpec>,
            budget: AiBudget,
            invoke: suspend (AiToolCall) -> AiToolOutcome,
        ) = Result.failure<AiAgentResult>(UnsupportedOperationException())

        override fun activeModel(): AiModelInfo? = model
    }

    /** Records what the helper asked the host to do, and can be made to misbehave. */
    private class FakeDialogs(
        private val confirm: Boolean,
        private val throwOnConfirm: Boolean = false,
    ) : GenericDialogProvider {
        val titles = mutableListOf<String>()
        var lastMessage: String = ""

        override suspend fun showConfirmationDialog(
            title: String,
            message: String,
            confirmText: String,
            cancelText: String,
            isDestructive: Boolean,
        ): Boolean {
            if (throwOnConfirm) error("the dialog host is broken")
            titles += title
            lastMessage = message
            return confirm
        }

        override suspend fun showTextInputDialog(
            title: String,
            message: String?,
            initialValue: String,
            placeholder: String,
            validation: ((String) -> String?)?,
        ): String? = null

        override suspend fun showChoiceDialog(
            title: String,
            message: String?,
            choices: List<DialogChoice>,
            selectedIndex: Int,
        ): DialogChoice? = null

        override suspend fun showMultiChoiceDialog(
            title: String,
            message: String?,
            choices: List<DialogChoiceItem>,
        ): List<DialogChoiceItem>? = null

        override suspend fun showAlertDialog(
            title: String,
            message: String,
            buttonText: String,
        ) = Unit

        override suspend fun showThreeButtonDialog(
            title: String,
            message: String,
            positiveText: String,
            negativeText: String,
            neutralText: String,
        ): DialogButton = DialogButton.NEGATIVE

        override fun showProgressDialog(
            title: String,
            message: String,
            isIndeterminate: Boolean,
            cancellable: Boolean,
        ): ProgressDialogHandle = error("not used")
    }

    private class FakeContext(
        private val gateway: Any? = null,
        private val gatewayThrows: Boolean = false,
        private val dialogs: FakeDialogs? = FakeDialogs(confirm = true),
        override val windowId: String? = "w1",
    ) : PluginContext {
        override val panelRegistry = PanelRegistry()
        override val tabRegistry = TabRegistry()
        override val pluginScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

        var openedPanel: String? = null
        var openedSection: String? = null

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getPluginAPI(apiClass: Class<T>): T? {
            if (gatewayThrows) throw NoSuchMethodError("gateway built against another api")
            return gateway as T?
        }

        override val genericDialogProvider: GenericDialogProvider? get() = dialogs

        override val panelEventProvider: PanelEventProvider
            get() =
                object : PanelEventProvider {
                    override suspend fun openPanel(
                        panelId: PanelId,
                        windowId: String,
                    ) {
                        openedPanel = panelId.panelId
                    }

                    override suspend fun closePanel(
                        panelId: PanelId,
                        windowId: String,
                    ) = Unit
                }

        override val settingsProvider: SettingsProvider
            get() =
                object : SettingsProvider {
                    override fun openSettings(
                        windowId: String,
                        section: String,
                    ) {
                        openedSection = section
                    }
                }
    }

    // ==================== which problem is it ====================

    @Test
    fun `no gateway and no provider are told apart`() {
        // The whole point of the enum: they have different fixes, so a single
        // "AI unavailable" message sends half of users to the wrong screen.
        assertEquals(AiReadiness.GATEWAY_MISSING, AiAvailability.check(FakeContext(gateway = null)))
        assertEquals(
            AiReadiness.NO_PROVIDER,
            AiAvailability.check(FakeContext(gateway = FakeGateway(model = null))),
        )
        assertEquals(
            AiReadiness.READY,
            AiAvailability.check(FakeContext(gateway = FakeGateway(AiModelInfo("O", "OpenAI", "gpt-x")))),
        )
    }

    @Test
    fun `a gateway that fails to link reads as missing`() {
        // A gateway built against another api revision is, from the user's side, the same
        // situation as not having one - and must not surface as a crash.
        assertEquals(AiReadiness.GATEWAY_MISSING, AiAvailability.check(FakeContext(gatewayThrows = true)))
    }

    // ==================== the prompt routes to the fix ====================

    @Test
    fun `a missing gateway offers the Toolbox and opens it`() =
        runBlocking {
            val ctx = FakeContext(gateway = null)

            val found = AiAvailability.promptToFix(ctx, "Fix with AI")

            assertEquals(AiReadiness.GATEWAY_MISSING, found)
            assertEquals(listOf("AI Gateway not installed"), ctx.dialogTitles())
            assertEquals("plugin-manager", ctx.openedPanel)
            assertNull(ctx.openedSection, "a missing plugin is not a settings problem")
        }

    @Test
    fun `a missing provider offers settings and opens the right section`() =
        runBlocking {
            val ctx = FakeContext(gateway = FakeGateway(model = null))

            val found = AiAvailability.promptToFix(ctx, "Cmd+K")

            assertEquals(AiReadiness.NO_PROVIDER, found)
            assertEquals(listOf("No AI provider configured"), ctx.dialogTitles())
            // The SettingsSection enum name, not the display name "AI Providers".
            assertEquals("LLM_PROVIDERS", ctx.openedSection)
            assertNull(ctx.openedPanel)
        }

    @Test
    fun `declining opens nothing`() =
        runBlocking {
            val ctx = FakeContext(gateway = null, dialogs = FakeDialogs(confirm = false))

            AiAvailability.promptToFix(ctx, "Fix with AI")

            assertNull(ctx.openedPanel)
            assertNull(ctx.openedSection)
        }

    @Test
    fun `a ready gateway shows no dialog at all`() =
        runBlocking {
            val ctx = FakeContext(gateway = FakeGateway(AiModelInfo("O", "OpenAI", "gpt-x")))

            assertEquals(AiReadiness.READY, AiAvailability.promptToFix(ctx, "Cmd+K"))

            assertEquals(emptyList(), ctx.dialogTitles(), "nothing is wrong, so say nothing")
        }

    @Test
    fun `the feature name reaches the user`() =
        runBlocking {
            // "Fix with AI needs..." is more use to someone than "this plugin needs...".
            val dialogs = FakeDialogs(confirm = false)
            val ctx = FakeContext(gateway = null, dialogs = dialogs)

            AiAvailability.promptToFix(ctx, "Fix with AI")

            assertTrue(dialogs.lastMessage.startsWith("Fix with AI needs"), dialogs.lastMessage)
        }

    // ==================== it must never throw ====================

    @Test
    fun `a host with no dialog support still reports what it found`() =
        runBlocking {
            // Degrades to "the caller shows its own message" rather than failing.
            val ctx = FakeContext(gateway = null, dialogs = null)

            assertEquals(AiReadiness.GATEWAY_MISSING, AiAvailability.promptToFix(ctx, "Cmd+K"))
        }

    @Test
    fun `a host with no windowId does not fail when accepted`() =
        runBlocking {
            // openPanel needs a window; without one the dialog still explains the problem.
            val ctx = FakeContext(gateway = null, windowId = null)

            assertEquals(AiReadiness.GATEWAY_MISSING, AiAvailability.promptToFix(ctx, "Cmd+K"))
            assertNull(ctx.openedPanel)
        }

    @Test
    fun `a throwing dialog provider does not propagate`() =
        runBlocking {
            val ctx = FakeContext(gateway = null, dialogs = FakeDialogs(confirm = true, throwOnConfirm = true))

            assertEquals(AiReadiness.GATEWAY_MISSING, AiAvailability.promptToFix(ctx, "Cmd+K"))
            assertNull(ctx.openedPanel, "a broken dialog must not be treated as consent")
        }

    private fun FakeContext.dialogTitles(): List<String> =
        (genericDialogProvider as? FakeDialogs)?.titles ?: emptyList()
}
