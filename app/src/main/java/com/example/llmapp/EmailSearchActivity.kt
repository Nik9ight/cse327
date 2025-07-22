package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class EmailSearchActivity : AppCompatActivity() {
    private lateinit var searchQueryInput: TextInputEditText
    private lateinit var searchButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_search)

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Email Search"

        // Initialize views
        searchQueryInput = findViewById(R.id.searchQueryInput)
        searchButton = findViewById(R.id.searchButton)
        val cancelButton = findViewById<Button>(R.id.cancelButton)
        statusText = findViewById(R.id.statusText)
        
        // Retrieve existing query if provided
        val existingQuery = intent.getStringExtra("current_query")
        if (!existingQuery.isNullOrEmpty()) {
            searchQueryInput.setText(existingQuery)
        }
        
        // Set up cancel button click listener
        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Set up search button click listener
        searchButton.setOnClickListener {
            val query = searchQueryInput.text.toString().trim()
            
            // Even if query is blank, we allow it (default behavior)
            val resultIntent = Intent()
            resultIntent.putExtra("search_query", query)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
