package com.example.stockwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        render()
        binding.logClear.setOnClickListener {
            CrashLog.clear(this)
            render()
        }
    }

    private fun render() {
        val text = CrashLog.read(this)
        binding.logText.text = if (text.isBlank()) getString(R.string.log_empty) else text
    }
}
