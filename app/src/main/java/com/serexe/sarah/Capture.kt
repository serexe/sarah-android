package com.serexe.sarah

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/** No file written. Stop/discard clears in-memory PCM; hard cap never auto-sends. */
class Capture(private val context:Context,private val onInterrupted:()->Unit = {}):AudioCapture {
 @Volatile private var recorder:AudioRecord?=null
 private val buffer=ByteArrayOutputStream()
 @Volatile override var capped=false;private set
 @Volatile private var stopping=false
 @Volatile private var interrupted=false
 private val manager=context.getSystemService(AudioManager::class.java)
 private val routes=object:AudioDeviceCallback() {
  override fun onAudioDevicesRemoved(removed:Array<out AudioDeviceInfo>) {if(removed.isNotEmpty() && !stopping) interrupt()}
 }
 private var focus:AudioFocusRequest?=null
 private fun interrupt() {interrupted=true;stop();onInterrupted()}
 @SuppressLint("MissingPermission")
 override suspend fun record()=withContext(Dispatchers.IO) {
  check(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
  val min=AudioRecord.getMinBufferSize(Wav.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  check(min>0) { "16 kHz PCM input unavailable" }
  val r=AudioRecord(MediaRecorder.AudioSource.MIC,Wav.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,4096))
  check(r.state==AudioRecord.STATE_INITIALIZED) { r.release();"16 kHz PCM input unavailable" }
  recorder=r;buffer.reset();capped=false
  try {
   if(stopping) return@withContext
   val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    .setOnAudioFocusChangeListener {if(it<=0 && !stopping) interrupt()}.build()
   check(manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {"Microphone audio focus unavailable"}
   focus=request
   manager.registerAudioDeviceCallback(routes,Handler(Looper.getMainLooper()))
   r.startRecording()
   val chunk=ByteArray(2048)
   while (coroutineContext.isActive && !capped && !stopping) {
    val n=r.read(chunk,0,chunk.size)
    if (n<0 && !stopping) throw IllegalStateException("Microphone interrupted")
    if (n>0 && !stopping) synchronized(buffer) {
     val left=Wav.MAX_SAMPLES*2-buffer.size()
     buffer.write(chunk,0,minOf(n,left));if (buffer.size()>=Wav.MAX_SAMPLES*2) capped=true
    }
   }
   check(!interrupted) {"Microphone interrupted"}
  } catch(e:Exception) {interrupted=true;throw e}
  finally {
   recorder=null;try {r.stop()} catch (_:Exception) {};r.release()
   manager.unregisterAudioDeviceCallback(routes)
   focus?.let {manager.abandonAudioFocusRequest(it)};focus=null
  }
 }
 override fun stop() { stopping=true;try {recorder?.stop()} catch (_:Exception) {} }
 override fun finish():ByteArray=synchronized(buffer) {
  check(!interrupted && ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {"Microphone interrupted"}
  val bytes=buffer.toByteArray();buffer.reset();Wav.encode(bytes)
 }
 override fun discard() { stop();synchronized(buffer) { buffer.reset() } }
}
