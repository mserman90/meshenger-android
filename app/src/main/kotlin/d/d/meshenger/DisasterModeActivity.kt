/*
 * Copyright (C) 2026 Meshenger Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package d.d.meshenger

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import d.d.meshenger.disaster.DisasterModeManager
import d.d.meshenger.disaster.DisasterSignal
import java.text.SimpleDateFormat
import java.util.*

class DisasterModeActivity : BaseActivity(), DisasterModeManager.OnSignalReceivedListener {

    private lateinit var switchDisasterMode: SwitchCompat
    private lateinit var radioGroupStatus: RadioGroup
    private lateinit var radioSafe: RadioButton
    private lateinit var radioHelp: RadioButton
    private lateinit var radioMedical: RadioButton
    private lateinit var editNotes: EditText
    private lateinit var btnWhistle: Button
    private lateinit var btnStrobe: Button
    private lateinit var listSignals: ListView
    private lateinit var textEmptySignals: TextView

    private lateinit var adapter: SignalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disaster_mode)

        val toolbar = findViewById<Toolbar>(R.id.disaster_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        switchDisasterMode = findViewById(R.id.switch_disaster_mode)
        radioGroupStatus = findViewById(R.id.radio_group_status)
        radioSafe = findViewById(R.id.radio_safe)
        radioHelp = findViewById(R.id.radio_help)
        radioMedical = findViewById(R.id.radio_medical)
        editNotes = findViewById(R.id.edit_medical_notes)
        btnWhistle = findViewById(R.id.btn_whistle)
        btnStrobe = findViewById(R.id.btn_strobe)
        listSignals = findViewById(R.id.list_disaster_signals)
        textEmptySignals = findViewById(R.id.text_empty_signals)

        adapter = SignalAdapter(this, ArrayList())
        listSignals.adapter = adapter

        // Sync initial state
        switchDisasterMode.isChecked = DisasterModeManager.isDisasterModeActive()
        
        when (DisasterModeManager.currentStatus) {
            DisasterSignal.StatusType.SAFE -> radioSafe.isChecked = true
            DisasterSignal.StatusType.HELP -> radioHelp.isChecked = true
            DisasterSignal.StatusType.MEDICAL -> radioMedical.isChecked = true
        }

        editNotes.setText(DisasterModeManager.medicalNotes)

        switchDisasterMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndRequestLocationPermissions()
                DisasterModeManager.startDisasterMode(this)
                Toast.makeText(this, "Afet Kipi Yayını Başlatıldı (Wi-Fi UDP + Bluetooth)", Toast.LENGTH_SHORT).show()
            } else {
                DisasterModeManager.stopDisasterMode()
                Toast.makeText(this, "Afet Kipi Yayını Durduruldu", Toast.LENGTH_SHORT).show()
            }
            updateButtonStates()
        }

        radioGroupStatus.setOnCheckedChangeListener { _, checkedId ->
            DisasterModeManager.currentStatus = when (checkedId) {
                R.id.radio_help -> DisasterSignal.StatusType.HELP
                R.id.radio_medical -> DisasterSignal.StatusType.MEDICAL
                else -> DisasterSignal.StatusType.SAFE
            }
        }

        editNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                DisasterModeManager.medicalNotes = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnWhistle.setOnClickListener {
            if (DisasterModeManager.isWhistleActive()) {
                DisasterModeManager.stopWhistle()
            } else {
                DisasterModeManager.startWhistle()
            }
            updateButtonStates()
        }

        btnStrobe.setOnClickListener {
            if (DisasterModeManager.isStrobeActive()) {
                DisasterModeManager.stopStrobe(this)
            } else {
                DisasterModeManager.startStrobe(this)
            }
            updateButtonStates()
        }

        updateButtonStates()
    }

    private fun checkAndRequestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            val missing = permissions.filter { !Utils.hasPermission(this, it) }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 101)
            }
        }
    }

    private fun updateButtonStates() {
        if (DisasterModeManager.isWhistleActive()) {
            btnWhistle.text = "🔊 Düdük Durdur"
            btnWhistle.setBackgroundColor(Color.RED)
        } else {
            btnWhistle.text = "🔊 Düdük (Whistle)"
            btnWhistle.setBackgroundColor(Color.parseColor("#D97706"))
        }

        if (DisasterModeManager.isStrobeActive()) {
            btnStrobe.text = "🔦 Flaş Durdur"
            btnStrobe.setBackgroundColor(Color.RED)
        } else {
            btnStrobe.text = "🔦 SOS Flaş (Strobe)"
            btnStrobe.setBackgroundColor(Color.parseColor("#4F46E5"))
        }
    }

    override fun onResume() {
        super.onResume()
        DisasterModeManager.registerListener(this)
        updateButtonStates()
    }

    override fun onPause() {
        super.onPause()
        DisasterModeManager.unregisterListener(this)
    }

    override fun onSignalsUpdated(signals: List<DisasterSignal>) {
        runOnUiThread {
            adapter.clear()
            adapter.addAll(signals)
            adapter.notifyDataSetChanged()
            if (signals.isEmpty()) {
                textEmptySignals.visibility = View.VISIBLE
                listSignals.visibility = View.GONE
            } else {
                textEmptySignals.visibility = View.GONE
                listSignals.visibility = View.VISIBLE
            }
        }
    }

    private class SignalAdapter(
        context: Context,
        signals: List<DisasterSignal>
    ) : ArrayAdapter<DisasterSignal>(context, 0, signals) {

        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_disaster_signal, parent, false)
            val signal = getItem(position) ?: return view

            val nameView = view.findViewById<TextView>(R.id.signal_sender_name)
            val badgeView = view.findViewById<TextView>(R.id.signal_status_badge)
            val locView = view.findViewById<TextView>(R.id.signal_location_text)
            val notesView = view.findViewById<TextView>(R.id.signal_notes_text)
            val timeView = view.findViewById<TextView>(R.id.signal_time_text)

            nameView.text = signal.senderName

            when (signal.status) {
                DisasterSignal.StatusType.HELP -> {
                    badgeView.text = "🟥 YARDIM İSTİYOR"
                    badgeView.setBackgroundColor(Color.RED)
                }
                DisasterSignal.StatusType.MEDICAL -> {
                    badgeView.text = "🟦 TIBBİ DESTEK"
                    badgeView.setBackgroundColor(Color.parseColor("#2563EB"))
                }
                DisasterSignal.StatusType.SAFE -> {
                    badgeView.text = "🟩 GÜVENDE"
                    badgeView.setBackgroundColor(Color.parseColor("#16A34A"))
                }
            }

            val locStr = if (signal.latitude != null && signal.longitude != null) {
                "Konum: Enlem ${String.format(Locale.US, "%.5f", signal.latitude)}, Boylam ${String.format(Locale.US, "%.5f", signal.longitude)} (IP: ${signal.ipAddress})"
            } else {
                "Konum: GPS Alınamadı (IP: ${signal.ipAddress})"
            }
            locView.text = locStr

            notesView.text = if (signal.medicalNotes.isNotEmpty()) "Not: ${signal.medicalNotes}" else "Not: Bilgi verilmedi"
            val hopStr = if (signal.hopCount == 0) "Doğrudan İletim" else "Aracı Cihazlar Üzerinden (${signal.hopCount} Sıçrama / Relay)"
            timeView.text = "Son Yayın: ${dateFormat.format(Date(signal.timestamp))} • $hopStr"

            return view
        }
    }
}
