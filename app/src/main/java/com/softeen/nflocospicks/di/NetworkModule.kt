package com.softeen.nflocospicks.di

import com.softeen.nflocospicks.data.remote.espn.EspnApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val ESPN_BASE_URL =
        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideEspnRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ESPN_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideEspnApiService(retrofit: Retrofit): EspnApiService =
        retrofit.create(EspnApiService::class.java)
}
