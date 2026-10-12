# NethCTI per Android 17

Fork indipendente di [nethesis/nethcti-app-android](https://github.com/nethesis/nethcti-app-android), basato su Linphone. Aggiorna il client a Android 17 e riduce i permessi alle funzioni utilizzate. **Non è una release ufficiale Nethesis.**

## APK di prova 1.6.2

Scarica APK e SHA-256 dalla [pre-release Android 17](https://github.com/Nitesam/nethcti-app-android/releases/tag/v1.6.2-android17-test).

- Android 9 o successivo, compilazione e target Android 17 / API 37.
- Package di prova: `it.nethesis.nethctiapp.debug`, versione `1.6.2-debug` / 4227.
- APK firmato con una chiave debug locale, senza firma ufficiale Play Store. Può coesistere con l'app ufficiale e aggiornare le precedenti build di prova firmate con la stessa chiave.
- **Push non configurato:** servono la configurazione Firebase e i parametri del notificatore del gestore. Le chiamate ad app chiusa non sono garantite.

Installazione e avvio verificati su Samsung S26 Ultra con Android 17. Su emulatore Android 17 a 16 KB sono verificati chiamate SIP locali entranti/ uscenti, RTP bidirezionale e continuità dei pacchetti in background. **Login e chiamate sul centralino aziendale, qualità audio percepita e Bluetooth reale restano da verificare.**

## Permessi

Nessun accesso generale a foto, video, musica o memoria condivisa. Gli allegati vengono scelti tramite il selettore Android, con accesso al solo file selezionato. Registrazioni e download restano nelle cartelle private dell'app.

Microfono nelle chiamate/prove audio, fotocamera per QR/video/scatto esplicito, rubrica alla sua apertura/modifica e Bluetooth alla selezione dell'uscita audio. All'apertura rimangono notifiche e rete locale per il centralino. Rimossi i permessi per stato/numero SIM e chiamate cellulari, oltre alla dipendenza Firebase Analytics inutilizzata.

## Compilazione

Aprire il progetto in Android Studio, configurare Android SDK in `local.properties` e sincronizzare Gradle. Toolchain verificata: JDK 25, Gradle 9.3.1, AGP 9.1.1, SDK / Build Tools 37, Linphone SDK 5.4.127. Il wrapper contiene i criteri per il JDK del daemon.

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. La compilazione senza `app/google-services.json` è supportata, con push disabilitato. La variante release richiede una propria configurazione di firma, altrimenti produce un APK non firmato. Chiavi e configurazioni locali non vanno pubblicate.

Le istruzioni complete, i test riproducibili e i limiti sono in [docs/ANDROID_17.md](docs/ANDROID_17.md). Il lint storico conserva errori noti: una build riuscita non equivale a un lint pulito.

## Origine e licenza

Codice derivato da Nethesis e Linphone / Belledonne Communications, con i rispettivi avvisi di copyright conservati. Licenza [GPL-2.0](LICENSE.txt). Il [README originale](docs/UPSTREAM_README.md) è conservato come riferimento storico.
