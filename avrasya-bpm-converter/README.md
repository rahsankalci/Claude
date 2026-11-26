# Avrasya (Oracle Workflow) → BAMOE BPM Dönüştürücü

Avrasya workflow XML'ini verirsiniz; **BPMN süreçlerini (kjar)** ve **servis entegrasyonunu (Spring Boot service)** içeren,
`ark-bpm`'e bağlı bir Maven projesi üretir.

| Modül | Ne |
|---|---|
| `converter-core` | Bağımlılıksız (yalnızca JDK) çekirdek: parser, BPMN üretici, proje üretici, CLI |
| `converter-desktop` | Swing masaüstü uygulaması (XML seç / sürükle → **Convert**) |
| `converter-intellij` | IntelliJ eklentisi: XML'e sağ tık → *Convert Avrasya Workflow to BAMOE BPM…* (**bu ortamda derlenemedi, test edilmedi**) |

## Kullanım

```bash
mvn clean package                                   # çekirdek + masaüstü, 20 test
java -jar converter-desktop/target/avrasya-bpm-converter.jar        # masaüstü arayüzü

# ya da komut satırı
java -jar converter-core/target/converter-core-1.0.0-SNAPSHOT.jar <workflow.xml> <cikti-klasoru> \
     [--group g] [--artifact a] [--package p] [--version v] [--config ayarlar.properties] [--no-ark]
```
Masaüstü ayarları `~/.avrasya-bpm-converter.properties` dosyasına kaydedilir.

IntelliJ eklentisi (ağı açık bir makinede): `cd converter-intellij && ./gradlew buildPlugin`
→ `build/distributions/*.zip` → *Settings ▸ Plugins ▸ ⚙ ▸ Install Plugin from Disk*. Eklenti `converter-core` kaynaklarını doğrudan derler.

## Ne üretir?

```
<artifactId>/
  pom.xml                          (aggregator)
  CONVERSION_REPORT.md             (aktivite eşlemesi, uyarılar, sonraki adımlar)
  <artifactId>-kjar/               parent: ark-bpm kjar-parent   (packaging kjar)
    src/main/resources/<paket>/    <SÜREÇ>.bpmn  (+ her Avrasya bloğu için <SÜREÇ>.<BLOK>.bpmn), AvrasyaService.wid
    src/main/resources/META-INF/   kmodule.xml, kie-deployment-descriptor.xml
    src/main/java/<paket>/         model/AvrasyaContainer, surec/Surec, handler/AvrasyaServiceWorkItemHandler (+Config)
  <artifactId>-service/            parent: ark-bpm-starter-parent
    src/main/java/<paket>/service/ Application, SurecEntegrasyonController, AvrasyaGeriDonusController, ...
    src/main/resources/            application.properties (ProgramID başına url/mode satırları)
```

### ark-bpm bağlantısı
Üretilen `kjar` ve `service` pom'ları `org.kie` / Spring Boot'a **doğrudan değil**, ark-bpm'e bağlanır
(*ark-bpm ayarları*: kjar parent, service parent, service bağımlılıkları). Varsayılanlar ark-bpm reposundaki klasör adlarından
(`kjar-parent`, `ark-bpm-starter-parent`, `ark-bpm-springboot-app`) türetilmiş **tahmindir**; groupId/version gerçek pom'lara göre
girilmelidir. `--no-ark` / "ark-bpm'e bağla" kutusunu kapatmak doğrudan KIE 7.74 pom'ları üretir (yalnızca deneme/doğrulama için).

## Dönüşüm kuralları

| Avrasya | BPMN |
|---|---|
| `Start` / `End` | start / end event |
| `StartOption=A` + `ProgramID` | `AvrasyaService` servis görevi → **EndCondition** kapısı → hatada **Hata Kuyruğu** insan görevi (karar `TEKRAR`/`ATLA`) |
| `StartOption=M` | insan görevi; `WorkQueueOption=S` → grup `WQ_<id>`, `C` → konteynerdeki değerden grup; EndCondition sağlanmazsa görev yeniden açılır |
| `Block` | ayrı BPMN + call activity |
| `Connection` | tek çıkış düz akış; çok çıkışın hepsi `TRUE` ise paralel (AND), değilse koşullu (XOR) |
| `NDM` eşlemeleri (`F`/`S`/`C`) | aktivitenin giriş script'i (alan kopyala / sabit / birleştir) |
| koşullar (`("<%yol%>"="00") AND …`) | Java ifadesi (`Surec.eq(Surec.v(c,"yol"),"00") && …`) |
| `Attributes` (RefBranchNum…) | entegrasyon uçlarında correlation key |

Konteyner yol tabanlı `AvrasyaContainer`'dır; JSON gösterimi konteynerin kendisidir, bilinmeyen düğümler kaybolmaz.

## Doğrulama (bu depodaki iki örnek XML ile)
- Üretilen kjar'lar gerçek `kie-maven-plugin` ile derlendi (BPMN şeması + script derlemesi). *Doğrudan-KIE modunda.*
- Üretilen `EBR_BASIC_TEST` jbpm-test ile çalıştırıldı: servis → asenkron mainframe dönüşü → bitiş; hata kuyruğu `WQ_585` → Tekrar Dene.
- Üretilen service modülü (Spring Boot + KIE Server API'leri) derlendi.
- **Doğrulanmadı:** ark-bpm pom'larına bağlanma (repo erişilemedi), IntelliJ eklentisi, service'in uçtan uca (HTTP) çalıştırılması, `IH_DAB_TANZIM_FC` akışının çalıştırılması.

## Bilinen sınırlar
- **Kesik XML** onarılır (dosya sonu kapatılır) ama eksik `<AllConnections>` olan kapsamların akışı veri eşlemelerinden ve aktivite sırasından **tahmin edilir**; rapordaki uyarıları kontrol edin. Tam XML ile akış birebir gelir.
- Avrasya, doğru olan tüm koşulları izler; koşullu çok çıkış burada **XOR** (ilk doğru dal) olur — uyarı verilir.
- `StartCondition=A` ve ≥2 giriş **AND** birleşim olur; dallar paralel değilse süreç bekler — uyarı verilir.
- Avrasya form/UI, bildirim, zamanlayıcı (`Duration`), rollback ve `DDC/DLC` çıktı eşlemeleri dönüştürülmez.
