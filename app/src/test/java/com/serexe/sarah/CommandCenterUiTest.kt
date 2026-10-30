package com.serexe.sarah

import org.junit.Assert.*
import org.junit.Test

class CommandCenterUiTest {
 @Test fun currentTurnNeverReusesOlderAnswer() {
  val events=listOf("YOU" to "older question","SARAH" to "older answer","YOU" to "new question")
  assertEquals(listOf("YOU" to "new question"),commandCurrentTurn(events))
  assertTrue(commandCurrentTurn(emptyList()).isEmpty())
  assertTrue(commandCurrentTurn(events+listOf("S.A.R.A.H" to "previous answer"),"recording").isEmpty())
  assertTrue(commandCurrentTurn(events+listOf("S.A.R.A.H" to "previous answer"),"polling").isEmpty())
  assertEquals(listOf("YOU" to "new question"),commandCurrentTurn(events,"polling"))
  assertEquals(listOf("YOU" to "new question","SARAH" to "actual answer"),commandCurrentTurn(events+listOf("SARAH" to "actual answer")))
 }
 @Test fun readinessNeverInventsConnectionOrCompletion() {
  assertEquals("Not ready",commandReadiness("done",false))
  assertEquals("Check request",commandReadiness("uncertain",true))
  assertEquals("Not connected",commandReadiness("unknown",true))
  assertEquals("Speaking",commandReadiness("playing",false))
 }
}
