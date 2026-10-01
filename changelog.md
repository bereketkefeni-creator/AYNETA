## Feature 1 — Voice trigger + Speech-to-Text

Add a full-screen tap target (Compose) that starts listening — don't rely on small buttons. Cloud Speech-to-Text (recommended for reliable Amharic): stream mic audio via `AudioRecord` to the REST/gRPC API with `languageCode = "am-ET"` and `alternativeLanguageCodes = ["en-US"]`. Use Retrofit/OkHttp:

```kotlin
// build.gradle.kts
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
```

Wrap the whole thing in a `SpeechToTextRepository` interface so you can swap A ↔ B without touching the ViewModel.
