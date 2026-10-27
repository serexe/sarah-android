package com.serexe.sarah

import android.Manifest
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

private val charcoal=Color(0xff151817)
private val surfaceDark=Color(0xff202523)
private val orange=Color(0xffff742e)
private val cream=Color(0xfff5f3ed)
private val sage=Color(0xffb6c9b6)
class MainActivity:ComponentActivity() {
 private val vm by viewModels<SarahViewModel>()
 private val interruptions=object:BroadcastReceiver() {
  override fun onReceive(context:Context?,intent:Intent?) {
   when(intent?.action) {
    Intent.ACTION_SCREEN_OFF ->vm.onBackground()
    AudioManager.ACTION_AUDIO_BECOMING_NOISY ->vm.routeInterrupted()
   }
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { val dark=androidx.compose.foundation.isSystemInDarkTheme()
   val colors=if(dark) darkColorScheme(primary=orange,onPrimary=charcoal,background=charcoal,onBackground=cream,surface=surfaceDark,onSurface=cream)
    else lightColorScheme(primary=Color(0xffa83c00),onPrimary=Color.White,background=cream,onBackground=charcoal,surface=Color(0xffe9e9e2),onSurface=charcoal)
   MaterialTheme(colorScheme=colors) { SarahScreen(vm) }
  }
 }
 override fun onStart() {super.onStart();vm.onForeground();registerReceiver(interruptions,IntentFilter().apply {addAction(Intent.ACTION_SCREEN_OFF);addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)})}
 override fun onStop() {unregisterReceiver(interruptions);vm.onBackground();super.onStop()}
}
@Composable private fun SarahScreen(vm:SarahViewModel) {
 val ui by vm.ui.collectAsStateWithLifecycle();val context=LocalContext.current
 var url by remember { mutableStateOf("") };var token by remember { mutableStateOf("") };var ca by remember { mutableStateOf<ByteArray?>(null) }
 var caStatus by remember { mutableStateOf("System trust only") }
 var permissionDenied by remember { mutableStateOf(false) };var confirmNew by remember {mutableStateOf(false)};var confirmReset by remember {mutableStateOf(false)}
 val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) {permissionDenied=false;vm.startCapture()} else permissionDenied=true }
 val certPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) try {
   context.contentResolver.openInputStream(uri)?.use {stream-> val bytes=Bounded.read(stream,65536);require(bytes.isNotEmpty());ca=bytes;caStatus="Private CA selected (certificate only)" }
  } catch (_:Exception) {ca=null;caStatus="Certificate rejected or too large"}
 }
 val bg=MaterialTheme.colorScheme.background;val fg=MaterialTheme.colorScheme.onBackground
 Surface(color=bg,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
   val wide=maxWidth>=700.dp
   if(wide && ui.hasConfig) Row(Modifier.fillMaxSize().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
    Column(Modifier.weight(1f).fillMaxHeight()) { Header(ui, {confirmNew=true}, {vm.clearLocal()}, {confirmReset=true});Status(ui);Thread(ui,Modifier.weight(1f)) }
    Column(Modifier.widthIn(max=400.dp).weight(.8f).fillMaxHeight(),verticalArrangement=Arrangement.Bottom) { ActionPanel(ui,vm,{mic.launch(Manifest.permission.RECORD_AUDIO)},permissionDenied,{ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) }
   } else Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
    Header(ui,{confirmNew=true},{vm.clearLocal()},{confirmReset=true});Status(ui)
    if(!ui.hasConfig) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
     Thread(ui,Modifier.weight(1f));ActionPanel(ui,vm,{mic.launch(Manifest.permission.RECORD_AUDIO)},permissionDenied,{ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) })
    }
   }
   if(confirmNew) AlertDialog(onDismissRequest={confirmNew=false},title={Text("New server conversation?")},text={Text("Recording will be discarded. Pending server work may continue; this will not undo actions. A separate conversation is created.")},confirmButton={TextButton(onClick={confirmNew=false;vm.newConversation()}) {Text("Create new")}},dismissButton={TextButton(onClick={confirmNew=false}) {Text("Keep current")}})
   if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("Clear local pairing?")},text={Text("Local token, transcript, and request identity will be erased. Server conversations and actions are not deleted. Ask bridge administrator for a new device token.")},confirmButton={TextButton(onClick={confirmReset=false;vm.resetPairing()}) {Text("Clear pairing")}},dismissButton={TextButton(onClick={confirmReset=false}) {Text("Keep")}})
  }
 }
}
@Composable private fun Header(ui:SarahViewModel.Ui,onNew:()->Unit,onClear:()->Unit,onReset:()->Unit) {
 Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text("◉",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.semantics {contentDescription="S.A.R.A.H microphone mark"})
   Spacer(Modifier.width(9.dp))
   Column(Modifier.weight(1f)) {Text("S.A.R.A.H",style=MaterialTheme.typography.titleLarge);Text("Voice companion",style=MaterialTheme.typography.labelSmall)}
   if(ui.hasConfig) TextButton(onClick=onNew,modifier=Modifier.heightIn(min=48.dp)) {Text("＋ New")}
  }
  if(ui.hasConfig) Row(Modifier.fillMaxWidth()) {
   TextButton(onClick=onClear,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Clear local",maxLines=2)}
   TextButton(onClick=onReset,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Connection settings",maxLines=2)}
  }
 }
}
@Composable private fun Status(ui:SarahViewModel.Ui) {
 val text="${ui.phase.replaceFirstChar {it.uppercase()}} · ${ui.status}"
 Surface(tonalElevation=2.dp,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)) {
  Text(text,modifier=Modifier.padding(12.dp).semantics {contentDescription="Status: $text"},style=MaterialTheme.typography.bodyMedium)
 }
}
@Composable private fun Thread(ui:SarahViewModel.Ui,modifier:Modifier=Modifier) {
 LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  if(ui.transcript.isEmpty()) item {Text("Conversation · tap to talk when connected. Server history remains after local clearing.",style=MaterialTheme.typography.bodyMedium)}
  items(ui.transcript) { (who,text) ->Column {
   Text(who,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
   Surface(shape=RoundedCornerShape(15.dp),tonalElevation=2.dp,modifier=Modifier.fillMaxWidth()) {Text(text,modifier=Modifier.padding(14.dp),style=MaterialTheme.typography.bodyLarge)}
  } }
 }
}
@Composable private fun ActionPanel(ui:SarahViewModel.Ui,vm:SarahViewModel,onMic:()->Unit,denied:Boolean,onSettings:()->Unit) {
 Column(Modifier.fillMaxWidth().imePadding().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  if(ui.phase=="recording") {
   Text("Microphone active · maximum 30 seconds",style=MaterialTheme.typography.labelMedium)
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
   Button(onClick=onMic,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {Text("● Tap to talk",style=MaterialTheme.typography.titleMedium)}
   if(denied) {Text("Microphone denied. Enable permission in Android settings.");TextButton(onClick=onSettings) {Text("Open app settings")}}
  } else {
   Text("Authenticated connection required before recording",style=MaterialTheme.typography.bodySmall)
   if(ui.hasConfig) OutlinedButton(onClick=vm::reconnect,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Check connection")}
  }
 }
}
