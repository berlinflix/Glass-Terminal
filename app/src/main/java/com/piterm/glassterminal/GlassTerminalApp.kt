package com.piterm.glassterminal

import android.app.Application

class GlassTerminalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install BouncyCastle as the primary crypto provider for Ed25519
        val bcProvider = org.bouncycastle.jce.provider.BouncyCastleProvider()
        java.security.Security.removeProvider(bcProvider.name)
        java.security.Security.insertProviderAt(bcProvider, 1)
    }
}
