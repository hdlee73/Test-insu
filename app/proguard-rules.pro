# SQLCipher: JNI가 리플렉션으로 접근한다.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# PdfBox-Android: 선택적 JPEG2000 디코더가 없어도 동작한다.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
