package com.softeen.nflocospicks.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Retrofit and OkHttp providers will be added in PR-4 (NFL Schedule)
}
