package com.devlens

import com.google.gson.Gson
import com.devlens.data.DevLensNode

fun main() {
    val json = """{"id":"1","name":"Test","host":"1.1.1.1","token":"test"}"""
    val gson = Gson()
    val node = gson.fromJson(json, DevLensNode::class.java)
    val safeKnownIps = node.knownIps ?: emptyList()
    val hostsToTry = (listOf(node.host) + safeKnownIps).distinct().filter { it.isNotBlank() }
    println(hostsToTry)
}
