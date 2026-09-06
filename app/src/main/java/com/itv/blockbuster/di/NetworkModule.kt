package com.itv.blockbuster.di

import com.itv.blockbuster.data.remote.StalkerApi
import com.itv.blockbuster.data.remote.StalkerInterceptor
import com.itv.blockbuster.data.session.ReauthManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionManager: StalkerSessionManager,
        reauthManager: ReauthManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Keep NONE on TV hardware; switch to HEADERS when debugging portals.
            level = HttpLoggingInterceptor.Level.NONE
        }
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 8
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(StalkerInterceptor(sessionManager, reauthManager))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://placeholder.local/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideStalkerApi(retrofit: Retrofit): StalkerApi {
        return retrofit.create(StalkerApi::class.java)
    }
}