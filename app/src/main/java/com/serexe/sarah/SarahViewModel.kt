package com.serexe.sarah

import android.app.Application
import androidx.lifecycle.AndroidViewModel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** One owner; no WorkManager, background submit, polling, or capture resurrection. */
class SarahViewModel internal constructor(app:Application,private val vault:StateStore,
 private val bridgeFactory:(String,String,ByteArray?)->BridgePort,
 private val captureFactory:(()->Unit)->AudioCapture,
 playbackFactory:(()->Unit)->AudioPlayback,
 private val scope:CoroutineScope,
 private val io:kotlin.coroutines.CoroutineContext):AndroidViewModel(app) {
 constructor(app:Application):this(app,Vault(app),{url,token,ca->Bridge(url,token,ca)},{interrupted->Capture(app,interrupted)},{done->Playback(app,done)},CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate),Dispatchers.IO)
 data class Ui(val phase:String="disconnected",val status:String="Set up HTTPS bridge",val conversation:String?=null,val transcript:List<Pair<String,String>> = emptyList(),val hasConfig:Boolean=false,val confirmation:JSONObject?=null,val canCapture:Boolean=false)
 private val generation=AtomicLong()
 private val _ui=MutableStateFlow(Ui());val ui=_ui.asStateFlow()
 private var state=JSONObject()
 private var bridge:BridgePort?=null
 private var capture:AudioCapture?=null
 private var captureJob:Job?=null
 private var job:Job?=null
 private var foreground=false
 private var authenticated=false
 private val playback=playbackFactory { val g=generation.get();val c=state.optString("conversation");val r=state.optString("request");scope.launch { if (owner(g,c,r) && _ui.value.phase=="playing") show("done","Reply ready") } }
 init {
  try {
   state=vault.load()
   if (state.has("origin") && state.has("token")) {
    bridge=makeBridge()
    val phase=state.optString("phase","ready")
    if (phase in listOf("uploading","recording","creating","cancelling","confirming","polling")) {
     state.put("phase","uncertain");save()
    } else if (phase=="playing") {
     state.put("phase","done");save()
    }
    restore()
   }
  } catch (_:Exception) { state=JSONObject();_ui.value=Ui(status="Secure storage unavailable. Re-pair required; no plaintext recovery.",phase="error") }
 }
 private fun makeBridge()=bridgeFactory(state.getString("origin"),state.getString("token"),state.optString("ca","").takeIf {it.isNotEmpty()}?.let { android.util.Base64.decode(it,android.util.Base64.DEFAULT) })
 private fun save()=vault.save(state)
 private fun messages():List<Pair<String,String>> {
  val a=state.optJSONArray("messages") ?: return emptyList()
  return (0 until a.length()).mapNotNull { i->a.optJSONObject(i)?.let { it.optString("who") to it.optString("text") } }
 }
 private fun restore() {
  val p=state.optString("phase","ready")
  _ui.value=Ui(p,if(p=="uncertain") "Outcome uncertain. Check existing request; never resend." else p.replaceFirstChar {it.uppercase()},state.optString("conversation").takeIf {it.isNotEmpty()},messages(),true,canCapture=authenticated && p in setOf("ready","done","error","cancelled"))
 }
 private fun show(phase:String,status:String,confirmation:JSONObject?=null) {
  state.put("phase",phase);save()
  _ui.value=Ui(phase,status,state.optString("conversation").takeIf {it.isNotEmpty()},messages(),bridge!=null,confirmation,authenticated && phase in setOf("ready","done","error","cancelled"))
 }
 private fun append(who:String,text:String) {
  val rows=state.optJSONArray("messages") ?: JSONArray()
  rows.put(JSONObject().put("who",who).put("text",if(text.length>3000) text.take(3000)+" … [locally truncated]" else text).put("time",System.currentTimeMillis()))
  val kept=JSONArray();val cutoff=System.currentTimeMillis()-30L*24*60*60*1000
  for (i in maxOf(0,rows.length()-100) until rows.length()) {
   val r=rows.getJSONObject(i);if(r.optLong("time",0)>=cutoff) kept.put(r)
  }
  state.put("messages",kept);save();_ui.value=_ui.value.copy(transcript=messages())
 }
 private fun owner(g:Long,c:String,r:String):Boolean=foreground && generation.get()==g && state.optString("conversation")==c && state.optString("request")==r
 private fun stopOperations() { generation.incrementAndGet();job?.cancel();job=null;capture?.stop();captureJob?.cancel();captureJob=null;capture?.discard();capture=null;playback.stop() }
 fun onForeground() { foreground=true;if(bridge!=null && !authenticated) reconnect() }
 fun onBackground() {
  foreground=false;authenticated=false;stopOperations();_ui.value=_ui.value.copy(canCapture=false)
  if (_ui.value.phase in setOf("recording","finalizing")) show("ready","Recording discarded when app left foreground")
  else if (_ui.value.phase=="playing") show("done","Playback stopped when app left foreground")
  else if (_ui.value.phase in listOf("uploading","polling","creating","cancelling","confirming")) show("uncertain","Connection interrupted. Check existing request; no retry.")
 }
 fun configure(url:String,token:String,ca:ByteArray?=null) {
  if (!foreground) return
  val origin=try { Origin.parse(url) } catch (_:Exception) { _ui.value=_ui.value.copy(status="Enter an HTTPS origin (no path or credentials)",phase="error");return }
  if (token.length !in 40..256 || token.any { it.code == 10 || it.code == 13 }) { _ui.value=_ui.value.copy(status="Enter separate revocable device token",phase="error");return }
  stopOperations();authenticated=false;_ui.value=_ui.value.copy(canCapture=false);val g=generation.get()
  job=scope.launch {
   try {
    val candidate=withContext(io) { bridgeFactory(origin,token,ca).also { val h=it.health();require(h.getInt("protocol")==1 && h.getBoolean("ready")) {"Bridge not ready"} } }
    if (!foreground || generation.get()!=g) return@launch
    val conversation=withContext(io) { candidate.create() }
    if (!foreground || generation.get()!=g) return@launch
    val next=JSONObject().put("origin",origin).put("token",token).put("conversation",conversation).put("phase","ready").put("messages",JSONArray())
    if(ca!=null) next.put("ca",android.util.Base64.encodeToString(ca,android.util.Base64.NO_WRAP))
    vault.save(next);state=next;bridge=candidate;authenticated=true;restore()
   } catch (e:Exception) { if (foreground && generation.get()==g) _ui.value=_ui.value.copy(phase="error",status=safeError(e)) }
  }
 }
 fun reconnect() {
  if (!foreground || bridge==null) return
  if(_ui.value.phase in setOf("recording","finalizing")) {stopOperations();show("disconnected","Recording discarded for connection check")}
  else if(_ui.value.phase in setOf("uploading","polling","cancelling","confirming","creating")) {stopOperations();show("uncertain","Connection check interrupted active work. Check existing request; never resend.")}
  authenticated=false;_ui.value=_ui.value.copy(canCapture=false)
  val g=generation.incrementAndGet();job?.cancel()
  job=scope.launch {try {
   val h=withContext(io) {bridge!!.health()}
   if (foreground && generation.get()==g) {
    if(h.optInt("protocol")!=1 || !h.optBoolean("ready")) _ui.value=_ui.value.copy(phase="disconnected",status="Bridge not ready",canCapture=false)
    else {authenticated=true;show(if (state.optString("request").isNotEmpty() && state.optString("phase")=="uncertain") "uncertain" else "ready","Connected")}
   }
  } catch (e:Exception) {if(foreground && generation.get()==g) _ui.value=_ui.value.copy(phase="disconnected",status=safeError(e),canCapture=false)} }
 }
 fun startCapture() {
  if(!foreground || !authenticated || !_ui.value.canCapture || bridge==null || _ui.value.phase !in listOf("ready","done","error","cancelled") || state.optString("conversation").isEmpty()) return
  stopOperations();val g=generation.get();val c=captureFactory {routeInterrupted()};capture=c
  show("recording","Recording · stop and send or discard")
  captureJob=scope.launch {try {c.record();if(c.capped && foreground && generation.get()==g) { c.discard();capture=null;show("ready","30-second limit reached. Recording discarded; tap again.") }} catch (_:Exception) {if(foreground && generation.get()==g && _ui.value.phase=="recording") {c.discard();capture=null;show("error","Microphone interrupted or 16 kHz input unavailable")}} }
 }
 fun discard() {if(_ui.value.phase=="recording") {stopOperations();show("ready","Recording discarded")}}
 fun send() {
  if (!foreground || _ui.value.phase!="recording") return
  val c=capture ?:return;val recordingJob=captureJob;val g=generation.get()
  show("finalizing","Finishing recording")
  c.stop()
  job=scope.launch {
   try {recordingJob?.join()} catch (_:CancellationException) {c.discard();return@launch}
   if(!foreground || generation.get()!=g || capture!==c) {c.discard();return@launch}
   captureJob=null;capture=null
   val wav=try { c.finish() } catch (_:Exception) {c.discard();show("error","Recording interrupted or too short; nothing sent");return@launch}
   val conversation=state.getString("conversation");val request=Identity.new()
   // durable identity and ambiguous status before any upload bytes leave process
   state.put("request",request).put("turn","").put("phase","uncertain").put("fingerprint",MessageDigest.getInstance("SHA-256").digest(wav).joinToString("") {"%02x".format(it)})
   try {save();show("uploading","Sending recording (outcome may be uncertain)")}
   catch (_:Exception) { _ui.value=_ui.value.copy(phase="error",status="Cannot securely save request; nothing sent");return@launch }
   try {
   val turn=withContext(io) {bridge!!.submit(conversation,request,wav)}
   if(!owner(g,conversation,request)) return@launch
   state.put("turn",turn);show("polling","Turn admitted. Waiting for server status")
   poll(g,conversation,request,turn)
   } catch (e:Exception) {if(owner(g,conversation,request)) show("uncertain","Submission may have reached server. ${safeError(e)} Check existing request; never resend.")}
  }
 }
 private suspend fun poll(g:Long,c:String,r:String,turn:String) {
  var delayMs=1500L;var attempts=0
  while(owner(g,c,r) && attempts++<50) {
   val row=try {withContext(io) {bridge!!.poll(turn)}} catch (e:Exception) {if(owner(g,c,r)) show("uncertain","Polling interrupted: ${safeError(e)}. Check existing request.");return}
   if (!owner(g,c,r)) return
   if(row.optString("id")!=turn || row.optString("conversation")!=c) {show("uncertain","Server identity mismatch; no further action");return}
   applyRow(row,g,c,r);if(row.optString("state") in listOf("done","error","cancelled","uncertain","confirming")) return
   delay(delayMs);delayMs=minOf(delayMs+500,5000)
  }
  if(owner(g,c,r)) show("uncertain","Polling limit reached. Check existing request later.")
 }
 private suspend fun applyRow(row:JSONObject,g:Long,c:String,r:String) {
  val stage=row.optString("state");val detail=row.optString("detail").take(8000);val transcript=row.optString("transcript").take(8000)
  if(transcript.isNotBlank() && state.optString("shownTranscript")!=r) {append("YOU",transcript);state.put("shownTranscript",r);save()}
  when(stage) {
   "done" -> {
    if(state.optString("shownReply")!=r) { append("S.A.R.A.H",detail);state.put("shownReply",r);save() }
    show("done","Reply ready${if (row.optBoolean("audio")) " · audio available" else " · text only"}")
    if(row.optBoolean("audio") && owner(g,c,r)) try {
     val bytes=withContext(io) {bridge!!.audio(row.getString("id"))}
     if(owner(g,c,r)) {playback.play(bytes);show("playing","Playing reply · stop audio does not cancel work")}
    } catch (_:Exception) {if(owner(g,c,r)) show("done","Reply text ready. Audio unavailable")}
   }
   "confirming" -> {
    val envelope=row.optJSONObject("confirmation")
    if(envelope==null || envelope.optString("conversation")!=c) show("uncertain","Confirmation unavailable or invalid. No action sent by app")
    else show("confirming","Server requests confirmation; acceptance does not prove action success",envelope)
   }
   "error","cancelled","uncertain" -> show(stage,detail.ifBlank {stage})
   "transcribing","thinking","acting","speaking" -> show("polling","Server: $stage${if(detail.isBlank()) "" else " · $detail"}")
   else -> show("uncertain","Unknown server state; no retry")
  }
 }
 fun checkExisting() {
  if(!foreground || bridge==null || _ui.value.phase!="uncertain") return
  val c=state.optString("conversation");val r=state.optString("request")
  if(!Identity.valid(r)) { _ui.value=_ui.value.copy(status="No request identity to reconcile. New conversation available.");return }
  val g=generation.incrementAndGet();job?.cancel()
  job=scope.launch {try {
   val row=withContext(io) {bridge!!.lookup(r)}
   if(!owner(g,c,r)) return@launch
   val turn=row.optString("id");if(!Identity.valid(turn) || row.optString("conversation")!=c) {show("uncertain","Request identity mismatch");return@launch}
   state.put("turn",turn);save();applyRow(row,g,c,r)
   if(row.optString("state") !in listOf("done","error","cancelled","uncertain","confirming")) poll(g,c,r,turn)
  } catch(e:Exception) {if(owner(g,c,r)) show("uncertain","Lookup inconclusive (${safeError(e)}). Never resend.")}}
 }
 fun cancelTurn() {
  val turn=state.optString("turn");val c=state.optString("conversation");val r=state.optString("request")
  if(!foreground || !Identity.valid(turn) || _ui.value.phase !in setOf("polling","confirming")) return
  val g=generation.incrementAndGet();job?.cancel();show("cancelling","Cancelling server turn; this cannot undo dispatched actions")
  job=scope.launch {try {
   val row=withContext(io) {bridge!!.cancel(turn)}
   if(owner(g,c,r)) applyRow(row,g,c,r)
  } catch(e:Exception) {if(owner(g,c,r)) show("uncertain","Cancel outcome uncertain. Check request; actions may have occurred.")}}
 }
 fun confirm(accept:Boolean) {
  val envelope=_ui.value.confirmation ?:return;val turn=state.optString("turn");val c=state.optString("conversation");val r=state.optString("request")
  if(!foreground || _ui.value.phase!="confirming" || !Identity.valid(turn)) return
  val g=generation.incrementAndGet();job?.cancel();show("confirming","Sending confirmation; acceptance is not action success")
  job=scope.launch {try {
   withContext(io) {bridge!!.confirm(turn,c,envelope.getString("nonce"),envelope.getString("action_hash"),accept)}
   if(owner(g,c,r)) poll(g,c,r,turn)
  } catch (_:Exception) {if(owner(g,c,r)) show("uncertain","Confirmation outcome uncertain. Check existing request.")}}
 }
 fun stopAudio() {playback.stop();if(_ui.value.phase=="playing") show("done","Playback stopped. Server work not cancelled.")}
 fun newConversation() {
  if(!foreground || bridge==null) return
  stopOperations();val g=generation.get();show("creating","Creating separate server conversation; pending work is not undone")
  job=scope.launch {try {
   val c=withContext(io) {bridge!!.create()}
   if(!foreground || generation.get()!=g) return@launch
   state.remove("request");state.remove("turn");state.remove("fingerprint");state.remove("shownTranscript");state.remove("shownReply")
   state.put("conversation",c).put("messages",JSONArray());authenticated=false;show("disconnected","New conversation created. Check connection before recording");reconnect()
  } catch (_:Exception) {if(foreground && generation.get()==g) show("uncertain","New conversation outcome unknown. No automatic retry; old conversation retained.")}}
 }
 fun clearLocal() {state.put("messages",JSONArray());save();_ui.value=_ui.value.copy(transcript=emptyList(),status="Local transcript cleared. Server history remains.")}
 fun resetPairing() {
  if(!foreground) return
  stopOperations();authenticated=false;try {vault.reset();state=JSONObject();bridge=null;_ui.value=Ui(status="Local pairing cleared. Server conversations remain; request new device token from bridge administrator.")}
  catch (_:Exception) {_ui.value=_ui.value.copy(phase="error",status="Secure storage reset failed; no plaintext recovery")}
 }
 fun routeInterrupted() {
  if (_ui.value.phase=="recording") discard()
  else if (_ui.value.phase=="playing") stopAudio()
 }
 private fun safeError(e:Exception)=when(e) {
  is javax.net.ssl.SSLException -> "TLS verification failed"
  is Bridge.Failure -> when(e.status) {403->"Authentication or access denied";429->"Rate limited";503->"Bridge not ready";else->"HTTP ${e.status}"}
  is java.net.SocketTimeoutException -> "Network timeout"
  is IOException -> "Network unavailable"
  else -> "Connection or format error"
 }
 override fun onCleared() {onBackground();scope.cancel();super.onCleared()}
}
