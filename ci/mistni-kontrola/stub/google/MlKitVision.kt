@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.mlkit.vision.common

class InputImage {
    companion object {
        @JvmStatic fun fromBitmap(b: android.graphics.Bitmap, rotation: Int): InputImage = InputImage()
        @JvmStatic fun fromMediaImage(i: android.media.Image, rotation: Int): InputImage = InputImage()
    }
}
