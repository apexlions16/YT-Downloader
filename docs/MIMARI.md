# Mimari

## Ana hedef

Uygulama, videoyu bir sunucuya uğratmadan doğrudan kullanıcının cihazına aktarır. Hız, düşük bekleme süresi ve gereksiz dönüştürmeden kaçınma temel önceliktir.

## Modüller

### `sharedUI`

Android ve Windows tarafından paylaşılan Türkçe arayüzü, veri modellerini ve ortak yardımcıları içerir.

### `androidApp`

- Uygulamaya özel medya depolama alanını kullanır.
- Kullanıcıdan genel depolama izni istemez.
- yt-dlp, FFmpeg ve aria2c bileşenlerini başlatır.
- Uygulama açılırken ve WorkManager üzerinden belirli aralıklarla Nightly güncellemesi denetler.

### `desktopApp`

- Windows disklerini listeler.
- Kullanıcı yalnızca hedef diski seçer; klasör seçmesi gerekmez.
- Seçilen diskte `YT İndirici Kütüphanesi` klasörü kullanılır.
- Seçim yerel kullanıcı ayarlarında saklanır.

## Depolama yaklaşımı

### Android

Medya, uygulamanın özel alanında tutulur. İlk prototipte Android'in uygulamaya özel dizini kullanılır. Sonraki aşamada parçalı AES-256-GCM şifreleme ve Media3 için özel veri kaynağı eklenecektir.

### Windows

Kullanıcı C:, D:, E: gibi kullanılabilir disklerden birini seçer. Uygulama seçilen diskin kökünde kendi kütüphane klasörünü oluşturur. Sistem diski varsayılan seçilmez; ilk açılışta en fazla boş alana sahip uygun disk önerilir.

## yt-dlp güncellemesi

Android tarafında youtubedl-android kitaplığının Nightly güncelleme kanalı kullanılır. Güncelleme:

1. Uygulama açıldığında tek seferlik denetlenir.
2. Ağ bağlantısı varken altı saatte bir WorkManager tarafından denetlenir.
3. Güncelleme işlemi başarısız olursa mevcut çalışan sürüm korunur.

Windows tarafında imza ve SHA-256 doğrulamalı ikili güncelleyici sonraki geliştirme aşamasında tamamlanacaktır.

## Hız motoru yol haritası

- DASH/HLS parçalarını uyarlanabilir eşzamanlı indirme
- 4, 8, 12 ve 16 parçalı hız profilleri
- İndirme sırasında disk yazma ve bağlantı ölçümü
- Yeniden kodlama yerine mümkün olduğunda remux
- Video, ses, kapak ve metadata işlerini paralel yürütme
- İndirme sırasında uygulama özel alanına akış tabanlı şifreleme
