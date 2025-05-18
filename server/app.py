import os
import json
from flask import Flask, request, jsonify, abort

app = Flask(__name__)

# Projenin çalıştığı ana dizini al (app.py dosyasının olduğu yer)
BASE_DIR = os.path.abspath(os.path.dirname(__file__))

# Çizimlerin saklanacağı klasör (app.py dosyasına göre göreceli)
# Bu, app.py'nin bulunduğu dizinde bir "user_drawings" klasörü oluşturur/kullanır.
USER_DRAWINGS_DIR = os.path.join(BASE_DIR, "user_drawings")

# Klasörün var olup olmadığını kontrol et ve yoksa oluştur
if not os.path.exists(USER_DRAWINGS_DIR):
    try:
        os.makedirs(USER_DRAWINGS_DIR)
        print(f"'{USER_DRAWINGS_DIR}' klasörü başarıyla oluşturuldu.")
    except OSError as e:
        print(f"HATA: '{USER_DRAWINGS_DIR}' klasörü oluşturulamadı: {e}")
        # Klasör oluşturulamazsa ciddi bir sorun var demektir.
        # Uygulamanın bu durumda devam etmesi sorunlu olabilir.
else:
    print(f"'{USER_DRAWINGS_DIR}' klasörü zaten mevcut.")


@app.route('/drawing/<user_id>', methods=['POST'])
def save_drawing(user_id):
    print(f"==> POST İsteği: /drawing/{user_id}")
    
    if not user_id or not user_id.strip():
        print("HATA: User ID boş veya geçersiz.")
        return jsonify({"error": "User ID cannot be empty"}), 400

    if not request.is_json:
        print("HATA: İstek gövdesi JSON formatında değil.")
        return jsonify({"error": "Request body must be JSON"}), 400
    
    try:
        data = request.get_json()
    except Exception as e:
        print(f"HATA: JSON parse edilemedi: {e}")
        return jsonify({"error": f"Could not parse JSON data: {e}"}), 400
        
    # Alınan JSON verisini logla (çok büyük olabilir, dikkatli kullanın)
    # print(f"Alınan Veri ({user_id}): {json.dumps(data, indent=2)}") 
    
    if 'elements' not in data: # Temel bir veri yapısı kontrolü
        print("HATA: JSON verisinde 'elements' anahtarı bulunmuyor.")
        return jsonify({"error": "Invalid drawing data structure, 'elements' key missing."}), 400

    # User ID'de dosya sistemi için geçersiz karakterler olmamasına dikkat edin.
    # Güvenlik için user_id'yi sanitize etmek iyi bir pratiktir.
    # Şimdilik doğrudan kullanıyoruz.
    filename = os.path.join(USER_DRAWINGS_DIR, f"{user_id}.json")
    
    try:
        with open(filename, 'w') as f:
            json.dump(data, f, indent=2) # indent=2 ile JSON dosyası daha okunaklı olur
        print(f"BAŞARILI: {user_id} için çizim '{filename}' dosyasına kaydedildi.")
        return jsonify({"message": f"Drawing for user '{user_id}' saved successfully."}), 200
    except IOError as e:
        print(f"HATA: Çizim kaydedilemedi. IOError: {e}")
        return jsonify({"error": f"Could not save drawing to file. Server IO Error: {e}"}), 500
    except Exception as e:
        print(f"HATA: Çizim kaydı sırasında beklenmedik bir hata oluştu: {e}")
        return jsonify({"error": f"An unexpected error occurred while saving the drawing: {e}"}), 500


@app.route('/drawing/<user_id>', methods=['GET'])
def load_drawing(user_id):
    print(f"==> GET İsteği: /drawing/{user_id}")

    if not user_id or not user_id.strip():
        print("HATA: User ID boş veya geçersiz.")
        return jsonify({"error": "User ID cannot be empty"}), 400

    filename = os.path.join(USER_DRAWINGS_DIR, f"{user_id}.json")
    
    if not os.path.exists(filename):
        print(f"BİLGİ: {user_id} için çizim dosyası ('{filename}') bulunamadı. Boş çizim döndürülüyor.")
        # İstemci tarafının (Android) null veya hata yerine boş bir canvas ile başlaması için
        # boş bir "elements" listesi içeren bir yapı dönelim.
        return jsonify({"elements": []}), 200 # 200 OK ile boş veri.
                                            # Alternatif olarak 404 de döndürebilirsiniz:
                                            # abort(404, description=f"Drawing not found for user '{user_id}'.")
    try:
        with open(filename, 'r') as f:
            data = json.load(f)
        print(f"BAŞARILI: {user_id} için çizim '{filename}' dosyasından yüklendi.")
        return jsonify(data), 200
    except IOError as e:
        print(f"HATA: Çizim yüklenemedi. IOError: {e}")
        return jsonify({"error": f"Could not load drawing from file. Server IO Error: {e}"}), 500
    except json.JSONDecodeError as e:
        # Eğer JSON dosyası bozuksa bu hata alınır.
        print(f"HATA: Kayıtlı çizim dosyasında ('{filename}') geçersiz JSON formatı. JSONDecodeError: {e}")
        # Bozuk veri yerine boş bir çizim göndermek istemci tarafında daha iyi bir kullanıcı deneyimi sunabilir.
        # Veya spesifik bir hata kodu ile istemciyi bilgilendirebilirsiniz.
        return jsonify({"error": f"Invalid JSON format in stored drawing for user '{user_id}'. Corrupted data.", "elements": []}), 500
    except Exception as e:
        print(f"HATA: Çizim yüklenirken beklenmedik bir hata oluştu: {e}")
        return jsonify({"error": f"An unexpected error occurred while loading the drawing: {e}"}), 500


@app.route('/')
def index():
    print("==> GET İsteği: / (Kök dizin)")
    return "Flask Çizim Sunucusu Aktif ve Çalışıyor! User ID ile /drawing/&lt;user_id&gt; endpoint'lerini kullanın."


if __name__ == '__main__':
    # Android emülatöründen veya aynı ağdaki diğer cihazların (telefon vb.)
    # Flask sunucusuna erişebilmesi için host='0.0.0.0' olarak ayarlanması önemlidir.
    # Port 5000 standart Flask portudur, eğer bu port başka bir uygulama tarafından kullanılıyorsa değiştirebilirsiniz.
    host_ip = '0.0.0.0'
    port_num = 5000
    print(f"Flask sunucusu başlatılıyor: http://{host_ip}:{port_num}")
    print(f"Android cihazınızdan veya emülatörden erişmek için kendi bilgisayarınızın yerel ağ IP adresini kullanın.")
    print(f"Örneğin: http://192.168.1.XXX:{port_num}/drawing/testuser")
    print(f"Çizimler '{USER_DRAWINGS_DIR}' klasörüne kaydedilecek/okunacak.")
    app.run(debug=True, host=host_ip, port=port_num)
    # debug=True geliştirme sırasında kullanışlıdır, ancak canlı (production) ortamda False olmalıdır.
    print("Flask sunucusu durduruldu.")