# Avrasya → BAMOE 8 (KIE) Süreçleri — Java 21

Avrasya Workflow tasarımları BAMOE 8 / jBPM 7 (KIE Server) üzerinde **Java 21** ile çalışan süreçlere dönüştürüldü. Otomatik aktivitelerin (ProgramID) servis entegrasyonu yapıldı. Mainframe'e gidip BPM'e geri dönen asenkron akış da çalışır durumda.

| Süreç | Kaynak | Akış |
|---|---|---|
| `IH_DAB_TANZIM_FC` | `docs/kaynak/IH_DAB_TANZIM_FC.avrasya.xml` (ACLID 11777) | İhracat IBKBG Fircosoft süreci (aşağıda) |
| `EBR_BASIC_TEST` | `docs/kaynak/EBR_BASIC_TEST.avrasya.xml` (ACLID 11566) | Start → Referans Process Aciklama Ekle (54866) → **WebClient (269812): mainframe'e gider, BPM'e geri döner** → End |

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
| `ebr-basic-test-kjar` | `EBR_BASIC_TEST` BPMN'i, `sGK_sALL` modeli (bilinmeyen düğümler aynen korunur), testler |
| `ih-dab-tanzim-fc-service` | Spring Boot KIE Server (BAMOE 8 çalışma zamanı): iki container, entegrasyon REST uçları, **mainframe geri dönüş ucu**; OpenShift profili, Dockerfile |
| `avrasya-mainframe-gateway` | BPM'in program çağrılarını mainframe'e (z/OS Connect / CICS) iletir; asenkron programlarda sonucu BPM'e geri döndürür; geliştirme için simülatör içerir |

## Mainframe'e gidip BPM'e geri dönüş (EBR_BASIC_TEST / WebClient)

```
 BPM (KIE)                               Mainframe Gateway                   Mainframe
 ─────────                               ─────────────────                   ─────────
 Referans Process Aciklama Ekle ──POST /avrasya/54866──▶ senkron ──────────▶ program 54866
            ◀────────────── 200 {"data":{sGK_sWIP.status_code:"0"}} ◀──────┘
 WebClient ─────POST /avrasya/269812 (callbackUrl=.../geri-donus/{pid}/{wid})──▶
            ◀── 202 Accepted          (work item AÇIK, süreç DB'de bekler)
                                         arka planda ──────────────────────▶ program 269812
 /api/avrasya/geri-donus/{pid}/{wid} ◀── POST {"data":{...}} | {"serviceError":"..."}
   → work item tamamlanır → End          (Basic auth: 'mainframe' yetkisi)
```
- **BPM tarafı.** Program `avrasya.service.program.269812.mode=async` olarak ayarlı. Servis `202` dönerse de asenkron sayılır. Handler work item'ı tamamlamaz; süreç güvenli bekleme noktasında DB'ye yazılır. Bu yüzden geri dönüş hangi pod'a gelirse gelsin süreç devam eder.
- **Geri dönüş ucu.** `POST /api/avrasya/geri-donus/{processInstanceId}/{workItemId}` yalnızca `mainframe` (veya `admin`) yetkisiyle çağrılabilir. Gövde `{"data":{...}}` gelirse konteyner sürece yazılır. `{"serviceError":"..."}` gelirse iş, aktivitenin kuyruğundaki **Hata Kuyruğu** görevine düşer (WebClient için `WQ_1131`). Mainframe, work item'ı oluşturan transaction commit edilmeden dönerse uç kısa bir süre tekrar dener.
- **Mainframe BPM'i doğrudan çağırıyorsa** (MQ köprüsü, CICS web client vb.) gateway'e gerek yok. Sözleşme aynı: istekteki `callbackUrl`'e sonucu POST etmesi yeterli.
- **Gateway.** `gateway.connector=zos-connect` ile her program `gateway.programs.<id>.url` adresindeki z/OS Connect API'sine JSON olarak gider. Varsayılan `simulator` modu geliştirme içindir. Asenkron programlar `gateway.programs.<id>.mode=async` ile işaretlenir. Geri dönüş kimliği `GATEWAY_BPM_USERNAME` / `GATEWAY_BPM_PASSWORD` ile verilir.


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

- **JDK 21** ile derlenir ve çalışır (`maven.compiler.release=21`).
  - jBPM 7'nin varsayılan `mvel2` 2.4.x sürümü JDK 21'de kaldırılan `java.lang.Compiler`'a bağlı (`NoClassDefFoundError`). Parent POM, `mvel2`'yi 2.5.2.Final'a zorluyor; `kie-maven-plugin`'in kendi bağımlılığında da aynı override var.
  - Kalan yığın JDK 21'de testlerle doğrulandı: kjar derleme, Java dialect script'leri, H2/Hibernate kalıcılığı, Spring Boot 2.5 + KIE Server.
  - Spring Boot 2.5, Java 21'i resmi olarak listelemiyor. Kurum içi BAMOE 8 sürümünüzün JDK 21 destek beyanını ayrıca kontrol edin.

```bash
# kjar + handler'lar (BAMOE Business Central / KIE Server'a deploy için)
mvn clean install

# Spring Boot KIE Server + Mainframe Gateway dahil
mvn clean install -Pservice
java -jar avrasya-mainframe-gateway/target/avrasya-mainframe-gateway-1.0.0-SNAPSHOT.jar        # :8091 (simülatör)
java -jar ih-dab-tanzim-fc-service/target/ih-dab-tanzim-fc-service-1.0.0-SNAPSHOT.jar          # :8090

# EBR_BASIC_TEST: başlat ve durumu izle
curl -u entegrasyon:entegrasyon -H 'Content-Type: application/json' -d '{"sGK_sWID":{"musteri_no":"98765"}}' \
  http://localhost:8090/api/ebr-basic-test/surecler
curl -u entegrasyon:entegrasyon http://localhost:8090/api/ebr-basic-test/surecler/1
```
- **İki kjar, tek uygulama.** İki kjar aynı classpath'te olduğu için container'lar `KieContainerTanimlari` sınıfında KBase/KSession seçimiyle tanımlı (`kieserver.deployments[n]` ile kbase seçilemiyor). Yeni bir Avrasya süreci eklemek için kjar'ı pom'a ve bu sınıfa eklemek yeterli.

- **BAMOE 8 reposu ile derleme:** `mvn clean install -Dbamoe -Dkie.version=<BAMOE 8 kie sürümü>`. `-Dbamoe` verildiğinde `maven-central` profili kapanır; bu profil yalnızca JBoss reposundaki `.redhat` sürümlerini Central eşdeğerleriyle değiştiriyor.
  - Sadece Maven Central ile derlenecekse: `kie-maven-plugin` `xmlpull:xmlpull:1.2.0` istiyor ve bu sürüm Central'da yok. Bir kez `mvn install:install-file -Dfile=xmlpull-1.1.3.1.jar -DgroupId=xmlpull -DartifactId=xmlpull -Dversion=1.2.0 -Dpackaging=jar` çalıştırın.
- **Mevcut KIE Server / Business Central:** kjar'ı deploy edin, handler jar'ı kjar bağımlılığı olarak gelir. Servis adreslerini `-Davrasya.service.base-url=...` veya ortam değişkeniyle verin.
- **OpenShift:**
  - `SPRING_PROFILES_ACTIVE=openshift` ile PostgreSQL bağlantısı (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) ortam değişkenlerinden okunur. Pod başına `narayana.transaction-manager-id=${HOSTNAME}` ayarlanır ve jBPM executor açılır.
  - Runtime strategy `PER_PROCESS_INSTANCE`; pod'lar arasında JVM-yerel durum tutulmuyor.
  - Üretimde şemayı BAMOE DDL script'leriyle oluşturun (`HBM2DDL=validate`) ve kurumsal kimlik sağlayıcıyı (LDAP/OIDC) bağlayın. Geliştirme kullanıcıları (`wbadmin`, `entegrasyon`, `ali`) yalnızca `openshift` dışındaki profillerde tanımlı.
  - Mainframe geri dönüş adresi `AVRASYA_SERVICE_CALLBACK_URL` ile verilir; pod adresi değil, Service/Route adresi olmalı.
  - Geri dönüş ucunu çağıran teknik kullanıcıya kimlik sağlayıcıda `mainframe` yetkisi verilmeli.
  - Her iki uygulamanın Dockerfile'ı UBI9 OpenJDK 21 imajını kullanır.

## Testler

| Test | Kapsam |
|---|---|
| `AvrasyaServiceWorkItemHandlerTest` (7) | Gerçek HTTP stub'ına karşı handler: başarı, düz yanıt, HTTP 500, tanımsız program, ortam değişkeni adları, `202` ile asenkron bekleme (`callbackUrl` gönderilir), `mode=async` |
| `IhDabTanzimFcProcessTest` (9) | jBPM + H2 kalıcılık + gerçek task servisiyle senaryolar: mutlu yol, şubeye iade sonrası iptal, yetkili iadesi, rework döngüsü, portföy boş / ErtesiGun, ileri valör + UYUM, UYUM iptal, servis hatası → Tekrar Dene, servis hatası → Atla. Her görevin doğru kuyruğa düştüğü de doğrulanır |
| `EbrBasicTestProcessTest` (3) | WebClient mainframe'de beklerken süreç aktif kalır, geri dönüşle tamamlanır; mainframe hatası → `WQ_1131` Hata Kuyruğu → Tekrar Dene; Referans `status_code != 0` → `WQ_585` |
| `AvrasyaProgramControllerTest` (5) | Gateway: senkron program, asenkron program (`202`, ardından konteyner korunarak BPM'e geri dönüş), asenkron hata → `serviceError`, senkron hata → `502`, `callbackUrl` zorunluluğu |
| `IhDabTanzimFcServiceTest` (4) | Spring Boot KIE Server ayağa kalkar; süreç REST ile başlatılır; servisler gerçek HTTP handler ile stub'a gider; tüm görevler entegrasyon ucuyla tamamlanır; süreç `COMPLETED` olur. Ayrıca EBR_BASIC_TEST gerçek HTTP ile mainframe'e gidip (`202`) geri dönüş ucuyla BPM'e dönüyor ve `COMPLETED` oluyor; geri dönüş ucu `mainframe` yetkisi olmayana `403` dönüyor |
