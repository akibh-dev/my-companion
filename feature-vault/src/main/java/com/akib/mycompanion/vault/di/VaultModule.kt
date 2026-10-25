package com.akib.mycompanion.vault.di

import android.content.Context
import com.akib.mycompanion.core.sinks.VaultSink
import com.akib.mycompanion.vault.crypto.InboxCipher
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.crypto.KeystoreInboxCipher
import com.akib.mycompanion.vault.crypto.KeystoreKeyWrapper
import com.akib.mycompanion.vault.data.VaultDao
import com.akib.mycompanion.vault.data.VaultDatabase
import com.akib.mycompanion.vault.data.VaultSinkImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultModule {
    @Binds abstract fun keyWrapper(impl: KeystoreKeyWrapper): KeyWrapper

    @Binds abstract fun inbox(impl: KeystoreInboxCipher): InboxCipher

    @Binds abstract fun sink(impl: VaultSinkImpl): VaultSink

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context) = VaultDatabase.create(context)

        @Provides
        fun dao(db: VaultDatabase): VaultDao = db.dao()
    }
}
