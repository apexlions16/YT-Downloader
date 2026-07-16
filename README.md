# YT İndirici

Android ve Windows için hız odaklı, tamamen Türkçe, yerel çalışan medya indirme ve uygulama içi kütüphane uygulaması.

![Uygulama ikonu](assets/marka/uygulama-ikonu.svg)

> Uygulama yalnızca indirme hakkına sahip olduğunuz içerikler için kullanılmalıdır. DRM, üyelik, ücretli içerik veya erişim kontrollerini aşan özellikler kapsam dışıdır.

## 0.2.0 özellikleri

### Hız ve indirme motoru

- Android ve Windows üzerinde doğrudan cihazdan indirme
- Bağlantı ve cihaz durumuna göre 4–16 eşzamanlı parça
- Dengeli, Hızlı, Turbo, Azami ve Otomatik hız profilleri
- Canlı yüzde, hız, kalan süre ve iptal
- Yeniden kodlama gerekmeyen durumlarda hızlı birleştirme
- Nightly yt-dlp otomatik güncellemesi
- Windows'ta FFmpeg, FFprobe ve Deno bileşenlerini otomatik hazırlama

### Format seçenekleri

- Videoda kaynağın sunduğu 144p–4K ve üzeri çözünürlükler
- Kaynağın sunduğu 30/60 FPS seçenekleri
- MP4 ve MKV video çıktıları
- M4A, Opus, MP3, OGG Vorbis, FLAC ve WAV ses çıktıları
- Tahmini boyut, kodek, çözünürlük ve FPS bilgileri

### Kütüphane

- İçerikleri kanal profillerine göre otomatik gruplama
- Başlık, kanal, kapak, süre, format ve boyut metadata'sı
- Uygulama içi oynatma
- Android'de Android Keystore korumalı AES-256-GCM medya deposu
- Windows'ta DPAPI korumalı AES-256-GCM medya deposu
- Açık medya dosyalarını normal İndirilenler klasörüne çıkarmama

### Platform özellikleri

#### Android

- YouTube paylaşım menüsünden bağlantı alma
- Arka plan indirme bildirimi
- Media3/ExoPlayer uygulama içi oynatıcı
- Uygulamaya özel dahili depolama
- ARM64, ARM32, x86, x86_64 ve evrensel APK

#### Windows

- Hedef diski seçme
- Seçilen diskte `YT İndirici Kütüphanesi` oluşturma
- MSI ve EXE kurulum paketleri
- Gömülü JavaFX oynatıcı
- yt-dlp/FFmpeg/Deno otomatik motor kurulumu

## Kullanılan teknoloji

- Kotlin 2.1.21
- Compose Multiplatform 1.8.2
- Android Media3
- Android WorkManager
- youtubedl-android 0.18.1
- FFmpeg ve aria2c
- JavaFX Media
- Windows DPAPI ve JNA
- Kotlin Serialization

## Derleme

JDK 17 ve Gradle 8.14.2 gereklidir.

```bash
gradle :androidApp:assembleRelease
gradle :desktopApp:packageMsi
gradle :desktopApp:packageExe
```

Her dal güncellemesinde GitHub Actions otomatik olarak Android APK, Windows MSI ve Windows EXE paketlerini üretir. Derleme adımları ve günlüklar tamamen Türkçedir.

## Güvenlik sınırı

Uygulamadaki şifreleme, indirilen dosyaların normal dosya yöneticileri ve diğer uygulamalar tarafından doğrudan kullanılmasını zorlaştırır. Bu sistem Widevine gibi lisans sunuculu ticari DRM değildir; root, yönetici erişimi veya uygulama tersine mühendisliğine karşı mutlak koruma garanti etmez.
