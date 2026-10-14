# NethCTI per Android

Fork non ufficiale di [Nethesis](https://github.com/nethesis/nethcti-app-android) per Android 9–17.

[Download APK & Checksum e note](https://github.com/Nitesam/nethcti-app-android/releases/tag/v1.6.5-test)

Installa senza disinstallare per aggiornare le precedenti build di questo fork e conservare i dati. L’APK usa una firma di prova e il package `it.nethesis.nethctiapp.debug`: non aggiorna l’app del Play Store.

## Uso

- **Ricevi chiamate** attiva il servizio in background e la riconnessione automatica. Da spento puoi comunque chiamare in uscita.
- **Configura ricezione** mostra permessi e impostazioni necessari per il tuo Android. La guida Samsung compare solo sui Samsung.
- Durante lo squillo il volume sale almeno al 50%, poi viene ripristinato rispettando le modifiche manuali.
- Microfono, fotocamera, rubrica e Bluetooth vengono richiesti quando servono. Nessun accesso generale a foto, musica o memoria condivisa.

Push non configurato. Dopo un arresto forzato occorre riaprire l’app; dopo il riavvio, sulle versioni recenti di Android, riprendere la ricezione dalla notifica. La cronologia resta sul singolo dispositivo.

## Compatibilità verificata

| Android | API | Test su emulatore |
|---|---:|---|
| 9 | 28 | Superati |
| 10 | 29 | Superati |
| 11 | 30 | Superati |
| 12 | 31 | Superati |
| 12L | 32 | Superati |
| 13 | 33 | Superati |
| 14 | 34 | Superati |
| 15 | 35 | Superati |
| 16 | 36 | Superati |
| 17 | 37 | Superati, pagine da 16 KB |

Test: installazione, avvio, configurazione, chiamate SIP locali e RTP bidirezionale, background oltre 95 secondi, rimozione dai recenti, toggle, riconnessione e ripristino audio. Aggiornamento dalla 1.6.4 e riavvio verificati su Android 9 e 17.

Su Samsung Android 17: aggiornamento e pulsante batteria verificati. **Da collaudare:** chiamate aziendali, squillo udibile, Bluetooth fisico e inattività prolungata.

## Compilazione

JDK 25, Android SDK / Build Tools 37. Imposta il percorso SDK in `local.properties`.

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Build possibile senza configurazione Firebase. Il lint conserva errori preesistenti.

Licenza [GPL-2.0](LICENSE.txt). Codice Nethesis e Linphone / Belledonne Communications.
