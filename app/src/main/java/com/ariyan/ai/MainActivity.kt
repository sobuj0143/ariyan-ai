package com.ariyan.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var chat: TextView
    private lateinit var keyBox: EditText
    private val ui = Handler(Looper.getMainLooper())
    private val model = "gemini-flash-latest"

    override fun onInit(status: Int) {}

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("a", 0)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 80, 32, 32)

        keyBox = EditText(this)
        keyBox.hint = "Gemini API key"
        keyBox.setText(prefs.getString("k", ""))

        val save = Button(this)
        save.text = "Key save karo"
        save.setOnClickListener {
            prefs.edit().putString("k", keyBox.text.toString().trim()).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        chat = TextView(this)
        chat.textSize = 18f
        val scroll = ScrollView(this)
        scroll.addView(chat)

        val talk = Button(this)
        talk.text = "Kotha bolo"
        talk.setOnClickListener {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            startActivityForResult(i, 1)
        }

        root.addView(keyBox)
        root.addView(save)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(talk)
        setContentView(root)
        tts = TextToSpeech(this, this)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK) {
            val q = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (q != null) ask(q)
        }
    }

    private fun ask(q: String) {
        chat.append("Tumi: $q\n\n")
        val key = getSharedPreferences("a", 0).getString("k", "") ?: ""
        thread {
            var ok = true
            val reply = try {
                val part = { t: String -> JSONObject().put("parts", JSONArray().put(JSONObject().put("text", t))) }
                val body = JSONObject()
                    .put("systemInstruction", part("You are Ariyan, a friendly personal assistant. Reply briefly in the same language the user speaks."))
                    .put("contents", JSONArray().put(part(q)))
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
                if (code in 200..299) {
                    JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                } else {
                    ok = false
                    "Error $code: $txt"
                }
            } catch (e: Exception) {
                ok = false
                "Error: ${e.message}"
            }
            ui.post {
                chat.append("Ariyan: $reply\n\n")
                if (ok) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "a")
            }
        }
    }
}
