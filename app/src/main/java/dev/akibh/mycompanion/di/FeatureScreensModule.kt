package dev.akibh.mycompanion.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.akibh.mycompanion.Screen
import dev.akibh.mycompanion.autoreply.ui.AutoReplyRoute
import dev.akibh.mycompanion.autoreply.ui.MessagingSelfTestRoute
import dev.akibh.mycompanion.autoreply.ui.ReplyLogRoute
import dev.akibh.mycompanion.context.ui.PlaceEditorRoute
import dev.akibh.mycompanion.context.ui.PlacesRoute
import dev.akibh.mycompanion.context.ui.RuleEditorRoute
import dev.akibh.mycompanion.context.ui.RuleHistoryRoute
import dev.akibh.mycompanion.context.ui.RulesRoute
import dev.akibh.mycompanion.voice.ui.VoiceRoute

/** The feature modules' screens, placed in the app's navigation. */
@Module
@InstallIn(SingletonComponent::class)
object FeatureScreensModule {
    // ---- Context rules (Phase 1) ----

    @Provides @IntoSet
    fun rules() = Screen("rules", "Context rules") { s ->
        RulesRoute(
            onOpenRule = { id -> s.navigate("rule/${id ?: NEW}") },
            onOpenPlaces = { s.navigate("places") },
            onOpenHistory = { s.navigate("rule-history") },
            modifier = s.modifier,
        )
    }

    @Provides @IntoSet
    fun ruleEditor() = Screen("rule/{id}", "Rule") { s -> RuleEditorRoute(onDone = s.back, modifier = s.modifier) }

    @Provides @IntoSet
    fun places() = Screen("places", "Saved places") { s ->
        PlacesRoute(onOpenPlace = { id -> s.navigate("place/${id ?: NEW}") }, modifier = s.modifier)
    }

    @Provides @IntoSet
    fun placeEditor() = Screen("place/{id}", "Place") { s -> PlaceEditorRoute(onDone = s.back, modifier = s.modifier) }

    @Provides @IntoSet
    fun ruleHistory() = Screen("rule-history", "Rule history") { s -> RuleHistoryRoute(s.modifier) }

    // ---- Messaging (Phase 2) ----

    @Provides @IntoSet
    fun autoReply() = Screen("autoreply", "Auto-reply") { s ->
        AutoReplyRoute(onOpenLog = {
            s.navigate("reply-log")
        }, onOpenSelfTest = { s.navigate("chat-selftest") }, modifier = s.modifier)
    }

    @Provides @IntoSet
    fun replyLog() = Screen("reply-log", "Reply log") { s -> ReplyLogRoute(s.modifier) }

    @Provides @IntoSet
    fun chatSelfTest() = Screen("chat-selftest", "Chat app self-test") { s -> MessagingSelfTestRoute(s.modifier) }

    @Provides @IntoSet
    fun voice() = Screen("voice", "Voice mode") { s -> VoiceRoute(s.modifier) }

    /** Route argument for "create a new one" (not a number, so the editor starts empty). */
    private const val NEW = "new"
}
