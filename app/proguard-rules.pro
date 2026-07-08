# kotlinx.serialization: keep generated serializers for the persisted survey types
# (the library ships its own consumer rules, but serializer lookup for our @Serializable
# classes goes through the synthetic Companion/serializer members — keep them explicitly).
-keepclassmembers class com.example.riverdischarge.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.riverdischarge.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.riverdischarge.**$$serializer { *; }
