package com.focusforce.app.di

import com.focusforce.app.BuildConfig
import com.focusforce.app.data.billing.BillingService
import com.focusforce.app.data.billing.FakeBillingService
import com.focusforce.app.data.billing.PlayBillingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/** Debug builds use the fake store so every Pro gate can be tried; release builds always use Google Play. */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun billing(play: Provider<PlayBillingService>, fake: Provider<FakeBillingService>): BillingService =
        if (BuildConfig.DEBUG) fake.get() else play.get()
}
