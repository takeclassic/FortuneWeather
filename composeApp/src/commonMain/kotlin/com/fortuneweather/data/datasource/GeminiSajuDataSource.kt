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
        todayDate: String
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

위 정보를 바탕으로 전통 동양 사주 명리학에 기반한 오늘 하루의 사주 풀이와 운세 정보를 분석해줘.
동일한 입력값(생년월일, 성별, 시각, 음양, 오늘 날짜)에 대해서는 언제나 엄격하고 동일한 해석 기준을 적용해 일관된 운세 수치와 총평을 도출해야 해.
결과는 반드시 JSON 형식으로만 응답해야 해. 마크다운 기호(```json 등)는 절대 포함하지 말고 순수 JSON 문자열만 반환해줘.

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
  "yearHanja": "정확한 명리학 절기 기준으로 계산된 출생년도의 천간지지 한자 2글자 (예: 庚子)",
  "monthHanja": "정확한 명리학 절기 기준으로 계산된 출생월의 천간지지 한자 2글자 (예: 戊子)",
  "dayHanja": "정확한 명리학 절기 기준으로 계산된 출생일의 천간지지 한자 2글자 (예: 己巳)",
  "hourHanja": "정확한 명리학 절기 기준으로 계산된 출생시각의 천간지지 한자 2글자. 단, 태어난 시간을 모를 경우 '？？'로 표시 (예: 丙子)"
}
""".trimIndent()

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$apiKey"
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
                    throw IllegalStateException("오늘의 분석 서비스 사용량이 일시적으로 초과되었습니다. 잠시 후(약 1분 뒤) 다시 요청해 주시기 바랍니다.")
                } else if (statusCode == 400) {
                    throw IllegalStateException("사주 연동 요청이 거부되었습니다. 설정 상태나 권한 누락 여부를 확인해 주십시오.")
                } else {
                    throw IllegalStateException("사주 분석 서버와 연동하지 못했습니다. (통신 상태 번호 $statusCode)")
                }
            }
        } catch (e: Exception) {
            Logger.e("Exception during Gemini API call", e)
            throw e
        }
    }
}
