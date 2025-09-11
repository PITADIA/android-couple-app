@file:Suppress("UnusedImport", "UNUSED_PARAMETER")
package com.love2love.ui.questions

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

// ------------------------------------------------------------
// NOTE: This file is a Kotlin/Compose port of the SwiftUI code
// "QuestionListView" + its supporting card views.
//
// ✅ Localization: all "... .localized" calls are replaced by
//    stringResource(R.string.your_key) (or context.getString(...)).
//    Make sure the following keys exist in strings.xml:
//    - on_count
//    - locked_content
//    - subscribe_access_questions
//    - congratulations_pack
//    - pack_completed
//    - tap_unlock_surprise
//    - add_to_favorites
//    - remove_from_favorites
//
// ⚠️ Integration points you must wire to your app:
//    - QuestionCacheManager.getQuestionsWithSmartCache(categoryId, fallbackLoader)
//    - PackProgressService.getAccessibleQuestions(from, categoryId)
//    - PackProgressService.unlockNextPack(categoryId), resetProgress(categoryId)
//    - PackProgressService.getUnlockedPacks(categoryId)
//    - CategoryProgressService.getCurrentIndex(categoryId), saveCurrentIndex(index, categoryId)
//    - FavoritesService.isFavorite(questionId), toggleFavorite(question, category)
//    - AppState.freemiumManager (getMaxFreeQuestions(category), handleQuestionAccess(...))
//    - AppState.currentUser?.isSubscribed
//    - ReviewRequestService.checkForReviewRequest()
//
//    Replace any placeholder types (Question, QuestionCategory, AppState, etc.)
//    with your concrete implementations.
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuestionListScreen(
    category: QuestionCategory,
    onBack: () -> Unit,
    // Services (inject your own instances)
    appState: AppState,
    questionCacheManager: QuestionCacheManager,
    favoritesService: FavoritesService,
    packProgressService: PackProgressService,
    categoryProgressService: CategoryProgressService,
    reviewRequestService: ReviewRequestService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val analytics = remember { Firebase.analytics }
    val scope = rememberCoroutineScope()

    // ---------------- State ----------------
    var isQuestionsLoaded by remember { mutableStateOf(false) }
    var cachedQuestions by remember { mutableStateOf(listOf<Question>()) }
    var accessibleQuestions by remember { mutableStateOf(listOf<Question>()) }

    var showNewPackReveal by remember { mutableStateOf(false) }
    var completedPackNumber by remember { mutableStateOf(1) }
    var showPackCompletionCard by remember { mutableStateOf(false) }

    // Paywall toggle (fallback)
    var showFreemiumPaywallCard by remember { mutableStateOf(false) }

    // ----------- Load questions once -----------
    LaunchedEffect(category.id) {
        if (isQuestionsLoaded) return@LaunchedEffect

        // Preload (if your manager supports it)
        runCatching { questionCacheManager.preloadCategory(category.id) }

        val loaded = runCatching {
            questionCacheManager.getQuestionsWithSmartCache(
                categoryId = category.id,
                fallbackLoader = { QuestionDataManager.shared.loadQuestions(category.id) }
            )
        }.getOrElse { emptyList() }

        cachedQuestions = loaded
        accessibleQuestions = packProgressService.getAccessibleQuestions(
            from = loaded,
            categoryId = category.id
        )

        isQuestionsLoaded = true

        // Debug-style print substitutes
        val unlockedPacks = packProgressService.getUnlockedPacks(category.id)
        println("QuestionListScreen: ${loaded.size} total, ${accessibleQuestions.size} accessible (Pack $unlockedPacks)")

        appState.freemiumManager?.let { fm ->
            val maxFree = fm.getMaxFreeQuestions(category)
            if (maxFree < loaded.size && !category.isPremium) {
                println("🔥 Freemium: Limit $maxFree for '${category.title}' (free users)")
            }
        }

        // Ask for review on open
        reviewRequestService.checkForReviewRequest()
    }

    // Compute freemium preview visibility
    val isSubscribed = appState.currentUser?.isSubscribed ?: false
    val freemiumManager = appState.freemiumManager
    val maxFreeQuestions by remember(category, cachedQuestions, accessibleQuestions, freemiumManager) {
        mutableStateOf(freemiumManager?.getMaxFreeQuestions(category) ?: Int.MAX_VALUE)
    }

    val shouldShowFreemiumPaywallPreview by remember(
        isSubscribed, category, cachedQuestions, accessibleQuestions, maxFreeQuestions
    ) {
        mutableStateOf(!isSubscribed &&
            !category.isPremium &&
            cachedQuestions.size > maxFreeQuestions &&
            accessibleQuestions.size >= maxFreeQuestions)
    }

    // Build list of items (normal questions + optional paywall + optional completion)
    val items: List<CardItem> by remember(
        accessibleQuestions, shouldShowFreemiumPaywallPreview, showPackCompletionCard, completedPackNumber
    ) {
        mutableStateOf(buildList {
            accessibleQuestions.forEach { add(CardItem.Q(it)) }
            if (shouldShowFreemiumPaywallPreview) add(CardItem.PaywallPreview)
            if (showPackCompletionCard) add(CardItem.PackCompletion(completedPackNumber))
        })
    }

    // Restore saved index
    val initialIndex = remember(isQuestionsLoaded) {
        val saved = categoryProgressService.getCurrentIndex(category.id)
        if (saved in accessibleQuestions.indices) saved else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size.coerceAtLeast(1) }
    )

    // Side-effects on page change (viewed question + save progress + review requests + checks)
    LaunchedEffect(pagerState, items.size) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page in accessibleQuestions.indices) {
                    val q = accessibleQuestions[page]
                    analytics.logEvent("question_vue", bundleOf(
                        "categorie" to category.id,
                        "question_id" to q.id
                    ))
                    println("📊 Firebase: question_vue - ${category.id} - ${q.id}")

                    categoryProgressService.saveCurrentIndex(page, category.id)
                    reviewRequestService.checkForReviewRequest()

                    checkForPackCompletionCard(
                        currentPage = page,
                        accessibleCount = accessibleQuestions.size,
                        cachedCount = cachedQuestions.size,
                        category = category,
                        isSubscribed = isSubscribed,
                        maxFreeQuestions = maxFreeQuestions,
                        showPackCompletionCard = showPackCompletionCard,
                        onShowPackCard = { number ->
                            completedPackNumber = number
                            showPackCompletionCard = true
                        }
                    )

                    checkForFreemiumPaywallCard(
                        currentPage = page,
                        isSubscribed = isSubscribed,
                        category = category,
                        maxFreeQuestions = maxFreeQuestions,
                        cachedCount = cachedQuestions.size,
                        onShowPaywall = { showFreemiumPaywallCard = true }
                    )
                }
            }
    }

    // ---------------------- UI ----------------------
    Column(
        modifier
            .fillMaxSize()
            .background(Color(red = 0.15f, green = 0.03f, blue = 0.08f))
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 60.dp, start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Spacer(Modifier.weight(1f))

            // "{current} on {total}" counter (keep same split as Swift)
            Text(
                text = "${pagerState.currentPage + 1} ${stringResource(R.string.on_count)} ${items.size}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(Modifier.weight(1f))

            IconButton(onClick = {
                scope.launch {
                    // Reset like Swift
                    packProgressService.resetProgress(category.id)
                    categoryProgressService.saveCurrentIndex(0, category.id)
                    // Reload accessible questions
                    accessibleQuestions = packProgressService.getAccessibleQuestions(
                        from = cachedQuestions,
                        categoryId = category.id
                    )
                    showPackCompletionCard = false
                    showFreemiumPaywallCard = false
                    completedPackNumber = 1
                    pagerState.animateScrollToPage(0)
                    println("🔄 RESET: Progress reset for ${category.title}")
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = Color.White)
            }
        }

        // Body
        if (accessibleQuestions.isEmpty()) {
            // Locked or empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 60.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.locked_content),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.subscribe_access_questions),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }
            }
        } else {
            // Pager rendering (like GeometryReader + ZStack)
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 30.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) { page ->
                    val cardWidthModifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)

                    when (val item = items.getOrNull(page)) {
                        is CardItem.Q -> {
                            QuestionCardView(
                                question = item.question,
                                category = category,
                                isBackground = page != pagerState.currentPage,
                                modifier = cardWidthModifier
                            )
                        }
                        CardItem.PaywallPreview -> {
                            FreemiumPaywallCardView(
                                category = category,
                                questionsUnlocked = accessibleQuestions.size,
                                totalQuestions = cachedQuestions.size,
                                onTap = {
                                    handlePaywallTap(appState = appState, category = category, index = pagerState.currentPage)
                                },
                                modifier = cardWidthModifier
                            )
                        }
                        is CardItem.PackCompletion -> {
                            PackCompletionCardView(
                                category = category,
                                packNumber = item.packNumber,
                                onTap = { showNewPackReveal = true },
                                modifier = cardWidthModifier
                            )
                        }
                        null -> Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        // Favorites button (shown only on normal questions)
        val onQuestionPage = pagerState.currentPage in accessibleQuestions.indices
        if (onQuestionPage) {
            val currentQ = accessibleQuestions[pagerState.currentPage]
            val isFav = remember(currentQ.id) { favoritesService.isFavorite(currentQ.id) }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        favoritesService.toggleFavorite(currentQ, category)
                        println("🔥 Toggle favorite: ${currentQ.text.take(50)}...")
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(red = 1.0f, green = 0.4f, blue = 0.6f),
                    contentColor = Color.White
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isFav) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.height(34.dp)) // keep bottom spacing like Swift
        } else {
            Spacer(Modifier.height(106.dp))
        }
    }

    // Sheet for new pack reveal
    if (showNewPackReveal) {
        NewPackRevealSheet(
            packNumber = completedPackNumber + 1,
            onStart = {
                showNewPackReveal = false
                // Unlock next pack
                val packNumber = packProgressService.getUnlockedPacks(category.id)
                packProgressService.unlockNextPack(category.id)

                // Analytics
                Firebase.analytics.logEvent("pack_complete", bundleOf(
                    "categorie" to category.id,
                    "pack_numero" to packNumber
                ))
                println("📊 Firebase: pack_complete - categorie: ${category.id} - pack_numero: $packNumber")

                // Recompute accessible
                accessibleQuestions = packProgressService.getAccessibleQuestions(
                    from = cachedQuestions,
                    categoryId = category.id
                )
                println("🔓 New pack unlocked! ${accessibleQuestions.size} questions now accessible")
            },
            onDismiss = { showNewPackReveal = false }
        )
    }
}

// ---------------- Helper logic ----------------

private fun checkForPackCompletionCard(
    currentPage: Int,
    accessibleCount: Int,
    cachedCount: Int,
    category: QuestionCategory,
    isSubscribed: Boolean,
    maxFreeQuestions: Int,
    showPackCompletionCard: Boolean,
    onShowPackCard: (packNumber: Int) -> Unit
) {
    // If freemium limit reached and not subscribed, don't show the unlock card
    if (accessibleCount >= maxFreeQuestions && !category.isPremium && !isSubscribed) {
        println("🔥 Freemium: limit reached ($maxFreeQuestions), no unlock card for non-paying user")
        return
    }

    val questionsPerPack = 32

    // Show completion card when user is on the last accessible question of a pack
    if (currentPage == accessibleCount - 1 &&
        accessibleCount < cachedCount &&
        !showPackCompletionCard
    ) {
        val completedPack = ((accessibleCount - 1) / questionsPerPack) + 1
        onShowPackCard(completedPack)
        println("🎉 Pack $completedPack finished for ${category.title}! Unlock card available on next swipe.")
        return
    }

    // Fallback if they somehow moved past the end
    if (currentPage >= accessibleCount &&
        accessibleCount < cachedCount &&
        !showPackCompletionCard
    ) {
        val completedPack = ((accessibleCount - 1) / questionsPerPack) + 1
        onShowPackCard(completedPack)
        println("🎉 Pack $completedPack finished (fallback). Unlock card available.")
    }
}

private fun checkForFreemiumPaywallCard(
    currentPage: Int,
    isSubscribed: Boolean,
    category: QuestionCategory,
    maxFreeQuestions: Int,
    cachedCount: Int,
    onShowPaywall: () -> Unit
) {
    if (!isSubscribed &&
        !category.isPremium &&
        currentPage >= maxFreeQuestions &&
        cachedCount > maxFreeQuestions
    ) {
        onShowPaywall()
        println("🔥 Freemium PAYWALL: limit reached (fallback). Paywall card available.")
    }
}

private fun handlePaywallTap(
    appState: AppState,
    category: QuestionCategory,
    index: Int
) {
    println("🔥 Freemium PAYWALL: tap on paywall card")
    appState.freemiumManager?.handleQuestionAccess(
        index,
        category
    ) {
        // This callback should not be called here (access is blocked)
        println("🔥 Freemium PAYWALL: Unexpected access granted callback")
    }
}

// ---------------- Card Items ----------------

private sealed interface CardItem {
    data class Q(val question: Question) : CardItem
    data object PaywallPreview : CardItem
    data class PackCompletion(val packNumber: Int) : CardItem
}

// ---------------- UI Components ----------------

@Composable
private fun PackCompletionCardView(
    category: QuestionCategory,
    packNumber: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "flame")
    val scale by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val rotate by infinite.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "rotate"
    )

    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(red = 0.2f, green = 0.1f, blue = 0.15f),
                        Color(red = 0.4f, green = 0.2f, blue = 0.3f),
                        Color(red = 0.6f, green = 0.3f, blue = 0.2f)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.congratulations_pack),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pack_completed),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "🔥",
                fontSize = 60.sp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale; rotationZ = rotate
                    }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tap_unlock_surprise),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.weight(1f))

            // Branding bottom
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                // Replace with your drawable
                Image(
                    painter = painterResource(id = R.drawable.leetchi2),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Love2Love",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun QuestionCardView(
    question: Question,
    category: QuestionCategory,
    isBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val cardShadow = if (isBackground) 5.dp else 10.dp
    val cardAlpha = if (isBackground) 0.8f else 1f

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(cardShadow, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = if (isBackground) 0.1f else 0.3f))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(red = 0.2f, green = 0.1f, blue = 0.15f),
                        Color(red = 0.4f, green = 0.2f, blue = 0.3f),
                        Color(red = 0.6f, green = 0.3f, blue = 0.2f)
                    )
                )
            )
            .fillMaxWidth()
            .height(500.dp)
            .padding(bottom = 0.dp)
            .alpha(cardAlpha)
    ) {
        // Header with category title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(red = 1.0f, green = 0.4f, blue = 0.6f),
                            Color(red = 1.0f, green = 0.6f, blue = 0.8f)
                        )
                    )
                )
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // Body with question text
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = question.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 30.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.leetchi2),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Love2Love",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun FreemiumPaywallCardView(
    category: QuestionCategory,
    questionsUnlocked: Int,
    totalQuestions: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF3E1D33),
                        Color(0xFF6A2A4D)
                    )
                )
            )
            .padding(24.dp)
            .fillMaxWidth()
            .height(500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.locked_content),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.subscribe_access_questions),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
        Spacer(Modifier.weight(1f))
        // Simple status line (optional)
        Text(
            text = "$questionsUnlocked / $totalQuestions",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NewPackRevealSheet(
    packNumber: Int,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onStart) {
                Text("C'est parti !")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
        title = { Text("Pack $packNumber") },
        text = { Text("Un nouveau pack est prêt. Appuie sur \"C'est parti !\" pour le débloquer.") }
    )
}

// ---------------- Placeholder model/service types ----------------
// Replace these with your real types or imports.
interface Question { val id: String; val text: String }
interface QuestionCategory { val id: String; val title: String; val isPremium: Boolean }
interface UserLite { val isSubscribed: Boolean }
interface AppState { val freemiumManager: FreemiumManager?; val currentUser: UserLite? }
interface FreemiumManager {
    fun getMaxFreeQuestions(category: QuestionCategory): Int
    fun handleQuestionAccess(index: Int, category: QuestionCategory, onGranted: () -> Unit)
}
interface QuestionCacheManager {
    fun preloadCategory(categoryId: String) {}
    fun getQuestionsWithSmartCache(categoryId: String, fallbackLoader: () -> List<Question>): List<Question>
}
interface QuestionDataManager { companion object { val shared: QuestionDataManager = object: QuestionDataManager{} } fun loadQuestions(categoryId: String): List<Question> = emptyList() }
interface FavoritesService { fun isFavorite(questionId: String): Boolean; suspend fun toggleFavorite(question: Question, category: QuestionCategory) }
interface PackProgressService {
    fun getAccessibleQuestions(from: List<Question>, categoryId: String): List<Question>
    fun unlockNextPack(categoryId: String)
    fun resetProgress(categoryId: String)
    fun getUnlockedPacks(categoryId: String): Int
}
interface CategoryProgressService { fun getCurrentIndex(categoryId: String): Int; fun saveCurrentIndex(index: Int, categoryId: String) }
interface ReviewRequestService { fun checkForReviewRequest() }
