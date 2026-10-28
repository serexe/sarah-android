package com.serexe.sarah

import android.content.Context

/** Local, bounded diagnostic labels only. Never receives user content or exception text. */
internal object StageTrail {
 private val labels=setOf("starting","listening","hit","released","locked","fallback","recording","discarded","interrupted","uploading","polling","speaking","done","confirmation-required","error","uncertain","cancelled","stopped","cleanup-failed")
 @Synchronized fun write(context:Context,label:String) {
  require(label in labels)
  val prefs=context.getSharedPreferences("local-stage",Context.MODE_PRIVATE)
  val previous=prefs.getString("history","").orEmpty().lines().filter {it.isNotBlank()}.takeLast(11)
  prefs.edit().putString("history",(previous+"${System.currentTimeMillis()} $label").joinToString("\n")).apply()
 }
 fun read(context:Context):String=context.getSharedPreferences("local-stage",Context.MODE_PRIVATE).getString("history","").orEmpty()
}
