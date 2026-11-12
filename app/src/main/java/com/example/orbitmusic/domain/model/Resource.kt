package com.example.orbitmusic.domain.model

/** Result wrapper used across repositories, use cases and ViewModels. */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val error: AppError) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

inline fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Success -> Resource.Success(transform(data))
    is Resource.Error -> this
    Resource.Loading -> Resource.Loading
}

fun <T> Resource<T>.dataOrNull(): T? = (this as? Resource.Success)?.data
