package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.*
import java.io.File
import java.util.UUID

/** Offline visual QA with isolated preferences. Never included in Release. */
class AnimalAvatarSmokeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val testPrefix = "animal_avatar_qa_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(this) {
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences(testPrefix + name, mode)
        }
        val result = runCatching {
            val initial = ProfileAvatarCatalog.getOrCreate(isolated)
            check(initial in ProfileAvatarCatalog.keys)
            check(ProfileAvatarCatalog.getOrCreate(isolated) == initial)
            isolated.getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE).edit()
                .putString("profile_avatar", "avatar_hornero").commit()
            check(ProfileAvatarCatalog.getOrCreate(isolated) == "avatar_hornero")
            val original = LocalGameFactory.createSession(humanName = "QA")
            LocalBotNameStore.save(isolated, 0, "Lucas")
            val renamed = LocalBotNameStore.apply(isolated, original)
            check(renamed.players.any { it.name == "Lucas" })
            check(renamed.playerProfiles.getValue("Lucas").avatarKey == "avatar_carpincho")
            LocalBotNameStore.save(isolated, 0, "Otro nombre")
            val renamedAgain = LocalBotNameStore.apply(isolated, renamed)
            check(renamedAgain.playerProfiles.getValue("Otro nombre").avatarKey == "avatar_carpincho")
            "OK: random persisted; choice persisted; bot identity survives two renames; 14 animals"
        }.getOrElse { "FAIL: ${it.stackTraceToString()}" }
        File(cacheDir, "animal_avatar_qa.txt").writeText(result)
        android.util.Log.i("ANIMAL_AVATAR_QA", result)
        deleteSharedPreferences(testPrefix + "TraidoresPrefs")
        if (intent.getStringExtra("screen") == "selector") {
            startActivity(ProfileSelectionActivity.intent(this, ProfileSelectionActivity.MODE_AVATAR, "avatar_mamona"))
            finish(); return
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 48, 16, 16) }
        column.addView(TextView(this).apply { text = result; textSize = 16f })
        val roster = LocalGameFactory.botSlots().map { slot ->
            val name = LocalGameFactory.defaultBotName(slot)!!
            GamePlayer(name, name.take(1), isHuman = false)
        }
        val session = PlayerProfileStore.withProfiles(this,
            LocalGameFactory.createSession(humanName = "QA").copy(players = roster))
        LocalGameFactory.botSlots().forEach { slot ->
            val player = session.players.first { it.name == LocalGameFactory.defaultBotName(slot) }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(GameplayAvatarView(this).apply { bind(session, player, player.initial, 18f) },
                LinearLayout.LayoutParams(150, 150))
            row.addView(TextView(this).apply { text = "${player.name} · ${ProfileAvatarCatalog.find(ProfileAvatarCatalog.forBotSlot(slot)).role.name}"; textSize = 20f })
            column.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
