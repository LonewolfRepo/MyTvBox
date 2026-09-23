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
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// Qualifies the interceptor-free OkHttpClient used for image loading (see
// provideImageOkHttpClient) so Hilt can distinguish it from the portal API's
// own OkHttpClient binding - both are the same OkHttpClient type.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageHttpClient

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

    // Shared connection pool + dispatcher between the portal API client
    // (below) and the image-loading client (provideImageOkHttpClient) -
    // this is the part actually worth sharing (warm TCP connections when
    // images live on the same host as the portal, one shared thread budget
    // instead of two separate pools). The portal client's own
    // StalkerInterceptor is NOT shared - see provideImageOkHttpClient's doc
    // comment for why.
    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    @Provides
    @Singleton
    fun provideDispatcher(): Dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 8
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionManager: StalkerSessionManager,
        reauthManager: ReauthManager,
        connectionPool: ConnectionPool,
        dispatcher: Dispatcher
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Keep NONE on TV hardware; switch to HEADERS when debugging portals.
            level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .addInterceptor(StalkerInterceptor(sessionManager, reauthManager))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // UPDATED (Coil was running its own fully separate default OkHttpClient
    // - a third connection pool/thread pool in memory alongside this one and
    // StreamValidator's): this client is what Coil now uses instead (see
    // BlockbusterApp's ImageLoaderFactory). It deliberately does NOT include
    // StalkerInterceptor - that interceptor attaches the portal's session
    // cookies, a spoofed set-top-box User-Agent, and a bearer token to every
    // request that passes through it with no host check at all, so sharing
    // the exact portal client with Coil would leak that auth to any image
    // host that isn't the portal itself. Sharing just the ConnectionPool and
    // Dispatcher gets the real resource-saving benefit (warm connections
    // when images ARE on the portal's own host, one shared thread budget
    // instead of two) without that risk.
    @Provides
    @Singleton
    @ImageHttpClient
    fun provideImageOkHttpClient(
        connectionPool: ConnectionPool,
        dispatcher: Dispatcher
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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