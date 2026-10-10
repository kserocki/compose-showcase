package pl.kserocki.scratchticket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.kserocki.scratchticket.redeem.RedeemScreen
import pl.kserocki.scratchticket.ui.theme.ScratchTicketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScratchTicketTheme {
                RedeemScreen()
            }
        }
    }
}
