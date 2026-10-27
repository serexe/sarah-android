package com.serexe.sarah

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class Playback(private val context:Context,private val onDone:()->Unit):AudioPlayback {
 private val manager=context.getSystemService(AudioManager::class.java)
 private var player:MediaPlayer?=null
 private var temp:File?=null
 private var focus:AudioFocusRequest?=null
 private var serial=0L
 private val routes=object:AudioDeviceCallback() {
  override fun onAudioDevicesRemoved(removedDevices:Array<out AudioDeviceInfo>) {if (removedDevices.isNotEmpty()) {stop();onDone()} }
 }
 private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
 override fun play(bytes:ByteArray) {
  stop()
  val id=++serial
  val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
   .setOnAudioFocusChangeListener { if (it<=0 && serial==id) { stop();onDone() } }.build()
  check(manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus unavailable" }
  focus=request
  manager.registerAudioDeviceCallback(routes,Handler(Looper.getMainLooper()))
  try {
   val file=File.createTempFile("reply-",".mp3",context.cacheDir);temp=file
   file.outputStream().use {it.write(bytes)}
   player=MediaPlayer().apply {
    setAudioAttributes(attributes);setDataSource(file.absolutePath)
    setOnCompletionListener { if (serial==id) {stop();onDone()} }
    setOnErrorListener { _,_,_->if(serial==id) {stop();onDone()};true }
    prepare();start()
   }
  } catch (e:Exception) { stop();throw e }
 }
 override fun stop() {
  serial++
  val p=player;player=null;try {p?.stop()} catch (_:Exception) {};p?.release()
  focus?.let {manager.abandonAudioFocusRequest(it)};focus=null
  manager.unregisterAudioDeviceCallback(routes)
  temp?.delete();temp=null
 }
}
