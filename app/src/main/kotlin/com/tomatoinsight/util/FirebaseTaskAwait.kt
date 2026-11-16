package com.tomatoinsight.util

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Firebase task failed"))
            }
        }
    }

/** For Firebase `Task<Void>` APIs such as reload() / sendEmailVerification(). */
suspend fun Task<*>.awaitComplete() {
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Firebase task failed"))
            }
        }
    }
}
