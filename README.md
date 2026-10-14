# NethCTI per Android

Fork non ufficiale di [Nethesis](https://github.com/nethesis/nethcti-app-android) per Android 9–17.

[Download APK & Checksum e note](https://github.com/Nitesam/nethcti-app-android/releases/tag/v1.6.6-test)

Installa senza disinstallare per aggiornare le precedenti build di questo fork e conservare i dati. L’APK usa una firma di prova e il package `it.nethesis.nethctiapp.debug`: non aggiorna l’app del Play Store.

## Uso

- **Ricevi chiamate** attiva il servizio in background e la riconnessione automatica. Da spento puoi comunque chiamare in uscita.
- **Configura ricezione** mostra permessi e impostazioni necessari per il tuo Android. La guida Samsung compare solo sui Samsung.
- **Amplifica voce** durante la chiamata regola il boost da 0 a +12 dB. Riduci se il suono distorce. Si azzera a fine chiamata o cambiando uscita audio.
- Durante lo squillo il volume sale almeno al 50%, poi viene ripristinato rispettando le modifiche manuali.
- Microfono, fotocamera, rubrica e Bluetooth vengono richiesti quando servono. Nessun accesso generale a foto, musica o memoria condivisa.

Push non configurato. Dopo un arresto forzato occorre riaprire l’app; dopo il riavvio, sulle versioni recenti di Android, riprendere la ricezione dalla notifica. La cronologia resta sul singolo dispositivo.

## Compatibilità verificata

| Android | API | Ricezione 1.6.5 | Audio 1.6.6 |
|---|---:|---|---|
| 9 | 28 | Superati | Superati |
| 10 | 29 | Superati | Superati |
| 11 | 30 | Superati | Superati |
| 12 | 31 | Superati | Superati |
| 12L | 32 | Superati | Superati |
| 13 | 33 | Superati | Superati |
| 14 | 34 | Superati | Superati |
| 15 | 35 | Superati | Superati |
| 16 | 36 | Superati | Superati |
| 17 | 37 | Superati, 16 KB | Superati, 16 KB |

**1.6.5:** installazione, avvio, configurazione, chiamate SIP locali e RTP bidirezionale, background oltre 95 secondi, rimozione dai recenti, toggle, riconnessione e ripristino audio. Aggiornamento dalla 1.6.4 e riavvio verificati su Android 9 e 17.

**1.6.6:** chiamate SIP locali entranti/uscenti, RTP in background, slider, boost misurato su tono sintetico, ricreazione schermata e ripristino dopo riavvio del processo.

Su Samsung Android 17: aggiornamento e pulsante batteria verificati. **Da collaudare:** volume della voce e commutazione auricolare sul telefono, chiamate aziendali, squillo udibile, Bluetooth fisico e inattività prolungata.

## Compilazione

JDK 25, Android SDK / Build Tools 37. Imposta il percorso SDK in `local.properties`.

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Build possibile senza configurazione Firebase. Il lint segnala ancora errori di traduzione e UI.

Licenza [GPL-2.0](LICENSE.txt). Codice Nethesis e Linphone / Belledonne Communications.
