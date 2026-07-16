# Bmobil ve BPC

Android ve Windows için hız odaklı, tamamen Türkçe YouTube medya indirme ve uygulama içi oynatma sistemi.

![Uygulama ikonu](assets/marka/uygulama-ikonu.svg)

> Uygulamalar yalnızca indirme hakkına sahip olduğunuz içerikler için kullanılmalıdır. DRM, ücretli içerik veya erişim kontrollerini aşan özellikler kapsam dışıdır.

## 0.3.0 dağıtımları

| Uygulama | Platform | Depolama |
|---|---|---|
| **Bmobil** | Android | AES-256-GCM şifreli uygulama kütüphanesi |
| **Bmobil Developer** | Android | `İndirilenler/Bmobil Developer` klasörüne açık medya |
| **BPC** | Windows | Seçilen diskte AES-256-GCM şifreli kütüphane |
| **BPC Developer** | Windows | Seçilen diskte `BPC Developer İndirmeleri` klasörüne açık medya |

Normal ve Developer sürümleri ayrı uygulama kimliklerine/kurulum adlarına sahiptir ve yan yana kurulabilir.

## Oynatıcı

### Android

- Media3/ExoPlayer tabanlı gerçek oynatıcı
- Oynat/duraklat ve zaman çizgisi
- ±10 saniye sarma
- Tam ekran
- 0.75×–2× oynatma hızı
- İndirilen ses/dublaj parçalarını seçme
- İndirilen altyazıyı seçme veya kapatma

### Windows

- Paketli MPV oynatıcı
- Türkçe kontrol penceresi
- Oynat/duraklat, ±10 saniye, başa dön ve tam ekran
- Ses seviyesi ve oynatma hızı
- Belirli ses/dublaj parçasını seçme
- Belirli altyazıyı seçme veya kapatma

## İndirme özellikleri

- Gerçek yt-dlp metadata analizi
- Kaynağın sunduğu çözünürlük, FPS, kodek ve boyut seçenekleri
- 4–16 eşzamanlı parçalı Turbo indirme
- MP4 ve MKV video
- M4A, Opus, MP3, OGG Vorbis, FLAC ve WAV ses
- Birden fazla dublaj/ses parçasını seçme
- Yayıncı ve otomatik altyazıları seçme
- Seçilen parçaları tek MKV dosyasına gömme
- Canlı yüzde, hız, kalan süre ve iptal
- Kanal profilleri ve metadata kütüphanesi

## Windows çevrimdışı motor başlangıcı

BPC ve BPC Developer kurulum paketleri aşağıdaki bileşenleri kendi içinde taşır:

- yt-dlp Nightly
- FFmpeg
- FFprobe
- Deno
- MPV

Bu nedenle ilk analiz/indirme internet üzerinden motor kurulumuna bağlı değildir. Güncelleme denetimi başarısız olursa paketli çalışan sürüm korunur.

## Derleme

JDK 17 ve Gradle 8.14.2 gereklidir.

```bash
# Android

gradle :androidApp:assembleNormalRelease
gradle :androidApp:assembleDeveloperRelease

# Windows normal

gradle :desktopApp:packageMsi :desktopApp:packageExe

# Windows Developer

gradle :desktopApp:packageMsi :desktopApp:packageExe -PdeveloperSurumu=true
```

GitHub Actions dört ürünü derler. `main` dalındaki başarılı 0.3.0 derlemesi otomatik GitHub Release oluşturur.

## Güvenlik sınırı

Şifreli Bmobil ve BPC sürümleri medya dosyalarını normal dosya yöneticilerinden gizlemek ve doğrudan açılmasını zorlaştırmak için cihaz anahtarlarıyla AES-256-GCM kullanır. Bu, lisans sunuculu Widevine benzeri ticari DRM değildir ve root/yönetici erişimine karşı mutlak koruma garanti etmez.
