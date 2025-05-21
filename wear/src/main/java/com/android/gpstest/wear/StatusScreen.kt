package com.android.gpstest.wear

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import com.android.gpstest.Application
import com.android.gpstest.library.data.FixState
import com.android.gpstest.library.model.CoordinateType
import com.android.gpstest.library.model.DilutionOfPrecision
import com.android.gpstest.library.model.SatelliteMetadata
import com.android.gpstest.library.model.SatelliteStatus
import com.android.gpstest.library.ui.SignalInfoViewModel
import com.android.gpstest.library.util.FormatUtils
import com.android.gpstest.wear.theme.GpstestTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration


/**
 * The main user interface for Wear OS that displays the basic information of GNSS
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalComposeUiApi::class)
@Composable
fun StatusScreen(signalInfoViewModel: SignalInfoViewModel) {
    val gnssStatuses: List<SatelliteStatus> by signalInfoViewModel.filteredGnssStatuses.observeAsState(
        emptyList()
    )
    val location: Location by signalInfoViewModel.location.observeAsState(Location("invalid"))
    val fixState: FixState by signalInfoViewModel.fixState.observeAsState(FixState.NotAcquired)
    val satelliteMetadata: SatelliteMetadata by signalInfoViewModel.filteredSatelliteMetadata.observeAsState(
        SatelliteMetadata()
    )
    val dop: DilutionOfPrecision by signalInfoViewModel.dop.observeAsState(
        DilutionOfPrecision(
            Double.NaN,
            Double.NaN,
            Double.NaN
        )
    )
    GpstestTheme {
        val listState = rememberScalingLazyListState()
        val coroutineScope = rememberCoroutineScope()
        Scaffold(
            timeText = {
                if (!listState.isScrollInProgress) {
                    TimeText(
                        timeSource = object : TimeSource {
                            override val currentTime: String
                                @Composable
                                get() = if (location.time == 0L) "" else SimpleDateFormat("HH:mm:ss").format(
                                    location.time
                                )
                        }
                    )
                }
            },
            positionIndicator = {
                PositionIndicator(
                    scalingLazyListState = listState
                )
            }
        ) {
            val showDetails = remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }
            val contentModifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .onRotaryScrollEvent {
                    coroutineScope.launch {
                        listState.scrollBy(it.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()

            ScalingLazyColumn(
                modifier = contentModifier,
                autoCentering = AutoCenteringParams(itemIndex = 3),
                state = listState
            ) {
                item {
                    startStopButton(showDetails)
                }
                if(showDetails.value) {
                    item {
                        CustomLinearProgressBar(fixState)
                    }
                    item {
                        Latitude(location)
                    }
                    item {
                        Longitude(location)
                    }
                    item {
                        NumSats(satelliteMetadata)
                    }
                    item {
                        Bearing(location)
                    }
                    item {
                        DoP(dop)
                    }
                    item {
                        HvDOP(dop)
                    }
                    item {
                        Speed(location)
                    }
                    item {
                        StatusRowHeader(isGnss = true)
                    }

                    for (satelliteStatus in gnssStatuses) {
                        item {
                            StatusRow(satelliteStatus = satelliteStatus)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}

@Composable
fun startStopButton(showDetails: MutableState<Boolean>) {


    val context = LocalContext.current // Access the context here for later use
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    var buttonText = remember { mutableStateOf(if (GlobalStuff.recordingStarted) "Stop" else "Start") }
    var buttonColor = remember { mutableStateOf(if (GlobalStuff.recordingStarted) Color.Red else Color.Green) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = {

                    showDetails.value = !showDetails.value
                    if(GlobalStuff.recordingStarted){
                        //shareFile(context)
                        var toast = Toast.makeText(context, "Logfile saved: " + GlobalStuff.recordingTimestamp, Toast.LENGTH_SHORT)
                        toast.show()
                        GlobalStuff.recordingTimestamp = "noTimestamp"
                        GlobalStuff.recordingStarted = false
                        MainActivity.setRecordingState(false)
                    }else{
                        GlobalStuff.recordingTimestamp = dateFormat.format(Date())
                        GlobalStuff.recordingStarted = true
                        MainActivity.setRecordingState(true)
                    }
                    // Update the button text and color state
                    buttonText.value = if (GlobalStuff.recordingStarted) "Stop" else "Start"
                    buttonColor.value = if (GlobalStuff.recordingStarted) Color.Red else Color.Green

                },
                colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor.value)
            ) {
                Text(buttonText.value)
            }
    }
}

fun readFileContent(file: File): String {
    return file.bufferedReader().use { it.readText() }
}

fun logAppsThatCanHandleTextPlain(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Status Log")
    }

    val resolveInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (resolveInfoList.isNotEmpty()) {
        for (resolveInfo in resolveInfoList) {
            Log.d("AppCapable", "Package: ${resolveInfo.activityInfo.packageName}, Activity: ${resolveInfo.activityInfo.name}")
        }
    } else {
        Log.d("AppCapable", "No apps found that can handle text/plain intents.")
    }
}


fun shareFile(context: Context) {
    val file = File(context.filesDir, "nmea_recording.txt")
    logAppsThatCanHandleTextPlain(context)

    if (!file.exists()) {
        Toast.makeText(context, "Log file does not exist", Toast.LENGTH_SHORT).show()
        //Log.e("Eneko", "The file doesn't exist")
        Toast.makeText(context, "The file doesn't exist", Toast.LENGTH_SHORT).show()
        return
    }else{
        /*
        // Read the content of the file
        val content = file.readText()

        // Log the content
        Log.d("FileContent", content)

        // Or log it line by line
        file.forEachLine { line ->
            Log.d("FileContentLine", line)
        }
        */
    }


    val uri = FileProvider.getUriForFile(context, "com.android.gpstest.wear.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Status Log")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Send log via Gmail"))
    } else {
        Toast.makeText(context, "No app available to send the log file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Latitude(location: Location) {
    Text(
        text = stringResource(R.string.latitude_label) + " " + FormatUtils.formatLatOrLon(
            Application.app, location.latitude, CoordinateType.LATITUDE,
            Application.prefs
        )
    )
}

@Composable
fun Longitude(location: Location) {
    Text(
        text = stringResource(R.string.longitude_label) + " " + FormatUtils.formatLatOrLon(
            Application.app,
            location.longitude,
            CoordinateType.LONGITUDE,
            Application.prefs
        )
    )
}

@Composable
fun NumSats(satelliteMetadata: SatelliteMetadata) {
    Text(
        text = stringResource(R.string.num_sats_label) + " " + FormatUtils.formatNumSats(
            Application.app,
            satelliteMetadata
        )
    )
}

@Composable
fun Bearing(location: Location) {
    Text(
        text = stringResource(R.string.bearing_label) + " " + FormatUtils.formatBearing(
            Application.app,
            location
        )
    )
}

@Composable
fun DoP(dop: DilutionOfPrecision) {
    Text(text = stringResource(R.string.pdop_label) + " " + FormatUtils.formatDoP(Application.app, dop))
}

@Composable
fun HvDOP(dop: DilutionOfPrecision) {
    Text(text = stringResource(R.string.hvdop_label) + " " + FormatUtils.formatHvDOP(Application.app,
                                                                               dop))
}

@Composable
fun Speed(location: Location) {
    Text(
        text = stringResource(R.string.speed_label) + " " + FormatUtils.formatSpeed(
            Application.app, location, Application.prefs
        )
    )
}

@Composable
private fun CustomLinearProgressBar(fixState: FixState) {
    AnimatedVisibility(visible = (fixState == FixState.NotAcquired)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            backgroundColor = Color.LightGray,
            color = Color.Gray
        )
    }
}