package com.calgapt.app.di

import com.calgapt.app.data.openai.OpenAIService
import com.calgapt.app.data.settings.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(settingsRepository: SettingsRepository): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                
                // Add Authorization header if calling OpenAI
                // We shouldn't block the main thread, but for interceptor initialization it's tricky.
                // A better approach is usually an Authenticator or a separate Interceptor that reads a volatile token.
                // For simplicity here, calling runBlocking strictly for the API key retrieval (which should be fast from DataStore mostly)
                // Note: ideally we check the hostname to only add the key for OpenAI
                if (chain.request().url.host.contains("api.openai.com")) {
                    // If the request already has an Authorization header (e.g., Settings test), do not override it.
                    val hasAuthorization = chain.request().header("Authorization")?.isNotBlank() == true
                    if (!hasAuthorization) {
                        val apiKey = runBlocking { settingsRepository.settings.first().openAiApiKey }
                        if (apiKey.isNotBlank()) {
                            requestBuilder.header("Authorization", "Bearer $apiKey")
                        }
                    }
                }
                
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIService(retrofit: Retrofit): OpenAIService {
        return retrofit.create(OpenAIService::class.java)
    }
}
