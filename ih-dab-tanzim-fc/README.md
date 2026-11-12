# IH_DAB_TANZIM_FC — İhracat IBKBG Fircosoft Süreci (BAMOE 8)

Avrasya Workflow tasarımı `IH_DAB_TANZIM_FC` (ACLID 11777, CategoryID 116042), BAMOE 8 / jBPM 7 (KIE Server) üzerinde çalışan bir sürece dönüştürüldü. Otomatik aktivitelerin (ProgramID) servis entegrasyonu da yapıldı.

> ⚠️ **Kaynak XML kesik.** Yüklenen dosya (67 KB) `Fircosoft` bloğunun ortasında bitiyor (`DestinationNodeName="sExport_sALL/s`).
> `UYUM` ve `PROCESS` aktiviteleri ile blokların çoğundaki `<AllConnections>` (geçiş koşulları) dosyada yok.
> Akış; aktivitelerin `DataMapping` kaynaklarından, `IncomingConnectorCount` değerlerinden ve `EndCondition`'lardan çıkarıldı.
> Varsayımlar aşağıda [Varsayımlar](#varsayımlar) bölümünde listelendi. Tam XML gelirse koşullar birebir hizalanmalı.
> Kaynak dosya: `docs/kaynak/IH_DAB_TANZIM_FC.avrasya.xml`.

## Modüller

| Modül | İçerik |
|---|---|
| `ih-dab-tanzim-fc-handlers` | `AvrasyaServiceWorkItemHandler`: Avrasya ProgramID servislerini REST/JSON ile çağırır |
| `ih-dab-tanzim-fc-kjar` | 5 BPMN süreci, `sExport_sALL` veri modeli, `kie-deployment-descriptor.xml`, uçtan uca testler |
| `ih-dab-tanzim-fc-service` | Spring Boot KIE Server (BAMOE 8 çalışma zamanı) ve entegrasyon REST uçları; OpenShift profili, Dockerfile |

## Süreç yapısı

Her Avrasya bloğu ayrı, tekrar kullanılabilir bir BPMN süreci oldu ve call activity ile çağrılıyor. Bloğun `DLC` döngüsü BPMN'de geri dönen akışla kuruldu.

```
IH_DAB_TANZIM_FC
  Start → [Referans Process Aciklama Ekle] → ENCLAVE → End

IH_DAB_TANZIM_FC.ENCLAVE (döngü)
  ┌→ restart_req = IPTAL → [Islem Iptal] → End
  ├→ restart_req = IADE  → Subeye Iade → (tekrar ENCLAVE)
  └→ diğer               → DAB Tanzim Giris → IADE/IPTAL ise tekrar ENCLAVE, değilse End

IH_DAB_TANZIM_FC.SUBEYE_IADE
  DOKUMAN BEKLEME (return_code=00 olana kadar)

IH_DAB_TANZIM_FC.DAB_TANZIM_GIRIS
  Portfoy Belirleme1 → (ERTESI_GUN ise ErtesiGun) → DAB Tanzim Giris
    → [ONY_1_ONAY] → DAB Tanzim Yetkili 2.Onay → [ONY_2_ONAY] → [Is istek Update]
    → WC Bekleme → [WC Control] → [Is istek Update] → Fircosoft → End

IH_DAB_TANZIM_FC.FIRCOSOFT
  (qIleriValor doluysa TIMEOUT) → FIRCO → (UYUM ise UYUM) → PROCESS → End
```
`[...]` ile gösterilenler otomatik servis aktivitesidir. Diğerleri insan görevi ya da bekleme görevidir.

### Aktivite eşlemesi

| Avrasya aktivitesi | ProgramID | BPMN | Kuyruk (KIE grubu) | Bitiş koşulu / eşleme |
|---|---|---|---|---|
| Referans Process Aciklama Ekle | 116093 | Servis (`AvrasyaService`) | hata → `WQ_585` | `sWIP/status_code = "0"` |
| Islem Iptal | 116094 | Servis | hata → `WQ_585` | `return_code = "00"`; girişte `restart_req = ''` |
| DOKUMAN BEKLEME | 50911 | İnsan görevi | `qPortfoyAdi + qYarim` | `return_code = "00"`; girişte `restart_act = ''` |
| Portfoy Belirleme1 | 50911 | İnsan görevi | `WQ_587` | `qKuyrukAdi != ""`; girişte `sPRO/status_code = ''` |
| ErtesiGun | 50911 | İnsan görevi | `qKuyrukAdi` | `return_code = "00"` |
| DAB Tanzim Giris | 116081 | İnsan görevi | `qKuyrukAdi` | `rework = "N"`; `approval_code = G`, `disable_flag = ''` |
| ONY_1_ONAY | 116092 | Servis | hata → `WQ_585` | `return_code = "00"`; `HIST_APPROVAL = O1` |
| DAB Tanzim Yetkili 2.Onay | 116081 | İnsan görevi | `qPortfoyAdi + qYetkiliOnay` | `rework = "N"`; `disable_flag = Y`, `approval_code = 2`, `status_code = K` |
| ONY_2_ONAY | 116092 | Servis | hata → `WQ_585` | `HIST_APPROVAL = O2` |
| Is istek Update (×2) | 116095 | Servis | hata → `WQ_585` | `return_code = "00"` |
| WC Bekleme | 50911 | İnsan görevi | `WQ_742` | TRUE |
| WC Control | 179348 | Servis | hata → `WQ_585` | `caller_application = PESINMAL`, `approval_flag = ''`, `rework = ''` |
| TIMEOUT | 50911 | İnsan görevi | `WQ_1361` | `qdetermine_portfolio = qPortfoyAdi + qIleriValor` |
| FIRCO | 50911 | İnsan görevi | `qdetermine_portfolio` | TRUE |
| UYUM *(XML'de yok)* | ? | İnsan görevi | `WQ_UYUM` | varsayım |
| PROCESS *(XML'de yok)* | ? | İnsan görevi | `qdetermine_portfolio` | varsayım |

- **Avrasya `EndCondition` → BPMN:** İnsan görevinde koşul sağlanmazsa görev tekrar açılır (Avrasya DLC davranışı). Otomatik aktivitede koşul sağlanmazsa ya da teknik hata olursa iş **Hata Kuyruğu** görevine düşer. Bu görev, aktivitenin WorkQueue'su olan `WQ_585` grubundadır ve `karar` çıktısı alır: `TEKRAR` (varsayılan) veya `ATLA`.
- **`ValueType="S"` / `"C"` eşlemeleri** aktivitenin giriş script'ine (onEntry) taşındı. `C` eşlemesi alanları birleştirir (`Surec.birlestir`).
- **Konteyner:** `sExport_sALL` tek bir `exportAll` süreç değişkeninde tutuluyor. JSON alan adları Avrasya düğüm adlarıyla birebir aynı (`sExport_sWID/job_req_unit_num` gibi).

## Varsayımlar

Bu varsayımlar kesik XML yüzünden zorunlu oldu. Hepsi `Surec.java` ve BPMN koşullarında tek yerde duruyor.

1. **Karar alanı.** Kesik dosyada bağlantı koşulları olmadığı için insan görevlerinin kararı yeni bir `sExport_sNAV/action` alanıyla taşınıyor. Değerler: `ONAY`, `IADE`, `IPTAL`, `ERTESI_GUN`, `UYUM`. Boş bırakılırsa varsayılan yol izlenir (ONAY / Temiz).
2. **ENCLAVE yönlendirmesi.** `IADE` ya da `IPTAL` kararı `sNAV/restart_req`'e yazılır. ENCLAVE bu alana göre `Islem Iptal` ya da `Subeye Iade` yolunu seçer. Avrasya'daki `Islem Iptal` girişindeki `restart_req = ''` eşlemesi bu yorumla uyumlu.
3. **ErtesiGun dalı** `Portfoy Belirleme1` adımında `action = ERTESI_GUN` ile seçilir.
4. **TIMEOUT dalı** `qIleriValor` doluysa (ileri valörlü işlem) çalışır.
5. **UYUM / PROCESS** insan görevi olarak modellendi (kuyruklar: `WQ_UYUM` / `qdetermine_portfolio`).
6. **Akış sırası.** Avrasya'da paralel çıkan yan dallar (ONY_x, Is istek Update) BPMN'de sıralı çalışıyor; sonuç aynı, akış deterministik.
7. **Bekleme görevleri.** Bekleme görevi (50911) `returnCode` göndermeden tamamlanırsa `return_code = "00"` sayılır.
8. **Ana süreç sırası** `Start → Referans → ENCLAVE` olarak kabul edildi (konum ve eşleme bilgisine göre).

## Servis entegrasyonu

Otomatik aktiviteler `AvrasyaService` work item handler'ı ile çağrılıyor. Handler `kie-deployment-descriptor.xml` içinde kayıtlı (`mvel`).

```
POST <url>
Content-Type: application/json
X-Correlation-Id: <processInstanceId>-<workItemId>
Authorization: <avrasya.service.auth-header>   (tanımlıysa)

{"programId":"116093","activityName":"Referans Process Aciklama Ekle",
 "processInstanceId":1,"workItemId":7,"data":{ ...sExport_sALL... }}
```
- Yanıt `2xx` olmalı. Gövde `{"data":{...}}` ya da doğrudan konteyner JSON'u olabilir. Dönen konteyner sürece geri yazılır (Avrasya DDC).
- Handler istisna fırlatmaz. Teknik hatayı `ServiceError` olarak döndürür; bitiş koşulu sağlanmayınca iş Hata Kuyruğu'na düşer.
- Adres çözümleme sırası:
  1. `avrasya.service.program.<ProgramID>.url`
  2. `AVRASYA_SERVICE_PROGRAM_<ProgramID>_URL`
  3. `avrasya.service.base-url` + `/<ProgramID>`

  Zaman aşımı `avrasya.service.timeout-ms` (varsayılan 30000). Ayarlar System property, ortam değişkeni ya da Spring `application.properties` ile verilebilir.

### Entegrasyon REST uçları (servis modülü)

Süreç, iş isteği kimliğiyle KIE correlation key olarak eşleniyor. Anahtar `job_req_unit_num : job_req_cust_num : job_req_doc_code : job_req_seq_num` biçiminde. Dış sistemlerin process instance id bilmesi gerekmez.

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/api/ih-dab-tanzim-fc/surecler` | Süreci başlatır. Gövde: `sExport_sALL` JSON |
| GET | `/api/ih-dab-tanzim-fc/surecler/{sube}/{musteri}/{tip}/{sira}` | Durum, aktif görevler (alt süreçler dahil), konteyner |
| POST | `/api/ih-dab-tanzim-fc/surecler/{sube}/{musteri}/{tip}/{sira}/gorevler/{gorevAdi}/tamamla` | Bekleyen görevi tamamlar. Gövde (tümü isteğe bağlı): `{"action","rework","kuyrukAdi","returnCode","karar"}` |

Fircosoft sonucu (`FIRCO`), World-Check (`WC Bekleme`), zamanlayıcı (`TIMEOUT`, `ErtesiGun`) ve şube sistemi (`DOKUMAN BEKLEME`) bu uç üzerinden süreci ilerletir. İsteği yapan kullanıcı görevin kuyruk grubunda olmalı. Standart KIE Server REST API'si de `/rest/server` altında açık.

Örnek:
```bash
curl -u entegrasyon:entegrasyon -H 'Content-Type: application/json' \
  -d '{"sExport_sWID":{"job_req_unit_num":"1234","job_req_cust_num":"98765","job_req_doc_code":"IHR","job_req_seq_num":"7"},
       "sExport_sQUE":{"qPortfoyAdi":"PRT","qYetkiliOnay":"_YO","qYarim":"_SB","qIleriValor":""}}' \
  http://localhost:8090/api/ih-dab-tanzim-fc/surecler

curl -u entegrasyon:entegrasyon -H 'Content-Type: application/json' -d '{"action":"UYUM"}' \
  "http://localhost:8090/api/ih-dab-tanzim-fc/surecler/1234/98765/IHR/7/gorevler/FIRCO/tamamla"
```

## Derleme ve çalıştırma

- **JDK 11 veya 17** gerekir. BAMOE 8'in desteklediği sürümler bunlar; jBPM 7'nin MVEL'i JDK 21'de çalışmıyor.

```bash
# kjar + handler'lar (BAMOE Business Central / KIE Server'a deploy için)
mvn clean install

# Spring Boot KIE Server dahil
mvn clean install -Pservice
java -jar ih-dab-tanzim-fc-service/target/ih-dab-tanzim-fc-service-1.0.0-SNAPSHOT.jar
```

- **BAMOE 8 reposu ile derleme:** `mvn clean install -Dbamoe -Dkie.version=<BAMOE 8 kie sürümü>`. `-Dbamoe` verildiğinde `maven-central` profili kapanır; bu profil yalnızca JBoss reposundaki `.redhat` sürümlerini Central eşdeğerleriyle değiştiriyor.
  - Sadece Maven Central ile derlenecekse: `kie-maven-plugin` `xmlpull:xmlpull:1.2.0` istiyor ve bu sürüm Central'da yok. Bir kez `mvn install:install-file -Dfile=xmlpull-1.1.3.1.jar -DgroupId=xmlpull -DartifactId=xmlpull -Dversion=1.2.0 -Dpackaging=jar` çalıştırın.
- **Mevcut KIE Server / Business Central:** kjar'ı deploy edin, handler jar'ı kjar bağımlılığı olarak gelir. Servis adreslerini `-Davrasya.service.base-url=...` veya ortam değişkeniyle verin.
- **OpenShift:**
  - `SPRING_PROFILES_ACTIVE=openshift` ile PostgreSQL bağlantısı (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) ortam değişkenlerinden okunur. Pod başına `narayana.transaction-manager-id=${HOSTNAME}` ayarlanır ve jBPM executor açılır.
  - Runtime strategy `PER_PROCESS_INSTANCE`; pod'lar arasında JVM-yerel durum tutulmuyor.
  - Üretimde şemayı BAMOE DDL script'leriyle oluşturun (`HBM2DDL=validate`) ve kurumsal kimlik sağlayıcıyı (LDAP/OIDC) bağlayın. Geliştirme kullanıcıları (`wbadmin`, `entegrasyon`, `ali`) yalnızca `openshift` dışındaki profillerde tanımlı.
  - `ih-dab-tanzim-fc-service/Dockerfile` UBI8 OpenJDK 17 imajını kullanır.

## Testler

| Test | Kapsam |
|---|---|
| `AvrasyaServiceWorkItemHandlerTest` (5) | Gerçek HTTP stub'ına karşı handler: başarı, düz yanıt, HTTP 500, tanımsız program, ortam değişkeni adları |
| `IhDabTanzimFcProcessTest` (9) | jBPM + H2 kalıcılık + gerçek task servisiyle senaryolar: mutlu yol, şubeye iade sonrası iptal, yetkili iadesi, rework döngüsü, portföy boş / ErtesiGun, ileri valör + UYUM, UYUM iptal, servis hatası → Tekrar Dene, servis hatası → Atla. Her görevin doğru kuyruğa düştüğü de doğrulanır |
| `IhDabTanzimFcServiceTest` (2) | Spring Boot KIE Server ayağa kalkar; süreç REST ile başlatılır; servisler gerçek HTTP handler ile stub'a gider; tüm görevler entegrasyon ucuyla tamamlanır; süreç `COMPLETED` olur |
