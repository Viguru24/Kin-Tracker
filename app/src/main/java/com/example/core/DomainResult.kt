package com.example.core

/**
 * Enterprise Type-Safe Domain Result Wrapper.
 * Replaces unchecked exceptions and raw try/catch blocks with an exhaustive compile-time checked sealed hierarchy.
 */
sealed interface DomainResult<out T> {
    data class Success<out T>(val data: T) : DomainResult<T>
    data class Failure(val error: Throwable, val message: String = error.localizedMessage ?: "Unknown error") : DomainResult<Nothing>
    object Loading : DomainResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

inline fun <T> DomainResult<T>.onSuccess(action: (value: T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) action(data)
    return this
}

inline fun <T> DomainResult<T>.onFailure(action: (error: Throwable, message: String) -> Unit): DomainResult<T> {
    if (this is DomainResult.Failure) action(error, message)
    return this
}
