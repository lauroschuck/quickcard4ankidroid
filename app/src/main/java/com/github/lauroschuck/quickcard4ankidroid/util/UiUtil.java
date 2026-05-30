package com.github.lauroschuck.quickcard4ankidroid.util;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UiUtil {

    public static boolean isDarkMode(Context context) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.isLightTheme, typedValue, true)) {
            // isLightTheme is true for light themes, so dark mode is the opposite
            return typedValue.data == 0;
        }
        // Fallback to configuration check
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String colorToHex(Context context, int colorResId) {
        int color = ContextCompat.getColor(context, colorResId);
        return String.format("#%06X", (0xFFFFFF & color));
    }
}
