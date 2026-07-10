# Android Widget-First App - Technical Specification

## Project Overview
A **widget-first Android app** for two people (couple) to share real-time messages, photos, and reactions.

**Core Philosophy:** Widgets ARE the interface. App UI is minimal (login + widget guide).

### MVP Scope
- **Minimal app UI:** Google login screen + widget guide
- **Current message widget** (4x2, 4x4 sizes)
  - Display: text/emoji message + photo with caption
  - Interactive: Tap emoji reactions directly from widget
  - Real-time updates from Firebase
  - Unread count badge

### Future Scope
- History widget (scrollable message archive)
- Reactions widget (who reacted to what)
- Interactive game widgets
- Multiple instances of same widget type

---

## Tech Stack

### Frontend (Android)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (for future UI, app screens)
- **Widget Framework:** RemoteViews (Android App Widgets)
- **Real-time Database:** Firebase Realtime Database
- **Authentication:** Firebase Authentication (Google OAuth)
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Storage:** Firebase Storage (photos)
- **Build Tool:** Gradle
- **Architecture:** MVVM + Clean Architecture (widget-agnostic)

### Backend
- **Firebase Realtime Database** (primary, real-time sync)
- **Firestore** (optional, for backups/structure)
- **Firebase Storage** (photos)
- **Firebase Authentication** (Google provider)
- **Firebase Cloud Messaging** (push notifications)

### CI/CD
- **GitHub Actions** (cloud build, no local Android Studio required)
- **Firebase App Distribution** (optional, for testing APK)

---

## Database Schema (Firebase Realtime Database)

### Structure
```
firebase_root/
├── users/
│   ├── uid1/
│   │   ├── email: "user1@gmail.com"
│   │   ├── displayName: "Alice"
│   │   ├── photoURL: "https://..."
│   │   ├── fcmTokens: ["token1", "token2"]
│   │   └── createdAt: 1234567890
│   └── uid2/
│       ├── email: "user2@gmail.com"
│       ├── displayName: "Bob"
│       ├── photoURL: "https://..."
│       ├── fcmTokens: ["token3", "token4"]
│       └── createdAt: 1234567890
│
├── shared/
│   ├── current_message/
│   │   ├── authorUid: "uid1"
│   │   ├── authorName: "Alice"
│   │   ├── type: "text|emoji|photo"
│   │   ├── content: "Hello!" or "😍" or ""
│   │   ├── photoUrl: "https://storage.../image.jpg"
│   │   ├── caption: "Look at this!"
│   │   ├── reactions: {
│   │   │   "❤️": ["uid1", "uid2"],
│   │   │   "😊": ["uid2"]
│   │   │ }
│   │   ├── createdAt: 1234567890
│   │   ├── updatedAt: 1234567890
│   │   └── expiresAt: 1234567890
│   │
│   ├── message_history/ (collection-like)
│   │   ├── msg_1/
│   │   │   ├── authorUid: "uid1"
│   │   │   ├── authorName: "Alice"
│   │   │   ├── type: "text|emoji|photo"
│   │   │   ├── content: "Hi there!"
│   │   │   ├── photoUrl: "https://..."
│   │   │   ├── caption: "..."
│   │   │   ├── reactions: { "❤️": ["uid2"] }
│   │   │   ├── createdAt: 1234567890
│   │   │   └── expiresAt: 1234567890
│   │   └── msg_2/ ...
│   │
│   └── settings/
│       ├── allowedUsers: ["uid1", "uid2"]
│       ├── createdAt: 1234567890
│       └── maxMessageAge: 2592000 (30 days in seconds)
```

---

## App Architecture

### Kotlin Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/example/couplewidget/
│   │   ├── MainActivity.kt (login + widget guide)
│   │   ├── SettingsActivity.kt (future)
│   │   │
│   │   ├── widgets/
│   │   │   ├── CurrentMessageWidget.kt (widget provider)
│   │   │   ├── CurrentMessageWidgetReceiver.kt (broadcast receiver)
│   │   │   ├── WidgetUpdateService.kt (Firebase sync service)
│   │   │   └── (future) HistoryWidget.kt, ReactionsWidget.kt, etc.
│   │   │
│   │   ├── ui/
│   │   │   ├── auth/
│   │   │   │   ├── LoginScreen.kt (Compose)
│   │   │   │   └── LoginViewModel.kt
│   │   │   ├── guide/
│   │   │   │   ├── WidgetGuideScreen.kt (how to add widgets)
│   │   │   │   └── WidgetGuideViewModel.kt
│   │   │   └── theme/
│   │   │       ├── Color.kt
│   │   │       ├── Theme.kt
│   │   │       └── Type.kt
│   │   │
│   │   ├── data/
│   │   │   ├── repository/
│   │   │   │   ├── MessageRepository.kt
│   │   │   │   ├── AuthRepository.kt
│   │   │   │   └── ReactionRepository.kt
│   │   │   ├── model/
│   │   │   │   ├── Message.kt
│   │   │   │   ├── User.kt
│   │   │   │   └── Reaction.kt
│   │   │   └── firebase/
│   │   │       ├── FirebaseManager.kt (init + config)
│   │   │       └── FirebaseSync.kt (real-time listeners)
│   │   │
│   │   ├── service/
│   │   │   ├── FCMService.kt (Firebase Cloud Messaging)
│   │   │   └── WidgetSyncService.kt (keep widgets updated)
│   │   │
│   │   ├── util/
│   │   │   ├── Constants.kt
│   │   │   ├── Permissions.kt
│   │   │   ├── LogUtil.kt
│   │   │   └── TimeUtil.kt
│   │   │
│   │   └── App.kt (application class)
│   │
│   └── res/
│       ├── layout/
│       │   ├── widget_current_message.xml (RemoteViews layout)
│       │   └── (future) widget_history.xml
│       ├── drawable/
│       │   ├── ic_react.png
│       │   ├── ic_send.png
│       │   └── emoji_*.png (reaction emojis)
│       ├── values/
│       │   ├── strings.xml
│       │   ├── colors.xml
│       │   └── dimens.xml
│       ├── xml/
│       │   ├── widget_current_message_info.xml (widget metadata)
│       │   └── (future) widget_history_info.xml
│       └── mipmap/
│           ├── ic_launcher.png
│           └── ic_launcher_round.png
│
├── build.gradle.kts (dependencies)
├── AndroidManifest.xml
└── proguard-rules.pro
```

---

## Core Components

### 1. Authentication Flow

**MainActivity.kt**
```kotlin
class MainActivity : ComponentActivity() {
    private val authViewModel: LoginViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            CoupleWidgetTheme {
                if (authViewModel.isUserLoggedIn.value) {
                    WidgetGuideScreen()
                } else {
                    LoginScreen(onLoginSuccess = {
                        // User logged in, show widget guide
                    })
                }
            }
        }
    }
}
```

**LoginScreen.kt (Compose)**
```kotlin
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { /* Trigger Google Sign-In */ }
        ) {
            Text("Login with Google")
        }
    }
}
```

**LoginViewModel.kt**
```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    val isUserLoggedIn = MutableLiveData<Boolean>()
    
    fun signInWithGoogle(idToken: String) {
        authRepository.signInWithGoogle(idToken) { success ->
            isUserLoggedIn.postValue(success)
        }
    }
}
```

---

### 2. Widget Architecture (MVP: Current Message Widget)

**CurrentMessageWidget.kt (Widget Provider)**
```kotlin
class CurrentMessageWidget : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        
        // Start service to keep widget in sync
        context.startService(
            Intent(context, WidgetUpdateService::class.java)
        )
    }
    
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Build RemoteViews (non-Compose, uses XML layout)
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_current_message)
        
        // Set on-click listeners for reactions
        setReactionClickListeners(context, remoteViews, appWidgetId)
        
        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
    
    private fun setReactionClickListeners(
        context: Context,
        remoteViews: RemoteViews,
        appWidgetId: Int
    ) {
        val emojis = listOf("❤️", "😊", "👍", "😂", "🎉")
        
        emojis.forEach { emoji ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                emoji.hashCode(),
                Intent(context, ReactionBroadcastReceiver::class.java).apply {
                    action = "ADD_REACTION"
                    putExtra("emoji", emoji)
                    putExtra("appWidgetId", appWidgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            remoteViews.setOnClickPendingIntent(
                getReactionButtonId(emoji),
                pendingIntent
            )
        }
    }
}
```

**widget_current_message.xml (RemoteViews Layout)**
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="@color/widget_bg">
    
    <!-- Author -->
    <TextView
        android:id="@+id/author_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textColor="@color/text_secondary" />
    
    <!-- Message Content -->
    <TextView
        android:id="@+id/message_content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textColor="@color/text_primary"
        android:layout_marginVertical="8dp" />
    
    <!-- Photo (if applicable) -->
    <ImageView
        android:id="@+id/message_photo"
        android:layout_width="match_parent"
        android:layout_height="100dp"
        android:scaleType="centerCrop"
        android:visibility="gone" />
    
    <!-- Photo Caption -->
    <TextView
        android:id="@+id/photo_caption"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textStyle="italic"
        android:visibility="gone" />
    
    <!-- Reactions Bar (scrollable) -->
    <HorizontalScrollView
        android:id="@+id/reactions_scroll"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp">
        
        <LinearLayout
            android:id="@+id/reactions_container"
            android:layout_width="wrap_content"
            android:layout_height="40dp"
            android:orientation="horizontal"
            android:gravity="center_vertical" />
    </HorizontalScrollView>
    
    <!-- Quick Action Buttons -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="8dp"
        android:gravity="space_around">
        
        <Button
            android:id="@+id/btn_react_heart"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:text="❤️"
            android:textSize="20sp" />
        
        <Button
            android:id="@+id/btn_react_smile"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:text="😊"
            android:textSize="20sp" />
        
        <Button
            android:id="@+id/btn_react_thumbsup"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:text="👍"
            android:textSize="20sp" />
        
        <Button
            android:id="@+id/btn_react_fire"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:text="🔥"
            android:textSize="20sp" />
    </LinearLayout>
</LinearLayout>
```

**widget_current_message_info.xml (Widget Metadata)**
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="100dp"
    android:updatePeriodMillis="1800000"
    android:previewImage="@drawable/widget_preview"
    android:initialLayout="@layout/widget_current_message"
    android:widgetCategory="home_screen"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="4"
    android:targetCellHeight="2" />
```

**ReactionBroadcastReceiver.kt**
```kotlin
class ReactionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val emoji = intent?.getStringExtra("emoji") ?: return
        val appWidgetId = intent.getIntExtra("appWidgetId", -1)
        
        // Add reaction to Firebase
        FirebaseSync.addReaction(emoji) { success ->
            if (success) {
                // Widget will update via Firebase listener
            }
        }
    }
}
```

---

### 3. Real-Time Firebase Sync

**WidgetUpdateService.kt**
```kotlin
class WidgetUpdateService : Service() {
    private val firebaseSync = FirebaseSync()
    private var currentMessageListener: ValueEventListener? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningToCurrentMessage()
        return START_STICKY
    }
    
    private fun startListeningToCurrentMessage() {
        currentMessageListener = firebaseSync.listenToCurrentMessage { message ->
            updateWidgets(message)
        }
    }
    
    private fun updateWidgets(message: Message) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(
                ComponentName(this, CurrentMessageWidget::class.java)
            )
        
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = RemoteViews(packageName, R.layout.widget_current_message)
            
            // Update text
            remoteViews.setTextViewText(R.id.message_content, message.content)
            remoteViews.setTextViewText(R.id.author_name, message.authorName)
            
            // Update photo if applicable
            if (message.type == "photo") {
                remoteViews.setImageViewUri(R.id.message_photo, message.photoUrl.toUri())
                remoteViews.setViewVisibility(R.id.message_photo, View.VISIBLE)
                remoteViews.setTextViewText(R.id.photo_caption, message.caption)
                remoteViews.setViewVisibility(R.id.photo_caption, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
                remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
            }
            
            // Update reactions
            updateReactionsDisplay(remoteViews, message.reactions)
            
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }
    
    private fun updateReactionsDisplay(
        remoteViews: RemoteViews,
        reactions: Map<String, List<String>>
    ) {
        val reactionsContainer = R.id.reactions_container
        remoteViews.removeAllViews(reactionsContainer)
        
        reactions.forEach { (emoji, userIds) ->
            val reactionText = "$emoji ${userIds.size}"
            remoteViews.addView(
                reactionsContainer,
                RemoteViews(packageName, R.layout.reaction_chip).apply {
                    setTextViewText(R.id.reaction_text, reactionText)
                }
            )
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

**FirebaseSync.kt**
```kotlin
object FirebaseSync {
    private val database = FirebaseDatabase.getInstance().reference
    
    fun listenToCurrentMessage(
        onMessage: (Message) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val message = snapshot.getValue(Message::class.java) ?: return
                onMessage(message)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSync", "Error: ${error.message}")
            }
        }
        
        database.child("shared").child("current_message")
            .addValueEventListener(listener)
        
        return listener
    }
    
    fun addReaction(emoji: String, onComplete: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reactionsRef = database.child("shared/current_message/reactions/$emoji")
        
        reactionsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val userIds = mutableData.value as? List<String> ?: emptyList()
                if (!userIds.contains(userId)) {
                    mutableData.value = userIds + userId
                }
                return Transaction.success(mutableData)
            }
            
            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                onComplete(error == null && committed)
            }
        })
    }
}
```

---

### 4. Widget Guide Screen

**WidgetGuideScreen.kt (Compose)**
```kotlin
@Composable
fun WidgetGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Add Widgets to Your Home Screen",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Step-by-step guide
        StepCard(
            number = 1,
            title = "Long-press on your home screen",
            description = "Hold your finger on an empty space"
        )
        
        StepCard(
            number = 2,
            title = "Select 'Widgets'",
            description = "Tap the widgets option"
        )
        
        StepCard(
            number = 3,
            title = "Find 'Couple Widget'",
            description = "Search for our app and select Current Message widget"
        )
        
        StepCard(
            number = 4,
            title = "Choose size (4x2 or 4x4)",
            description = "Pick your preferred widget size"
        )
        
        StepCard(
            number = 5,
            title = "Tap reactions directly from widget!",
            description = "No need to open the app"
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { /* Close guide */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Got it, dismiss this guide")
        }
    }
}

@Composable
fun StepCard(number: Int, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Blue, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$number",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

---

## Firebase Security Rules

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "shared": {
      "$resource": {
        ".read": "root.child('shared/settings/allowedUsers').child(auth.uid).exists()",
        ".write": "root.child('shared/settings/allowedUsers').child(auth.uid).exists()"
      }
    }
  }
}
```

---

## GitHub Actions Build Pipeline

**.github/workflows/build.yml**
```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build APK
      run: ./gradlew assembleDebug
    
    - name: Upload APK to artifacts
      uses: actions/upload-artifact@v3
      with:
        name: app-debug.apk
        path: app/build/outputs/apk/debug/app-debug.apk
    
    - name: Upload to Firebase App Distribution (optional)
      run: |
        ./gradlew appDistributionUploadDebug \
          --serviceCredentialsFile=${{ secrets.FIREBASE_CREDS_JSON }}
```

---

## Implementation Phases

### Phase 1: Core Setup
- [ ] Kotlin project init (Gradle + dependencies)
- [ ] Firebase setup (Realtime Database, Auth, Storage)
- [ ] GitHub Actions workflow
- [ ] Local build verification

### Phase 2: Authentication
- [ ] Google Sign-In implementation
- [ ] User creation in Firebase
- [ ] Login/Logout screens (Compose)
- [ ] Auth state persistence

### Phase 3: Widget Guide Screen
- [ ] Compose UI for widget guide
- [ ] Step-by-step instructions
- [ ] "Dismiss guide" option

### Phase 4: Current Message Widget (MVP)
- [ ] Widget provider setup
- [ ] RemoteViews layout
- [ ] Firebase listener service
- [ ] Display text/emoji messages
- [ ] Display photo + caption

### Phase 5: Reactions
- [ ] Broadcast receiver for reaction taps
- [ ] Add reaction to Firebase
- [ ] Display reactions in widget
- [ ] Real-time reaction updates

### Phase 6: Push Notifications
- [ ] FCM integration
- [ ] Send notifications when message/reaction posted
- [ ] Notification tap opens app

### Phase 7: Polish & Deployment
- [ ] Testing on multiple devices
- [ ] Performance optimization
- [ ] APK signing
- [ ] Deploy to GitHub Releases

---

## Dependencies (build.gradle.kts)

```gradle
dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core:1.10.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.2.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Google Auth
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.4.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

---

## AndroidManifest.xml (Key Declarations)

```xml
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.couplewidget">
    
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application>
        <!-- Main Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <!-- Current Message Widget -->
        <receiver
            android:name=".widgets.CurrentMessageWidget"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_current_message_info" />
        </receiver>
        
        <!-- Reaction Broadcast Receiver -->
        <receiver
            android:name=".widgets.ReactionBroadcastReceiver"
            android:exported="false" />
        
        <!-- Widget Update Service -->
        <service
            android:name=".service.WidgetUpdateService"
            android:exported="false" />
        
        <!-- FCM Service -->
        <service
            android:name=".service.FCMService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

---

## Testing Checklist

- [ ] User can log in with Google
- [ ] Non-allowed users are denied
- [ ] Widget displays current message in real-time
- [ ] Photos + captions display correctly
- [ ] Tap reaction emoji updates Firebase
- [ ] Other user sees reaction in real-time
- [ ] Reactions display count + avatars
- [ ] Message history shows last 30 days
- [ ] Old messages auto-delete
- [ ] FCM notifications received when app closed
- [ ] Widget supports 4x2 and 4x4 sizes
- [ ] Widget UI is responsive
- [ ] App works on Android 10+
- [ ] GitHub Actions builds APK successfully

---

## Deployment Checklist

1. **Setup Firebase:**
   - Create `shared/settings` doc with `allowedUsers: [uid1, uid2]`
   - Enable Realtime Database, Storage, Auth, Messaging
   - Set security rules above

2. **Sign APK:**
   ```bash
   ./gradlew bundleRelease
   ```

3. **Release on GitHub:**
   - Build APK via GitHub Actions
   - Download from artifacts
   - Test on both phones
   - Share download link or distribute via Firebase App Distribution

4. **Install on phones:**
   - Download APK
   - Enable "Unknown sources" on Android
   - Tap APK to install
   - Log in with Google
   - Add widgets to home screen
   - Test reactions

---

## Future Expansion

### History Widget
- Scrollable list of last 20 messages
- Tap to view full message
- Swipe to react

### Reactions Widget
- Show "who liked what"
- Track reaction trends

### Interactive Game Widget
- Simple game (tic-tac-toe, 2048, etc.)
- Turn-based multiplayer
- Real-time moves via Firebase

### Media Widget
- Photo gallery widget
- Swipe through recent photos

---

## Key Decisions & Rationale

| Decision | Why |
|----------|-----|
| **RemoteViews** over Compose for widget | Android widgets require RemoteViews; Compose support is limited |
| **Realtime Database** over Firestore | Lower latency, simpler real-time sync for widgets |
| **Minimal app UI** | Widgets are the primary interface; app is just login + guide |
| **GitHub Actions** | No need for local Android Studio; cloud build is faster |
| **Broadcast receivers** | Handle widget button clicks without opening app |
| **Service for sync** | Keep widgets updated even when app is closed |

