package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.WeakHashMap

object GameDialog {
    private val openDialogs = WeakHashMap<Activity, MutableSet<AlertDialog>>()

    fun dismissAll(activity: Activity) {
        openDialogs[activity]
            ?.toList()
            ?.forEach { dialog -> dialog.dismiss() }
        openDialogs.remove(activity)
    }

    fun confirm(
        activity: Activity,
        title: String,
        message: String,
        positiveLabel: String,
        negativeLabel: String = "CANCELAR",
        onConfirm: () -> Unit
    ): AlertDialog {
        val host = createHost(activity, title, message)
        host.negative.text = negativeLabel
        host.positive.text = positiveLabel
        host.negative.setOnClickListener { host.dialog.dismiss() }
        host.positive.setOnClickListener {
            host.dialog.dismiss()
            onConfirm()
        }
        show(activity, host.dialog)
        return host.dialog
    }

    fun choose(
        activity: Activity,
        title: String,
        message: String,
        options: List<String>,
        negativeLabel: String = "CANCELAR",
        onSelected: (Int) -> Unit
    ): AlertDialog {
        val host = createHost(activity, title, message)
        host.positive.visibility = View.GONE
        host.content.visibility = View.VISIBLE
        host.negative.text = negativeLabel
        host.negative.setOnClickListener { host.dialog.dismiss() }
        val optionList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        options.forEachIndexed { index, label ->
            optionList.addView(Button(activity).apply {
                text = label
                setTextColor(activity.getColor(R.color.text_primary))
                textSize = 13f
                setAllCaps(false)
                background = activity.getDrawable(R.drawable.bg_btn_dark_ripple)
                setOnClickListener {
                    host.dialog.dismiss()
                    onSelected(index)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(46)
            ).apply {
                if (index > 0) topMargin = activity.dp(7)
            })
        }
        val optionHeight = (options.size.coerceAtLeast(1) * 53).coerceAtMost(330)
        val optionScroll = ScrollView(activity).apply {
            isFillViewport = false
            overScrollMode = if (options.size * 53 > optionHeight) {
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
            } else {
                View.OVER_SCROLL_NEVER
            }
            addView(
                optionList,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        host.content.addView(
            optionScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(optionHeight)
            )
        )
        show(activity, host.dialog, heightConstrainedView = optionScroll)
        return host.dialog
    }

    fun input(
        activity: Activity,
        title: String,
        currentValue: String,
        hint: String,
        maxLength: Int,
        positiveLabel: String = "APLICAR",
        negativeLabel: String = "CANCELAR",
        onAccept: (String) -> String?
    ): AlertDialog {
        val host = createHost(activity, title, "")
        host.content.visibility = View.VISIBLE
        val input = EditText(activity).apply {
            setText(currentValue)
            setSelection(text.length)
            this.hint = hint
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setSingleLine()
            setTextColor(activity.getColor(R.color.text_primary))
            setHintTextColor(activity.getColor(R.color.text_muted))
            background = activity.getDrawable(R.drawable.bg_chat_input)
            setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
        }
        host.content.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(48)
        ))
        host.negative.text = negativeLabel
        host.positive.text = positiveLabel
        host.negative.setOnClickListener { host.dialog.dismiss() }
        host.positive.setOnClickListener {
            val error = onAccept(input.text.toString().trim())
            if (error == null) {
                host.dialog.dismiss()
            } else {
                input.error = error
            }
        }
        show(activity, host.dialog)
        host.dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        input.requestFocus()
        return host.dialog
    }

    fun custom(
        activity: Activity,
        contentView: View,
        widthDp: Int = 500,
        contentHeightDp: Int? = null,
        negativeLabel: String? = "CERRAR",
        neutralLabel: String? = null,
        positiveLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        onPositive: (() -> Unit)? = null
    ): AlertDialog {
        val host = createHost(activity, "", "")
        host.content.visibility = View.VISIBLE
        contentView.background = null
        (contentView.parent as? ViewGroup)?.removeView(contentView)
        host.content.addView(
            contentView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                contentHeightDp?.let { activity.dp(it) }
                    ?: LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        configureOptionalButton(host.negative, negativeLabel) {
            host.dialog.dismiss()
        }
        configureOptionalButton(host.neutral, neutralLabel) {
            onNeutral?.invoke()
        }
        configureOptionalButton(host.positive, positiveLabel) {
            onPositive?.invoke()
            host.dialog.dismiss()
        }
        show(
            activity = activity,
            dialog = host.dialog,
            widthDp = widthDp,
            heightConstrainedView = contentView.takeIf { it is ScrollView }
        )
        return host.dialog
    }

    private fun createHost(activity: Activity, title: String, message: String): DialogHost {
        val root = activity.layoutInflater.inflate(R.layout.dialog_game_prompt, null)
        val titleView: TextView = root.findViewById(R.id.gameDialogTitle)
        val messageView: TextView = root.findViewById(R.id.gameDialogMessage)
        val content: LinearLayout = root.findViewById(R.id.gameDialogContent)
        val negative: Button = root.findViewById(R.id.gameDialogNegative)
        val neutral: Button = root.findViewById(R.id.gameDialogNeutral)
        val positive: Button = root.findViewById(R.id.gameDialogPositive)
        titleView.text = title.uppercase()
        titleView.visibility = if (title.isBlank()) View.GONE else View.VISIBLE
        messageView.text = message
        messageView.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        content.visibility = View.GONE
        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .create()
        neutral.visibility = View.GONE
        return DialogHost(dialog, content, negative, neutral, positive)
    }

    private fun configureOptionalButton(button: Button, label: String?, action: () -> Unit) {
        button.visibility = if (label == null) View.GONE else View.VISIBLE
        if (label == null) return
        button.text = label
        button.setOnClickListener { action() }
    }

    private fun show(
        activity: Activity,
        dialog: AlertDialog,
        widthDp: Int = 420,
        heightConstrainedView: View? = null
    ) {
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = (activity.resources.displayMetrics.widthPixels - activity.dp(24))
                    .coerceAtMost(activity.dp(widthDp))
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setDimAmount(0.58f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                decorView.post {
                    val availableHeight = activity.resources.displayMetrics.heightPixels - activity.dp(24)
                    if (decorView.measuredHeight > availableHeight) {
                        heightConstrainedView?.let { scrollableContent ->
                            val overflow = decorView.measuredHeight - availableHeight
                            val constrainedHeight = (scrollableContent.measuredHeight - overflow)
                                .coerceAtLeast(activity.dp(140))
                            scrollableContent.layoutParams = scrollableContent.layoutParams.apply {
                                height = constrainedHeight
                            }
                            scrollableContent.requestLayout()
                        }
                        setLayout(width, availableHeight)
                    }
                }
            }
        }
        track(activity, dialog)
        dialog.show()
    }

    private fun track(activity: Activity, dialog: AlertDialog) {
        openDialogs.getOrPut(activity) { linkedSetOf() }.add(dialog)
        dialog.setOnDismissListener {
            openDialogs[activity]?.let { dialogs ->
                dialogs.remove(dialog)
                if (dialogs.isEmpty()) openDialogs.remove(activity)
            }
        }
    }

    private data class DialogHost(
        val dialog: AlertDialog,
        val content: LinearLayout,
        val negative: Button,
        val neutral: Button,
        val positive: Button
    )

    private fun Activity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
