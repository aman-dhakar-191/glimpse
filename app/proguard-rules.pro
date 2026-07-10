# Add project specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Firebase Realtime Database deserializes these via reflection using the
# property names — R8 renaming/stripping them breaks it silently at runtime
# (build still succeeds, snapshot.getValue() just returns nulls/defaults).
-keepclassmembers class com.glimpse.app.data.model.** {
  *;
}
