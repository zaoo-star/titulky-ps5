// Testy logiky z Androidu (app/src/test/java/cz/recaption/core) jsou psané pro JUnit 4.
// Tady se pouští v Kotlinu pro iPhone / Linux přes kotlin.test – stejná jména jako JUnit, ať se testy nemění.
@file:Suppress("PackageDirectoryMismatch")
package org.junit.Assert

import kotlin.math.abs


fun assertEquals(expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual)
fun assertEquals(message: String?, expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual, message)
fun assertEquals(expected: Double, actual: Double, delta: Double) = assertEquals(null, expected, actual, delta)
fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {
        if (!(expected == actual || abs(expected - actual) <= delta)) kotlin.test.fail("${message ?: ""} expected:<$expected> but was:<$actual>")
    }
fun assertEquals(expected: Float, actual: Float, delta: Float) = assertEquals(null, expected.toDouble(), actual.toDouble(), delta.toDouble())
fun assertEquals(message: String?, expected: Float, actual: Float, delta: Float) =
        assertEquals(message, expected.toDouble(), actual.toDouble(), delta.toDouble())
fun assertEquals(expected: Long, actual: Long) = kotlin.test.assertEquals(expected, actual)
fun assertEquals(message: String?, expected: Long, actual: Long) = kotlin.test.assertEquals(expected, actual, message)
fun assertNotEquals(unexpected: Any?, actual: Any?) = kotlin.test.assertNotEquals(unexpected, actual)
fun assertNotEquals(message: String?, unexpected: Any?, actual: Any?) = kotlin.test.assertNotEquals(unexpected, actual, message)
fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)
fun assertTrue(message: String?, condition: Boolean) = kotlin.test.assertTrue(condition, message)
fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)
fun assertFalse(message: String?, condition: Boolean) = kotlin.test.assertFalse(condition, message)
fun assertNull(value: Any?) = kotlin.test.assertNull(value)
fun assertNull(message: String?, value: Any?) = kotlin.test.assertNull(value, message)
fun assertNotNull(value: Any?) { kotlin.test.assertNotNull(value) }
fun assertNotNull(message: String?, value: Any?) { kotlin.test.assertNotNull(value, message) }
fun assertArrayEquals(expected: ByteArray?, actual: ByteArray?) = kotlin.test.assertContentEquals(expected, actual)
fun assertArrayEquals(expected: IntArray?, actual: IntArray?) = kotlin.test.assertContentEquals(expected, actual)
fun assertArrayEquals(expected: Array<*>?, actual: Array<*>?) = kotlin.test.assertEquals(expected?.toList(), actual?.toList())
fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        kotlin.test.assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertEquals("[$i]", expected[i], actual[i], delta)
    }
fun assertArrayEquals(message: String?, expected: FloatArray, actual: FloatArray, delta: Float) {
        kotlin.test.assertEquals(expected.size, actual.size, message)
        for (i in expected.indices) assertEquals(message, expected[i], actual[i], delta)
    }
fun fail(message: String? = null): Nothing = kotlin.test.fail(message)
