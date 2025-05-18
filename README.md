# Dijital Çizim Tahtası ve İşbirliği Uygulaması

Bu proje, Android üzerinde çalışan bir dijital çizim tahtası uygulaması ve bu çizimleri kullanıcı bazlı kaydedip yüklemek için Python Flask tabanlı bir backend sunucusundan oluşmaktadır. Kullanıcıların serbest çizim yapmasına, metin eklemesine, çizimlerini PDF olarak dışa aktarmasına ve (temel düzeyde) aynı çizim tahtası üzerinde farklı zamanlarda çalışmasına olanak tanır.

## 🎯 Projenin Amacı (Ödev Kapsamı)

Bu uygulama aşağıdaki temel işlevleri yerine getirmek üzere geliştirilmiştir:
* Öğrencilerin dokunmatik veya kalemle çizim yapabilmesi ve not alabilmesi.
* Metin tabanlı notların eklenebilmesi.
* Kullanıcıların çalışmalarını kaydedip farklı zamanlarda tekrar yükleyebilmesi (temel işbirliği).
* Çizim tahtası oturumlarının PDF olarak kaydedilip paylaşılabilmesi.

## ✨ Temel Özellikler

* **Serbest Çizim:** Farklı renk ve kalınlıklarda çizim yapabilme.
* **Renk Paleti:** Siyah, kırmızı, mavi, yeşil gibi temel renkler.
* **Kalınlık Ayarı:** Fırça ve silgi için ayarlanabilir kalınlık.
* **Silgi Aracı:** Çizimleri silme (çift tıklama ile tüm sayfayı temizleme).
* **Metin Ekleme:** Çizim alanına klavye ile metin ekleyebilme.
* **Kullanıcı Bazlı Kaydetme/Yükleme:** Her kullanıcıya özel bir "User ID" ile çizimleri Flask sunucusuna kaydetme ve daha sonra bu ID ile geri yükleme.
* **PDF Export:** Mevcut çizim tahtasını PDF dosyası olarak dışa aktarma ve paylaşma.

## 🛠️ Kullanılan Teknolojiler

* **Android Uygulaması:**
    * Dil: Kotlin
    * Mimarî: (Belirtilmediyse boş bırakılabilir veya MVVM vb. yazılabilir)
    * Temel Kütüphaneler:
        * OkHttp (Ağ istekleri için)
        * Gson (JSON işleme için)
        * AndroidX Kütüphaneleri (AppCompat, ConstraintLayout vb.)
* **Backend Sunucusu:**
    * Dil: Python
    * Framework: Flask
* **Versiyon Kontrol:** Git & GitHub

## 🚀 Kurulum ve Çalıştırma

Projeyi yerel makinenizde kurmak ve çalıştırmak için aşağıdaki adımları izleyin.

### Ön Gereksinimler

* Android Studio (Arctic Fox veya daha yenisi önerilir)
* Android SDK (API Seviyesi 30+ önerilir, Minimum SDK 23)
* Python 3.7+
* pip (Python paket yöneticisi)
* Git

### 1. Projeyi GitHub'dan Klonlama

Terminali açın ve projenin saklanacağı bir dizine gidin. Ardından aşağıdaki komutu çalıştırın:

```bash
git clone [https://www.google.com/search?q=https://github.com/Taylan361/CizimUygulamasiGitHub.git](https://www.google.com/search?q=https://github.com/Taylan361/CizimUygulamasiGitHub.git)
cd CizimUygulamasiGitHub
