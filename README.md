# NethCTI per Android

Fork indipendente di [nethesis/nethcti-app-android](https://github.com/nethesis/nethcti-app-android), basato su Linphone. Client per Android 9–17 con configurazione adattiva e permessi limitati alle funzioni utilizzate. **Non è una release ufficiale Nethesis.**

## APK di prova 1.6.5

Scarica APK e SHA-256 dalla [pre-release 1.6.5](https://github.com/Nitesam/nethcti-app-android/releases/tag/v1.6.5-test).

- Android 9 o successivo, compilazione e target Android 17 / API 37.
- Package di prova: `it.nethesis.nethctiapp.debug`, versione `1.6.5-debug` / 4230.
- APK firmato con una chiave debug locale, senza firma ufficiale Play Store. Può coesistere con l'app ufficiale e aggiornare le precedenti build di prova firmate con la stessa chiave.
- **Push non configurato:** servono la configurazione Firebase e i parametri del notificatore del gestore. La ricezione richiede il servizio attivo e non è disponibile dopo un arresto forzato; dopo il riavvio le versioni recenti di Android richiedono di riprenderla dalla notifica.

La 1.6.5 adatta **Configura ricezione** alla versione Android e al produttore.
Ogni voce mostra descrizione e stato verificato. **Funzionamento in background**
richiede l’esenzione solo quando manca; se già concessa apre le informazioni
dell’app. Sono previsti percorsi alternativi quando una schermata non è disponibile.
Le istruzioni Samsung sono visibili soltanto sui Samsung e restano una verifica manuale.

Il toggle **Ricevi chiamate** mantiene disponibili le chiamate in uscita anche
da spento. Restano invariati servizio persistente, riconnessione, squillo tramite
canale Android e ripristino del volume rispettando le modifiche manuali.
Aggiornare senza disinstallare conserva account, cronologia locale e preferenze.

Le note della pre-release riportano la matrice dei test Android e i risultati
eseguiti. **Squillo fisico, centralino aziendale e prova di otto ore restano da
collaudare.** Gli emulatori non certificano tutte le personalizzazioni dei produttori.
La cronologia non viene sincronizzata tra dispositivi.

## Permessi

Nessun accesso generale a foto, video, musica o memoria condivisa. Gli allegati vengono scelti tramite il selettore Android, con accesso al solo file selezionato. Registrazioni e download restano nelle cartelle private dell'app.

Microfono nelle chiamate/prove audio, fotocamera per QR/video/scatto esplicito, rubrica alla sua apertura/modifica e Bluetooth alla selezione dell'uscita audio. All'apertura vengono richieste le notifiche da Android 13 e l'accesso alla rete locale da Android 17, per il centralino. Rimossi i permessi per stato/numero SIM e chiamate cellulari, oltre alla dipendenza Firebase Analytics inutilizzata.

## Compilazione

Aprire il progetto in Android Studio, configurare Android SDK in `local.properties` e sincronizzare Gradle. Toolchain verificata: JDK 25, Gradle 9.3.1, AGP 9.1.1, SDK / Build Tools 37, Linphone SDK 5.4.127. Il wrapper contiene i criteri per il JDK del daemon.

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. La compilazione senza `app/google-services.json` è supportata, con push disabilitato. La variante release richiede una propria configurazione di firma, altrimenti produce un APK non firmato. Chiavi e configurazioni locali non vanno pubblicate.

Per riprodurre la matrice su emulatori dedicati, installare le immagini Google APIs
x86_64 delle API 28–36 e l’immagine API 37 a 16 KB, poi compilare i test:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
python tools/android_compat_matrix.py
```

Il runner usa un emulatore alla volta e account SIP sintetici locali. Azzera
soltanto i dati dell’app negli emulatori dedicati, mai su un telefono.
I risultati dettagliati restano locali. Le istruzioni e i collaudi delle versioni
precedenti sono conservati in [docs/ANDROID_17.md](docs/ANDROID_17.md). Il lint storico conserva errori noti: una build riuscita non equivale a un lint pulito.

## Origine e licenza

Codice derivato da Nethesis e Linphone / Belledonne Communications, con i rispettivi avvisi di copyright conservati. Licenza [GPL-2.0](LICENSE.txt). Il [README originale](docs/UPSTREAM_README.md) è conservato come riferimento storico.
