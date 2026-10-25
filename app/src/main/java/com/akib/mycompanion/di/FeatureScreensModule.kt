package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.context.ui.PlaceEditorRoute
import com.akib.mycompanion.context.ui.PlacesRoute
import com.akib.mycompanion.context.ui.RuleEditorRoute
import com.akib.mycompanion.context.ui.RuleHistoryRoute
import com.akib.mycompanion.context.ui.RulesRoute
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

    /** Route argument for "create a new one" (not a number, so the editor starts empty). */
    private const val NEW = "new"
}
