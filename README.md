# YT İndirici

Android ve Windows için hız odaklı, yerel çalışan medya kütüphanesi.

![Uygulama ikonu](assets/marka/uygulama-ikonu.svg)

> Proje geliştirme aşamasındadır. İlerleme, GitHub Issues ve taslak pull request üzerinden Türkçe olarak paylaşılır.

## İlk çalışan iskelet

- Ortak Türkçe Compose arayüzü
- Android uygulama modülü
- Windows masaüstü uygulama modülü
- Windows'ta yalnızca hedef disk seçimi
- Android'de uygulamaya özel medya alanı
- Android için otomatik yt-dlp Nightly güncelleme işçisi
- Kanal ve medya veri modelleri
- Android ve Windows için özgün uygulama ikonları
- Türkçe GitHub Actions derleme akışı

## Kullanılan teknoloji

- Kotlin
- Compose Multiplatform
- Android WorkManager
- youtubedl-android 0.18.1
- FFmpeg ve aria2c Android bileşenleri

## Yerel geliştirme

JDK 17 ve Gradle 8.14.2 kurulu olmalıdır.

```bash
gradle :desktopApp:run
gradle :androidApp:assembleDebug
```

## Yasal kullanım

Uygulama yalnızca kullanıcının indirme hakkına sahip olduğu içeriklerde kullanılmak üzere geliştirilmektedir. DRM veya erişim kontrollerini aşmaya yönelik özellikler kapsam dışıdır.
