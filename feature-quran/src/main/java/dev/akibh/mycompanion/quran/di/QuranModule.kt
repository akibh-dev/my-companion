package dev.akibh.mycompanion.quran.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.akibh.mycompanion.quran.data.QuranContentDao
import dev.akibh.mycompanion.quran.data.QuranContentDb
import dev.akibh.mycompanion.quran.data.QuranUserDao
import dev.akibh.mycompanion.quran.data.QuranUserDb
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QuranModule {
    @Provides @Singleton
    fun content(@ApplicationContext context: Context): QuranContentDb = QuranContentDb.create(context)

    @Provides
    fun contentDao(db: QuranContentDb): QuranContentDao = db.dao()

    @Provides @Singleton
    fun user(@ApplicationContext context: Context): QuranUserDb = QuranUserDb.create(context)

    @Provides
    fun userDao(db: QuranUserDb): QuranUserDao = db.dao()
}
