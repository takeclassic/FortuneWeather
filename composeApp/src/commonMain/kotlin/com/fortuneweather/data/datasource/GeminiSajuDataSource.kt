package com.fortuneweather.data.datasource

import com.fortuneweather.BuildKonfig
import com.fortuneweather.utils.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SajuFortune(
    val overallStars: Int,
    val overallMsg: String,
    val moneyStars: Int,
    val moneyMsg: String,
    val loveStars: Int,
    val loveMsg: String,
    val healthMsg: String,
    val luckyItem: String,
    val luckyDirection: String,
    val luckyTime: String,
    val luckyColor: String,
    val yearHanja: String,
    val monthHanja: String,
    val dayHanja: String,
    val hourHanja: String
)

@Serializable
data class SajuHanja(
    val yearHanja: String,
    val monthHanja: String,
    val dayHanja: String,
    val hourHanja: String
)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String,
    val temperature: Double,
    val topP: Double,
    val topK: Int
)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

class GeminiSajuDataSource(client: HttpClient) : BaseRemoteDataSource(client) {

    private val apiKey = BuildKonfig.GEMINI_API_KEY

    suspend fun fetchSajuFortune(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String,
        todayDate: String,
        todayIljin: String,
        localHanja: SajuHanja
    ): SajuFortune {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API 키가 등록되지 않았습니다. local.properties에 GEMINI_API_KEY를 설정해주세요.")
        }

        val prompt = """
성별: $gender
생년월일: $birthDate
태어난 시각: $birthTime
음력/양력 여부: ${if (isLunar) "음력" else "양력"}
오늘 날짜: $todayDate
오늘 날짜의 일진 기운 (천간지지): $todayIljin

[제공된 사용자의 사주팔자 한자 (100% 고정값)]
- 년주: ${localHanja.yearHanja}
- 월주: ${localHanja.monthHanja}
- 일주: ${localHanja.dayHanja}
- 시주: ${localHanja.hourHanja}

당신은 정확한 역법 계산을 제공하는 정통 명리학 대가입니다.
위에 제공된 [제공된 사용자의 사주팔자 한자 (100% 고정값)]를 사용자의 출생 사주팔자로 고정하십시오.
그리고 [오늘 날짜의 일진 기운 (천간지지): $todayIljin]을 오늘의 고유한 일진 간지로 완벽하게 대입하여 분석을 실행해야 합니다.

오늘 하루의 운세(overallMsg, moneyMsg, loveMsg, healthMsg 등)를 작성할 때는 반드시 사용자의 고정 사주팔자와 [오늘 날짜의 일진 기운: $todayIljin] 사이의 오행 상생상극, 십신(육친) 관계, 합(삼합/방합/육합), 충(육충) 관계를 철저히 논리적으로 분석하여 도출하십시오.
날짜가 달라져 [오늘 날짜의 일진 기운: $todayIljin]의 천간과 지지가 변경되면, 그날의 기운 상호작용이 180도 완전히 달라지므로 오늘 하루에 주어지는 구체적인 점괘 총평과 돈의 흐름, 애정의 방향, 조언 등이 그날의 간지에 걸맞게 명확하게 다르고 독창적으로 도출되어야 합니다.

[별점 도출 원칙 (결정론적 계산)]
- 각 별점(overallStars, moneyStars, loveStars)은 출생 사주팔자(특히 일간)와 오늘의 운기(오늘 날짜의 일진: $todayIljin) 사이의 오행 상생상극, 합/충 관계를 논리적으로 계산하여 1성부터 5성까지 다양하게 도출해야 합니다.
- 동일한 입력값(생년월일, 성별, 시각, 음양, 오늘 날짜 및 오늘의 일진)에 대해서는 항상 정확히 동일한 별점과 풀이가 출력되도록 결정론적으로 연산하십시오. 절대로 대충 4성으로 일관되게 채우지 말고, 오늘의 오행 분포와 조화에 따라 1성에서 5성 사이의 정수를 논리적 규칙에 의해 도출하십시오.

결과는 반드시 JSON 형식으로만 응답해야 하며, 마크다운 기호(```json 등)는 절대 포함하지 말고 순수 JSON 문자열만 반환해주세요.

JSON 구조:
{
  "overallStars": (1부터 5 사이의 정수),
  "overallMsg": "오늘의 종합 사주 총평 및 조언 (3~4문장)",
  "moneyStars": (1부터 5 사이의 정수),
  "moneyMsg": "오늘의 재물운 흐름과 대처 방안 (2~3문장)",
  "loveStars": (1부터 5 사이의 정수),
  "loveMsg": "오늘의 애정운 흐름과 대처 방안 (2~3문장)",
  "healthMsg": "건강 관리 및 오늘 조심해야 할 부분 (2~3문장)",
  "luckyItem": "오늘 지니면 좋은 행운의 아이템",
  "luckyDirection": "오늘 운을 상승시켜주는 행운의 방향 (예: 동쪽)",
  "luckyTime": "오늘 좋은 기운이 맴도는 시간대 (예: 오후 3시~5시)",
  "luckyColor": "오늘 어울리는 행운의 컬러",
  "yearHanja": "${localHanja.yearHanja}",
  "monthHanja": "${localHanja.monthHanja}",
  "dayHanja": "${localHanja.dayHanja}",
  "hourHanja": "${localHanja.hourHanja}"
}
""".trimIndent()

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey"
            val requestBody = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.0,
                    topP = 0.1,
                    topK = 1
                )
            )
            
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.value in 200..299) {
                val responseStr = response.bodyAsText()
                val responseJson = json.parseToJsonElement(responseStr)
                val textResponse = responseJson
                    .jsonObject["candidates"]
                    ?.jsonArray
                    ?.getOrNull(0)
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonObject
                    ?.get("parts")
                    ?.jsonArray
                    ?.getOrNull(0)
                    ?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content

                if (textResponse != null) {
                    Json { ignoreUnknownKeys = true }.decodeFromString<SajuFortune>(textResponse)
                } else {
                    throw IllegalStateException("API 응답을 파싱하지 못했습니다. 응답 데이터가 비어 있거나 형식이 어긋납니다.")
                }
            } else {
                val statusCode = response.status.value
                val errorBody = response.bodyAsText()
                Logger.e("사주 분석 호출 실패 (상태 코드: $statusCode, 상세 내용: $errorBody)")
                if (statusCode == 429) {
                    throw IllegalStateException("네트워크 연결이 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.")
                } else if (statusCode == 400) {
                    throw IllegalStateException("네트워크 연결이 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.")
                } else {
                    throw IllegalStateException("네트워크 연결이 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.")
                }
            }
        } catch (e: Exception) {
            Logger.e("Exception during Gemini API call", e)
            throw e
        }
    }
}
