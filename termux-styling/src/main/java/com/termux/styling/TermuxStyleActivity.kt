package com.termux.styling

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

const val DEFAULT_FILENAME = "Default"

fun capitalize(str: String): String {
    var lastWhitespace = true
    val chars = str.toCharArray()
    for (i in chars.indices) {
        if (Character.isLetter(chars[i])) {
            if (lastWhitespace) chars[i] = Character.toUpperCase(chars[i])
            lastWhitespace = false
        } else {
            lastWhitespace = Character.isWhitespace(chars[i])
        }
    }
    return String(chars)
}

class TermuxStyleActivity : Activity() {

    internal class Selectable(val fileName: String) {
        val displayName: String

        init {
            var name = fileName.replace('-', ' ')
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex != -1) name = name.substring(0, dotIndex)

            this.displayName = capitalize(name)
        }

        override fun toString(): String {
            return displayName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avoid dim behind:
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(R.layout.layout)

        val colorSpinner = findViewById<Button>(R.id.color_spinner)
        val fontSpinner = findViewById<Button>(R.id.font_spinner)

        val colorAdapter = ArrayAdapter<Selectable>(this, android.R.layout.simple_spinner_dropdown_item)

        colorSpinner.setOnClickListener {
            val dialog = AlertDialog.Builder(this@TermuxStyleActivity).setAdapter(colorAdapter) { _, which -> sendFile(colorAdapter.getItem(which), true) }.create()
            dialog.setOnShowListener {
                val lv = dialog.listView
                lv.setOnItemLongClickListener { _, _, position, _ ->
                    showLicense(colorAdapter.getItem(position) as Selectable, true)
                    true
                }
            }
            dialog.show()
        }

        val fontAdapter = ArrayAdapter<Selectable>(this, android.R.layout.simple_spinner_dropdown_item)
        fontSpinner.setOnClickListener {
            val dialog = AlertDialog.Builder(this@TermuxStyleActivity).setAdapter(fontAdapter) { _, which -> sendFile(fontAdapter.getItem(which), false) }.create()
            dialog.setOnShowListener {
                val lv = dialog.listView
                lv.setOnItemLongClickListener { _, _, position, _ ->
                    showLicense(fontAdapter.getItem(position) as Selectable, false)
                    true
                }
            }
            dialog.show()
        }

        val colorList = ArrayList<Selectable>()
        val fontList = ArrayList<Selectable>()
        for (assetType in arrayOf("colors", "fonts")) {
            val isColors = assetType == "colors"
            val assetsFileExtension = if (isColors) ".properties" else ".ttf"
            val currentList = if (isColors) colorList else fontList

            currentList.add(Selectable(DEFAULT_FILENAME))

            try {
                assets.list(assetType)!!
                        .filter { it.endsWith(assetsFileExtension) }
                        .forEach { currentList.add(Selectable(it)) }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }

        colorAdapter.addAll(colorList)
        fontAdapter.addAll(fontList)
    }

    private fun showLicense(mCurrentSelectable: Selectable, colors: Boolean) {
        try {
            val assetsFolder = if (colors) "colors" else "fonts"
            var fileName = mCurrentSelectable.fileName
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex != -1) fileName = fileName.substring(0, dotIndex)
            fileName += ".txt"

            assets.open("$assetsFolder/$fileName").use { `in` ->
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
                val license = SpannableString(String(buffer))
                Linkify.addLinks(license, Linkify.ALL)
                val dialog = AlertDialog.Builder(this)
                        .setTitle(mCurrentSelectable.displayName)
                        .setMessage(license)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                (dialog.findViewById<View>(android.R.id.message) as TextView).movementMethod = LinkMovementMethod.getInstance()
            }
        } catch (e: IOException) {
            // Ignore.
        }

    }

    private fun sendFile(mCurrentSelectable: Selectable?, colors: Boolean) {
        try {
            // Note: Must match string in TermuxActivity#onCreate():
            val newStyleIntent = Intent("com.termux.app.NEW_STYLE")
            newStyleIntent.setClassName("com.termux", "com.termux.app.TermuxActivityInternal")

            val assetsFolder = if (colors) "colors" else "fonts"
            var bytesToSend: ByteArray
            val defaultChoice = mCurrentSelectable!!.fileName == DEFAULT_FILENAME
            if (defaultChoice) {
                if (colors) {
                    bytesToSend = "# Using default color theme.".toByteArray(StandardCharsets.UTF_8)
                } else {
                    // Just leave an empty font file as a marker.
                    bytesToSend = ByteArray(0)
                }
            } else {
                bytesToSend = assets.open(assetsFolder + "/" + mCurrentSelectable.fileName).readBytes()
            }

            Log.e("termux", "SENDING BYTES: " + bytesToSend.size);
            newStyleIntent.putExtra("com.termux.app.extra.NEW_" + if (colors) "COLORS" else "FONT", bytesToSend)
            val fileName = if (defaultChoice) { "default" } else { mCurrentSelectable.fileName }
            newStyleIntent.clipData = ClipData("style-bytes", arrayOf(), ClipData.Item(Uri.parse("content://com.termux.styling.fileprovider/${assetsFolder}/${mCurrentSelectable.fileName}")))
            newStyleIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(newStyleIntent)
        } catch (e: Exception) {
            Log.w("termux", "Failed to sendFile", e)
            val message = resources.getString(R.string.writing_failed) + e.message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

}
