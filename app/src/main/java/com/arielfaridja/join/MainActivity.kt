package com.arielfaridja.join

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color.GREEN
import android.graphics.Color.RED
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main._red_word_button
import kotlinx.android.synthetic.main.fragment_add_word.*
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity(), RecognitionListener {

    //main Views
    private lateinit var wordViewer: TextView
    private lateinit var addWordButton: FloatingActionButton

    //add word views
    private lateinit var greenWordButton: Button
    private lateinit var redWordButton: Button
    private lateinit var addWordBox: EditText

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val speechStreamService: SpeechStreamService? = null

    //helpful properties
    private var result: JSONObject? = null //to get the recognition input as JSON
    private var resultStr = "" //to get only the recognized text without other data
    internal var wordToAdd = "" //when adding word this contain the word

    private var redWords = arrayListOf("pay attention", "stop", "be careful")
    private var greenWords = arrayListOf("art show", "museum", "artshow")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission: ", "Granted")
            } else {
                Log.i("Permission: ", "Denied")
            }
        }

    //navigation vars
 //   private val navHostFragment =
//        supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
 //   private val navController = navHostFragment.navController

    //Constants
    private val STATE_START = 0
    private val STATE_READY = 1
    private val STATE_DONE = 2
    private val LISTENING = 11
    private val NOT_LISTENING = 12
    private val TAG = "_____Main Activity_____"
    private val NOTIFICATION_ID = 888
    private val RECOGNITION_CHANNEL = "word recognized"

    private var currentState = STATE_START
    private var listeningState = NOT_LISTENING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initModel()
        createNotificationChannel()
        getPermissions()
        LibVosk.setLogLevel(LogLevel.INFO)
        readWordsFromFile()
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container, MainFragment::class.java, null)
                .commit()
        }
    }

    /***
     * we want to enable user to save words on device. this app o that
     * @param color indicate words list color (green/red)
     */
    private fun writeWordsIntoFile(color: Int) {
        if (color == RED) {
            File(filesDir, "red_words").writeText(wordListToString(redWords))
        }
        if (color == GREEN) {
            File(filesDir, "green_words").writeText(wordListToString(greenWords))
        }
    }

    /***
     * on start-up we have to read saved words from device storage
     *
     */
    private fun readWordsFromFile() {
        if (File(filesDir, "green_words").exists())
            greenWords = stringToWordList(File(filesDir, "green_words").readText())
        else {
            File(filesDir, "green_words").createNewFile()
            writeWordsIntoFile(GREEN)
        }
        if (File(filesDir, "red_words").exists())
            redWords = stringToWordList(File(filesDir, "red_words").readText())
        else {
            File(filesDir, "green_words").createNewFile()
            writeWordsIntoFile(RED)
        }
    }

    /***
     * To make files write easier we parse ArrayList into a string n that way: "first|second|third..."
     * @param list ArrayList we want to parse
     */
    private fun wordListToString(list: ArrayList<String>): String {
        var str = list[0]
        for (word in list.subList(1, list.count()))
            str = "$str|$word"
        return str
    }

    /**
     * make reading files easier. list saved on file as "first|second|third"... so here we parse it into an ArrayList
     * @param str the string we parse into an Arraylist
     */
    private fun stringToWordList(str: String): ArrayList<String> {
        return ArrayList(str.split("|"))
    }


    /**
     * Create the NotificationChannel, but only on API 26+ because
     * the NotificationChannel class is new and not in the support library
     */
    private fun createNotificationChannel() {

        val name = getString(R.string.channel_recognition_name)
        val descriptionText = getString(R.string.channel_recognition_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(RECOGNITION_CHANNEL, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun initModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model: Model ->
                this.model = model
                currentState = STATE_READY
                Log.i(TAG, "initModel is over")
                getActivityReady()
            },
            { e: java.lang.Exception -> throw Exception(e) })
    }

    private fun getActivityReady() {
        setViews()
        recognizeMicrophone()
    }

    private fun getPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                {}
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            ) -> {//TODO: add shouldShowRequestPermissionRationale implementation
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }

    /***
     * get all views from layout to code
     */
    private fun setViews() {
        wordViewer = findViewById(R.id.word_viewer)
        addWordButton = findViewById(R.id.add_word_button)
        greenWordButton = findViewById(R.id._green_word_button)
        redWordButton = findViewById(R.id._red_word_button)
        addWordBox = findViewById(R.id.add_word_box)
        setListeners()
    }

    /**
     * set views Listeners
     */
    private fun setListeners() {
        addWordButton.setOnClickListener { addWordClick() }



    }

    /**
     * when we choose to add word, we stop the speech listening service,
     * disable main views, and enable dedicated view.
     * (yes, I know, using fragment is better, and less messy. but I'm lazy, and don't want to change what I only did)
     */
    private fun addWordClick() {
        if (speechService != null) {
            currentState = STATE_DONE
            speechService!!.stop()
            speechService = null
            listeningState = NOT_LISTENING
        }

        fragment_container.findNavController().navigate(R.id.action_mainFragment_to_addWordFragment)
        resultStr = ""
        //enableAddWordViews()
        //disableMainViews()

    }

    /***
     * add user's word to red or green list
     * @param view the button that clicked. for red word or green word
     * TODO save added words to database too. not only as lists.
     */
    internal fun addWord(view: View?) {
        when {
            wordToAdd == "" -> {
                Toast.makeText(this, "You have to set a word to add.", Toast.LENGTH_LONG)
                return
            }
            view == red_word_button -> {
                redWords.add(wordToAdd)
                writeWordsIntoFile(RED)
            }
            view == green_word_button -> {
                greenWords.add(wordToAdd)
                writeWordsIntoFile(GREEN)
            }

        }
        wordToAdd = ""
        addWordBox.setText("")
        disableAddWordViews()
        enableMainViews()
        recognizeMicrophone()
    }

    /**
     * disable the main views
     */
    private fun disableMainViews() {
        wordViewer.visibility = View.GONE
        addWordButton.visibility = View.GONE
    }

    /**
     * enable the main views
     */
    private fun enableMainViews() {
        wordViewer.visibility = View.VISIBLE
        addWordButton.visibility = View.VISIBLE
    }

    /**
     * enable views for adding a word
     */
    private fun enableAddWordViews() {
        greenWordButton.isEnabled = true
        redWordButton.isEnabled = true
        addWordBox.isEnabled = true
        greenWordButton.visibility = View.VISIBLE
        redWordButton.visibility = View.VISIBLE
        addWordBox.visibility = View.VISIBLE
    }

    /**
     * disable views for adding a word
     */
    private fun disableAddWordViews() {
        greenWordButton.isEnabled = false
        redWordButton.isEnabled = false
        addWordBox.isEnabled = false
        greenWordButton.visibility = View.GONE
        redWordButton.visibility = View.GONE
        addWordBox.visibility = View.GONE

    }


    /***
     * called when a word from green or red list recognized
     * @param wordColor indicate if the recognized word is red or green
     */
    private fun wordRecognition(wordColor: Int) {
        var vib = getSystemService(Context.VIBRATOR_SERVICE)
        if (wordColor == RED) {
            background.setBackgroundColor(RED)
            (vib as Vibrator).vibrate(
                VibrationEffect.createOneShot(
                    500,
                    255
                )
            )
            wordViewer.visibility = View.VISIBLE
            wordViewer.text = resultStr.findAnyOf(redWords)!!.second
        } else if (wordColor == GREEN) {
            background.setBackgroundColor(GREEN)
            (vib as Vibrator).vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 200, 200, 200),
                    intArrayOf(0, 100, 0, 100, 0),
                    -1
                )
            )
            wordViewer.visibility = View.VISIBLE
            wordViewer.text = resultStr.findAnyOf(greenWords)!!.second
        }
        sendNotification(wordColor)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                background.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
                wordViewer.visibility = View.GONE
                wordViewer.text = ""
            },
            1200
        )
    }

    /**send a notification with the word and the color (so can caught on a smartwatch)
     * @param color by this we know if to send green circle or red circle
     */
    private fun sendNotification(color: Int) {
        var text = ""
        var builder = NotificationCompat.Builder(this, RECOGNITION_CHANNEL)

        /*val intent = Intent(this, MainActivity::class.java)
        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(intent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }*/

        if (color == RED)
            text = resultStr.findAnyOf(redWords)!!.second
        else if (color == GREEN)
            text = resultStr.findAnyOf(greenWords)!!.second
        if (color == RED)
            builder
                .setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.drawable.red_circle))
                .setContentTitle(text)
                .setContentText("").priority = NotificationCompat.PRIORITY_HIGH
        else if (color == GREEN)
            builder
                .setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.drawable.green_circle))
                .setContentTitle(text)
                .setContentText("").priority = NotificationCompat.PRIORITY_HIGH
        var notification = builder.build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Called when partial recognition result is available.
     */
    override fun onPartialResult(hypothesis: String?) {
        result = JSONObject(hypothesis)
        resultStr = result!!["partial"].toString()
        Log.d(TAG, hypothesis + '\n')
        if (resultStr.findAnyOf(redWords) != null) {
            wordRecognition(RED)
        } else if (resultStr.findAnyOf(greenWords) != null) {
            wordRecognition(GREEN)
        }
    }

    /**
     * Called after silence occurred.
     */
    override fun onResult(hypothesis: String?) {
        result = JSONObject(hypothesis)
        if (result!!.has("text"))
            resultStr = result!!["text"].toString()
        /*if(resultStr.findAnyOf(warningWords) != null) {
            warningRecognition()
        }*/
    }

    /**
     * Called after stream end.
     */
    override fun onFinalResult(hypothesis: String?) {
        result = JSONObject(hypothesis)
        resultStr = ""
        if (result!!.has("text"))
            resultStr = result!!["text"] as String
        wordViewer.text = resultStr
    }

    /**
     * Called when an error occurs.
     */
    override fun onError(exception: Exception?) {
        TODO("Not yet implemented")
    }

    /**
     * Called after timeout expired
     */
    override fun onTimeout() {
        TODO("Not yet implemented")
    }


    private fun recognizeMicrophone() {
        if (speechService != null) {
            currentState = STATE_DONE
            speechService!!.stop()
            speechService = null
            listeningState = NOT_LISTENING
        } else {
            try {
                val rec = Recognizer(model, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService!!.startListening(this)
                listeningState = LISTENING
            } catch (e: IOException) {
                throw e
            }
        }
    }
}