package com.fortuneweather.utils

import kotlin.math.abs

data class ZodiacFortune(
    val animal: String,
    val emoji: String,
    val overallStars: Int,
    val overallMsg: String,
    val moneyStars: Int,
    val moneyMsg: String,
    val loveStars: Int,
    val loveMsg: String,
    val luckyItem: String,
    val luckyDirection: String,
    val luckyTime: String
)

object FortuneGenerator {

    private val ZODIAC_ANIMALS = listOf(
        "쥐띠" to "🐭",
        "소띠" to "🐂",
        "호랑이띠" to "🐯",
        "토끼띠" to "🐰",
        "용띠" to "🐲",
        "뱀띠" to "🐍",
        "말띠" to "🐴",
        "양띠" to "🐑",
        "원숭이띠" to "🐵",
        "닭띠" to "🐔",
        "개띠" to "🐶",
        "돼지띠" to "🐷"
    )

    private val OVERALL_MESSAGES = listOf(
        "기회는 준비된 자에게 찾아옵니다. 오늘 계획했던 일들을 주저하지 말고 밀고 나가세요.",
        "뜻밖의 귀인이 나타나 꼬여있던 실타래를 풀어줍니다. 경청하는 자세가 중요합니다.",
        "마음이 급할수록 돌어가라는 속담을 기억하세요. 신중한 판단이 실수를 줄여줍니다.",
        "평소 소홀했던 주변 사람에게 안부를 물어보세요. 따뜻한 대화 속에 큰 행운이 깃듭니다.",
        "오늘 하루는 긍정적인 에너지가 가득합니다. 새로운 도전을 하기에 매우 좋은 시기입니다.",
        "몸과 마음에 휴식이 필요한 날입니다. 너무 무리하지 말고 가벼운 산책으로 에너지를 충전하세요.",
        "노력한 만큼 결실을 맺는 정직한 하루입니다. 성실함을 무기로 묵묵히 나아가세요.",
        "스쳐 지나가는 대화 속에서 귀중한 아이디어를 얻게 됩니다. 메모하는 습관이 행운을 부릅니다.",
        "작은 일에 연연하기보다 큰 흐름을 읽는 지혜가 필요한 날입니다. 대범하게 대처하세요.",
        "예상치 못한 변화가 찾아올 수 있으나 이는 성장을 위한 기회입니다. 유연하게 받아들이세요."
    )

    private val MONEY_MESSAGES = listOf(
        "투자나 계약에 있어 유리한 기운이 감돕니다. 꼼꼼히 검토 후 결단하세요.",
        "지출 관리에 신경 써야 하는 날입니다. 충동구매를 피하면 머지않아 재물이 모입니다.",
        "생각지 못한 곳에서 소소한 횡재수가 있습니다. 빌려준 돈을 돌려받거나 보너스가 생길 수 있습니다.",
        "돈의 흐름이 활발해지는 날입니다. 정직한 노력을 통한 이익이 크게 다가옵니다.",
        "오늘은 지갑을 굳게 닫는 것이 좋습니다. 대인관계 지출이라도 과도하면 후회할 수 있습니다."
    )

    private val LOVE_MESSAGES = listOf(
        "연인 혹은 배우자와의 대화가 무척 매끄러운 날입니다. 평소 전하지 못한 고마움을 표현해 보세요.",
        "솔로라면 매력 지수가 상승하는 날입니다. 가벼운 모임에서 좋은 인연을 만날 확률이 높습니다.",
        "말 한마디로 오해가 생길 수 있는 날이니 상대방의 입장에서 한 번 더 생각하고 말하세요.",
        "서로에 대한 신뢰가 한층 깊어지는 하루입니다. 함께 맛있는 음식을 먹으며 소소한 행복을 나누세요.",
        "옛 인연이나 그리운 사람에게서 연락이 올 수 있습니다. 마음의 정리가 필요한 시점입니다."
    )

    private val LUCKY_ITEMS = listOf("따뜻한 아메리카노", "푸른색 손수건", "작은 소형 우산", "메모 패드와 펜", "다이어리", "키링", "텀블러", "안경", "책 한 권", "향수")
    private val LUCKY_DIRECTIONS = listOf("동쪽", "서쪽", "남쪽", "북쪽", "북동쪽", "남서쪽", "동남쪽")
    private val LUCKY_TIMES = listOf("오전 9시 ~ 11시", "오전 11시 ~ 오후 1시", "오후 1시 ~ 3시", "오후 3시 ~ 5시", "오후 5시 ~ 7시", "오후 7시 ~ 9시")

    fun getZodiacList(): List<Pair<String, String>> = ZODIAC_ANIMALS

    fun generate(dateStr: String, location: String, animalName: String): ZodiacFortune {
        val emoji = ZODIAC_ANIMALS.firstOrNull { it.first == animalName }?.second ?: "🔮"
        
        // 날짜 + 지역 + 띠 이름을 시드로 사용하여 난수 결정
        val seed = (dateStr + location + animalName).hashCode()
        val random = kotlin.random.Random(seed)

        val overallStars = random.nextInt(3, 6) // 3~5성
        val overallMsg = OVERALL_MESSAGES[random.nextInt(OVERALL_MESSAGES.size)]

        val moneyStars = random.nextInt(2, 6) // 2~5성
        val moneyMsg = MONEY_MESSAGES[random.nextInt(MONEY_MESSAGES.size)]

        val loveStars = random.nextInt(2, 6) // 2~5성
        val loveMsg = LOVE_MESSAGES[random.nextInt(LOVE_MESSAGES.size)]

        val luckyItem = LUCKY_ITEMS[random.nextInt(LUCKY_ITEMS.size)]
        val luckyDirection = LUCKY_DIRECTIONS[random.nextInt(LUCKY_DIRECTIONS.size)]
        val luckyTime = LUCKY_TIMES[random.nextInt(LUCKY_TIMES.size)]

        return ZodiacFortune(
            animal = animalName,
            emoji = emoji,
            overallStars = overallStars,
            overallMsg = overallMsg,
            moneyStars = moneyStars,
            moneyMsg = moneyMsg,
            loveStars = loveStars,
            loveMsg = loveMsg,
            luckyItem = luckyItem,
            luckyDirection = luckyDirection,
            luckyTime = luckyTime
        )
    }
}
