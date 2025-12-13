package aeza.hostmaster.mobile.domain.model

sealed class CheckType(
    val route: String,
    val backendName: String,
    val title: String,
    val description: String,
    val inputHint: String
) {
    object Ping : CheckType(
        route = "ping",
        backendName = "PING",
        title = "Ping",
        description = "Проверка доступности сервера и времени отклика.",
        inputHint = "example.com"
    )

    object Http : CheckType(
        route = "http",
        backendName = "HTTP",
        title = "HTTP",
        description = "Запрос к HTTP/HTTPS ресурсу и проверка кода ответа.",
        inputHint = "https://example.com"
    )

    object Tcp : CheckType(
        route = "tcp",
        backendName = "TCP_CONNECT",
        title = "TCP",
        description = "Проверка доступности TCP-порта.",
        inputHint = "example.com:22"
    )

    object Dns : CheckType(
        route = "dns",
        backendName = "DNS_LOOKUP",
        title = "DNS",
        description = "Получение DNS-записей домена.",
        inputHint = "example.com"
    )

    object Info : CheckType(
        route = "info",
        backendName = "TRACEROUTE",
        title = "Info",
        description = "Сбор общей информации о хостинге или домене.",
        inputHint = "example.com"
    )

    companion object {
        val items = listOf(Ping, Http, Tcp, Dns, Info)

        fun fromRoute(route: String?): CheckType? =
            items.firstOrNull { it.route == route }

        fun fromBackendName(name: String?): CheckType? {
            val normalized = name
                ?.replace('-', '_')
                ?.uppercase()
                ?: return null

            return when (normalized) {
                Ping.backendName -> Ping
                Http.backendName -> Http
                Tcp.backendName, "TCP" -> Tcp
                Dns.backendName, "DNS" -> Dns
                Info.backendName, "INFO" -> Info
                else -> null
            }
        }
    }
}
