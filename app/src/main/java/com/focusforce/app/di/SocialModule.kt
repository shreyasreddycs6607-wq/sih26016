package com.focusforce.app.di

import android.content.Context
import com.focusforce.app.BuildConfig
import com.focusforce.app.data.social.FirebaseAuthService
import com.focusforce.app.data.social.FirebaseSocialBackend
import com.focusforce.app.data.social.SimulatedAuthService
import com.focusforce.app.data.social.SimulatedSocialBackend
import com.focusforce.app.data.social.UnavailableAuthService
import com.focusforce.app.data.social.UnavailableSocialBackend
import com.focusforce.app.domain.social.AuthService
import com.focusforce.app.domain.social.SocialBackend
import com.google.firebase.FirebaseApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Chooses the multiplayer backend once per process:
 * 1. Firebase, if the app was built with google-services.json (a Firebase project is configured);
 * 2. otherwise, in DEBUG builds only, the in-memory simulation so the screens can be developed;
 * 3. otherwise "unavailable": every call fails fast and nothing is ever sent over the network.
 */
@Module
@InstallIn(SingletonComponent::class)
object SocialModule {
    private fun firebaseConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    @Provides
    @Singleton
    fun socialBackend(
        @ApplicationContext context: Context,
        firebase: Provider<FirebaseSocialBackend>,
        simulated: Provider<SimulatedSocialBackend>,
        unavailable: Provider<UnavailableSocialBackend>,
    ): SocialBackend = when {
        firebaseConfigured(context) -> firebase.get()
        BuildConfig.DEBUG -> simulated.get()
        else -> unavailable.get()
    }

    @Provides
    @Singleton
    fun authService(
        @ApplicationContext context: Context,
        firebase: Provider<FirebaseAuthService>,
        simulated: Provider<SimulatedAuthService>,
        unavailable: Provider<UnavailableAuthService>,
    ): AuthService = when {
        firebaseConfigured(context) -> firebase.get()
        BuildConfig.DEBUG -> simulated.get()
        else -> unavailable.get()
    }
}
