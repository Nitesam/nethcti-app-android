# Note storiche del port Android 17

La versione corrente è un client Android 9–17. Vedere il README e le note della
pre-release per configurazione adattiva e collaudi correnti. Questo documento
conserva il percorso e le evidenze delle versioni precedenti.

Branch `android-17`, base upstream `97d80d7fa`.

Questa build serve a verificare NethCTI sul Samsung S26 Ultra. L'installazione
e l'avvio sono verificati su emulatore Android 17 e sul telefono. I test
funzionali sul centralino reale rimangono da completare.
Non è una release ufficiale Nethesis e non sostituisce una build del Play Store.

## Compilazione e Android Studio

Aprire la cartella del repository in Android Studio. Usare il Gradle wrapper
del progetto e JDK 25. Impostare JAVA_HOME sul percorso del JDK installato:

```bash
export JAVA_HOME=/percorso/al/jdk-25
./gradlew :app:assembleDebug :app:lintDebug
```

SDK locale configurato in `local.properties`,
non versionato. Toolchain: AGP 9.1.1, Gradle 9.3.1, compile/target SDK 37,
Build Tools 37.0.0, min SDK 28. Linphone SDK aggiornato da 4.5.26 a 5.4.127.

Output: `app/build/outputs/apk/debug/app-debug.apk`, versione `1.6.4-debug`
(4229), package `it.nethesis.nethctiapp.debug`, firmato con la chiave debug
locale. Conservare quella chiave per aggiornare questa installazione senza
disinstallarla. La chiave originale Play Store non è inclusa nel repository.

La variante debug usa provider e account di sincronizzazione separati.
Il progetto è aperto e importato in Android Studio. Il log dell'IDE conferma
la sincronizzazione Gradle completata il 12 ottobre 2026 alle 18:47:35
(in 34 secondi) e l'indicizzazione conclusa alle 18:47:46. Il progetto usa
il JDK `jbr-25`; anche la compilazione con il wrapper è verificata.

## Ricezione 1.6.3

La 1.6.3 aggiunge un toggle di ricezione nella home, permanenza del servizio,
riconnessione e squillo mediante canale Android. Risultati e limiti sono
riportati nelle note della pre-release.
Le verifiche storiche riportate sotto riguardano la precedente build 1.6.2.

## Modifiche principali

- Migrazione delle chiamate alle API e dei listener Linphone 5.
- Librerie ARM64 e x86_64 con segmenti ELF allineati a 16 KB.
- Dichiarazione dei tipi di servizio telefonico, microfono e fotocamera.
  Il servizio iniziale usa il tipo telefonico, l'SDK gestisce i tipi media
  durante la chiamata.
- Registrazione esplicita dei receiver e controlli dei permessi Bluetooth e
  stato del telefono. Il test ha rilevato e corretto un crash nativo causato
  da `getCallState()` quando `READ_PHONE_STATE` era negato.
- Richiesta del nuovo permesso Android 17 `ACCESS_LOCAL_NETWORK` prima del
  login manuale o tramite QR e per gli account già configurati. Senza questo
  permesso il test SIP verso la rete locale falliva con errore I/O.
- Notifica delle chiamate in arrivo con canale dedicato e full-screen intent.
- Dopo un riavvio, Android 15+ mostra una notifica per riaprire l'app invece
  di avviare un servizio telefonico dal receiver di boot.
- Navigazione indietro tramite dispatcher AndroidX e gestione dei margini
  delle barre di sistema per i layout esistenti.
- Firebase opzionale in assenza della configurazione privata, senza crash
  al primo avvio. Rimossi i log HTTP di header/body e dei token Firebase.
- Rimosso il vecchio downloader OpenH264, già disabilitato dall'app su tutte
  le versioni Android attualmente supportate.

## Verifiche del 12 ottobre 2026

- `assembleDebug` e `assembleRelease` (R8): riusciti.
- Release R8 firmata temporaneamente con la chiave debug per la sola prova
  nell'emulatore: installazione e apertura del login riuscite, senza crash Java.
- Permesso di rete locale: dialogo di sistema visualizzato al login, rifiuto
  gestito tornando alla schermata di accesso senza crash.
- Firma APK: valida, schema v2.
- `zipalign -c -P 16 4`: riuscito.
- Controllo dei segmenti ELF: 28 librerie ARM64/x86_64 compatibili con 16 KB.
- Emulatore `NethCTI_API37_QPR_16KB`, immagine
  `system-images;android-37.2;google_apis_ps16k;x86_64`, Android 17,
  `getconf PAGE_SIZE = 16384`: installazione e apertura fino al login riuscite.
- Rifiuto del permesso fotocamera: ritorno al login senza crash.
- Concessione del permesso fotocamera e ritorno dalla schermata QR: senza crash.
  L'emulatore non ha una fotocamera configurata, la scansione QR non è verificata.
- Riapertura dal background: riuscita. Nessun login o chiamata al centralino
  aziendale eseguiti.
- `lintDebug` eseguito, **non pulito**: restano 472 MissingTranslation,
  41 UseAppTint, 4 Range e 1 MissingInflatedId. Il progetto conserva
  `abortOnError false`, il successo del task non significa assenza di errori.
  Report completo in `app/build/reports/lint-results-debug.html`.

Le immagini Android 37.0 provate si bloccavano nel servizio di sistema
AdvancedProtection prima dell'installazione dell'app. L'immagine 37.2 ha
completato il boot. Su questo host SwiftShader GLES si blocca, usare `swangle`:

```bash
~/Android/Sdk/emulator/emulator -avd NethCTI_API37_QPR_16KB \
  -gpu swangle -memory 2048 -cores 2 -no-snapshot
```

Per ripetere i controlli statici:

```bash
python3 tools/check_native_alignment.py app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/build-tools/37.0.0/zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/build-tools/37.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

## Test telefonico locale riproducibile

`app/src/androidTest/java/org/linphone/LocalCallInstrumentation.java` usa il
servizio e il Core dell'app. Il peer Python ascolta solo su `127.0.0.1` e
trasmette silenzio PCMU. Il test rifiuta dispositivi diversi dall'emulatore e
account già configurati. Non usa credenziali né contatta un centralino reale.

Su Android 17 a 16 KB sono riusciti:

- Chiamata uscente: Connected, StreamsRunning, End e Released.
- Chiamata entrante: IncomingReceived, Connected, StreamsRunning, End e Released.
- RTP in entrambe le direzioni, anche dopo aver portato l'app in background.
- Servizio foreground con tipi `phoneCall|microphone` (132).
- Permesso `READ_PHONE_STATE` negato durante entrambe le chiamate, senza crash.

Evidenza in `local-call-test.txt` e `local-sip-peer.txt`. Sono verificati
segnalazione e pacchetti audio, non qualità percepita, dispositivi Bluetooth,
SIP TLS/SRTP del centralino, login NethCTI o push.

Con l'emulatore dedicato acceso e la build debug installata senza account:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell pm grant it.nethesis.nethctiapp.debug android.permission.RECORD_AUDIO
adb -s emulator-5580 shell pm grant it.nethesis.nethctiapp.debug android.permission.POST_NOTIFICATIONS
adb -s emulator-5580 shell pm grant it.nethesis.nethctiapp.debug android.permission.ACCESS_LOCAL_NETWORK
```

In un terminale avviare `python3 tools/local_sip_peer.py --incoming`, poi in
un altro terminale:

```bash
adb -s emulator-5580 shell am instrument -r -w -e incoming true \
  it.nethesis.nethctiapp.debug.test/org.linphone.LocalCallInstrumentation
```

Il risultato atteso è `INSTRUMENTATION_CODE: -1`, insieme ai contatori RTP
positivi e a `incoming=RTP and hangup passed`. Un codice 0 è un fallimento,
anche se il comando `adb` termina con exit code 0.

## Prova sul telefono e limiti

Trasferire l'APK al telefono e aprirlo, autorizzando l'installazione per
l'app usata per aprire il file, se Android lo richiede. Inserire dominio,
utente e password direttamente sul telefono. Non condividere la password
nei log o nella conversazione.

Verificare con una chiamata concordata: login, chiamata uscente, entrante,
audio in entrambe le direzioni, vivavoce, Bluetooth, schermo bloccato e
ritorno dopo sospensione. Le prove effettuate non certificano questi flussi.

**Le notifiche push non sono configurate in questa build.** Mancano
`app/google-services.json` e i valori privati del notificatore. Per renderle
operative serve una configurazione Firebase coerente con il package e con
il servizio del gestore NethCTI. Il solo APK non può ricrearla. Le chiamate
in arrivo ad app chiusa non sono garantite. L'opzione esistente del servizio
in background può essere provata con il gestore, ma non equivale al push.

La build release con ottimizzazione R8 viene compilata, ma non dispone di una
firma di distribuzione del gestore. Il test funzionale sul S26 Ultra e sul centralino aziendale rimane necessario
prima dell'uso lavorativo continuativo.

Riferimenti: [SDK Android 17](https://developer.android.com/about/versions/17/setup-sdk),
[tipi di servizio](https://developer.android.com/develop/background-work/services/fgs/service-types),
[supporto 16 KB](https://developer.android.com/guide/practices/page-sizes),
[modifiche Android 17 e rete locale](https://developer.android.com/about/versions/17/behavior-changes-17).


## Stato della verifica finale

Repository, importazione in Android Studio, build debug/release, APK firmato
di prova, controlli a 16 KB e chiamate SIP locali hanno evidenze positive.
L'APK consegnato 1.6.2 coincide con la build debug e con il suo SHA-256.

Il 12 ottobre 2026 il telefono Samsung SM-S948B è stato rilevato via ADB:
Android 17, API 37, pagine da 4096 byte. La build debug già presente è stata
aggiornata con `adb install -r` alla versione 1.6.1 (4226), senza disinstallare
o cancellare dati. Lo SHA-256 dell'APK installato coincide con quello consegnato.
L'avvio tramite launcher ha restituito `Status: ok`, l'attività
`org.linphone.LinphoneActivity` è risultata in primo piano e il processo è
rimasto attivo durante il controllo. Nel buffer crash filtrato sul nuovo
processo non sono emersi errori. Questa è una verifica di installazione e
avvio, non di login o chiamate.

La verifica completa rimane aperta: non è disponibile l'esito delle prove
funzionali sul S26 Ultra e non sono presenti la configurazione Firebase e
i parametri privati del notificatore. Serve
l'esito di login e chiamate sul dispositivo/centralino reale e, per validare
il push, la configurazione del gestore. Non sono sostituibili dai test SIP
locali e non sono stati dichiarati superati.


## Versione 1.6.2: permessi limitati alle funzioni utilizzate

Rimossa la richiesta in blocco da `LinphoneActivity.onStart`. All'apertura
restano notifiche e rete locale, richieste una sola volta da questa schermata.
Microfono nelle chiamate/calibrazione, fotocamera per QR, video o scatto
esplicito, rubrica alla sua apertura/modifica, Bluetooth quando si seleziona
l'uscita Bluetooth. Non viene più attivata automaticamente la sovrapposizione.

Eliminati dal manifest READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO,
READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, CALL_PHONE, READ_PHONE_NUMBERS,
READ_PHONE_STATE, BLUETOOTH_SCAN e le vecchie dichiarazioni per gli account.
La dipendenza Firebase Analytics non utilizzata è rimossa, evitando anche i
permessi pubblicitari introdotti dal relativo manifest.

Gli allegati e gli avatar usano ACTION_OPEN_DOCUMENT con accesso al singolo
URI, copiato nella cache dell'app tramite ContentResolver. Lo scatto è una
scelta separata, con permesso fotocamera richiesto prima della cattura e URI
FileProvider. La selezione annullata non accede a file o fotocamera. Download
e registrazioni utilizzano già cartelle private, non richiedono memoria
condivisa. La suoneria usa il default di sistema o il suono incluso nell'app,
con ripiego sul suono incluso se una vecchia suoneria non è più accessibile.

L'app non legge più lo stato della SIM: il precedente controllo delle chiamate
cellulari ritorna false senza READ_PHONE_STATE. La convivenza con una chiamata
cellulare e l'instradamento su cuffie fisiche restano da verificare sul telefono.

Verifiche: compilazione debug e release, controllo manifest dell'APK,
strumentazione su emulatore API 37 a 16 KB con permessi memoria/telefono assenti,
file URI selezionato copiato correttamente, chiamate locali entrante/uscente con
RTP bidirezionale anche in background. Nessuna prova contatta il centralino
aziendale. Evidenze in `permissions-call-test.txt` e `permissions-sip-peer.txt`.
La prova UI dell'editor contatti apre il selettore documenti di Android senza
permessi galleria, annullando torna all'editor. La rubrica viene richiesta
solo dopo l'apertura dei contatti.
La richiesta fotocamera è stata osservata soltanto scegliendo “Take a photo”
nell'editor, dopo avere verificato CAMERA negato durante la scelta del documento.
APK consegnato: `NethCTI-1.6.2-Android17-test.apk`, SHA-256
`060daa1360b02ab9356b05330d38d8f90d4e47cabf8caae0148095ce586f3477`.

Installazione sul Samsung SM-S948B: aggiornamento con `adb install -r`
riuscito, versione 1.6.2-debug / 4227, SHA-256 identico al file consegnato,
attività LinphoneActivity in primo piano e processo attivo. Il pacchetto sul
telefono non dichiara più permessi media/memoria, stato/numero SIM, chiamate
cellulari o identificativi pubblicitari. Non sono stati cancellati dati o account.
