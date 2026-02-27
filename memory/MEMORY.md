# RecEmotion Project Memory

## Architecture
- Clean Architecture: data/{db,di,llm,parser,repository} / domain/{model,repository,service,usecase} / presentation
- Hilt DI, Room DB, Kotlin Coroutines, KSP, MediaPipe
- Package: `com.example.recemotion`
- Build: KSP 2.0.21-1.0.28, Kotlin 2.0.21, Hilt 2.51.1, minSdk 26, compileSdk 35
- Java: JDK 17 (Homebrew) — do NOT use `jvmToolchain()`, use `sourceCompatibility` + `kotlin { compilerOptions { jvmTarget = JVM_11 } }`

## Settings System (implemented)
- Module: `settings-processor/` — annotations + KSP processor (KotlinPoet)
- User writes `@SettingsGroup` annotated interface in `app/.../settings/`
- KSP generates: `{Name}Store.kt` + `{Name}Module.kt` per interface
- Backing storage: DataStore (Preferences)
- Pattern: `compileOnly(project(":settings-processor"))` + `ksp(project(":settings-processor"))` in app

### SetupSettings
- Schema: `app/src/main/java/com/example/recemotion/settings/SetupSettings.kt`
- Generated: `app/build/generated/ksp/debug/kotlin/com/example/recemotion/settings/`
- Old SharedPreferences (PREFS_NAME="recemotion_setup") migrated to DataStore

## Key Files
- `MainActivity.kt` — @AndroidEntryPoint, navigation drawer, `@Inject setupSettings: SetupSettingsStore`
- `SetupFragment.kt` — @AndroidEntryPoint, `by viewModels()`, uses `viewModel.getSavedAutoCalibrate()` / `saveSetup()`
- `SetupViewModel.kt` — @HiltViewModel, injects SetupSettingsStore, exposes `saveSetup()` + `getSavedAutoCalibrate()`
- `MainScreenFragment.kt` — @AndroidEntryPoint, `@Inject setupSettings: SetupSettingsStore`
- `AppDatabase.kt` — Room DB, version 4, 5 entities
- `DatabaseModule.kt` — Hilt module pattern reference
