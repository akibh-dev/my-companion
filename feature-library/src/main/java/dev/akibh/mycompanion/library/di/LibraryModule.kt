package dev.akibh.mycompanion.library.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.akibh.mycompanion.library.data.LibraryDao
import dev.akibh.mycompanion.library.data.LibraryDb
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides @Singleton
    fun db(@ApplicationContext context: Context): LibraryDb = LibraryDb.create(context)

    @Provides
    fun dao(db: LibraryDb): LibraryDao = db.dao()
}
