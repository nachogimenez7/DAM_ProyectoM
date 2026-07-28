package com.traidores.juego

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.traidores.juego.GameToast as Toast

class AcercaDeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_acerca_de)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version,
            installedVersionName()
        )
        findViewById<Button>(R.id.btnContactSupport).setOnClickListener {
            openSupportEmail()
        }
        findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            openWebPage(getString(R.string.privacy_policy_url))
        }
        findViewById<Button>(R.id.btnAccountDeletionInfo).setOnClickListener {
            openWebPage(getString(R.string.account_deletion_url))
        }
    }

    private fun installedVersionName(): String {
        return packageManager
            .getPackageInfo(packageName, 0)
            .versionName
            .orEmpty()
            .ifBlank { "—" }
    }

    private fun openSupportEmail() {
        val email = getString(R.string.bandido_games_support_email)
        val subject = Uri.encode(getString(R.string.about_support_subject))
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:$email?subject=$subject")
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.about_support_unavailable, email),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openWebPage(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.about_link_unavailable),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
