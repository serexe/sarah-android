package com.serexe.sarah

import android.app.NotificationManager
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

/** Production safety helpers, production Bridge, and production ViewModel paths. */
class OffscreenRemediationTest {
 @Test fun cancelBeforeCommandDispatchStillRunsProductionFinallyAndRequiresNativeAck()=runBlocking {
  // Guard the production call site too: launchOwned's receiver is a CoroutineScope,
  // not the OffscreenCommand that acquired the identity-based lease.
  val commandSource=File("src/main/java/com/serexe/sarah/OffscreenCommand.kt").readText()
  assertTrue("cleanup must release acquired command, not coroutine scope",
   commandSource.contains("cleanupComplete=true;OffscreenOwnership.release(this@OffscreenCommand);finished()"))
  val queued=ArrayDeque<Runnable>()
  val dispatcher=object:CoroutineDispatcher() {
   override fun dispatch(context:CoroutineContext,block:Runnable) {queued.addLast(block)}
  }
  for(cancelBeforeLaunch in listOf(false,true)) for(ack in listOf(false,true)) {
   val owner=Any()
   val prior=OffscreenOwnership.releases.value
   assertTrue(OffscreenOwnership.acquire(owner))
   try {
    val scope=CoroutineScope(SupervisorJob()+dispatcher)
    if(cancelBeforeLaunch) scope.cancel() // Watchdog may stop between acquisition and launch.
    var entered=false
    var cleaned=false
    CommandCleanup.launchOwned(scope) {
     try {entered=true;delay(1000)} finally {
      cleaned=true
      val native=CommandCleanup.await(null,{ack},CompletableFuture.completedFuture(true))
      if(native) OffscreenOwnership.release(owner)
     }
    }
    scope.cancel() // Service Stop arrives before Main dispatcher runs coroutine.
    while(queued.isNotEmpty()) queued.removeFirst().run()
    assertTrue("production command entered try before Stop",entered)
    assertTrue("production command finally ran",cleaned)
    assertEquals(!ack,OffscreenOwnership.held())
    assertEquals(prior+if(ack) 1 else 0,OffscreenOwnership.releases.value)
   } finally {OffscreenOwnership.release(owner)}
  }
 }
 @Test fun recorderClockAndPcmBudgetRejectDispatcherDelay() {
  var now=1_000_000_000L
  val limit=CommandCaptureLimit(now,{now})
  assertEquals(2048,limit.bytesAllowed(0,2048))
  assertEquals(2,limit.bytesAllowed(Wav.RATE*8*2-2,2048))
  now+=8_000_000_000L
  assertTrue(limit.expired)
  assertEquals(0,limit.bytesAllowed(0,2048))
  assertEquals(0,limit.bytesAllowed(Wav.RATE*8*2,2048))
 }
 @Test fun notificationTransitionsFailClosedAtEveryCheck() {
  val allowed={permission:Boolean,enabled:Boolean,importance:Int?->NotificationAvailability.evaluate(permission,enabled,importance)}
  assertTrue(allowed(true,true,NotificationManager.IMPORTANCE_LOW))
  assertFalse(allowed(false,true,NotificationManager.IMPORTANCE_LOW)) // revoked at handoff
  assertFalse(allowed(true,false,NotificationManager.IMPORTANCE_LOW)) // disabled during capture
  assertFalse(allowed(true,true,NotificationManager.IMPORTANCE_NONE)) // muted before upload
  assertFalse(allowed(true,true,null)) // missing during playback
 }
 @Test fun sharedLeaseProtectsPersistentWritesAndOwnerAcrossInterleavings() {
  val first=Any();val second=Any();var request="older"
  assertTrue(OffscreenOwnership.acquire(first))
  try {
   assertFalse(OffscreenOwnership.acquire(second))
   OffscreenOwnership.guarded(first) {request="admitted"}
   assertThrows(IllegalStateException::class.java) {OffscreenOwnership.guarded(null) {request="erased"}}
   assertEquals("admitted",request)
   assertTrue(OffscreenOwnership.owns(first))
  } finally {OffscreenOwnership.release(first)}
  OffscreenOwnership.guarded(null) {assertEquals("admitted",request)}
 }
 @Test fun processStartupPurgesOnlyLegacyPlaintextReplies() {
  val directory=Files.createTempDirectory("sarah-reply-fixture").toFile()
  try {
   val old=File(directory,"reply-abcd.mp3").apply {writeText("synthetic private reply")}
   val unrelated=File(directory,"other.mp3").apply {writeText("keep")}
   purgeLegacyReplies(directory)
   assertFalse(old.exists());assertTrue(unrelated.exists())
   purgeLegacyReplies(directory)
  } finally {directory.listFiles()?.forEach {it.delete()};directory.delete()}
 }
 private fun server():Pair<MockWebServer,ByteArray> {
  val ca=HeldCertificate.Builder().certificateAuthority(1).commonName("Synthetic CA").build()
  val leaf=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").signedBy(ca).build()
  val server=MockWebServer()
  server.useHttps(HandshakeCertificates.Builder().heldCertificate(leaf,ca.certificate).build().sslSocketFactory(),false)
  server.start()
  return server to ca.certificate.encoded
 }
 @Test fun droppedTlsResponseDoesNotReplayPostAndLookupIsReadOnly() {
  val (server,ca)=server()
  try {
   val bridge=Bridge("https://localhost:${server.port}","x".repeat(48),ca)
   server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
   assertThrows(Exception::class.java) {runBlocking {bridge.submitCancellable("c1","request-1",Wav.encode(ByteArray(8000)))}}
   val post=server.takeRequest(3,TimeUnit.SECONDS)!!
   assertEquals("POST",post.method)
   assertEquals("request-1",post.getHeader("Idempotency-Key"))
   assertEquals("c1",post.getHeader("X-Conversation"))
   assertEquals(1,server.requestCount)
   server.enqueue(MockResponse().setBody("{\"id\":\"t1\",\"conversation\":\"c1\",\"state\":\"uncertain\"}"))
   assertEquals("uncertain",bridge.lookup("request-1").getString("state"))
   assertEquals("GET",server.takeRequest(3,TimeUnit.SECONDS)!!.method)
   assertEquals(2,server.requestCount)
  } finally {server.shutdown()}
 }
 @Test fun cancellationClosesProductionTlsCallAfterPostAdmission()=runBlocking {
  val (server,ca)=server()
  try {
   val bridge=Bridge("https://localhost:${server.port}","x".repeat(48),ca)
   server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
   val job=launch(Dispatchers.Default) {bridge.submitCancellable("c1","request-stop",Wav.encode(ByteArray(8000)))}
   val admitted=withContext(Dispatchers.IO) {server.takeRequest(3,TimeUnit.SECONDS)}!!
   assertEquals("request-stop",admitted.getHeader("Idempotency-Key"))
   withTimeout(2000) {job.cancelAndJoin()}
   assertEquals(1,server.requestCount)
  } finally {server.shutdown()}
 }
 @Test fun partialTlsResponseNeverReplaysAdmittedPost() {
  val (server,ca)=server()
  try {
   val bridge=Bridge("https://localhost:${server.port}","x".repeat(48),ca)
   server.enqueue(MockResponse().setBody("{\"turn\":\"synthetic-admitted\"}").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
   assertThrows(Exception::class.java) {runBlocking {bridge.submitCancellable("c1","response-cut",Wav.encode(ByteArray(8000)))}}
   val admission=server.takeRequest(3,TimeUnit.SECONDS)!!
   assertEquals("POST",admission.method)
   assertEquals("response-cut",admission.getHeader("Idempotency-Key"))
   assertEquals(1,server.requestCount)
  } finally {server.shutdown()}
 }
 @Test fun retryableTlsServiceResponseStillDoesNotReplayPost() {
  val (server,ca)=server()
  try {
   val bridge=Bridge("https://localhost:${server.port}","x".repeat(48),ca)
   server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After","0").setBody("{\"error\":\"synthetic\"}"))
   server.enqueue(MockResponse().setBody("{\"turn\":\"must-not-reach\"}"))
   assertThrows(Bridge.Failure::class.java) {runBlocking {bridge.submitCancellable("c1","retry-guard",Wav.encode(ByteArray(8000)))}}
   assertEquals("retry-guard",server.takeRequest(3,TimeUnit.SECONDS)!!.getHeader("Idempotency-Key"))
   assertEquals(1,server.requestCount)
  } finally {server.shutdown()}
 }
}
