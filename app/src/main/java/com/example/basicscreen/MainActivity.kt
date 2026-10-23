package com.example.basicscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.basicscreen.ui.TodoScreen
import com.example.basicscreen.ui.theme.BasicScreenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BasicScreenTheme {
                TodoScreen()
            }
        }
    }
}
