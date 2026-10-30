package com.serexe.sarah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CommandBackground=Color(0xff101416)
internal val CommandPanel=Color(0xff1a2427)
internal val CommandTeal=Color(0xffa5d8cb)
internal val CommandMuted=Color(0xffa5b4b2)
private val CommandLine=Color(0xff304143)

internal fun commandReadiness(phase:String,canCapture:Boolean):String = when(phase) {
 "recording" -> "Listening"
 "playing" -> "Speaking"
 "uncertain" -> "Check request"
 "confirming" -> "Confirm needed"
 "finalizing","uploading","polling","reply_audio_pending","creating","cancelling" -> "Working"
 "error" -> "Needs attention"
 "ready","done","cancelled" -> if(canCapture) "Ready" else "Not ready"
 else -> "Not connected"
}

// Current turn begins at the latest real user event. Never pair it with an older answer.
internal fun commandCurrentTurn(events:List<Pair<String,String>>,phase:String="done"):List<Pair<String,String>> {
 if(phase in setOf("recording","finalizing","uploading","creating")) return emptyList()
 val start=events.indexOfLast {it.first=="YOU"}
 val latest=if(start>=0) events.drop(start) else events.takeLast(1)
 // Ui has no per-event request id. During pending/failed work, an older answered
 // exchange cannot truthfully represent this request; keep it in the timeline.
 return if(phase in setOf("polling","uncertain","error","cancelled","confirming","cancelling") && latest.any {it.first!="YOU"}) emptyList() else latest
}

@Composable internal fun CommandHeader(paired:Boolean,onNew:()->Unit,onSettings:()->Unit) {
 Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
  Box(Modifier.size(36.dp).border(1.dp,CommandTeal.copy(alpha=.55f),CircleShape),contentAlignment=Alignment.Center) {
   Text("∿",color=CommandTeal,fontSize=25.sp,modifier=Modifier.semantics {contentDescription="Sarah voice companion"})
  }
  Spacer(Modifier.width(12.dp))
  Column(Modifier.weight(1f)) {Text("Sarah",style=MaterialTheme.typography.titleLarge);Text("Voice companion",color=CommandMuted,style=MaterialTheme.typography.labelSmall)}
  if(paired) OutlinedButton(onClick=onNew,contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)) {Text("＋ New")}
  IconButton(onClick=onSettings,modifier=Modifier.size(48.dp)) {Text("⚙",fontSize=24.sp,modifier=Modifier.semantics {contentDescription="Open settings"})}
 }
}

@Composable internal fun CommandSession(ui:SarahViewModel.Ui,modifier:Modifier=Modifier,wide:Boolean=false) {
 val turn=commandCurrentTurn(ui.transcript,ui.phase)
 if(wide) Row(modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
  LazyColumn(Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   item {SessionHeading(ui)}
   item {CurrentTurn(ui,turn)}
  }
  Timeline(ui,Modifier.weight(1f).fillMaxHeight())
 } else LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item {SessionHeading(ui)}
  item {CurrentTurn(ui,turn)}
  item {TimelineHeading(ui)}
  itemsIndexed(ui.transcript) {index,event -> TimelineEvent(event,index==ui.transcript.lastIndex)}
  if(ui.transcript.isEmpty()) item {Text("Your conversation events appear here after a request. No local history yet.",color=CommandMuted,style=MaterialTheme.typography.bodyMedium)}
  item {Text("Conversation events, not a wall of logs.\nConnection details stay in Settings.",color=CommandMuted,style=MaterialTheme.typography.bodySmall)}
 }
}

@Composable private fun SessionHeading(ui:SarahViewModel.Ui) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("This session",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
  Surface(color=Color(0xff192825),shape=RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,CommandLine)) {
   Text("• ${commandReadiness(ui.phase,ui.canCapture)}",color=CommandTeal,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
  }
 }
}

@Composable private fun CurrentTurn(ui:SarahViewModel.Ui,turn:List<Pair<String,String>>) {
 var details by remember {mutableStateOf(false)}
 Surface(color=CommandPanel,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,CommandLine),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   val prompt=turn.lastOrNull {it.first=="YOU"}?.second
   val answer=turn.lastOrNull {it.first!="YOU"}?.second
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
    Text("CURRENT TURN",color=CommandMuted,style=MaterialTheme.typography.labelSmall,letterSpacing=1.5.sp,modifier=Modifier.weight(1f))
    if(answer!=null) Text("Reply received",color=CommandTeal,style=MaterialTheme.typography.labelSmall)
   }
   if(prompt!=null) Text(prompt,color=CommandMuted,style=MaterialTheme.typography.bodyLarge)
   if(answer!=null) Text(answer,style=MaterialTheme.typography.headlineSmall)
   else Text(when(ui.phase) {"recording"->"Listening. Stop & send finishes this take.";"uncertain"->"Request outcome unknown. Check existing request before trying again.";"confirming"->"Review the requested confirmation below.";"error"->"Something needs attention.";"disconnected"->"Connect before talking.";"polling","uploading","finalizing","reply_audio_pending"->"Waiting for this request.";"creating"->"Creating a new conversation.";"cancelling"->"Cancellation requested. Actions may already have happened.";"cancelled"->"Request cancelled. Completed actions are not undone.";else->if(ui.canCapture) "Ready for your next question." else "Connection not ready."},style=MaterialTheme.typography.titleLarge)
   if(ui.phase in setOf("playing","reply_audio_pending")) {
    HorizontalDivider(color=CommandLine)
    Text(if(ui.phase=="playing") "◖ Voice reply playing" else "Voice reply pending",color=CommandTeal,style=MaterialTheme.typography.labelMedium)
   }
   if(ui.phase in setOf("error","disconnected","uncertain")) Text(ui.status,style=MaterialTheme.typography.bodyMedium,color=CommandMuted)
   TextButton(onClick={details=!details},contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {Text(if(details) "Hide request details ▴" else "Request details ▾",style=MaterialTheme.typography.labelMedium)}
   if(details) Text("${ui.phase} · ${ui.status}",style=MaterialTheme.typography.bodySmall,color=CommandMuted)
  }
 }
}

@Composable private fun TimelineHeading(ui:SarahViewModel.Ui) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  Text("SESSION TIMELINE",color=CommandMuted,style=MaterialTheme.typography.labelSmall,letterSpacing=1.5.sp,modifier=Modifier.weight(1f))
  Text("${ui.transcript.size} events",color=CommandMuted,style=MaterialTheme.typography.labelSmall)
 }
}
@Composable private fun Timeline(ui:SarahViewModel.Ui,modifier:Modifier) {
 LazyColumn(modifier,contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item {TimelineHeading(ui)}
  itemsIndexed(ui.transcript) {i,event->TimelineEvent(event,i==ui.transcript.lastIndex)}
  if(ui.transcript.isEmpty()) item {Text("No local conversation events yet.",color=CommandMuted)}
 }
}
@Composable private fun TimelineEvent(event:Pair<String,String>,last:Boolean) {
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
  Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(8.dp).padding(top=6.dp)) {
   Box(Modifier.size(7.dp).background(CommandTeal,CircleShape))
   if(!last) Box(Modifier.padding(top=5.dp).width(1.dp).height(34.dp).background(CommandLine))
  }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
   Text(if(event.first=="YOU") "You asked" else if(event.first in setOf("SARAH","S.A.R.A.H")) "Sarah answered" else event.first,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Medium)
   Text(event.second,color=CommandMuted,style=MaterialTheme.typography.bodyMedium)
  }
 }
}

@Composable internal fun CommandMicMark() {
 Canvas(Modifier.size(26.dp).semantics {contentDescription="Microphone"}) {
  val c=Color(0xff112823);val w=size.width;val h=size.height
  drawRoundRect(c,topLeft=Offset(w*.38f,h*.05f),size=androidx.compose.ui.geometry.Size(w*.24f,h*.56f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.12f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
  drawArc(c,0f,180f,false,topLeft=Offset(w*.2f,h*.25f),size=androidx.compose.ui.geometry.Size(w*.6f,h*.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
  drawLine(c,Offset(w*.5f,h*.75f),Offset(w*.5f,h*.95f),2.dp.toPx());drawLine(c,Offset(w*.33f,h*.95f),Offset(w*.67f,h*.95f),2.dp.toPx())
 }
}
