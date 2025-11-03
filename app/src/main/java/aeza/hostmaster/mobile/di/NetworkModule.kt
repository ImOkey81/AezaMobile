package aeza.hostmaster.mobile.di

import aeza.hostmaster.mobile.BuildConfig
import aeza.hostmaster.mobile.data.remote.ApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.nio.charset.StandardCharsets
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            val credentials = resolveCredentials()
            if (credentials != null) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", credentials)
                        .build()
                    chain.proceed(request)
                }
                authenticator { _, response ->
                    if (response.request.header("Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Authorization", credentials)
                            .build()
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(logging)
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}

private fun resolveBaseUrl(): String {
    val configured = BuildConfig.API_BASE_URL.ifBlank { DEFAULT_BASE_URL }
    val normalised = configured.ensureScheme()
    return if (normalised.endsWith("/")) normalised else "$normalised/"
}

private fun resolveCredentials(): String? {
    val username = BuildConfig.API_USERNAME
    val password = BuildConfig.API_PASSWORD
    if (username.isBlank() || password.isBlank()) {
        return null
    }
    return Credentials.basic(username, password, StandardCharsets.UTF_8)
}

private fun String.ensureScheme(): String {
    val trimmed = trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    return "https://$trimmed"
}
