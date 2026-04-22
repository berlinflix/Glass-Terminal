# ProGuard rules for GlassTerminal

# ── SSHJ ─────────────────────────────────────────────────────────────────────
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**

# ── EdDSA ─────────────────────────────────────────────────────────────────────
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# ── BouncyCastle ──────────────────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── SLF4J (used by SSHJ) ─────────────────────────────────────────────────────
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
