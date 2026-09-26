package com.sethg.app.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
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
        // Keep the Activity as the base (Hilt's hiltViewModel() needs to find it);
        // only the resources come from the localized configuration.
        val ctx = LocalizedContext(context, context.createConfigurationContext(cfg).resources)
        ctx to cfg
    }

    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalContext provides localizedContext,
        content = content
    )
}

/** Activity context whose strings/resources are in the selected language. */
private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}
