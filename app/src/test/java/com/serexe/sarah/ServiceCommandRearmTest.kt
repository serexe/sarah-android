package com.serexe.sarah

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CompletableFuture

/** Real service completion boundary + production listener/command ownership; no Android lifecycle emulation. */
class ServiceCommandRearmTest {
 @Test fun sameServiceReturnsToListenerAfterSuccessErrorAndCancelCleanup()=runBlocking {
  var now=10_000L
  val started=now
  var listener=ListenerSession()
  assertTrue(AudioArbiter.acquire(listener));assertTrue(listener.gate.start(true))
  var restarts=0;var stops=0
  try {
   for(outcome in listOf("done","error","cancelled")) {
    assertTrue(listener.gate.hit())
    assertTrue(listener.releaseAfter({true},{true},{}))
    val command=Any();assertTrue(OffscreenOwnership.acquire(command))
    var ack=false
    val completion=ServiceCommandCompletion(
     {ack && listener.released && !OffscreenOwnership.held()},
     {ServiceRearmPolicy.allowed(true,false,false,started,now,true,true,OffscreenWakePolicy.resolved(outcome))},
     {val next=ListenerSession();if(!AudioArbiter.acquire(next)) false else {
      assertTrue(next.gate.start(true));listener=next;restarts++;true}},
     {stops++})
    try {
     assertFalse(completion.complete()) // terminal row/cancellation is not native ACK
     assertEquals(0,stops)
     ack=CommandCleanup.await(null,{true},CompletableFuture.completedFuture(true))
     assertFalse(completion.complete()) // ACK without command lease release also insufficient
     OffscreenOwnership.release(command)
     assertTrue("$outcome must return same service to listening",completion.complete())
     assertFalse("duplicate cleanup callback cannot start another listener",completion.complete())
     assertTrue(listener.gate.listening(true));assertEquals(0,stops)
     now+=1000
    } finally {OffscreenOwnership.release(command)}
   }
   assertEquals(3,restarts)
  } finally {
   listener.stopListening();assertTrue(listener.releaseAfter({true},{false},{}))
  }
  assertTrue(AudioArbiter.idle())
 }
 @Test fun stopLockRoleNotificationDeadlineAndUnknownOutcomeFailClosed() {
  fun allowed(owner:Boolean=true,stop:Boolean=false,destroyed:Boolean=false,start:Long=10_000L,
   now:Long=11_000L,safe:Boolean=true,unlocked:Boolean=true,phase:String="done")=
   ServiceRearmPolicy.allowed(owner,stop,destroyed,start,now,safe,unlocked,OffscreenWakePolicy.resolved(phase))
  assertTrue(allowed()) // no screen-interactivity requirement: unlocked screen-off remains eligible
  val rejected=listOf(allowed(stop=true),allowed(unlocked=false),allowed(safe=false), // role/notification/audio
   allowed(now=130_000L),allowed(start=0),allowed(phase="uncertain"),allowed(phase="confirming"),
   allowed(phase="polling"),allowed(destroyed=true),allowed(owner=false))
  for(admitted in rejected) {
   var starts=0;var stops=0
   val completion=ServiceCommandCompletion({true},{admitted},{starts++;true},{stops++})
   assertFalse(completion.complete());assertEquals(0,starts);assertEquals(1,stops)
   assertFalse(completion.complete());assertEquals(1,stops)
  }
  var stopped=0
  val busy=Any();assertTrue(AudioArbiter.acquire(busy))
  try {
   val completion=ServiceCommandCompletion({true},{true},{AudioArbiter.acquire(Any())},{stopped++})
   assertFalse(completion.complete());assertEquals(1,stopped)
  } finally {AudioArbiter.release(busy)}
 }
 @Test fun failedNativeCleanupCannotRestartOrReportOff()=runBlocking {
  val listener=ListenerSession();assertTrue(AudioArbiter.acquire(listener));listener.gate.start(true);listener.gate.hit()
  var restarted=false;var off=false
  try {
   assertFalse(listener.releaseAfter({false},{true},{error("no wake before native ACK")}))
   val commandAck=CommandCleanup.await(null,{false},CompletableFuture.completedFuture(true))
   val completion=ServiceCommandCompletion({listener.released && commandAck},{true},{restarted=true;true},{off=true})
   assertFalse(completion.complete());assertFalse(restarted);assertFalse(off);assertFalse(AudioArbiter.idle())
  } finally {AudioArbiter.release(listener)}
 }
 @Test fun pendingNativeReleaseBlocksRearmUntilAcknowledged()=runBlocking {
  val command=Any();assertTrue(OffscreenOwnership.acquire(command))
  val nativeReleased=CompletableDeferred<Unit>();var released=false;var ack=false;var starts=0;var off=0
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  val recording=scope.launch {
   try {awaitCancellation()} finally {withContext(NonCancellable) {nativeReleased.await();released=true}}
  }
  val completion=ServiceCommandCompletion({ack && !OffscreenOwnership.held()},{true},{starts++;true},{off++})
  try {
   recording.cancel()
   val cleanup=async(start=CoroutineStart.UNDISPATCHED) {
    CommandCleanup.await(recording,{released},CompletableFuture.completedFuture(true))
   }
   assertFalse(cleanup.isCompleted);assertFalse(completion.complete());assertEquals(0,starts);assertEquals(0,off)
   nativeReleased.complete(Unit);ack=cleanup.await();assertTrue(ack)
   assertFalse(completion.complete());assertEquals(0,starts) // lease release still required
   OffscreenOwnership.release(command)
   assertTrue(completion.complete());assertEquals(1,starts);assertEquals(0,off)
  } finally {nativeReleased.complete(Unit);scope.cancel();OffscreenOwnership.release(command)}
 }
 @Test fun visibleProductionHandoffRetainsOwnerUntilCompletionAndOriginalDeadline() {
  val source=File("src/main/java/com/serexe/sarah/HeySarahListenerService.kt").readText()
  val visible=source.substringAfter("val listenerOwner=session").substringBefore("} else if(AudioEligibility")
  assertTrue(visible.contains("visibleCommand=handoff"));assertTrue(visible.contains("ServiceCommandCompletion("))
  assertTrue(visible.contains("AudioArbiter.idle()"));assertTrue(visible.contains("ServiceRearmPolicy.allowed("))
  assertTrue(visible.contains("current===listenerOwner"));assertTrue(visible.contains("{visibleCommand=null;rearmAfterCommand()}"))
  assertTrue(visible.contains("if(!handed) {visibleCommand=null;stage(\"fallback\");notifyWake();finishService()}"))
  assertFalse(visible.contains("startForegroundService("));assertFalse(visible.contains("optedStart="))
  assertFalse(visible.contains("postDelayed(cutoff"))
 }
 @Test fun productionWiringKeepsExistingForegroundOwnerAndNeverRepostsOnReturn() {
  val service=File("src/main/java/com/serexe/sarah/HeySarahListenerService.kt").readText()
  val boundary=service.substringAfter("lateinit var completion:ServiceCommandCompletion").substringBefore("private fun stage(")
  assertTrue(boundary.contains("{completion.complete()}"));assertTrue(boundary.contains("owner.cleanupComplete"))
  assertTrue(boundary.contains("!OffscreenOwnership.held()"));assertTrue(boundary.contains("ServiceRearmPolicy.allowed("))
  assertTrue(boundary.contains("{rearmAfterCommand()}"));assertTrue(boundary.contains("AudioArbiter.acquire(next)"))
  assertFalse(boundary.contains("startForegroundService("));assertFalse(boundary.contains("isInteractive"))
  assertFalse(boundary.contains("optedStart="));assertFalse(boundary.contains("postDelayed(cutoff"))
  val command=File("src/main/java/com/serexe/sarah/OffscreenCommand.kt").readText()
  assertEquals(1,Regex("bridge\\.submitCancellable\\(").findAll(command).count())
  assertFalse(command.contains("bridge.confirm"));assertTrue(command.contains("rearmAllowed=!stopped && eligible() && OffscreenWakePolicy.resolved"))
  assertTrue(command.indexOf("CommandCleanup.await(")<command.indexOf("rearmAllowed=!stopped"))
  assertTrue(command.indexOf("OffscreenOwnership.release(this@OffscreenCommand);finished()")>command.indexOf("rearmAllowed=!stopped"))
 }
}
