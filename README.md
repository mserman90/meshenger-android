# MeshengerTR 🚀

**MeshengerTR**, hücresel ağ, internet veya herhangi bir merkezi sunucuya ihtiyaç duymadan yerel Wi-Fi, ağlar ve Bluetooth Mesh üzerinden doğrudan uçtan uca sesli/görüntülü arama yapabilen ve gelişmiş **Afet Kipi (Disaster Mode) & Enkaz Dinleme** mekanizmalarına sahip açık kaynaklı acil durum mesh iletişim uygulamasıdır.

[English Description below](#english-summary)

---

## 🌟 Öne Çıkan Özellikler (Key Features)

### 📞 1. Sunucusuz Sesli ve Görüntülü Arama (Serverless Voice & Video Calls)
- **Uçtan Uca Şifreleme (libsodium & WebRTC):** Herhangi bir hesap kaydı, telefon numarası veya sunucu olmadan doğrudan IP/QR kod eşleştirmesi ile arama yapma.
- **Çevrimdışı Çalışma (Off-Grid Capability):** Ev, iş yeri veya afet anındaki topluluk mesh ağlarında (Freifunk, Yggdrasil vb.) internet erişimi olmadan kesintisiz iletişim.

### 🚨 2. Afet Kipi & Acil Durum Yayın Sistemi (Disaster Mode & SOS Beacon)
- **Şebekesiz Acil Durum Yayını:** UDP Multicast (Port 9876) ve Bluetooth Mesh üzerinden konum, kan grubu, enkaz bilgisi ve tıbbi not yayınlama.
- **ISO 22324 Güvenlik Standartları:** Uluslararası acil durum renk ve sembol kodlaması:
  - 🟢 **SAFE / GÜVENDEYİM**
  - 🔴 **HELP / SOS (YARDIM İSTİYORUM)**
  - 🔵 **MEDICAL ASSISTANCE NEEDED (TIBBİ YARDIM İSTİYORUM)**
- **Sesli & Görsel İkazlar:** 3.5 kHz yüksek frekanslı Akustik Düdük (Acoustic Siren) ve Strobe Flaşör (Visual SOS Flash).
- **Yanlış Basım Koruması:** Afet kipi yayını kapalıyken acil durum butonlarının kilitli kalması ve etkileşimli uyarı mekanizması.

### 🎙️ 3. Enkaz Dinleme ve Ses Yükseltici (Rubble Audio Listener & Amplifier)
- **Frekans Ayrıştırma & Gürültü Filtreleme:** Donanımsal gürültü engelleme (`NoiseSuppressor` / `AutomaticGainControl`) ve 300Hz–3.4kHz insan sesi ve enkaz altı tıkırtı frekans süzgeci.
- **Dinamik Ses Yükseltme (Gain Boost):** Enkaz altından gelen cılız sesleri kulaklık/hoparlöre yüksek netlikle ileten **3x, 5x ve 10x (Maksimum)** kazanç seviyeleri.
- **Canlı Ses Seviyesi ve Tıkırtı Tespiti:** Canlı grafik çubuğu (`ProgressBar`) ve otomatik peak tıkırtı uyarısı (`⚠️ YÜKSEK SES / TIKIRTI ALGILANDI!`).
- **Çakışma Önleyici Ses Odağı:** Arama geldiğinde dinleme otomatik duraklatılır, arama bitince kaldığı yerden devam eder.

### 🔄 4. Çok Sıçramalı Mesh Aktarımı (Multi-hop Mesh Relay)
- **Menzil Genişletme:** Acil durum sinyallerini aradaki çevre cihazlar üzerinden otomatik aktararak kapsama alanını genişletme (`ttl = 5`, `hopCount`).
- **Döngü & Mükerrer Kayıt Engelleme:** `messageId` benzersiz kimliği, `senderDeviceId` genel anahtar doğrulaması ve 10 dakikalık önbellek koruması.

### 🎨 5. Uluslararası Çift Dilli Arayüz & Kullanıcı Rehberi (Bilingual UI & In-App Guide)
- **Çift Dilli (TR / EN) Navigasyon:** Tüm butonlar, sekmeler ve durum rozetleri çift dilli ve ISO 22324 standartlarıyla uyumlu.
- **Uygulama İçi Buton ve Ayarlar Rehberi:** Hakkında ekranında tüm eylem butonlarının ve ayar seçeneklerinin detaylı açıklamaları.
- **Doğrudan GitHub Güncelleme:** Hakkında ekranından GitHub Releases sayfasına tek tıkla erişim ve direkt APK indirme imkanı.

### 📱 6. %100 Geriye Dönük Uyumluluk (Backward Compatibility)
- **Geniş Cihaz Desteği:** Android 5.0 (API Level 21 - Lollipop) ve üzeri 10-12 yıllık eski telefonlardan en yeni Android 14+ cihazlara kadar tam performans.

---

## 📥 İndirme ve Güncelleme (Download & Updates)

En güncel **MeshengerTR** `.apk` sürümlerini doğrudan GitHub üzerindeki sürümler sayfasından indirebilirsiniz:

👉 **[MeshengerTR GitHub Releases (İndir / Update)](https://github.com/mserman90/meshenger-android/releases)**

---

## 📱 Ekran Görüntüleri (Screenshots)

<img src="graphical-assets/phone-screenshots/01_logo_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/02_contacts_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/03_ringing.png" width="170"> <img src="graphical-assets/phone-screenshots/04_call_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/05_qrcode_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/06_settings_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/07_address_management_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/08_history_4.4.5.png" width="170">

---

## <a id="english-summary"></a>🌐 English Summary

**MeshengerTR** is an open-source, serverless off-grid P2P communication and disaster emergency beacon system for Android.

### Features:
- **Serverless Peer-to-Peer Voice & Video Calls:** WebRTC & libsodium encrypted communication over local Wi-Fi or community mesh networks without internet or registration.
- **Disaster Mode (SOS Beacon):** Off-grid UDP Multicast (Port 9876) & Bluetooth Mesh broadcasting for status (`SAFE`, `HELP/SOS`, `MEDICAL`), blood group, medical notes, and rubble trap details.
- **Rubble Audio Listener & Amplifier:** Real-time microphone audio processing, noise suppression (`NoiseSuppressor`/`AGC`), bandpass filtering (300Hz-3.4kHz), 3x/5x/10x gain boost, and peak tapping detection.
- **Multi-hop Mesh Relay:** Extends broadcast coverage by forwarding emergency packets across intermediate mesh nodes with deduplication (`ttl = 5`, `senderDeviceId` self-loop prevention).
- **ISO 22324 Standards & Bilingual UI:** Fully bilingual (Turkish & English) interface with standardized safety color badges.
- **Direct Updates:** In-app one-click update link to GitHub Releases.
- **Backward Compatible:** Supports Android 5.0 (Lollipop) up to Android 14+.

---

## 📄 Lisans (License)

GNU General Public License v3.0 or later (GPL-3.0-or-later). Details can be found in the [LICENSE](LICENSE) file.

- **MeshengerTR Repository:** [https://github.com/mserman90/meshenger-android](https://github.com/mserman90/meshenger-android)
- **Original Meshenger Repository:** [https://github.com/meshenger-app/meshenger-android](https://github.com/meshenger-app/meshenger-android)
