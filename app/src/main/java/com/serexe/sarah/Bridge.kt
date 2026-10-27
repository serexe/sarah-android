package com.serexe.sarah

import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

class Bridge(private val origin:String,private val token:String,ca:ByteArray?=null):BridgePort {
 init { require(Origin.parse(origin)==origin && token.length in 40..256) }
 private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
  .connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).writeTimeout(40,TimeUnit.SECONDS)
  .callTimeout(55,TimeUnit.SECONDS).apply {
   if (ca!=null) {
    val cert=CertificateFactory.getInstance("X.509").generateCertificate(ca.inputStream()) as X509Certificate
    cert.checkValidity();require(cert.basicConstraints>=0) { "Certificate is not a CA" }
    val store=KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null);setCertificateEntry("explicit-ca",cert) }
    val tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
    val tm=tmf.trustManagers.filterIsInstance<X509TrustManager>().single()
    val tls=SSLContext.getInstance("TLS").apply { init(null,arrayOf(tm),null) }
    sslSocketFactory(tls.socketFactory,tm) // OkHttp default hostname verifier stays enabled
   }
  }.build()
 class Failure(val status:Int,val reason:String):IOException("Bridge status $status: $reason")
 private fun boundedJson(body:ResponseBody):JSONObject {
  require(body.contentLength()<=262144) { "Payload too large" }
  return JSONObject(Bounded.read(body.byteStream(),262144).toString(Charsets.UTF_8))
 }
 private fun call(path:String,method:String="GET",body:RequestBody?=null,headers:Map<String,String> = emptyMap()):Pair<Int,ResponseBody> {
  require(path.startsWith("/v1/") && !path.contains(".."))
  val request=Request.Builder().url(origin+path).header("Authorization","Bearer $token").header("Cache-Control","no-store")
  headers.forEach { (k,v)->request.header(k,v) }
  val response=client.newCall(request.method(method,body).build()).execute()
  // response body ownership transferred to caller; redirects rejected, never forward token
  if (response.code !in 200..299) {
   val code=response.code;val reason=try { response.body?.let {boundedJson(it).optString("error","http_error").take(100)} ?: "http_error" } catch (e:IllegalArgumentException) {response.close();throw e} catch (_:Exception) { "http_error" }
   response.close();throw Failure(code,reason)
  }
  return response.code to (response.body ?: run { response.close();throw IOException("Empty response") })
 }
 private fun json(path:String,method:String="GET",body:RequestBody?=null,headers:Map<String,String> = emptyMap()):JSONObject = call(path,method,body,headers).second.use { boundedJson(it) }
 private fun empty()="{}".toRequestBody("application/json".toMediaType())
 override fun health():JSONObject=json("/v1/health")
 override fun create():String=json("/v1/conversations","POST",empty()).getString("conversation").also { require(Identity.valid(it)) }
 override fun submit(conversation:String,request:String,wav:ByteArray):String {
  require(Identity.valid(conversation) && Identity.valid(request))
  return json("/v1/turns","POST",wav.toRequestBody("audio/wav".toMediaType()),mapOf("X-Conversation" to conversation,"Idempotency-Key" to request)).getString("turn").also { require(Identity.valid(it)) }
 }
 override fun lookup(request:String):JSONObject { require(Identity.valid(request));return json("/v1/requests/$request") }
 override fun poll(turn:String):JSONObject { require(Identity.valid(turn));return json("/v1/turns/$turn") }
 override fun cancel(turn:String):JSONObject { require(Identity.valid(turn));return json("/v1/turns/$turn/cancel","POST",empty()) }
 override fun confirm(turn:String,conversation:String,nonce:String,hash:String,accept:Boolean):JSONObject {
  require(listOf(turn,conversation,nonce,hash).all(Identity::valid))
  return json("/v1/turns/$turn/confirmation","POST",JSONObject().put("conversation",conversation).put("nonce",nonce).put("action_hash",hash).put("accept",accept).toString().toRequestBody("application/json".toMediaType()))
 }
 override fun audio(turn:String):ByteArray {
  require(Identity.valid(turn))
  val body=call("/v1/turns/$turn/audio").second
  return body.use { require(it.contentType()?.let { type->type.type=="audio" && type.subtype=="mpeg" }==true && (it.contentLength() in -1..4194304));it.byteStream().use { stream->Bounded.read(stream,4194304).also { b->require(b.isNotEmpty()) } } }
 }
}
