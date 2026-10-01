import com.google.gson.Gson
import com.devlens.data.DevLensNode

fun main() {
    val json = """{"id":"1","name":"Test","host":"1.1.1.1","token":"test"}"""
    val gson = Gson()
    val node = gson.fromJson(json, DevLensNode::class.java)
    println(node.knownIps)
}
