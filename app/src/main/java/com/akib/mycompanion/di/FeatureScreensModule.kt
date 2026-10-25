package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.autoreply.ui.AutoReplyRoute
import com.akib.mycompanion.autoreply.ui.MessagingSelfTestRoute
import com.akib.mycompanion.autoreply.ui.ReplyLogRoute
import com.akib.mycompanion.context.ui.PlaceEditorRoute
import com.akib.mycompanion.context.ui.PlacesRoute
import com.akib.mycompanion.context.ui.RuleEditorRoute
import com.akib.mycompanion.context.ui.RuleHistoryRoute
import com.akib.mycompanion.context.ui.RulesRoute
import com.akib.mycompanion.presentation.home.HomeLink
import com.akib.mycompanion.vault.ui.VaultItemRoute
import com.akib.mycompanion.vault.ui.VaultRoute
import com.akib.mycompanion.vault.ui.VaultTrashBackupRoute
import com.akib.mycompanion.voice.ui.VoiceRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

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

    // ---- Vault (Phase 3) ----

    @Provides @IntoSet
    fun vault() = Screen("vault", "Vault") { s ->
        VaultRoute(onOpenItem = {
            s.navigate("vault-item/$it")
        }, onOpenTrashAndBackup = { s.navigate("vault-more") }, modifier = s.modifier)
    }

    @Provides @IntoSet
    fun vaultItem() = Screen(
        "vault-item/{id}",
        "Vault item"
    ) { s -> VaultItemRoute(onDone = s.back, modifier = s.modifier) }

    @Provides @IntoSet
    fun vaultMore() = Screen("vault-more", "Trash & backup") { s -> VaultTrashBackupRoute(s.modifier) }

    @Provides @IntoSet
    fun vaultLink() = HomeLink("Vault", "Encrypted links, notes, images and files", "vault")

    /** Route argument for "create a new one" (not a number, so the editor starts empty). */
    private const val NEW = "new"
}
