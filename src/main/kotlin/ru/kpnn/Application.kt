package ru.kpnn

import rawhttp.core.*
import java.net.ServerSocket
import java.net.Socket

import rawhttp.core.body.FileBody
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException

import java.util.concurrent.Semaphore

fun handleProxy(uri: String, request: RawHttpRequest) : EagerHttpResponse<Void> {

    val (host, path) = if (uri.contains("/")) uri.split("/", limit=2) else listOf(uri, "")

    println("Connecting client: to $host/$path")

    val port = 80

    val http = RawHttp()

    val newRequestLine = RequestLine("GET", URI(path), HttpVersion.HTTP_1_1).withHost(host)
    val requestProxy = request.withRequestLine(newRequestLine)

    val responseProxy = try {
        val socket = Socket(host, port)

        socket.use {
            requestProxy.writeTo(it.getOutputStream())
            http.parseResponse(it.getInputStream()).eagerly()
        }

    } catch (e: UnknownHostException) {
        RawHttp().parseResponse("""
            HTTP/1.1 404 Not found
            Server: Lab3Server
            Content-Length: 0
        """.trimIndent()).eagerly()
    }


    return responseProxy
}

fun handleClient(client: Socket) {
    println("Client in work: ${client}")

    val reader = client.getInputStream()
    val writer = client.getOutputStream()

    val http = RawHttp()

//    val (host, path) = if (uri.contains("/")) uri.split("/", limit=2) else listOf(uri, "")

    while (client.isConnected) {

        val request = http.parseRequest(reader)

        val uri = request.uri.path.drop(1)
        println("Client requests: ${request.uri}")

        val responseProxy = handleProxy(uri, request)

        responseProxy.writeTo(writer)
    }


    client.close()

    println("Client socket closed: ${client}")
}

fun main(args: Array<String>) {

    val port = 8088 //args[0].toInt()
    val permits = 4 //args[1].toInt()

    val s = Semaphore(permits)

    val server = ServerSocket(port)
    println("Server is running on port ${server.localPort}")

    while (true) {
        val client = server.accept()

        println("Client connected: ${client}")

        Thread {
            s.acquire()
            handleClient(client)
            s.release()
        }.start()
    }

}