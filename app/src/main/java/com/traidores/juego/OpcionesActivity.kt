package com.traidores.juego

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class OpcionesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opciones)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Sound Switch
        val switchSound: SwitchCompat = findViewById(R.id.switchSound)
        val sharedPref = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        val isSoundOn = sharedPref.getBoolean("sound_on", true)
        switchSound.isChecked = isSoundOn

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("sound_on", isChecked).apply()
            val msg = if (isChecked) "Sonido Activado" else "Sonido Silenciado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Language Spinner
        val spinnerLanguage: Spinner = findViewById(R.id.spinnerLanguage)
        val languages = arrayOf("Español (ES)", "English (EN)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        // Set default selection
        val selectedLang = sharedPref.getString("language", "Español (ES)")
        val defaultPos = languages.indexOf(selectedLang)
        if (defaultPos >= 0) {
            spinnerLanguage.setSelection(defaultPos)
        }

        // Handle item selection without firing toast during initialization
        var isInit = true
        spinnerLanguage.post {
            spinnerLanguage.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val lang = languages[position]
                    sharedPref.edit().putString("language", lang).apply()
                    if (!isInit) {
                        Toast.makeText(this@OpcionesActivity, "Idioma cambiado a: $lang", Toast.LENGTH_SHORT).show()
                    }
                    isInit = false
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })
        }

        // Account Logic
        val etUsername: EditText = findViewById(R.id.etUsername)
        val etPassword: EditText = findViewById(R.id.etPassword)
        val btnLogin: Button = findViewById(R.id.btnLogin)
        val btnRegister: Button = findViewById(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresá usuario y contraseña.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "¡Bienvenido/a, $username!", Toast.LENGTH_LONG).show()
                finish() // simulated login success
            }
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresá usuario y contraseña para registrarte.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Registro exitoso para $username. ¡Ya podés iniciar sesión!", Toast.LENGTH_LONG).show()
            }
        }

        // Focus navigation from shortcuts
        if (intent.getBooleanExtra("focus_language", false)) {
            spinnerLanguage.requestFocus()
            Toast.makeText(this, "Seleccioná tu idioma preferido", Toast.LENGTH_SHORT).show()
        } else if (intent.getBooleanExtra("focus_account", false)) {
            etUsername.requestFocus()
            Toast.makeText(this, "Iniciá sesión o registrate para jugar en línea", Toast.LENGTH_SHORT).show()
        }
    }
}
