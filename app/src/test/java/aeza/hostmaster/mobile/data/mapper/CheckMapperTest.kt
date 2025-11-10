package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseContextDto
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckMapperTest {
    private val gson = Gson()
    private val mapper = CheckMapper(gson)

    @Test
    fun `formats ping payload into readable summary`() {
        val payloadJson = """
            {
              "ping": [
                {
                  "country": "sidereagisart_78",
                  "ip": "213.180.204.186",
                  "location": "siderea_78",
                  "packets": {
                    "loss": "0%",
                    "received": 4,
                    "transmitted": 4
                  },
                  "roundTrip": {
                    "avg": "36.930 ms",
                    "max": "50.041 ms",
                    "min": "25.781 ms"
                  }
                }
              ]
            }
        """.trimIndent()

        val dto = CheckResponseDto(
            jobId = "job",
            status = "complete",
            type = "ping",
            target = "music.yandex.ru",
            results = null,
            payload = JsonParser.parseString(payloadJson),
            createdAt = null,
            updatedAt = null,
            context = CheckResponseContextDto(
                host = "music.yandex.ru",
                port = null,
                target = null
            )
        )

        val result = mapper.toDomain(dto, CheckType.Ping)

        assertTrue(result.details.contains("IP: 213.180.204.186"))
        assertTrue(result.details.contains("Пакеты: потери 0%"))
        assertTrue(result.details.contains("RTT: ср. 36.930 ms"))
    }
}
