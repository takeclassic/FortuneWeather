package com.fortuneweather.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.ui.theme.*
import com.fortuneweather.utils.FortuneGenerator
import com.fortuneweather.utils.ZodiacFortune
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.BorderStroke
import com.fortuneweather.BuildKonfig
import com.fortuneweather.data.datasource.SajuFortune
import com.fortuneweather.data.datasource.SajuHanja
import com.fortuneweather.data.cache.CacheManager
import kotlinx.serialization.json.Json
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping

@Composable
fun FortuneDetailScreen(
    viewModel: WeatherViewModel,
    onShowAd: (onAdClosed: () -> Unit) -> Unit,
    onBackClick: () -> Unit
) {
    var isConsentGranted by remember {
        mutableStateOf(CacheManager.getCache("saju_consent") == "true")
    }

    // 사주 입력 필드 상태 (로컬 캐시에서 불러오기)
    var birthDate by remember {
        mutableStateOf(
            CacheManager.getCache("saju_birth_date")?.filter { it.isDigit() } ?: ""
        )
    }
    val rawBirthTime = remember { CacheManager.getCache("saju_birth_time") ?: "" }
    var isTimeUnknown by remember {
        mutableStateOf(rawBirthTime == "모름")
    }
    var birthTime by remember {
        mutableStateOf(
            if (isTimeUnknown) "" else rawBirthTime.filter { it.isDigit() }
        )
    }
    var isLunar by remember { mutableStateOf(CacheManager.getCache("saju_is_lunar") == "true") }
    var gender by remember { mutableStateOf(CacheManager.getCache("saju_gender") ?: "남성") }

    var isEditing by remember { mutableStateOf(birthDate.isBlank()) }
    var hasSeenAd by remember { mutableStateOf(false) }

    val sajuState by viewModel.sajuState.collectAsState()

    val todayDateStr = remember {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        "${today.year}-${today.monthNumber}-${today.dayOfMonth}"
    }

    // 날짜가 기기 타임존 기준으로 바뀌었는지 확인하여 변경 시 사주 상태 초기화
    LaunchedEffect(todayDateStr) {
        val cachedDate = CacheManager.getCache("saju_result_date")
        if (cachedDate != todayDateStr) {
            viewModel.resetSaju()
        }
    }

    // 동의가 되어 있고, 생년월일이 등록되어 있으며, 아직 로드하지 않은 상태이고 편집 중이 아니라면 자동 로드 실행
    LaunchedEffect(isConsentGranted, birthDate, todayDateStr) {
        if (isConsentGranted && birthDate.length == 8 && !isEditing && sajuState is WeatherViewModel.SajuUiState.Initial) {
            val formattedDate = "${birthDate.substring(0, 4)}-${birthDate.substring(4, 6)}-${birthDate.substring(6, 8)}"
            val formattedTime = if (isTimeUnknown) "모름" else {
                if (birthTime.length == 4) {
                    "${birthTime.substring(0, 2)}:${birthTime.substring(2, 4)}"
                } else {
                    "모름"
                }
            }
            viewModel.loadSajuFortune(
                birthDate = formattedDate,
                birthTime = formattedTime,
                isLunar = isLunar,
                gender = gender,
                forceRefresh = false
            )
        }
    }

    // 입력 중 완료되었을 때 백그라운드에서 사주 한자를 미리 조회해 둡니다.
    LaunchedEffect(birthDate, birthTime, isLunar, gender, isTimeUnknown) {
        if (birthDate.length == 8 && (isTimeUnknown || birthTime.length == 4)) {
            val formattedDate = "${birthDate.substring(0, 4)}-${birthDate.substring(4, 6)}-${birthDate.substring(6, 8)}"
            val formattedTime = if (isTimeUnknown) "모름" else {
                if (birthTime.length == 4) {
                    "${birthTime.substring(0, 2)}:${birthTime.substring(2, 4)}"
                } else {
                    "모름"
                }
            }
            viewModel.prefetchSajuHanja(
                birthDate = formattedDate,
                birthTime = formattedTime,
                isLunar = isLunar,
                gender = gender
            )
        } else {
            viewModel.clearPreviewHanja()
        }
    }

    // 고급스러운 차콜-블랙 그라데이션 백그라운드
    val modernDarkGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E1E22), // Dark Slate Gray
                Color(0xFF121214)  // Charcoal Black
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(modernDarkGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 헤더 영역 (뒤로가기 및 타이틀)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "◀ 날씨",
                    color = FortuneGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "오늘의 사주팔자",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isConsentGranted) {
                // 개인정보 동의 화면
                SajuConsentScreen(
                    onAgree = {
                        CacheManager.saveCache("saju_consent", "true")
                        isConsentGranted = true
                    },
                    onBackClick = onBackClick
                )
            } else {
                if (BuildKonfig.GEMINI_API_KEY.isBlank()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF5350)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ Gemini API 키 미설정",
                                color = Color(0xFFEF5350),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "AI 사주 풀이를 사용하려면 local.properties 파일에 GEMINI_API_KEY를 설정해야 합니다.\n\n예: GEMINI_API_KEY=AIzaSy...",
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (isEditing) {
                    // 사주 입력 폼 UI
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "개인 사주 정보 입력",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Column {
                                Text("성별", color = WhiteTrans80, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("남성", "여성").forEach { item ->
                                        val isSelected = gender == item
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    color = if (isSelected) Color(0x44FFD54F) else WhiteTrans5,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { gender = item }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item,
                                                color = if (isSelected) FortuneGold else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Column {
                                Text("달력 구분", color = WhiteTrans80, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("양력" to false, "음력" to true).forEach { (label, value) ->
                                        val isSelected = isLunar == value
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    color = if (isSelected) Color(0x44FFD54F) else WhiteTrans5,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { isLunar = value }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) FortuneGold else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Column {
                                Text("생년월일 (예: 19950515)", color = WhiteTrans80, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = birthDate,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() }
                                        if (filtered.length <= 8) {
                                            birthDate = filtered
                                        }
                                    },
                                    placeholder = { Text("YYYYMMDD (8자리 숫자)", color = Color.Gray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    visualTransformation = DateVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = WhiteTrans5,
                                        unfocusedContainerColor = WhiteTrans5,
                                        focusedBorderColor = FortuneGold,
                                        unfocusedBorderColor = WhiteTrans20
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column {
                                Text("출생 시간 (예: 1430)", color = WhiteTrans80, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = if (isTimeUnknown) "태어난 시간 모름" else birthTime,
                                        onValueChange = { input ->
                                            if (!isTimeUnknown) {
                                                val filtered = input.filter { it.isDigit() }
                                                if (filtered.length <= 4) {
                                                    birthTime = filtered
                                                }
                                            }
                                        },
                                        enabled = !isTimeUnknown,
                                        placeholder = { Text("HHMM (4자리 숫자)", color = Color.Gray) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        visualTransformation = if (isTimeUnknown) VisualTransformation.None else TimeVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            disabledTextColor = Color.Gray,
                                            focusedContainerColor = WhiteTrans5,
                                            unfocusedContainerColor = WhiteTrans5,
                                            disabledContainerColor = WhiteTrans5.copy(alpha = 0.02f),
                                            focusedBorderColor = FortuneGold,
                                            unfocusedBorderColor = WhiteTrans20,
                                            disabledBorderColor = WhiteTrans10
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Button(
                                        onClick = {
                                            isTimeUnknown = !isTimeUnknown
                                            if (isTimeUnknown) {
                                                birthTime = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isTimeUnknown) FortuneGold else WhiteTrans10
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text(
                                            text = "시간 모름",
                                            color = if (isTimeUnknown) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            val previewState by viewModel.previewHanjaState.collectAsState()
                            
                            when (val state = previewState) {
                                is WeatherViewModel.SajuPreviewState.Empty -> {
                                    // Show nothing
                                }
                                is WeatherViewModel.SajuPreviewState.Loading -> {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = FortuneGold,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = "나의 사주팔자 계산 중...",
                                                color = WhiteTrans80,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                is WeatherViewModel.SajuPreviewState.Success -> {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "🔮 나의 사주팔자 (미리보기)",
                                                color = FortuneGold,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            SajuMyungbanGrid(
                                                hourHanja = state.hanja.hourHanja,
                                                dayHanja = state.hanja.dayHanja,
                                                monthHanja = state.hanja.monthHanja,
                                                yearHanja = state.hanja.yearHanja
                                            )

                                            FiveElementsSection(
                                                hourHanja = state.hanja.hourHanja,
                                                dayHanja = state.hanja.dayHanja,
                                                monthHanja = state.hanja.monthHanja,
                                                yearHanja = state.hanja.yearHanja
                                            )
                                        }
                                    }
                                }
                                is WeatherViewModel.SajuPreviewState.Error -> {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "사주팔자를 도출하지 못했습니다: ${state.message}",
                                                color = Color(0xFFEF5350),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (birthDate.length == 8) {
                                        val formattedDate = "${birthDate.substring(0, 4)}-${birthDate.substring(4, 6)}-${birthDate.substring(6, 8)}"
                                        val formattedTime = if (isTimeUnknown) "모름" else {
                                            if (birthTime.length == 4) {
                                                "${birthTime.substring(0, 2)}:${birthTime.substring(2, 4)}"
                                            } else {
                                                "모름"
                                            }
                                        }

                                        // 로컬 캐시에 저장
                                        CacheManager.saveCache("saju_birth_date", formattedDate)
                                        CacheManager.saveCache("saju_birth_time", formattedTime)
                                        CacheManager.saveCache("saju_is_lunar", isLunar.toString())
                                        CacheManager.saveCache("saju_gender", gender)

                                        val runAnalysis = {
                                            isEditing = false
                                            viewModel.loadSajuFortune(
                                                birthDate = formattedDate,
                                                birthTime = formattedTime,
                                                isLunar = isLunar,
                                                gender = gender,
                                                forceRefresh = false
                                            )
                                        }
                                        if (!hasSeenAd) {
                                            onShowAd {
                                                hasSeenAd = true
                                                runAnalysis()
                                            }
                                        } else {
                                            runAnalysis()
                                        }
                                    }
                                },
                                enabled = birthDate.length == 8 && (isTimeUnknown || birthTime.length == 4) && BuildKonfig.GEMINI_API_KEY.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FortuneGold,
                                    disabledContainerColor = WhiteTrans20
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Text("✨ AI 사주 운세 분석하기", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    // 결과 표시 영역 (Loading, Success, Error)
                    when (val sajuUi = sajuState) {
                        is WeatherViewModel.SajuUiState.Initial -> {
                            isEditing = birthDate.isBlank()
                        }
                        is WeatherViewModel.SajuUiState.Loading -> {
                            val cachedCombo = remember { CacheManager.getCache("saju_result_input") ?: "" }
                            val currentComboPrefix = "${birthDate}_${birthTime}_${isLunar}_${gender}_"
                            val isCacheMatch = cachedCombo.startsWith(currentComboPrefix)
                            
                            val cachedSajuJson = remember { CacheManager.getCache("saju_result_cache") }
                            val cachedSaju = remember(cachedSajuJson, isCacheMatch) {
                                if (isCacheMatch && !cachedSajuJson.isNullOrBlank()) {
                                    try {
                                        Json { ignoreUnknownKeys = true }.decodeFromString<SajuFortune>(cachedSajuJson)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Text(
                                        text = "🔮 오늘의 사주팔자 조율 중",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // 1. 사주 명반 한자 8글자 2x4 레이아웃
                                    if (cachedSaju != null) {
                                        SajuMyungbanGrid(
                                            hourHanja = cachedSaju.hourHanja,
                                            dayHanja = cachedSaju.dayHanja,
                                            monthHanja = cachedSaju.monthHanja,
                                            yearHanja = cachedSaju.yearHanja
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FiveElementsSection(
                                            hourHanja = cachedSaju.hourHanja,
                                            dayHanja = cachedSaju.dayHanja,
                                            monthHanja = cachedSaju.monthHanja,
                                            yearHanja = cachedSaju.yearHanja
                                        )
                                    } else if (sajuUi.previewHanja != null) {
                                        SajuMyungbanGrid(
                                            hourHanja = sajuUi.previewHanja.hourHanja,
                                            dayHanja = sajuUi.previewHanja.dayHanja,
                                            monthHanja = sajuUi.previewHanja.monthHanja,
                                            yearHanja = sajuUi.previewHanja.yearHanja
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FiveElementsSection(
                                            hourHanja = sajuUi.previewHanja.hourHanja,
                                            dayHanja = sajuUi.previewHanja.dayHanja,
                                            monthHanja = sajuUi.previewHanja.monthHanja,
                                            yearHanja = sajuUi.previewHanja.yearHanja
                                        )
                                    } else {
                                        SajuMyungbanGrid(
                                            hourHanja = "？？",
                                            dayHanja = "日柱",
                                            monthHanja = "月柱",
                                            yearHanja = "年柱"
                                        )
                                    }

                                    // 2. 입력된 정보 가이드라인
                                    val formattedTimeGuide = if (isTimeUnknown) "시간 모름" else {
                                        if (birthTime.length == 4) {
                                            "${birthTime.substring(0, 2)}시 ${birthTime.substring(2, 4)}분"
                                        } else {
                                            "시간 모름"
                                        }
                                    }
                                    val formattedDateGuide = if (birthDate.length == 8) {
                                        "${birthDate.substring(0, 4)}년 ${birthDate.substring(4, 6)}월 ${birthDate.substring(6, 8)}일"
                                    } else {
                                        ""
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "$formattedDateGuide $formattedTimeGuide 생",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "(${if (isLunar) "음력" else "양력"} · $gender)",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                    }

                                    // 3. 무한 물결 형태의 인디터미네이트 프로그레스바
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        LinearProgressIndicator(
                                            color = FortuneGold,
                                            trackColor = WhiteTrans10,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .background(Color.Transparent, RoundedCornerShape(3.dp))
                                        )
                                        
                                        Text(
                                            text = "동양 명리학 기운 분석 및 AI 사주팔자 조율 중...",
                                            color = WhiteTrans80,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        is WeatherViewModel.SajuUiState.Success -> {
                            val saju = sajuUi.saju
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 8글자 실제 사주 명반 카드 노출
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "🔮 나의 사주팔자",
                                            color = FortuneGold,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        SajuMyungbanGrid(
                                            hourHanja = saju.hourHanja,
                                            dayHanja = saju.dayHanja,
                                            monthHanja = saju.monthHanja,
                                            yearHanja = saju.yearHanja
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        FiveElementsSection(
                                            hourHanja = saju.hourHanja,
                                            dayHanja = saju.dayHanja,
                                            monthHanja = saju.monthHanja,
                                            yearHanja = saju.yearHanja
                                        )
                                    }
                                }

                                FortuneCard(
                                    title = "오늘의 종합 사주 총평",
                                    stars = saju.overallStars,
                                    content = saju.overallMsg,
                                    headerColor = FortuneGold
                                )

                                FortuneCard(
                                    title = "금전운 흐름",
                                    stars = saju.moneyStars,
                                    content = saju.moneyMsg,
                                    headerColor = Color(0xFF81C784)
                                )

                                FortuneCard(
                                    title = "애정운 흐름",
                                    stars = saju.loveStars,
                                    content = saju.loveMsg,
                                    headerColor = Color(0xFFFF8A80)
                                )

                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            text = "건강 및 조심할 점",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = saju.healthMsg,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }

                                LuckySajuGuideCard(
                                    luckyItem = saju.luckyItem,
                                    luckyDirection = saju.luckyDirection,
                                    luckyTime = saju.luckyTime,
                                    luckyColor = saju.luckyColor
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        hasSeenAd = false
                                        isEditing = true
                                    },
                                    border = BorderStroke(1.dp, WhiteTrans30),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("✍️ 사주 정보 수정하기", color = Color.White)
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                TextButton(
                                    onClick = {
                                        // 모든 로컬 캐시 데이터 파기
                                        CacheManager.saveCache("saju_consent", "false")
                                        CacheManager.saveCache("saju_birth_date", "")
                                        CacheManager.saveCache("saju_birth_time", "")
                                        CacheManager.saveCache("saju_is_lunar", "false")
                                        CacheManager.saveCache("saju_gender", "남성")
                                        CacheManager.saveCache("saju_result_cache", "")
                                        CacheManager.saveCache("saju_result_date", "")
                                        CacheManager.saveCache("saju_result_input", "")
                                        
                                        // 컴포저블 상태 변수 초기화
                                        birthDate = ""
                                        birthTime = ""
                                        isTimeUnknown = false
                                        isLunar = false
                                        gender = "남성"
                                        isConsentGranted = false
                                        isEditing = true
                                        viewModel.resetSaju()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🔒 내 사주 정보 삭제 및 동의 철회",
                                        color = Color(0xFFEF5350).copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is WeatherViewModel.SajuUiState.Error -> {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("⚠️ 사주 분석 실패", color = Color(0xFFEF5350), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = sajuUi.message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            hasSeenAd = false
                                            isEditing = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FortuneGold)
                                    ) {
                                        Text("수정 및 다시 시도", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun FortuneCard(
    title: String,
    stars: Int,
    content: String,
    headerColor: Color
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = headerColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 별점 표시
                Row {
                    repeat(5) { index ->
                        Text(
                            text = "★",
                            color = if (index < stars) FortuneGold else Color.Gray.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun LuckyGuideCard(
    fortune: ZodiacFortune,
    weatherInfo: WeatherInfo
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans15),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🍀 오늘의 행운 가이드",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LuckyGuideRow(label = "행운의 아이템", value = fortune.luckyItem, icon = "🎒")
                LuckyGuideRow(label = "행운의 방향", value = fortune.luckyDirection, icon = "🧭")
                LuckyGuideRow(label = "행운의 시간", value = fortune.luckyTime, icon = "⏰")
                // 날씨 데이터에 저장된 실시간 행운의 컬러 활용
                LuckyGuideRow(label = "행운의 컬러", value = weatherInfo.luckyColor, icon = "🎨")
            }
        }
    }
}

@Composable
fun LuckyGuideRow(
    label: String,
    value: String,
    icon: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            text = label,
            color = WhiteTrans80,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun LuckySajuGuideCard(
    luckyItem: String,
    luckyDirection: String,
    luckyTime: String,
    luckyColor: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans15),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🍀 오늘의 AI 행운 가이드",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LuckyGuideRow(label = "행운의 아이템", value = luckyItem, icon = "🎒")
                LuckyGuideRow(label = "행운의 방향", value = luckyDirection, icon = "🧭")
                LuckyGuideRow(label = "행운의 시간", value = luckyTime, icon = "⏰")
                LuckyGuideRow(label = "행운의 컬러", value = luckyColor, icon = "🎨")
            }
        }
    }
}

@Composable
fun SajuConsentScreen(
    onAgree: () -> Unit,
    onBackClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "개인정보 수집 및 이용 동의",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider(color = WhiteTrans20, thickness = 1.dp)
            
            Text(
                text = "오늘의 사주 운세 서비스 제공을 위해 아래와 같이 개인정보를 수집 및 이용합니다.",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "• 수집 항목: 성별, 생년월일, 출생 시간, 조회 지역",
                    color = WhiteTrans80,
                    fontSize = 11.sp
                )
                Text(
                    text = "• 수집 및 이용 목적: 구글 Gemini AI를 활용한 개인 맞춤형 명리학 사주 운세 풀이 및 결과 제공",
                    color = WhiteTrans80,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    text = "• 보유 및 이용 기간: 사용자 기기(로컬 캐시)에만 안전하게 저장되며, 앱 삭제 또는 데이터 직접 삭제 시 즉시 파기됩니다. (서버에 별도로 저장되지 않습니다.)",
                    color = WhiteTrans80,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    text = "• 전송 정보 안내: AI 분석을 위해 상기 정보가 구글 Gemini API 서버로 안전하게 전송됩니다.",
                    color = WhiteTrans80,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            
            Text(
                text = "* 본 동의를 거부하실 수 있으나, 거부 시 사주 서비스 이용이 불가합니다.",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
                lineHeight = 13.sp
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackClick,
                    border = BorderStroke(1.dp, WhiteTrans30),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "동의 안함",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                
                Button(
                    onClick = onAgree,
                    colors = ButtonDefaults.buttonColors(containerColor = FortuneGold),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "동의하고 계속",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = StringBuilder()
        for (i in raw.indices) {
            out.append(raw[i])
            if (i == 3 || i == 5) {
                out.append('-')
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 6) return offset + 1
                return offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 7) return offset - 1
                return offset - 2
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

class TimeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = StringBuilder()
        for (i in raw.indices) {
            out.append(raw[i])
            if (i == 1) {
                out.append(':')
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                return offset + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                return offset - 1
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

@Composable
fun SajuMyungbanGrid(
    hourHanja: String,
    dayHanja: String,
    monthHanja: String,
    yearHanja: String,
    modifier: Modifier = Modifier
) {
    val pillars = remember(hourHanja, dayHanja, monthHanja, yearHanja) {
        listOf(
            Pair(if (hourHanja == "？？") "時" else hourHanja.take(1), if (hourHanja == "？？") "柱" else hourHanja.drop(1)),
            Pair(if (dayHanja == "日柱") "日" else dayHanja.take(1), if (dayHanja == "日柱") "柱" else dayHanja.drop(1)),
            Pair(if (monthHanja == "月柱") "月" else monthHanja.take(1), if (monthHanja == "月柱") "柱" else monthHanja.drop(1)),
            Pair(if (yearHanja == "年柱") "年" else yearHanja.take(1), if (yearHanja == "年柱") "柱" else yearHanja.drop(1))
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        val labels = listOf("시주 (時)", "일주 (日)", "월주 (月)", "년주 (年)")
        pillars.forEachIndexed { index, pillar ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = labels[index],
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                // 천간
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF2B2B30), RoundedCornerShape(8.dp))
                        .background(WhiteTrans5, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pillar.first,
                        color = FortuneGold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 지지
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
                        .background(WhiteTrans5, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pillar.second,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class FiveElementsCount(
    val wood: Int,
    val fire: Int,
    val earth: Int,
    val metal: Int,
    val water: Int
)

fun calculateFiveElements(
    hourHanja: String,
    dayHanja: String,
    monthHanja: String,
    yearHanja: String
): FiveElementsCount {
    val allChars = (hourHanja + dayHanja + monthHanja + yearHanja).filter { it != '？' }
    var wood = 0
    var fire = 0
    var earth = 0
    var metal = 0
    var water = 0
    
    for (char in allChars) {
        when (char) {
            '甲', '乙', '寅', '卯' -> wood++
            '丙', '丁', '巳', '午' -> fire++
            '戊', '己', '辰', '戌', '丑', '未' -> earth++
            '庚', '辛', '申', '酉' -> metal++
            '壬', '癸', '亥', '子' -> water++
        }
    }
    return FiveElementsCount(wood, fire, earth, metal, water)
}

data class ElementData(
    val name: String,
    val count: Int,
    val color: Color
)

@Composable
fun FiveElementsSection(
    hourHanja: String,
    dayHanja: String,
    monthHanja: String,
    yearHanja: String,
    modifier: Modifier = Modifier
) {
    val counts = remember(hourHanja, dayHanja, monthHanja, yearHanja) {
        calculateFiveElements(hourHanja, dayHanja, monthHanja, yearHanja)
    }
    
    val total = (counts.wood + counts.fire + counts.earth + counts.metal + counts.water).coerceAtLeast(1)
    
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "☯️ 나의 오행 분포",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        
        val elements = listOf(
            ElementData("목 (木 · 나무)", counts.wood, Color(0xFF4CAF50)),
            ElementData("화 (火 · 불)", counts.fire, Color(0xFFE53935)),
            ElementData("토 (土 · 흙)", counts.earth, Color(0xFFFFB300)),
            ElementData("금 (金 · 쇠)", counts.metal, Color(0xFFB0BEC5)),
            ElementData("수 (水 · 물)", counts.water, Color(0xFF29B6F6))
        )
        
        elements.forEach { element ->
            val ratio = element.count.toFloat() / total.toFloat()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = element.name,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    modifier = Modifier.width(90.dp)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(5.dp))
                ) {
                    if (element.count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .background(element.color, RoundedCornerShape(5.dp))
                        )
                    }
                }
                
                Text(
                    text = "${element.count}개",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

