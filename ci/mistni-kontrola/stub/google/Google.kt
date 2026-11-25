// Náhrady knihoven Google (ML Kit, úlohy Play services) – jen to, co aplikace používá.
// Pouze pro místní kontrolu; skutečné knihovny se berou při sestavení na GitHubu.
@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.android.gms.tasks

fun interface OnSuccessListener<T> { fun onSuccess(result: T) }
fun interface OnFailureListener { fun onFailure(e: Exception) }
abstract class Task<TResult> {
    abstract fun addOnSuccessListener(l: OnSuccessListener<in TResult>): Task<TResult>
    abstract fun addOnFailureListener(l: OnFailureListener): Task<TResult>
}
