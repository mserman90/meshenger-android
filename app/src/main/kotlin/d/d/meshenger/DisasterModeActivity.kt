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
import android.view.MotionEvent
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
    private lateinit var textBeaconStatus: TextView
    private lateinit var containerInteractiveControls: View
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
        textBeaconStatus = findViewById(R.id.text_beacon_status)
        containerInteractiveControls = findViewById(R.id.container_interactive_controls)
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

        // Disabled touch listener that prompts user if broadcast is OFF
        val disabledTouchListener = View.OnTouchListener { _, event ->
            if (!DisasterModeManager.isDisasterModeActive()) {
                if (event.action == MotionEvent.ACTION_UP) {
                    showDisabledWarning()
                }
                return@OnTouchListener true
            }
            false
        }

        radioSafe.setOnTouchListener(disabledTouchListener)
        radioHelp.setOnTouchListener(disabledTouchListener)
        radioMedical.setOnTouchListener(disabledTouchListener)
        editNotes.setOnTouchListener(disabledTouchListener)

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
            if (DisasterModeManager.isDisasterModeActive()) {
                DisasterModeManager.currentStatus = when (checkedId) {
                    R.id.radio_help -> DisasterSignal.StatusType.HELP
                    R.id.radio_medical -> DisasterSignal.StatusType.MEDICAL
                    else -> DisasterSignal.StatusType.SAFE
                }
            }
        }

        editNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (DisasterModeManager.isDisasterModeActive()) {
                    DisasterModeManager.medicalNotes = s?.toString() ?: ""
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnWhistle.setOnClickListener {
            checkBroadcastActive {
                if (DisasterModeManager.isWhistleActive()) {
                    DisasterModeManager.stopWhistle()
                } else {
                    DisasterModeManager.startWhistle()
                }
                updateButtonStates()
            }
        }

        btnStrobe.setOnClickListener {
            checkBroadcastActive {
                if (DisasterModeManager.isStrobeActive()) {
                    DisasterModeManager.stopStrobe(this)
                } else {
                    DisasterModeManager.startStrobe(this)
                }
                updateButtonStates()
            }
        }

        updateButtonStates()
    }

    private fun showDisabledWarning() {
        Toast.makeText(this, "⚠️ Lütfen önce Afet Kipi Yayınını etkinleştirin! / Please enable Disaster Broadcast first!", Toast.LENGTH_SHORT).show()
    }

    private fun checkBroadcastActive(onActiveAction: () -> Unit) {
        if (DisasterModeManager.isDisasterModeActive()) {
            onActiveAction()
        } else {
            showDisabledWarning()
        }
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
        val isActive = DisasterModeManager.isDisasterModeActive()
        containerInteractiveControls.alpha = if (isActive) 1.0f else 0.5f

        if (isActive) {
            textBeaconStatus.text = "Yayın Açık (Wi-Fi & Bluetooth Active)"
            textBeaconStatus.setTextColor(Color.parseColor("#4ADE80"))
        } else {
            textBeaconStatus.text = "Yayın Kapalı / Broadcast Inactive"
            textBeaconStatus.setTextColor(Color.parseColor("#94A3B8"))
        }

        if (DisasterModeManager.isWhistleActive()) {
            btnWhistle.text = "🔊 SIREN DURDUR"
            btnWhistle.setBackgroundColor(Color.RED)
        } else {
            btnWhistle.text = "🔊 ACOUSTIC SIREN\n(3.5 kHz DÜDÜK)"
            btnWhistle.setBackgroundColor(Color.parseColor("#D97706"))
        }

        if (DisasterModeManager.isStrobeActive()) {
            btnStrobe.text = "🔦 FLAŞ DURDUR"
            btnStrobe.setBackgroundColor(Color.RED)
        } else {
            btnStrobe.text = "🔦 VISUAL SOS\n(STROBE FLAŞ)"
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
                    badgeView.text = "🔴 SOS / RED ALERT (HELP NEEDED)"
                    badgeView.setBackgroundColor(Color.parseColor("#DC2626"))
                }
                DisasterSignal.StatusType.MEDICAL -> {
                    badgeView.text = "🔵 MEDICAL ASSISTANCE NEEDED"
                    badgeView.setBackgroundColor(Color.parseColor("#2563EB"))
                }
                DisasterSignal.StatusType.SAFE -> {
                    badgeView.text = "🟢 STATUS OK / SAFE"
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
