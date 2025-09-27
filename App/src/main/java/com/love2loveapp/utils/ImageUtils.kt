package com.love2loveapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 🖼️ ImageUtils - Utilitaires pour la gestion des images
 * 
 * Fonctions helper pour convertir, sauvegarder et manipuler les images
 */

/**
 * Helper pour convertir un Bitmap en URI temporaire
 * Utilisé pour convertir les images de la caméra en URI compatible avec les launchers
 */
fun saveBitmapToTempUri(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        Uri.fromFile(tempFile)
    } catch (e: Exception) {
        null
    }
}

/**
 * Nettoyer les fichiers temporaires d'images
 */
fun cleanupTempImages(context: Context) {
    try {
        val cacheDir = context.cacheDir
        val tempFiles = cacheDir.listFiles { file ->
            file.name.startsWith("temp_image_") && file.name.endsWith(".jpg")
        }
        tempFiles?.forEach { file ->
            // Supprimer les fichiers plus anciens que 1 heure
            if (System.currentTimeMillis() - file.lastModified() > 3600000) {
                file.delete()
            }
        }
    } catch (e: Exception) {
        // Ignorer les erreurs de nettoyage
    }
}
