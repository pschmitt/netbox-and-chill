package dev.pschmitt.netboxandchill.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.netboxandchill.BuildConfig
import dev.pschmitt.netboxandchill.data.api.AuthInterceptor
import dev.pschmitt.netboxandchill.data.api.DynamicBaseUrlInterceptor
import dev.pschmitt.netboxandchill.data.api.NetBoxApi
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
            }
        return OkHttpClient.Builder()
            // Rewrites scheme/host/path to the configured instance - added first so auth/logging
            // see the real request.
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // Placeholder - DynamicBaseUrlInterceptor rewrites every request to the configured
            // NetBox instance at request time, so this host is never actually contacted.
            .baseUrl("http://netbox.invalid/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideNetBoxApi(retrofit: Retrofit): NetBoxApi = retrofit.create(NetBoxApi::class.java)
}
