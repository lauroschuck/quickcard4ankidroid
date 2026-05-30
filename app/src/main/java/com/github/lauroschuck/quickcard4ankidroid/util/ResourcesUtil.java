package com.github.lauroschuck.quickcard4ankidroid.util;

import android.content.Context;
import java.io.IOException;
import kotlin.io.ByteStreamsKt;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ResourcesUtil {

    public static String loadRaw(int rawResource, Context context) {
        try (var resource = context.getResources().openRawResource(rawResource)) {
            return new String(ByteStreamsKt.readBytes(resource));
        } catch (IOException e) {
            throw new RuntimeException("Error loading asset: " + e.getMessage(), e);
        }
    }
}
