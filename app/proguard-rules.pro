# App-specific R8 rules. Libraries ship their own consumer rules.

# ONNX Runtime creates and reads its Java objects from native code.
-keep class ai.onnxruntime.** { *; }

# Keep file names and line numbers so the diagnostics screen can point at the failing line.
-keepattributes SourceFile,LineNumberTable
