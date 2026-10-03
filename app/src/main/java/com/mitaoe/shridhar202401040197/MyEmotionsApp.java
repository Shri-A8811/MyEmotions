package com.mitaoe.shridhar202401040197;

import android.app.Application;
import android.util.Log;

/**
 * Application class for My Emotions.
 * Sets up global safety handlers to prevent unhandled crashes from abruptly stopping the app.
 */
public class MyEmotionsApp extends Application {

    private static final String TAG = "MyEmotionsApp";
    private static MyEmotionsApp instance;

    public static MyEmotionsApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception intercepted in thread: " + thread.getName(), throwable);

            // Log details safely to avoid unhandled crash dialog loops
            try {
                // If it's an OutOfMemoryError, suggest garbage collection
                if (throwable instanceof OutOfMemoryError) {
                    System.gc();
                }
            } catch (Throwable ignored) {
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
