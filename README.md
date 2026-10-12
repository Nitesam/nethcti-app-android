# NethCTI per Android 17

Fork indipendente di [nethesis/nethcti-app-android](https://github.com/nethesis/nethcti-app-android), basato su Linphone. Aggiorna il client a Android 17 e riduce i permessi alle funzioni utilizzate. **Non è una release ufficiale Nethesis.**

## APK di prova 1.6.3

Scarica APK e SHA-256 dalla [pre-release Android 17](https://github.com/Nitesam/nethcti-app-android/releases/tag/v1.6.3-android17-test).

- Android 9 o successivo, compilazione e target Android 17 / API 37.
- Package di prova: `it.nethesis.nethctiapp.debug`, versione `1.6.3-debug` / 4228.
- APK firmato con una chiave debug locale, senza firma ufficiale Play Store. Può coesistere con l'app ufficiale e aggiornare le precedenti build di prova firmate con la stessa chiave.
- **Push non configurato:** servono la configurazione Firebase e i parametri del notificatore del gestore. Le chiamate ad app chiusa non sono garantite.

La 1.6.3 aggiunge **Ricevi chiamate** nella home, ricezione persistente,
riconnessione, chiamate in uscita anche da spento e squillo tramite canale Android.
La prima attivazione richiede consenso per l’override temporaneo del volume e
configurazione di notifiche, Non disturbare e batteria. Account e cronologia
sono conservati, la ricezione parte spenta dopo l’aggiornamento.

Test locali su emulatore Android 17 a 16 KB: ricezione dopo 95 secondi in
background, rifiuto degli ingressi da spento, uscita da spento, perdita rete e
riconnessione, override/ripristino audio e RTP bidirezionale. **La 1.6.3 non è
ancora collaudata sul Samsung/PBX aziendale; il test di otto ore resta pendente.**

Le note della pre-release riportano configurazione, verifiche eseguite e limiti.

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
