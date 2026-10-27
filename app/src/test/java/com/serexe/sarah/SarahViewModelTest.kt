package com.serexe.sarah

import android.app.Application
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

/** Exercises production SarahViewModel, not unused TurnState. No Android device/network. */
class SarahViewModelTest {
 private class Memory(initial:JSONObject):StateStore {
  var value=JSONObject(initial.toString())
  override fun load()=JSONObject(value.toString())
  override fun save(value:JSONObject) {this.value=JSONObject(value.toString())}
  override fun reset() {value=JSONObject()}
 }
 private class Wire:BridgePort {
  var healthy=true;var failure:Exception?=null;var posts=0;var lookups=0
  var onSubmit:(()->Unit)?=null
  var row=JSONObject().put("id","t1").put("conversation","c1").put("state","done").put("detail","reply").put("audio",false)
  override fun health():JSONObject {failure?.let {throw it};return JSONObject().put("protocol",1).put("ready",healthy)}
  override fun create()="c1"
  override fun submit(conversation:String,request:String,wav:ByteArray):String {posts++;onSubmit?.invoke();return "t1"}
  override fun lookup(request:String):JSONObject {lookups++;return row}
  override fun poll(turn:String)=row
  override fun cancel(turn:String)=row
  override fun confirm(turn:String,conversation:String,nonce:String,hash:String,accept:Boolean)=row
  override fun audio(turn:String)=byteArrayOf(1)
 }
 private class Mic:AudioCapture {
  val end=CompletableDeferred<Unit>();var stopped=false;var joined=false;var discarded=false;var finished=0;var permissionRevoked=false
  override val capped=false
  override suspend fun record() {end.await();joined=true}
  override fun stop() {stopped=true}
  override fun finish():ByteArray {check(joined);if(permissionRevoked) throw SecurityException("permission revoked");finished++;return Wav.encode(ByteArray(8000))}
  override fun discard() {discarded=true;stop();end.complete(Unit)}
 }
 private class Sound:AudioPlayback {var played=0;override fun play(bytes:ByteArray) {played++};override fun stop() {}}
 private fun persisted(phase:String="ready")=JSONObject().put("origin","https://fixture.invalid").put("token","x".repeat(48)).put("conversation","c1").put("phase",phase)
 private data class Fixture(val vm:SarahViewModel,val memory:Memory,val wire:Wire,val mic:Mic,val sound:Sound)
 private fun fixture(initial:JSONObject=persisted()):Fixture {
  val memory=Memory(initial);val wire=Wire();val mic=Mic();val sound=Sound()
  val vm=SarahViewModel(Application(),memory,{_,_,_->wire},{_->mic},{sound},CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),Dispatchers.Unconfined)
  return Fixture(vm,memory,wire,mic,sound)
 }
 @Test fun restoredPlayingStopsWithoutAudioReplay() {
  val f=fixture(persisted("playing"));assertEquals("done",f.vm.ui.value.phase)
  assertEquals("done",f.memory.value.getString("phase"));assertFalse(f.vm.ui.value.canCapture)
  assertEquals(0,f.sound.played)
  f.vm.onForeground();assertTrue(f.vm.ui.value.canCapture);assertEquals(0,f.sound.played)
 }
 @Test fun reconnectRevocationTlsOfflineNotReadyPreventsCapture() {
  val f=fixture();f.vm.onForeground();assertTrue(f.vm.ui.value.canCapture)
  for (failure in listOf(Bridge.Failure(403,"denied"),SSLHandshakeException("bad cert"),IOException("offline"),Bridge.Failure(503,"not ready"))) {
   f.wire.failure=failure;f.vm.reconnect();assertEquals("disconnected",f.vm.ui.value.phase)
   assertFalse(f.vm.ui.value.canCapture);f.vm.startCapture();assertEquals(0,f.mic.finished)
   f.wire.failure=null;f.vm.reconnect();assertTrue(f.vm.ui.value.canCapture)
  }
  f.wire.healthy=false;f.vm.reconnect();assertFalse(f.vm.ui.value.canCapture)
  f.vm.startCapture();assertEquals("disconnected",f.vm.ui.value.phase)
 }
 @Test fun duplicateTapWaitsForRecorderAndLostAdmissionReconcilesReadOnly() {
  val f=fixture();f.vm.onForeground();f.vm.startCapture();f.vm.startCapture()
  assertEquals("recording",f.vm.ui.value.phase)
  f.vm.send();f.vm.send();assertEquals("finalizing",f.vm.ui.value.phase)
  assertEquals(0,f.wire.posts);assertEquals(0,f.mic.finished)
  f.wire.onSubmit={throw IOException("response lost")};f.mic.end.complete(Unit)
  assertEquals(1,f.wire.posts);assertEquals(1,f.mic.finished)
  assertEquals("uncertain",f.vm.ui.value.phase);assertTrue(f.memory.value.has("request"))
  f.vm.checkExisting();assertEquals(1,f.wire.lookups);assertEquals(1,f.wire.posts)
  assertEquals("done",f.vm.ui.value.phase)
 }
 @Test fun staleReplyAndBackgroundCaptureNeverSubmit() {
  val f=fixture();f.vm.onForeground();f.vm.startCapture();f.vm.onBackground()
  assertTrue(f.mic.discarded);assertEquals(0,f.wire.posts)
  assertFalse(f.vm.ui.value.canCapture);f.vm.startCapture();assertEquals(0,f.wire.posts)
  val another=fixture();another.vm.onForeground();another.vm.startCapture();another.vm.send()
  another.vm.onBackground();another.mic.end.complete(Unit)
  assertEquals(0,another.wire.posts)
 }
 @Test fun staleSubmitCallbackCannotPublishAfterBackground() {
  val f=fixture();f.vm.onForeground();f.vm.startCapture()
  f.wire.onSubmit={f.vm.onBackground()}
  f.vm.send();f.mic.end.complete(Unit)
  assertEquals(1,f.wire.posts);assertNotEquals("polling",f.vm.ui.value.phase)
  assertFalse(f.vm.ui.value.canCapture)
 }
 @Test fun reconnectDuringUploadPreservesUncertainAdmission() {
  val f=fixture();f.vm.onForeground();f.vm.startCapture()
  f.wire.onSubmit={f.vm.reconnect()}
  f.vm.send();f.mic.end.complete(Unit)
  assertEquals("uncertain",f.vm.ui.value.phase);assertFalse(f.vm.ui.value.canCapture)
  f.vm.startCapture();f.vm.send();assertEquals(1,f.wire.posts)
  f.vm.checkExisting();assertEquals(1,f.wire.lookups);assertEquals(1,f.wire.posts)
 }
 @Test fun routeInterruptionDiscardsAndPermissionRevokeBlocksSend() {
  val f=fixture();f.vm.onForeground();f.vm.startCapture();f.vm.routeInterrupted()
  assertTrue(f.mic.discarded);assertEquals(0,f.wire.posts)
  val revoked=fixture();revoked.vm.onForeground();revoked.vm.startCapture()
  revoked.mic.permissionRevoked=true;revoked.vm.send();revoked.mic.end.complete(Unit)
  assertEquals("error",revoked.vm.ui.value.phase);assertEquals(0,revoked.wire.posts)
 }
 @Test fun uncertainRestoreKeepsRequestForReadOnlyLookupAfterReconnect() {
  val initial=persisted("uncertain").put("request","r1");val f=fixture(initial)
  f.vm.onForeground();f.wire.failure=Bridge.Failure(403,"denied");f.vm.reconnect()
  assertFalse(f.vm.ui.value.canCapture);assertEquals("uncertain",f.memory.value.getString("phase"))
  f.wire.failure=null;f.vm.reconnect();assertEquals("uncertain",f.vm.ui.value.phase)
  f.vm.checkExisting();assertEquals(1,f.wire.lookups);assertEquals(0,f.wire.posts)
 }
}
