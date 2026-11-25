import com.riverrunner.engine.ComputeProtocol
fun main() {
    val scope = js("self")
    scope.onmessage = { event: dynamic ->
        val response = try { ComputeProtocol.run(event.data.payload as String) }
            catch (e: Throwable) { "{\"error\":\"Calculation failed\"}" }
        val message = js("({})")
        message.id = event.data.id
        message.payload = response
        scope.postMessage(message)
    }
}
