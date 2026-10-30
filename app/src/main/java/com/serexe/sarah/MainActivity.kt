package com.serexe.sarah

import android.Manifest
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException

class MainActivity:ComponentActivity() {
 private val vm by viewModels<SarahViewModel>()
 private var roleAvailable by mutableStateOf(false)
 private var roleHeld by mutableStateOf(false)
 private var assistantOpened by mutableStateOf(false)
 private var receiverRegistered=false
 private fun assistantSettings() {try {startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))} catch (_:Exception) {try {startActivity(Intent(Settings.ACTION_SETTINGS))} catch (_:Exception) {}}}
 private val roleRequest=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {refreshRole();if(!roleHeld) assistantSettings()}
 private fun refreshRole() {
  if(Build.VERSION.SDK_INT<29) {roleAvailable=false;roleHeld=false;return}
  val manager=roleManager(this)
  roleAvailable=manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT)==true
  roleHeld=roleAvailable && manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT)==true
 }
 private fun locked()=getSystemService(KeyguardManager::class.java)?.isDeviceLocked==true
 private val interruptions=object:BroadcastReceiver() {
  override fun onReceive(context:Context?,intent:Intent?) {
   when(intent?.action) {
    Intent.ACTION_SCREEN_OFF -> {HeySarahListenerService.visible=false;vm.onBackground()}
    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {HeySarahListenerService.stop(this@MainActivity);vm.routeInterrupted()}
   }
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  if(locked()) {finish();return}
  assistantOpened=isAssistantInvocation(intent)
  refreshRole()
  setContent {
   val colors=darkColorScheme(primary=CommandTeal,onPrimary=Color(0xff112823),background=CommandBackground,onBackground=Color(0xffedf2ef),surface=CommandPanel,onSurface=Color(0xffedf2ef),onSurfaceVariant=CommandMuted,outline=Color(0xff405454))
   MaterialTheme(colorScheme=colors) { SarahScreen(vm,roleAvailable,roleHeld,assistantOpened) {
    if(Build.VERSION.SDK_INT>=29) {
     val manager=roleManager(this)
     if(manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT)==true && !manager.isRoleHeld(RoleManager.ROLE_ASSISTANT))
      try {roleRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))} catch (_:Exception) {assistantSettings()}
     else if(!roleHeld) assistantSettings()
    }
   } }
  }
 }
 override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);if(locked()) {finish();return};assistantOpened=isAssistantInvocation(intent)}
 override fun onResume() {super.onResume();if(locked()) {finish();return};refreshRole();HeySarahListenerService.visible=true
  HeySarahListenerService.onWake={
   if(locked() || !getSystemService(android.os.PowerManager::class.java).isInteractive || !HeySarahListenerService.visible) false else vm.acceptWake().also {accepted->
    if(accepted) runCatching {getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(60,VibrationEffect.DEFAULT_AMPLITUDE))}
   }
  }
 }
 override fun onPause() {HeySarahListenerService.visible=false;HeySarahListenerService.onWake=null;super.onPause()}
 override fun onStart() {super.onStart();if(locked()) {finish();return};vm.onForeground();registerReceiver(interruptions,IntentFilter().apply {addAction(Intent.ACTION_SCREEN_OFF);addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)});receiverRegistered=true}
 override fun onStop() {HeySarahListenerService.visible=false;if(receiverRegistered) {unregisterReceiver(interruptions);receiverRegistered=false};vm.onBackground();super.onStop()}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SarahScreen(vm:SarahViewModel,roleAvailable:Boolean,roleHeld:Boolean,assistantOpened:Boolean,onRequestRole:()->Unit) {
 val ui by vm.ui.collectAsStateWithLifecycle();val context=LocalContext.current
 var showAbout by rememberSaveable { mutableStateOf(false) }
 var showSettings by rememberSaveable {mutableStateOf(false)}
 var confirmClear by remember {mutableStateOf(false)}
 var technical by remember {mutableStateOf(false)}
 BackHandler(enabled=showAbout) {showAbout=false}
 var url by remember { mutableStateOf("") };var token by remember { mutableStateOf("") };var ca by remember { mutableStateOf<ByteArray?>(null) }
 var caStatus by remember { mutableStateOf("System trust only") }
 var permissionDenied by remember { mutableStateOf(false) };var confirmNew by remember {mutableStateOf(false)};var confirmReset by remember {mutableStateOf(false)}
 val listener by HeySarahListenerService.status.collectAsStateWithLifecycle()
 LaunchedEffect(listener) {if(listener=="off") vm.syncAfterService()}
 var listenerConsent by remember {mutableStateOf(false)}
 var listenerPermissionReady by remember {mutableStateOf(false)}
 val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted && HeySarahListenerService.status.value=="off") {permissionDenied=false;vm.startCapture()} else permissionDenied=true }
 val listenerPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
  // Android may deliver this before onResume/reconnect. Never drop or replay a start
  // across that boundary: permission grant requires a new visible confirmation.
  listenerPermissionReady=ListenerPermissionFlow.permissionResult(
   (if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)).all {granted[it]==true})==ListenerPermissionFlow.Action.CONFIRM_AGAIN
 }
 val startListener={listenerConsent=true}
 val certPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) try {
   context.contentResolver.openInputStream(uri)?.use {stream-> val bytes=Bounded.read(stream,65536);require(bytes.isNotEmpty());ca=bytes;caStatus="Private CA selected (certificate only)" }
  } catch (_:Exception) {ca=null;caStatus="Certificate rejected or too large"}
 }
 val bg=MaterialTheme.colorScheme.background;val fg=MaterialTheme.colorScheme.onBackground
 Surface(color=bg,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
   val wide=maxWidth>=700.dp
   if(showAbout) AboutPage(context,vm) {showAbout=false}
   else Column(Modifier.fillMaxSize()) {
    CommandHeader(ui.hasConfig,{confirmNew=true},{showSettings=true})
    if(!ui.hasConfig) Column(Modifier.weight(1f).padding(horizontal=20.dp).verticalScroll(rememberScrollState())) {
     Text(ui.status,style=MaterialTheme.typography.bodyMedium,color=CommandMuted)
     Spacer(Modifier.height(16.dp));Text("Your private voice companion",style=MaterialTheme.typography.headlineMedium)
     Text("Enter HTTPS bridge origin and separate revocable Android device token. No provider or smart-home credentials.",color=fg)
     Spacer(Modifier.height(18.dp))
     OutlinedTextField(url,{url=it},label={Text("HTTPS bridge address")},placeholder={Text("https://companion.example.invalid")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
     Spacer(Modifier.height(12.dp))
     OutlinedTextField(token,{token=it},label={Text("Device token")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
     Spacer(Modifier.height(12.dp))
     Text(caStatus,color=fg)
     TextButton(onClick={certPicker.launch(arrayOf("application/x-x509-ca-cert","application/pkix-cert","application/octet-stream","*/*"))},modifier=Modifier.heightIn(min=48.dp)) {Text("Import private CA certificate")}
     Text("Optional CA replaces system trust for this connection; normal hostname verification remains required.",style=MaterialTheme.typography.bodySmall)
     Spacer(Modifier.height(12.dp))
     Button(onClick={vm.configure(url,token,ca)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) {Text("Save & connect")}
     if(ui.status.startsWith("Secure storage unavailable")) TextButton(onClick={confirmReset=true}) {Text("Reset inaccessible local pairing")}
    } else {
     CommandSession(ui,Modifier.weight(1f),wide)
     Surface(color=Color(0xff141c1d),modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.heightIn(max=330.dp).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp)) {
       ListenerControls(listener,ui,ui.canCapture && roleHeld,listenerPermissionReady,startListener,{HeySarahListenerService.stop(context);vm.discard()})
       ActionPanel(ui,vm,{mic.launch(Manifest.permission.RECORD_AUDIO)},permissionDenied,{ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) })
      }
     }
    }
   }
   if(showSettings) ModalBottomSheet(onDismissRequest={showSettings=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=CommandPanel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Text("Settings",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
      TextButton(onClick={showSettings=false}) {Text("Close")}
     }
     Text("Pairing & connection",style=MaterialTheme.typography.titleMedium)
     Text(if(ui.hasConfig) "Local device pairing saved. Connection state: ${commandReadiness(ui.phase,ui.canCapture)}. No credentials displayed." else "No local pairing. Enter the HTTPS bridge and device token on the setup screen.",color=CommandMuted)
     if(ui.hasConfig) OutlinedButton(onClick=vm::reconnect,modifier=Modifier.heightIn(min=48.dp)) {Text("Check connection")}
     AssistantSetup(roleAvailable,roleHeld,assistantOpened,onRequestRole)
     TextButton(onClick={technical=!technical},modifier=Modifier.heightIn(min=48.dp)) {Text(if(technical) "Hide technical details ▴" else "Assistant & diagnostics ▾")}
     if(technical) {
      Text("Request: ${ui.phase} · ${ui.status}\nHey Sarah: $listener",style=MaterialTheme.typography.bodySmall)
      Text("Local stages (no content): ${StageTrail.read(context).lines().takeLast(3).joinToString(" · ")}",style=MaterialTheme.typography.bodySmall)
      Text("Black-screen phone behavior remains unverified.",style=MaterialTheme.typography.bodySmall)
     }
     TextButton(onClick={showSettings=false;showAbout=true},modifier=Modifier.heightIn(min=48.dp)) {Text("About Sarah & capture diagnostics")}
     HorizontalDivider()
     if(ui.hasConfig) {
      TextButton(onClick={confirmClear=true},modifier=Modifier.heightIn(min=48.dp)) {Text("Clear local conversation…")}
      TextButton(onClick={confirmReset=true},modifier=Modifier.heightIn(min=48.dp)) {Text("Clear pairing…")}
     }
    }
   }
   if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear local conversation?")},text={Text("Only the local transcript is removed. Server history and actions remain; this does not cancel pending work.")},confirmButton={TextButton(onClick={confirmClear=false;vm.clearLocal()}) {Text("Clear local")}},dismissButton={TextButton(onClick={confirmClear=false}) {Text("Keep")}})

   if(listenerConsent) AlertDialog(onDismissRequest={listenerConsent=false},title={Text("Enable persistent local Hey Sarah?")},text={Text("Until you explicitly Stop, a foreground microphone service listens locally even after closing or swiping away the app. Android shows an ongoing microphone notification and privacy indicator; continuous listening and CPU wake lock use battery. No wake buffer is saved/uploaded. On an unlocked wake, the listener microphone releases before command recording with a readiness chime, an adaptive 1.6–3.2-second silence endpoint after speech, and a 30-second discard-only safety cap; a completed command is sent once to the paired bridge; reply may play aloud. Locked wake only notifies: no unlock bypass, recording, or automatic confirmation. OS process death, reboot, or Force stop ends listening until a visible new opt-in. Black-screen phone behavior remains unverified.")},confirmButton={TextButton(onClick={
    listenerConsent=false
    val required=if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO)
    when(ListenerPermissionFlow.confirmed(required.all {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED})) {
     ListenerPermissionFlow.Action.START -> {listenerPermissionReady=false;HeySarahListenerService.start(context,vm.ui.value.canCapture && roleHeld)}
     ListenerPermissionFlow.Action.REQUEST_PERMISSION -> {listenerPermissionReady=false;listenerPermission.launch(required)}
     else -> Unit
    }
   }) {Text("Enable until Stop")}},dismissButton={TextButton(onClick={listenerConsent=false}) {Text("Not now")}})
   if(confirmNew) AlertDialog(onDismissRequest={confirmNew=false},title={Text("New server conversation?")},text={Text("Recording will be discarded. Pending server work may continue; this will not undo actions. A separate conversation is created.")},confirmButton={TextButton(onClick={confirmNew=false;vm.newConversation()}) {Text("Create new")}},dismissButton={TextButton(onClick={confirmNew=false}) {Text("Keep current")}})
   if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("Clear local pairing?")},text={Text("Local token, transcript, and request identity will be erased. Server conversations and actions are not deleted. Ask bridge administrator for a new device token.")},confirmButton={TextButton(onClick={confirmReset=false;vm.resetPairing()}) {Text("Clear pairing")}},dismissButton={TextButton(onClick={confirmReset=false}) {Text("Keep")}})
  }
 }
}
@Composable private fun ListenerControls(state:String,ui:SarahViewModel.Ui,canStart:Boolean,permissionReady:Boolean,onStart:()->Unit,onStop:()->Unit) {
 Column(Modifier.fillMaxWidth()) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Column(Modifier.weight(1f)) {
    Text("Hey Sarah",style=MaterialTheme.typography.labelLarge)
    Text(if(state=="off") "Off · optional" else "Microphone service · $state",style=MaterialTheme.typography.labelSmall,color=CommandMuted)
   }
   if(state!="off") TextButton(onClick=onStop,modifier=Modifier.heightIn(min=48.dp)) {Text("Stop wake")}
   Switch(checked=state!="off",onCheckedChange={if(it) onStart() else onStop()},enabled=state!="off" || canStart,modifier=Modifier.semantics {contentDescription="Persistent Hey Sarah microphone, opt-in"})
  }
  if(state=="off" && !canStart) Text("Wake listening needs a ready connection and default-assistant role. See Settings.",style=MaterialTheme.typography.bodySmall,color=CommandMuted)
  if(permissionReady) Text("Permission granted. Enable Hey Sarah again to start.",style=MaterialTheme.typography.bodySmall)
  if(state=="off" && ui.phase in setOf("recording","finalizing","uploading","polling","reply_audio_pending","playing")) TextButton(onClick=onStop,modifier=Modifier.heightIn(min=48.dp)) {Text("Stop hands-free listening (server work continues)")}
 }
}
@Composable private fun AssistantSetup(available:Boolean,held:Boolean,opened:Boolean,onRequest:()->Unit) {
 Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
  Text(assistantRoleStatus(available,held),style=MaterialTheme.typography.bodySmall)
  if(opened) Text("Assistant opened. Unlock and tap to talk; no recording starts automatically.",style=MaterialTheme.typography.bodySmall)
  if(available && !held) TextButton(onClick=onRequest,modifier=Modifier.heightIn(min=48.dp)) {Text("Set as default assistant")}
 }
}
internal fun formatInstalledVersion(versionName:String?,versionCode:Long):String =
 "${versionName?.takeIf {it.isNotBlank()} ?: "Unknown"} (build $versionCode)"

@Composable private fun AboutPage(context:Context,vm:SarahViewModel,onBack:()->Unit) {
 val info=remember(context) {context.packageManager.getPackageInfo(context.packageName,0)}
 val name=remember(context) {context.applicationInfo.loadLabel(context.packageManager).toString()}
 var diagnostic by remember {mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
  TextButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp)) {Text("‹ Back")}
  Spacer(Modifier.height(12.dp))
  Text("About $name",style=MaterialTheme.typography.headlineMedium)
  Spacer(Modifier.height(20.dp))
  Text("App version",style=MaterialTheme.typography.labelLarge)
  val buildCode=if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
  Text(formatInstalledVersion(info.versionName,buildCode),style=MaterialTheme.typography.bodyLarge)
  Spacer(Modifier.height(16.dp))
  Text("App name",style=MaterialTheme.typography.labelLarge)
  Text(name,style=MaterialTheme.typography.bodyLarge)
  Spacer(Modifier.height(16.dp))
  Text("Package",style=MaterialTheme.typography.labelLarge)
  Text(context.packageName,style=MaterialTheme.typography.bodyLarge)
  Spacer(Modifier.height(16.dp))
  Text("Android version / API",style=MaterialTheme.typography.labelLarge)
  Text("${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}",style=MaterialTheme.typography.bodyLarge)
  Spacer(Modifier.height(16.dp))
  Text("Local diagnostics · not a fix",style=MaterialTheme.typography.labelLarge)
  Button(onClick={diagnostic=vm.diagnosticSnapshot(buildCode)},modifier=Modifier.heightIn(min=48.dp)) {Text("Capture diagnostics")}
  diagnostic?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
 }
}
@Composable private fun ActionPanel(ui:SarahViewModel.Ui,vm:SarahViewModel,onMic:()->Unit,denied:Boolean,onSettings:()->Unit) {
 Column(Modifier.fillMaxWidth().imePadding().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  if(ui.phase=="recording") {
   Text(if(ui.status.startsWith("Starting microphone")) "Microphone starting · wait for cue" else "Microphone active · adaptive speech pause · maximum 30 seconds",style=MaterialTheme.typography.labelMedium)
   Button(onClick=vm::send,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {Text("■ Stop & send",style=MaterialTheme.typography.titleMedium)}
   OutlinedButton(onClick=vm::discard,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Discard recording")}
  } else if(ui.phase=="playing") {
   Text("Stops sound only · does not cancel completed work")
   Button(onClick=vm::stopAudio,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {Text("■ Stop audio")}
  } else if(ui.phase=="uncertain") {
   Text("No automatic resend. Server actions may already have happened.")
   Button(onClick=vm::checkExisting,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text("Check existing request status")}
   OutlinedButton(onClick=vm::reconnect,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Check connection")}
  } else if(ui.phase=="confirming" && ui.confirmation!=null) {
   Text("Server confirmation: ${ui.confirmation.optString("label").take(200)}")
   Text("Accepting is not proof action succeeded.")
   Button(onClick={vm.confirm(true)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("Confirm once")}
   OutlinedButton(onClick={vm.confirm(false)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Deny")}
   TextButton(onClick=vm::cancelTurn) {Text("Cancel server turn (no rollback)")}
  } else if(ui.phase=="polling") {
   Text("Waiting for actual server status; no progress estimate")
   OutlinedButton(onClick=vm::cancelTurn,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("Cancel server turn (no rollback)")}
  } else if(ui.hasConfig && ui.canCapture && ui.phase in setOf("ready","done","error","cancelled")) {
   val listening by HeySarahListenerService.status.collectAsStateWithLifecycle()
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
    FilledIconButton(onClick=onMic,enabled=listening=="off",shape=RoundedCornerShape(22.dp),modifier=Modifier.size(64.dp).semantics {contentDescription="Tap to talk"}) {CommandMicMark()}
    Column(Modifier.weight(1f)) {
     Text("Tap to talk",style=MaterialTheme.typography.titleMedium)
     Text(if(listening=="off") "No command recording active." else "Wake microphone service: $listening",style=MaterialTheme.typography.bodySmall,color=CommandMuted)
    }
   }
   if(listening!="off") Text("Stop Hey Sarah before manual command capture.")
   if(denied) {Text("Microphone denied. Enable permission in Android settings.");TextButton(onClick=onSettings) {Text("Open app settings")}}
  } else {
   Text("Authenticated connection required before recording",style=MaterialTheme.typography.bodySmall)
   if(ui.hasConfig) OutlinedButton(onClick=vm::reconnect,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Check connection")}
  }
 }
}
