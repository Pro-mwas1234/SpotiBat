-keepattributes SourceFile,LineNumberTable
-repackageclasses
-allowaccessmodification

# Rhino's Java-to-JSON bean converter references Java Beans APIs that Android does not provide.
# This converter is optional; suppress these missing-class warnings for Android release builds.
-dontwarn java.beans.**
