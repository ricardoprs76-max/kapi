package com.aura.finanzas

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import com.aura.finanzas.ui.components.revealOrigin
import com.aura.finanzas.ui.components.LocalGrowOrigin
import com.aura.finanzas.ui.screens.AssetKindDetail
import com.aura.finanzas.data.UserSpace
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.aura.finanzas.ui.components.LocalPremium
import com.aura.finanzas.ui.components.bgStyles
import com.aura.finanzas.ui.screens.PremiumSheet
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.google.android.gms.ads.MobileAds
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Rect
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.AccountType
import com.aura.finanzas.data.AiProvider
import com.aura.finanzas.data.Asset
import com.aura.finanzas.data.Budget
import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Debt
import com.aura.finanzas.data.FixedExpense
import com.aura.finanzas.data.Goal
import com.aura.finanzas.data.Keys
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.AuraBackground
import com.aura.finanzas.ui.components.Fab
import com.aura.finanzas.ui.components.NavBar
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.LocalAnimScope
import com.aura.finanzas.ui.components.LocalSharedScope
import com.aura.finanzas.ui.components.NavTab
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.WaveScreen
import com.aura.finanzas.ui.screens.AccountAnalysis
import com.aura.finanzas.ui.screens.AnalysisScreen
import com.aura.finanzas.ui.screens.BudgetScreen
import com.aura.finanzas.ui.screens.AccountSheet
import com.aura.finanzas.ui.screens.AiSheet
import com.aura.finanzas.ui.screens.AssetSheet
import com.aura.finanzas.ui.screens.BudgetSheet
import com.aura.finanzas.ui.screens.BusyDialog
import com.aura.finanzas.ui.screens.CategoriesScreen
import com.aura.finanzas.ui.screens.CategorySheet
import com.aura.finanzas.ui.screens.ContributeDialog
import com.aura.finanzas.ui.screens.DebtSheet
import com.aura.finanzas.ui.screens.FixedSheet
import com.aura.finanzas.ui.screens.GoalSheet
import com.aura.finanzas.ui.screens.HomeScreen
import com.aura.finanzas.ui.screens.MoreActions
import com.aura.finanzas.ui.screens.MoreScreen
import com.aura.finanzas.ui.screens.MovesScreen
import com.aura.finanzas.data.Auth
import com.aura.finanzas.ui.screens.LoginScreen
import com.aura.finanzas.ui.screens.Onboarding
import com.aura.finanzas.ui.screens.ReceiptDialog
import com.aura.finanzas.ui.screens.ReceiptSheet
import com.aura.finanzas.ui.screens.TxSheet
import com.aura.finanzas.ui.screens.VoiceDialog
import com.aura.finanzas.ui.screens.WorthActions
import com.aura.finanzas.ui.screens.WorthScreen
import com.aura.finanzas.ui.theme.AuraTheme
import com.aura.finanzas.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val vm: FinanceViewModel by viewModels()

  /** Con el bloqueo activo, la app queda tapada hasta que la persona se autentica. */
  private var locked by mutableStateOf(false)
  private var initialLockChecked = false

  /** Momento en que la app dejó de estar a la vista; 0 mientras está en uso. */
  private var stoppedAt = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    MobileAds.initialize(this)
    Auth.init(this)
    if (savedInstanceState == null) handleIntent(intent)
    setContent {
      val settings by vm.settings.collectAsStateWithLifecycle()
      val user by Auth.user.collectAsStateWithLifecycle()
      // Solo al ENTRAR (con Google o como invitado) se cambia de espacio. Se espera un momento para que Firebase
      // termine de guardar la sesión en el teléfono: si el proceso se cerraba al instante, el cambio se perdía y
      // volvía a entrar la cuenta anterior.
      var switching by remember { mutableStateOf(false) }
      LaunchedEffect(user?.uid, settings.loaded, settings.guest) {
        val entering = settings.loaded && (user != null || settings.guest)
        if (entering && UserSpace.needsRestart(this@MainActivity, user?.uid)) {
          switching = true
          kotlinx.coroutines.delay(900)
          restartApp()
        }
      }
      // Modo: 0 sistema, 1 claro, 2 oscuro, 3 AMOLED (oscuro con negro puro).
      val dark = when (settings.themeMode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
      val amoled = settings.themeMode == 3
      SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
          isAppearanceLightStatusBars = !dark
          isAppearanceLightNavigationBars = !dark
        }
      }
      LaunchedEffect(settings.loaded) {
        if (settings.loaded && !initialLockChecked) {
          initialLockChecked = true
          if (settings.lockEnabled && canLock()) { locked = true; authenticate() }
        }
      }
      AuraTheme(settings.palette, dark, amoled) {
        Box(Modifier.fillMaxSize()) {
          when {
            !settings.loaded -> AuraBackground(false)
            !settings.onboarded -> {
              AuraBackground(settings.animatedBackground, style = shownBg(settings))
              Onboarding { vm.setPref(Keys.onboarded, true) }
            }
            // Sin sesión se entra con Google o como invitado (los datos quedan solo en este teléfono).
            user == null && !settings.guest -> {
              AuraBackground(settings.animatedBackground, style = shownBg(settings))
              LoginScreen(onGuest = { vm.setPref(Keys.guest, true) })
            }
            else -> AuraApp(vm, ::canLock)
          }
          if (locked) LockScreen(::authenticate)
          if (switching) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
              Text("Abriendo tu cuenta…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
          }
        }
      }
    }
  }

  /** Vuelve a abrir la app en un proceso nuevo, para que tome la base de datos de la persona que acaba de entrar. */
  private fun restartApp() {
    val component = packageManager.getLaunchIntentForPackage(packageName)?.component ?: return
    startActivity(Intent.makeRestartActivityTask(component))
    finishAffinity()
    Runtime.getRuntime().exit(0)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  /** El botón "+" del widget abre la app directamente en el registro de un gasto. */
  private fun handleIntent(intent: Intent?) {
    when (intent?.action) {
      AuraWidget.ACTION_ADD -> vm.pendingAdd.value = true
      // Un archivo de cuenta compartida abierto desde WhatsApp, el correo o el explorador de archivos.
    }
  }

  override fun onStop() {
    super.onStop()
    // Salir un momento no bloquea: solo se anota la hora, y al volver se mira cuánto tiempo pasó.
    if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
  }

  override fun onStart() {
    super.onStart()
    vm.expectingResult = false
    vm.syncNow()
    val away = if (stoppedAt == 0L) 0L else SystemClock.elapsedRealtime() - stoppedAt
    stoppedAt = 0L
    // Se bloquea al abrir la app y después de 10 minutos sin usarla.
    if (initialLockChecked && away >= LOCK_AFTER_MS && vm.settings.value.lockEnabled && canLock()) locked = true
    if (locked && initialLockChecked) authenticate()
  }

  /** Solo se puede bloquear si el teléfono tiene huella, rostro o PIN configurado (Android 11 o superior). */
  private fun canLock(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val manager = getSystemService(BiometricManager::class.java) ?: return false
    return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
  }

  private fun authenticate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { locked = false; return }
    val prompt = BiometricPrompt.Builder(this)
      .setTitle("Kapi")
      .setSubtitle("Desbloquea para ver tus finanzas")
      .setAllowedAuthenticators(AUTHENTICATORS)
      .build()
    prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { locked = false }
    })
  }

  private companion object {
    const val LOCK_AFTER_MS = 10 * 60 * 1000L
    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
  }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Column(
    Modifier.fillMaxSize().background(scheme.background).clickable(remember { MutableInteractionSource() }, null) {},
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
  ) {
    IsoIcon("SAFE", scheme.primary, size = 120.dp)
    Text("Kapi está bloqueada", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
    Pill("Desbloquear", true, onClick = onUnlock)
  }
}

private val tabs = listOf(
  NavTab("home", "Inicio", Glyph.DASH),
  NavTab("worth", "Patrimonio", Glyph.PIE),
  NavTab("budget", "Presupuesto", Glyph.TREND),
  NavTab("analysis", "Análisis IA", Glyph.SPARK),
)

/** Fondo que se dibuja: el elegido, salvo que sea de Premium y la persona ya no lo tenga. */
private fun shownBg(s: com.aura.finanzas.data.Settings): Int =
  s.bgStyle.takeIf { it in bgStyles.indices && (!bgStyles[it].premium || s.isPremium) } ?: 0

/** Petición de abrir la hoja de movimiento: nuevo (con tipo y cuenta sugerida) o existente. */
private class TxRequest(val type: String, val existing: Tx? = null, val accountId: Long? = null, val origin: Rect? = null)

/** Huella SHA-1 con la que está firmada esta instalación; es la que pide Google Cloud. */
private fun signingSha1(context: Context): String = try {
  if (Build.VERSION.SDK_INT < 28) "" else {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
    if (signer == null) "" else MessageDigest.getInstance("SHA-1").digest(signer.toByteArray()).joinToString(":") { "%02X".format(it) }
  }
} catch (e: Exception) {
  ""
}

@Composable
fun AuraApp(vm: FinanceViewModel, canLock: () -> Boolean) {
  val state by vm.state.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  val ai by vm.ai.collectAsStateWithLifecycle()
  val draft by vm.draft.collectAsStateWithLifecycle()
  val notice by vm.notice.collectAsStateWithLifecycle()
  val receipt by vm.receipt.collectAsStateWithLifecycle()
  val nav = rememberNavController()
  val entry by nav.currentBackStackEntryAsState()
  val route = entry?.destination?.route ?: "home"
  var txRequest by remember { mutableStateOf<TxRequest?>(null) }
  var accountEdit by remember { mutableStateOf<Account?>(null) }
  var categoryEdit by remember { mutableStateOf<Category?>(null) }
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val haze = remember { HazeState() }
  val newAccount = { accountEdit = Account(name = "", type = AccountType.CASH, icon = "WALLET", colorIdx = state.accounts.size % 7) }
  val editTx: (Tx) -> Unit = { txRequest = TxRequest(it.type, it) }
  var assetEdit by remember { mutableStateOf<Asset?>(null) }
  // Botón + (de ahí nace la hoja al abrirla con él), hoja de activo abierta desde el + y tipo de activo abierto.
  var fabRect by remember { mutableStateOf(Rect.Zero) }
  var assetOrigin by remember { mutableStateOf<Rect?>(null) }
  var kindOpen by remember { mutableStateOf<Pair<String, Rect>?>(null) }
  val loginEmail = Auth.user.collectAsStateWithLifecycle().value?.email
  var debtEdit by remember { mutableStateOf<Debt?>(null) }
  var budgetEdit by remember { mutableStateOf<Budget?>(null) }
  var fixedEdit by remember { mutableStateOf<FixedExpense?>(null) }
  var goalEdit by remember { mutableStateOf<Goal?>(null) }
  var goalContribute by remember { mutableStateOf<Goal?>(null) }
  var aiSheet by remember { mutableStateOf(false) }
  var voiceDialog by remember { mutableStateOf(false) }
  var receiptDialog by remember { mutableStateOf(false) }
  var receiptHint by remember { mutableStateOf<String?>(null) }
  var restoreUri by remember { mutableStateOf<Uri?>(null) }
  // Cuenta cuyo análisis está abierto, y el rectángulo de su tarjeta (de ahí nace la animación).
  var openAccount by rememberSaveable { mutableStateOf<Long?>(null) }
  var accountOrigin by remember { mutableStateOf(Rect.Zero) }
  // Cuenta a la que se limita la lista de movimientos; null = todas.
  var movesAccount by rememberSaveable { mutableStateOf<Long?>(null) }
  var shareAsk by remember { mutableStateOf<Long?>(null) }
  var premiumSheet by remember { mutableStateOf(false) }
  val notes by vm.notes.collectAsStateWithLifecycle()
  val chatSince by vm.chatSince.collectAsStateWithLifecycle()
  val focusAccount by vm.focusAccount.collectAsStateWithLifecycle()
  val sharedPeople by vm.sharedPeople.collectAsStateWithLifecycle()
  val offers by vm.offers.collectAsStateWithLifecycle()
  val billingMessage by vm.billingMessage.collectAsStateWithLifecycle()

  // Selectores de archivos del sistema para exportar, respaldar y restaurar.
  val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
  val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
  val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
  val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::exportPdf) }
  val context = LocalContext.current
  // La cámara y la galería se registran aquí, en la raíz, para que la foto elegida llegue aunque el sistema
  // haya cerrado y vuelto a crear la pantalla mientras la persona estaba en la galería.
  val photoUri = remember {
    val file = File(context.cacheDir, "recibos").apply { mkdirs() }.resolve("recibo.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  }
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
    vm.expectingResult = false
    if (ok) vm.scanReceipt(photoUri)
  }
  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    vm.expectingResult = false
    if (uri != null) vm.scanReceipt(uri)
  }
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) vm.setReminders(true)
    else scope.launch { snackbar.showSnackbar("Sin permiso de notificaciones no puedo avisarte de los pagos") }
  }
  // Recordatorio diario (viene activado): en Android 13 o superior se pide una sola vez el permiso de notificaciones.
  val dailyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (!granted) scope.launch { snackbar.showSnackbar("Sin permiso de notificaciones no puedo recordarte registrar tus movimientos") }
  }
  val settingsNow by vm.settings.collectAsStateWithLifecycle()
  LaunchedEffect(settingsNow.loaded) {
    val s = settingsNow
    if (s.loaded && s.dailyLog && !s.dailyAsked && Build.VERSION.SDK_INT >= 33 &&
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      vm.setPref(Keys.dailyAsked, true)
      dailyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
  }
  val cloud by vm.cloud.collectAsStateWithLifecycle()
  val refreshing by vm.refreshing.collectAsStateWithLifecycle()
  val pendingAdd by vm.pendingAdd.collectAsStateWithLifecycle()
  LaunchedEffect(pendingAdd) {
    if (pendingAdd) { vm.pendingAdd.value = false; txRequest = TxRequest(TxType.EXPENSE) }
  }

  // Empujón a las partículas del fondo cada vez que se cambia de pantalla.
  val burst = remember { Animatable(0f) }
  LaunchedEffect(route) {
    burst.snapTo(1f)
    burst.animateTo(0f, tween(1700, easing = FastOutSlowInEasing))
  }

  val worthActions = remember {
    WorthActions(
      editAsset = { assetOrigin = null; assetEdit = it }, deleteAsset = { vm.deleteAsset(it) },
      openKind = { kind, bounds -> kindOpen = kind to bounds }, unpayFixed = { vm.unpayFixed(it) },
      editDebt = { debtEdit = it }, editBudget = { budgetEdit = it },
      editFixed = { fixedEdit = it }, editGoal = { goalEdit = it },
      payFixed = {
        vm.payFixed(it)
        scope.launch { snackbar.showSnackbar(if (it.accountId != null) "${it.name}: pago registrado" else "${it.name}: marcado como pagado") }
      },
      contribute = { goalContribute = it },
    )
  }
  val moreActions = remember {
    MoreActions(
      ai = { aiSheet = true },
      assistant = { revealOrigin = Offset(0.875f, 0.97f); nav.goTab("analysis") },
      lock = { on ->
        if (on && !canLock()) scope.launch { snackbar.showSnackbar("Primero configura huella, rostro o PIN en los ajustes del teléfono") }
        else vm.setPref(Keys.lockEnabled, on)
      },
      exportCsv = { vm.expectingResult = true; csvPicker.launch("movimientos-${LocalDate.now()}.csv") },
      backup = { vm.expectingResult = true; backupPicker.launch("aura-respaldo-${LocalDate.now()}.json") },
      restore = { vm.expectingResult = true; restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
      blur = { vm.setPref(Keys.blur, it) },
      hide = { vm.setHideAmounts(it) },
      premium = { premiumSheet = true },
      bgStyle = { vm.setBgStyle(it) },
      back = { nav.popBackStack() },
      pdf = { vm.expectingResult = true; pdfPicker.launch("aura-informe-${LocalDate.now()}.pdf") },
      googleSync = { vm.syncNow(force = true) },
      // Al salir, los datos de esta persona quedan en su nube de Kapi; quien entre después tiene su propio espacio.
      signOut = { scope.launch { vm.setPref(Keys.guest, false); Auth.signOut(context) } },
      signIn = { vm.setPref(Keys.guest, false) },
      dailyLog = { on ->
        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
          context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) dailyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        vm.setDailyLog(on)
      },
      dailyTime = { h, m -> vm.setDailyLog(true, h, m) },
      dailyTest = { vm.testDailyLog() },
      reminders = { on ->
        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
          context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else vm.setReminders(on)
      },
    )
  }
  // Guarda el movimiento y avisa si con él se alcanza o se supera el presupuesto de su categoría.
  val saveTx: (Tx) -> Unit = { tx ->
    vm.saveTx(tx)
    budgetWarning(state, tx)?.let { scope.launch { snackbar.showSnackbar(it) } }
  }

  // El movimiento leído de un recibo o de una frase se abre en la hoja para revisarlo antes de guardar.
  LaunchedEffect(draft) {
    draft?.let { txRequest = TxRequest(it.type, it); vm.draft.value = null }
  }
  LaunchedEffect(notice) {
    notice?.let { vm.notice.value = null; snackbar.showSnackbar(it) }
  }

  // Deslizar el dedo a los lados pasa a la pestaña vecina. Las filas que se desplazan de lado (cuentas, meses,
  // chips) consumen su propio gesto, así que aquí solo llegan los deslizamientos sobre el resto de la pantalla.
  val swipeTabs = Modifier.pointerInput(route) {
    val index = tabs.indexOfFirst { it.route == route }
    if (index < 0) return@pointerInput
    var total = 0f
    detectHorizontalDragGestures(
      onDragStart = { total = 0f },
      onDragEnd = {
        val next = if (total < 0) index + 1 else index - 1
        if (kotlin.math.abs(total) > 64.dp.toPx() && next in tabs.indices) {
          // La pantalla nueva entra desde el lado hacia el que se arrastra.
          revealOrigin = Offset(if (total < 0) 1f else 0f, 0.5f)
          nav.goTab(tabs[next].route)
        }
      },
    ) { _, dx -> total += dx }
  }

  CompositionLocalProvider(LocalPremium provides settings.isPremium) {
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().hazeSource(haze).then(swipeTabs)) {
      AuraBackground(settings.animatedBackground, style = shownBg(settings), burst = { burst.value }, paused = openAccount != null || kindOpen != null)

      SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
          NavHost(
            nav, "home", Modifier.fillMaxSize(),
            // El NavHost no anima nada por sí mismo: mantiene ambas pantallas mientras la ola (WaveScreen) las recorta.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.KeepUntilTransitionsFinished },
          ) {
            screen("home") {
              HomeScreen(
                state, settings.hideAmounts,
                onSettings = { revealOrigin = Offset(0.9f, 0.07f); nav.navigate("more") { launchSingleTop = true } },
                onAddAccount = newAccount,
                onSeeAll = { revealOrigin = Offset(0.82f, 0.72f); movesAccount = null; nav.navigate("moves") { launchSingleTop = true } },
                onOpenAccount = { id, bounds -> accountOrigin = bounds; openAccount = id },
                onEditAccount = { accountEdit = it },
                onEditTx = editTx,
                onAssistant = { revealOrigin = Offset(0.5f, 0.2f); nav.goTab("analysis") },
                onVoice = { vm.expectingResult = true; voiceDialog = true },
                onScan = { receiptHint = null; receiptDialog = true },
                onReorder = vm::reorderAccounts,
                refreshing = refreshing, onRefresh = vm::refresh,
              )
            }
            screen("moves") { MovesScreen(state, settings.hideAmounts, movesAccount, { nav.popBackStack() }, editTx, vm::deleteTx) }
            screen("worth") { WorthScreen(state, settings.hideAmounts, worthActions) }
            screen("budget") { BudgetScreen(state, settings.hideAmounts, worthActions) }
            screen("analysis") {
              AnalysisScreen(
                state, settings, ai, notes, chatSince, settings.hideAmounts, onBack = { nav.goTab("home") }, onConfigure = { aiSheet = true },
                onSend = vm::chat, onToggleMemo = { vm.toggleMemo(it) }, onDelete = { vm.deleteNote(it) }, onNewChat = { vm.newChat() },
                onDeleteHistory = { vm.deleteHistory(it) },
                onCancel = vm::cancelAi, onListening = { vm.expectingResult = true },
                focusAccount = focusAccount, onFocus = { vm.focusAccount.value = it },
              )
            }
            screen("more") {
              MoreScreen(
                state, settings, vm::setPalette, vm::setThemeMode, vm::setAnimatedBg, newAccount,
                onEditAccount = { accountEdit = it }, onCategories = { revealOrigin = Offset(0.5f, 0.45f); nav.navigate("categories") { launchSingleTop = true } },
                actions = moreActions, cloud = cloud, user = Auth.user.collectAsStateWithLifecycle().value,
              )
            }
            screen("categories") { CategoriesScreen(state, { nav.popBackStack() }) { categoryEdit = it } }
          }
        }
      }
    }

    AnimatedVisibility(
      tabs.any { it.route == route },
      enter = slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects()),
      exit = slideOutVertically(Motion.spatial()) { it / 3 } + fadeOut(Motion.effects()),
    ) {
      Box(Modifier.fillMaxSize()) {
        NavBar(
          tabs, route,
          { r ->
            // La pantalla nueva nace desde la pestaña que se tocó.
            revealOrigin = Offset((tabs.indexOfFirst { it.route == r } + 0.5f) / tabs.size, 0.97f)
            nav.goTab(r)
          },
          Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 10.dp),
          haze = if (settings.blur) haze else null,
        )
      }
    }
    AnimatedVisibility(
      route == "home" || route == "worth", Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 88.dp),
      enter = scaleIn(Motion.spatialBouncy()) + fadeIn(Motion.effects()), exit = scaleOut(Motion.spatial()) + fadeOut(Motion.effects()),
    ) {
      // La hoja que abre el + nace del propio botón, igual que el análisis nace de la tarjeta de una cuenta.
      Fab(if (route == "worth") "Agregar activo" else "Registrar movimiento", Modifier.onGloballyPositioned { fabRect = it.boundsInRoot() }) {
        if (route == "worth") { assetOrigin = fabRect; assetEdit = Asset(name = "", kind = "INVERSION", value = 0.0, icon = "CHART") }
        else txRequest = TxRequest(TxType.EXPENSE, origin = fabRect)
      }
    }

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))

    openAccount?.let { id ->
      AccountAnalysis(
        state, id, accountOrigin, settings.hideAmounts,
        onClosed = { openAccount = null },
        onNewTx = { txRequest = TxRequest(it, accountId = id) },
        onMoves = { revealOrigin = Offset(0.3f, 0.85f); openAccount = null; movesAccount = id; nav.navigate("moves") { launchSingleTop = true } },
        onShare = { shareAsk = id },
        refreshing = refreshing, onRefresh = vm::refresh,
        people = state.accountById[id]?.shareId?.let { sharedPeople[it] } ?: emptyList(), myEmail = loginEmail ?: settings.cloudEmail,
      )
    }
    kindOpen?.let { (kind, bounds) -> AssetKindDetail(state, kind, bounds, settings.hideAmounts, worthActions) { kindOpen = null } }
  }
  }

  if (premiumSheet) {
    PremiumSheet(
      settings, offers, billingMessage, onOpen = vm::connectBilling, onBuy = vm::buy, onRestore = vm::restorePurchases,
      onDevPremium = { vm.setPref(Keys.devPremium, it) },
    ) { premiumSheet = false }
  }

  txRequest?.let { r ->
    CompositionLocalProvider(LocalGrowOrigin provides r.origin) { TxSheet(state, r.type, r.existing, r.accountId, saveTx, vm::deleteTx) { txRequest = null } }
  }
  receipt?.let { scan ->
    ReceiptSheet(
      state, scan, vm::saveReceipt,
      // Volver a leer: se cierra la hoja y se lee la misma foto, o se abre la cámara/galería para otra.
      onReadAgain = scan.photo?.takeIf { settings.aiKey.isNotBlank() }?.let { photo -> { vm.receipt.value = null; vm.scanReceipt(photo, careful = true) } },
      onNewPhoto = if (scan.voice) null else ({ vm.receipt.value = null; receiptHint = null; receiptDialog = true }),
    ) { vm.receipt.value = null }
  }
  assetEdit?.let { a ->
    CompositionLocalProvider(LocalGrowOrigin provides assetOrigin) { AssetSheet(a, vm::saveAsset, vm::deleteAsset) { assetEdit = null; assetOrigin = null } }
  }
  debtEdit?.let { DebtSheet(it, vm::saveDebt, vm::deleteDebt) { debtEdit = null } }
  budgetEdit?.let { BudgetSheet(state, it, vm::saveBudget, vm::deleteBudget) { budgetEdit = null } }
  fixedEdit?.let { FixedSheet(state, it, vm::saveFixed, vm::deleteFixed) { fixedEdit = null } }
  goalEdit?.let { GoalSheet(it, vm::saveGoal, vm::deleteGoal) { goalEdit = null } }
  goalContribute?.let { g -> ContributeDialog(g, { vm.contributeGoal(g, it) }) { goalContribute = null } }
  accountEdit?.let { a ->
    AccountSheet(a, vm::saveAccount, onDelete = {
      vm.deleteAccount(it)
      if (openAccount == it.id) openAccount = null
    }, movements = state.txs.count { it.accountId == a.id || it.toAccountId == a.id }) { accountEdit = null }
  }
  categoryEdit?.let { c -> CategorySheet(c, vm::saveCategory, vm::deleteCategory) { categoryEdit = null } }

  if (aiSheet) {
    AiSheet(
      settings, ai,
      onProvider = { vm.setPref(Keys.aiProvider, it) },
      onKey = { provider, key -> vm.setAiKey(provider, key) },
      onModel = { provider, model -> vm.setPref(if (provider == AiProvider.CLAUDE) Keys.claudeModel else Keys.geminiModel, model) },
      onTest = vm::testAi,
    ) { aiSheet = false; vm.clearAiError() }
  }
  if (voiceDialog) VoiceDialog(vm::parseSpoken) { voiceDialog = false }
  if (receiptDialog) {
    ReceiptDialog(
      receiptHint,
      onCamera = {
        vm.expectingResult = true
        try {
          camera.launch(photoUri)
          receiptDialog = false
        } catch (e: ActivityNotFoundException) {
          receiptHint = "No encontré una app de cámara. Usa la galería."
        }
      },
      onGallery = {
        vm.expectingResult = true
        receiptDialog = false
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
    ) { receiptDialog = false }
  }
  // En Análisis IA y en la hoja de IA la espera y el error se muestran en la propia pantalla.
  val inline = route == "analysis" || aiSheet
  ai.busy?.let { label -> if (!inline) BusyDialog(label, vm::cancelAi) }
  ai.error?.let { message ->
    if (!inline) {
      AlertDialog(
        onDismissRequest = vm::clearAiError,
        title = { Text("No se pudo completar") },
        text = { Text(message) },
        confirmButton = { TextButton(vm::clearAiError) { Text("Entendido") } },
      )
    }
  }
  shareAsk?.let { id ->
    var email by remember(id) { mutableStateOf("") }
    val valid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    AlertDialog(
      onDismissRequest = { shareAsk = null },
      title = { Text("Compartir cuenta con otra persona") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "Escribe el correo de Google de la otra persona. Ella instala Kapi, entra con ese mismo correo y la cuenta le aparece sola (solo esta cuenta, no el resto de tus datos). No se envía ningún archivo ni correo.\n\n" +
              "Los dos pueden registrar gastos e ingresos en esta cuenta: lo que registra cada uno le llega al otro al abrir la app o al deslizar hacia abajo para actualizar.",
          )
          state.accountById[id]?.shareId?.takeIf { it.isNotEmpty() }?.let { shareId ->
            com.aura.finanzas.ui.screens.sharedLines(sharedPeople[shareId] ?: emptyList(), loginEmail ?: settings.cloudEmail).forEach {
              Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
          }
          if (loginEmail != null) {
            OutlinedTextField(
              email, { email = it.trim().take(80) }, label = { Text("Correo de Google") }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
          } else {
            Text("Para compartir, entra a Kapi con tu cuenta de Google.", color = MaterialTheme.colorScheme.error)
          }
        }
      },
      confirmButton = {
        if (loginEmail != null) TextButton({ vm.shareWith(id, email.trim().lowercase()); shareAsk = null }, enabled = valid) { Text("Compartir") }
        else TextButton({ shareAsk = null }) { Text("Entendido") }
      },
      dismissButton = { TextButton({ shareAsk = null }) { Text("Cancelar") } },
    )
  }
  if (cloud.conflict) {
    AlertDialog(
      onDismissRequest = vm::dismissConflict,
      title = { Text("Hay dos versiones de tus datos") },
      text = { Text("Este teléfono y tu nube de Kapi tienen cambios distintos. Elige cuál conservar; la otra se reemplaza.") },
      confirmButton = { TextButton({ vm.syncNow(resolve = "REMOTE") }) { Text("Usar la de la nube") } },
      dismissButton = { TextButton({ vm.syncNow(resolve = "LOCAL") }) { Text("Usar la de este teléfono") } },
    )
  }
  restoreUri?.let { uri ->
    AlertDialog(
      onDismissRequest = { restoreUri = null },
      title = { Text("¿Restaurar este respaldo?") },
      text = { Text("Se reemplazarán TODOS los datos actuales (cuentas, movimientos, patrimonio, presupuestos y metas) por los del archivo. No se puede deshacer.") },
      confirmButton = { TextButton({ vm.importBackup(uri); restoreUri = null }) { Text("Restaurar", color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton({ restoreUri = null }) { Text("Cancelar") } },
    )
  }
}

/**
 * Mensaje de aviso si el gasto [tx] deja algún presupuesto en o por encima de su umbral; null si no aplica.
 * Se miran del más específico al más general: categoría de su cuenta, total de su cuenta, categoría de todas
 * las cuentas y total de todas.
 */
private fun budgetWarning(state: FinanceState, tx: Tx): String? {
  if (tx.type != TxType.EXPENSE) return null
  val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  if (tx.date < monthStart) return null
  // Al editar, el movimiento anterior ya está contado: se descuenta y se suma el nuevo.
  val others = state.txs.filter { it.type == TxType.EXPENSE && it.date >= monthStart && !(tx.id != 0L && it.id == tx.id) } + tx
  fun spent(b: Budget) = others.filter { (b.accountId == com.aura.finanzas.data.ALL_ACCOUNTS || it.accountId == b.accountId) && (b.isTotal || it.categoryId == b.categoryId) }.sumOf { it.amount }
  fun name(b: Budget): String {
    val what = if (b.isTotal) "todos los gastos" else state.categoryById[b.categoryId]?.name ?: "la categoría"
    return state.accountById[b.accountId]?.let { "$what de ${it.name}" } ?: what
  }
  val all = com.aura.finanzas.data.ALL_ACCOUNTS
  val order = listOf(
    state.budgets.firstOrNull { !it.isTotal && it.categoryId == tx.categoryId && it.accountId == tx.accountId },
    state.budgets.firstOrNull { it.isTotal && it.accountId == tx.accountId },
    state.budgets.firstOrNull { !it.isTotal && it.categoryId == tx.categoryId && it.accountId == all },
    state.budgets.firstOrNull { it.isTotal && it.accountId == all },
  )
  for (b in order) {
    if (b == null || b.monthlyLimit <= 0) continue
    val pct = (spent(b) / b.monthlyLimit * 100).toInt()
    when {
      pct >= 100 -> return "Te pasaste del presupuesto de ${name(b)} ($pct %)"
      pct >= b.alertPct -> return "Llevas el $pct % del presupuesto de ${name(b)}"
    }
  }
  return null
}

/** Destino que expone su ámbito de animación para los elementos compartidos. */
private fun NavGraphBuilder.screen(
  route: String,
  arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
  content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { e ->
  CompositionLocalProvider(LocalAnimScope provides this) { WaveScreen { content(e) } }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
  popUpTo(graph.startDestinationId) { saveState = true }
  launchSingleTop = true
  restoreState = true
}
