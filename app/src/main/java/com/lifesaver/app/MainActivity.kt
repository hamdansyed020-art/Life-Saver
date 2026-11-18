package com.lifesaver.app

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var timerDisplay: TextView
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var minutesSeekBar: SeekBar
    private lateinit var selectedMinutesLabel: TextView
    private lateinit var btSwitch: Switch
    private lateinit var morningSwitch: Switch

    private var selectedMinutes = 30

    private val uiUpdater = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUiFromService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()

        val prefs = getSharedPreferences("life_saver_prefs", MODE_PRIVATE)
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Life Saver"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Sleep Timer & Morning Banner"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(4), 0, dp(28))
        }

        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = roundedBox("#16161A", 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        timerDisplay = TextView(this).apply {
            text = "30:00"
            textSize = 52f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Ready to sleep"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        timerCard.addView(timerDisplay)
        timerCard.addView(statusText)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }

        listOf(15, 30, 45, 60).forEach { mins ->
            val btn = Button(this).apply {
                text = "${mins}m"
                setTextColor(Color.WHITE)
                textSize = 14f
                isAllCaps = false
                background = roundedBox("#222228", 12)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                setOnClickListener {
                    selectedMinutes = mins
                    minutesSeekBar.progress = mins
                    selectedMinutesLabel.text = "Timer duration: $mins min"
                    if (!LifeSaverService.isTimerRunning) {
                        timerDisplay.text = String.format("%02d:00", mins)
                    }
                }
            }
            presetRow.addView(btn)
        }

        selectedMinutesLabel = TextView(this).apply {
            text = "Timer duration: 30 min"
            textSize = 14f
            setTextColor(Color.parseColor("#D1D1D6"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        minutesSeekBar = SeekBar(this).apply {
            max = 120
            progress = 30
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val mins = if (progress < 1) 1 else progress
                    selectedMinutes = mins
                    selectedMinutesLabel.text = "Timer duration: $mins min"
                    if (!LifeSaverService.isTimerRunning) {
                        timerDisplay.text = String.format("%02d:00", mins)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val settingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = roundedBox("#16161A", 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        }

        btSwitch = Switch(this).apply {
            text = "Turn off Bluetooth when timer ends"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = prefs.getBoolean("bt_off", true)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("bt_off", checked).apply()
            }
        }

        morningSwitch = Switch(this).apply {
            text = "Show \"Good Morning\" banner on unlock"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = prefs.getBoolean("morning_greeting", true)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("morning_greeting", checked).apply()
                syncBackgroundService()
            }
        }

        settingsCard.addView(btSwitch)
        settingsCard.addView(morningSwitch)

        actionButton = Button(this).apply {
            text = "Start Sleep Timer"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            background = roundedBox("#2E7D32", 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                if (LifeSaverService.isTimerRunning) {
                    sendServiceAction(LifeSaverService.ACTION_STOP_TIMER)
                } else {
                    val intent = Intent(this@MainActivity, LifeSaverService::class.java).apply {
                        action = LifeSaverService.ACTION_START_TIMER
                        putExtra("minutes", selectedMinutes)
                    }
                    startForegroundService(intent)
                }
            }
        }

        val testMorningBtn = Button(this).apply {
            text = "Test \"Good Morning\" Banner"
            setTextColor(Color.parseColor("#A1A1AA"))
            textSize = 14f
            isAllCaps = false
            background = roundedBox("#16161A", 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            )
            setOnClickListener {
                sendServiceAction(LifeSaverService.ACTION_TEST_MORNING)
            }
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(timerCard)
        container.addView(presetRow)
        container.addView(selectedMinutesLabel)
        container.addView(minutesSeekBar)
        container.addView(settingsCard)
        container.addView(actionButton)
        container.addView(testMorningBtn)

        root.addView(container)
        setContentView(root)

        syncBackgroundService()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LifeSaverService.BROADCAST_UI_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uiUpdater, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiUpdater, filter)
        }
        updateUiFromService()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(uiUpdater) } catch (_: Exception) {}
    }

    private fun updateUiFromService() {
        if (LifeSaverService.isTimerRunning) {
            val sec = LifeSaverService.secondsRemaining
            timerDisplay.text = String.format("%02d:%02d", sec / 60, sec % 60)
            statusText.text = "Timer running — sleep well"
            actionButton.text = "Cancel Timer"
            actionButton.background = roundedBox("#C62828", 14)
        } else {
            timerDisplay.text = String.format("%02d:00", selectedMinutes)
            statusText.text = "Ready to sleep"
            actionButton.text = "Start Sleep Timer"
            actionButton.background = roundedBox("#2E7D32", 14)
        }
    }

    private fun syncBackgroundService() {
        val prefs = getSharedPreferences("life_saver_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("morning_greeting", true)) {
            sendServiceAction(LifeSaverService.ACTION_KEEP_ALIVE)
        } else if (!LifeSaverService.isTimerRunning) {
            stopService(Intent(this, LifeSaverService::class.java))
        }
    }

    private fun sendServiceAction(actionName: String) {
        val intent = Intent(this, LifeSaverService::class.java).apply { action = actionName }
        startForegroundService(intent)
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            requestPermissions(perms.toTypedArray(), 101)
        }
    }

    private fun roundedBox(hex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

class LifeSaverService : Service() {

    companion object {
        const val ACTION_START_TIMER = "com.lifesaver.START_TIMER"
        const val ACTION_STOP_TIMER = "com.lifesaver.STOP_TIMER"
        const val ACTION_KEEP_ALIVE = "com.lifesaver.KEEP_ALIVE"
        const val ACTION_TEST_MORNING = "com.lifesaver.TEST_MORNING"
        const val BROADCAST_UI_UPDATE = "com.lifesaver.UI_UPDATE"
        const val CHANNEL_TIMER_ID = "life_saver_timer"
        const val CHANNEL_MORNING_ID = "life_saver_morning"

        var isTimerRunning = false
        var secondsRemaining = 0L
    }

    private var countDownTimer: CountDownTimer? = null
    private var originalVolume = -1

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                checkAndShowMorningBanner()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildTimerNotification("Life Saver is ready", showCancel = false))

        when (intent?.action) {
            ACTION_START_TIMER -> {
                val mins = intent.getIntExtra("minutes", 30)
                startSleepTimer(mins * 60 * 1000L)
            }
            ACTION_STOP_TIMER -> {
                cancelTimer()
            }
            ACTION_TEST_MORNING -> {
                showMorningBanner()
            }
            ACTION_KEEP_ALIVE -> {
                if (!isTimerRunning) {
                    updateTimerNotification("Morning banner active", showCancel = false)
                }
            }
        }
        return START_STICKY
    }

    private fun startSleepTimer(durationMs: Long) {
        countDownTimer?.cancel()
        isTimerRunning = true
        originalVolume = -1

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = millisUntilFinished / 1000
                val text = String.format("Sleep timer: %02d:%02d", secondsRemaining / 60, secondsRemaining % 60)
                updateTimerNotification(text, showCancel = true)
                sendBroadcast(Intent(BROADCAST_UI_UPDATE).setPackage(packageName))

                if (secondsRemaining in 1..10) {
                    if (originalVolume == -1) {
                        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    }
                    val targetVol = (originalVolume * (secondsRemaining / 10.0)).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                secondsRemaining = 0
                executeSleepActions()
                sendBroadcast(Intent(BROADCAST_UI_UPDATE).setPackage(packageName))
            }
        }.start()
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        secondsRemaining = 0
        if (originalVolume != -1) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            originalVolume = -1
        }
        sendBroadcast(Intent(BROADCAST_UI_UPDATE).setPackage(packageName))
        updateTimerNotification("Morning banner active", showCancel = false)

        val prefs = getSharedPreferences("life_saver_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("morning_greeting", true)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun executeSleepActions() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener {}
            .build()
        audioManager.requestAudioFocus(focusRequest)

        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))

        val prefs = getSharedPreferences("life_saver_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("bt_off", true)) {
            turnOffBluetooth()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (originalVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                originalVolume = -1
            }
            audioManager.abandonAudioFocusRequest(focusRequest)
        }, 2000)

        updateTimerNotification("Sleep actions complete. Goodnight!", showCancel = false)

        if (!prefs.getBoolean("morning_greeting", true)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun turnOffBluetooth() {
        try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter
            if (adapter != null && adapter.isEnabled) {
                adapter.disable()
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun checkAndShowMorningBanner() {
        val prefs = getSharedPreferences("life_saver_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("morning_greeting", true)) return

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val lastGreetDay = prefs.getInt("last_greet_day", -1)

        if (hour in 5..11 && lastGreetDay != today) {
            prefs.edit().putInt("last_greet_day", today).apply()
            showMorningBanner()
        }
    }

    private fun showMorningBanner() {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = Notification.Builder(this, CHANNEL_MORNING_ID)
            .setContentTitle("Good Morning!")
            .setContentText("Hope you slept well.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .build()
        nm.notify(2, notif)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val timerChannel = NotificationChannel(
            CHANNEL_TIMER_ID,
            "Sleep Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }

        val morningChannel = NotificationChannel(
            CHANNEL_MORNING_ID,
            "Good Morning Banner",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        nm.createNotificationChannel(timerChannel)
        nm.createNotificationChannel(morningChannel)
    }

    private fun buildTimerNotification(text: String, showCancel: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IM
