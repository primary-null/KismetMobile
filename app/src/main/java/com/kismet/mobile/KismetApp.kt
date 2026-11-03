package com.kismet.mobile

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class KismetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Replace Android's stripped BC with the full provider so sshj can use X25519/ECDH
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
