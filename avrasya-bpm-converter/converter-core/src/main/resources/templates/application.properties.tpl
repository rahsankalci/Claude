# =====================================================================
# @@processName@@ - servis ayarlari (donusturucu tarafindan uretildi)
# KIE Server / veritabani / guvenlik ayarlari ark-bpm-springboot-* kutuphanelerinden gelir;
# burada yalnizca bu surece ozel ayarlar var. Gerekirse ark-bpm'in kendi
# application.properties anahtarlariyla birlestirin.
# =====================================================================
server.port=8090

# ---- Avrasya servisleri (AvrasyaServiceWorkItemHandler)
# Tum ProgramID'ler icin ortak adres: <base-url>/<ProgramID>
avrasya.service.base-url=http://localhost:8091/avrasya
avrasya.service.timeout-ms=30000
# Yetkilendirme basligi (Secret'tan ortam degiskeni olarak: AVRASYA_SERVICE_AUTH_HEADER)
#avrasya.service.auth-header=

# Mainframe'e gidip BPM'e geri donen programlar icin: sonuc buraya POST edilir
avrasya.service.callback-url=http://localhost:${server.port}/api/avrasya/geri-donus

# ---- ProgramID bazinda adres / mod (otomatik uretildi)
# Program servisi farkli bir adreste ise .url satirini acin. Mainframe'e gidip geri donenler
# icin .mode=async yapin (work item acik kalir, sonuc geri donus ucuna gelir).
@@programProps@@
