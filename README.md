Reststop – installatiegids

 1. Project openen
1. Pak de zip uit en open de map `Reststop` in Android Studio (Ladybug of nieuwer, JDK 17).
2. Laat Gradle synchroniseren. Lukt dat niet door de wrapper: *File > Settings > Build > Gradle* en kies de
   ingebouwde Gradle (8.9+), of voer `gradle wrapper` uit in de projectmap.
3. Geef in `AppContainer.kt` bij `USER_AGENT` je eigen e-mailadres in (Nominatim-voorwaarde).



2. Testen
- Registreer één account als **handelaar** en één als **klant** (twee emulators of uitloggen/inloggen).
- Handelaar: Mijn winkel > tik op de kaart > adres wordt ingevuld > opslaan > Mijn pakketten > Nieuw pakket (met foto).
- Klant: Ontdekken > locatie toestaan (in de emulator: Extended controls > Location) > pakket > reserveren.
- Handelaar: bij Mijn pakketten > "Opgehaald" > ophaalcode van de klant intypen.

3. Git
Maak een repository, commit regelmatig met duidelijke boodschappen, en zorg dat alle groepsleden commits hebben.
`google-services.json` kan je best niet publiek zetten.
