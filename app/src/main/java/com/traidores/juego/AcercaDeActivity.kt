package com.traidores.juego

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.traidores.juego.GameToast as Toast

class AcercaDeActivity : BaseActivity() {

    private var accountDeletionInProgress = false

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
        val deleteAccountButton = findViewById<Button>(R.id.btnDeleteAccount)
        val deletionDivider = findViewById<View>(R.id.accountDeletionDivider)
        val hasRegisteredAccount = !GuestIdentity.isGuest()
        deleteAccountButton.visibility = if (hasRegisteredAccount) View.VISIBLE else View.GONE
        deletionDivider.visibility = if (hasRegisteredAccount) View.VISIBLE else View.GONE
        deleteAccountButton.setOnClickListener { showDeleteAccountDialog() }
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

    private fun showDeleteAccountDialog() {
        if (GuestIdentity.isGuest() || accountDeletionInProgress) return
        val hasPasswordProvider = FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.any { it.providerId == "password" } == true
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        content.addView(
            TextView(this).apply {
                text = getString(R.string.account_delete_explanation)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setLineSpacing(0f, 1.15f)
            }
        )
        val confirmationInput = EditText(this).apply {
            hint = AccountDeletion.CONFIRMATION_TEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
        }
        content.addView(
            confirmationInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        val passwordInput = if (hasPasswordProvider) {
            EditText(this).apply {
                hint = getString(R.string.account_delete_password_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                setTextColor(getColor(R.color.text_primary))
                setHintTextColor(getColor(R.color.text_muted))
                content.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                )
            }
        } else {
            null
        }
        val errorText = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(errorText)

        val dialog = GameDialog.custom(
            activity = this,
            contentView = content,
            widthDp = 420,
            negativeLabel = getString(R.string.account_delete_cancel),
            positiveLabel = getString(R.string.account_delete_action)
        )
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.setOnClickListener {
            if (confirmationInput.text.toString().trim() != AccountDeletion.CONFIRMATION_TEXT) {
                errorText.text = getString(
                    R.string.account_delete_confirmation_error,
                    AccountDeletion.CONFIRMATION_TEXT
                )
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (hasPasswordProvider && passwordInput?.text.isNullOrBlank()) {
                errorText.text = getString(R.string.account_delete_password_error)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dialog.dismiss()
            submitAccountDeletion(passwordInput?.text?.toString())
        }
    }

    private fun submitAccountDeletion(password: String?) {
        accountDeletionInProgress = true
        findViewById<Button>(R.id.btnDeleteAccount).apply {
            isEnabled = false
            alpha = 0.5f
        }
        GameNotice.show(
            activity = this,
            message = getString(R.string.account_delete_progress),
            duration = GameNotice.Duration.LONG
        )
        AccountDeletion.delete(this, password) { result ->
            accountDeletionInProgress = false
            if (isFinishing || isDestroyed) return@delete
            when (result) {
                AccountDeletionResult.Deleted -> {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                            putExtra("account_deleted", true)
                        }
                    )
                    finish()
                }
                is AccountDeletionResult.Failed -> {
                    result.error?.let {
                        OnlineDebugLog.e("account_deletion_failure", it)
                    }
                    GameNotice.show(
                        activity = this,
                        message = result.message,
                        duration = GameNotice.Duration.LONG
                    )
                    findViewById<Button>(R.id.btnDeleteAccount).apply {
                        isEnabled = true
                        alpha = 1f
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
