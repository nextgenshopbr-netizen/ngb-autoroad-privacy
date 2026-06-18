package com.ngbautoroad.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object NavigationHelper {
    // v5.0.0: FLAG_ACTIVITY_NEW_TASK em todos os intents (obrigatório quando chamado de Service/non-Activity)
    fun openNavigation(context: Context, address: String, preferWaze: Boolean = true) {
        val encoded = Uri.encode(address)
        if (preferWaze && isAppInstalled(context, "com.waze")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$encoded&navigate=yes"))
            intent.setPackage("com.waze")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else if (isAppInstalled(context, "com.google.android.apps.maps")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
            intent.setPackage("com.google.android.apps.maps")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun isAppInstalled(context: Context, pkg: String): Boolean {
        return try { context.packageManager.getPackageInfo(pkg, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }
}
