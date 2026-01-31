package somnia.vm.stdlib

import somnia.vm.SomniaCallable
import somnia.vm.SomniaVM
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Standard HTTP Module (std/http)
 * Functions for HTTP client and server operations.
 */
object StdHttp {
    
    private val httpClient = HttpClient.newBuilder().build()
    
    val get = object : SomniaCallable {
        override val name = "httpGet"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val url = args[0] as? String ?: throw RuntimeException("httpGet expects a URL string")
            return try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                mapOf(
                    "status" to response.statusCode(),
                    "body" to response.body(),
                    "headers" to response.headers().map()
                )
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    val post = object : SomniaCallable {
        override val name = "httpPost"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val url = args[0] as? String ?: throw RuntimeException("httpPost expects URL as first arg")
            val body = args[1]?.toString() ?: ""
            return try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                mapOf(
                    "status" to response.statusCode(),
                    "body" to response.body()
                )
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    val put = object : SomniaCallable {
        override val name = "httpPut"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val url = args[0] as? String ?: throw RuntimeException("httpPut expects URL as first arg")
            val body = args[1]?.toString() ?: ""
            return try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                mapOf("status" to response.statusCode(), "body" to response.body())
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    val delete = object : SomniaCallable {
        override val name = "httpDelete"
        override val arity = 1
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val url = args[0] as? String ?: throw RuntimeException("httpDelete expects a URL")
            return try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                mapOf("status" to response.statusCode(), "body" to response.body())
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    val serve = object : SomniaCallable {
        override val name = "httpServe"
        override val arity = 2
        override fun call(vm: SomniaVM, args: List<Any?>): Any? {
            val port = (args[0] as? Number)?.toInt() ?: 8080
            val handlerName = args[1] as? String ?: "handler"
            
            println("[Somnia HTTP] Starting server on port $port...")
            
            val server = HttpServer.create(InetSocketAddress(port), 0)
            server.createContext("/") { exchange ->
                val requestData = mapOf(
                    "method" to exchange.requestMethod,
                    "path" to exchange.requestURI.path,
                    "query" to (exchange.requestURI.query ?: ""),
                    "body" to exchange.requestBody.bufferedReader().readText()
                )
                
                // For now, return a simple response
                val response = """{"message": "Hello from Somnia HTTP!", "request": "$requestData"}"""
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
            server.executor = Executors.newFixedThreadPool(4)
            server.start()
            
            println("[Somnia HTTP] Server running at http://localhost:$port")
            return mapOf("port" to port, "status" to "running")
        }
    }
    
    fun all(): List<SomniaCallable> = listOf(get, post, put, delete, serve)
}
