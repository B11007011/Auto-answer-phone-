package com.example.auto_answer_app

import io.flutter.app.FlutterApplication
import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.view.FlutterMain

class AutoAnswerApplication : FlutterApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            // Initialize Flutter early
            FlutterMain.startInitialization(this)
            FlutterLoader().startInitialization(this)
            Log.d("AutoAnswerApplication", "Flutter initialization successful")
        } catch (e: Exception) {
            Log.e("AutoAnswerApplication", "Flutter initialization failed: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Ensure FlutterMain is initialized
            FlutterMain.ensureInitializationComplete(this, arrayOf())
            Log.d("AutoAnswerApplication", "Flutter initialization completed")
        } catch (e: Exception) {
            Log.e("AutoAnswerApplication", "Error during initialization: ${e.message}")
        }
    }
} 