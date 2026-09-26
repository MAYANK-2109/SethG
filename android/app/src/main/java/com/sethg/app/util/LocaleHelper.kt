package com.sethg.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

object LocaleHelper {
    fun updateLocale(context: Context, languageCode: String): Configuration {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        return config
    }
}

@Composable
fun ProvideAppLocale(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val (localizedContext, config) = remember(languageCode, context) {
        val cfg = LocaleHelper.updateLocale(context, languageCode)
        val ctx = context.createConfigurationContext(cfg)
        ctx to cfg
    }

    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalContext provides localizedContext,
        content = content
    )
}
