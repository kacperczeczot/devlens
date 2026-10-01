# Changelog

Wszystkie istotne zmiany w projekcie DevLens są dokumentowane w tym pliku.

Format oparty jest na [Keep a Changelog](https://keepachangelog.com/pl/1.0.0/),
a projekt stosuje [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.3] - 2026-10-01

### Naprawiono (Hotfix)
- **Aplikacja Android**: Usunięto krytyczny błąd NullPointerException powodujący zawieszanie się (crash) aplikacji u użytkowników, którzy zaktualizowali ją z wersji 1.0.1. Błąd wynikał z deseralizacji starszych węzłów zapisanych w konfiguracji bez pola `knownIps`.
## [1.0.2] - 2026-10-01

### Naprawiono
- **Wieloadresowość węzłów (Multi-IP)**:
  - Węzły pamiętają teraz wszystkie pomyślnie wykryte adresy IP (np. Tailscale i lokalny adres Wi-Fi) na liście `knownIps`.
  - Usprawniono proces odświeżania (`refreshNode`) tak, by automatycznie testował zapasowe adresy w przypadku braku odpowiedzi z głównego (np. po wyjściu z domu). Zapobiega to trwałemu nadpisywaniu adresów VPN przez skaner LAN.
- **Odczyt zewnętrznych dysków**:
  - Zwiększono limit czasu oczekiwania na odczyt katalogów i plików z 5 do 30 sekund w daemonsie, co eliminuje błędy odczytu (TCC / timeout) przy wybudzaniu uśpionych zewnętrznych dysków HDD.

## [1.0.1] - 2026-09-20

### Naprawiono
- **Audyt uprawnień macOS TCC (`check_filesystem` & `check_full_disk_access`)**:
  - Wyeliminowano fałszywe raportowanie pełnych uprawnień (`all_granted: true`), gdy dostęp do dysków zewnętrznych był zablokowany.
  - Dodano aktywną inspekcję `/Volumes` oraz podłączonych dysków wymiennych do audytu systemu plików na macOS.
  - Skorygowano warunek FDA — brak dostępu do dysków wymiennych jest teraz jednoznacznie oznaczany jako brak uprawnień krytycznych (`action_required`).
  - Dodano do `Info.plist` brakujące deskryptory uprawnień systemowych macOS: `NSRemovableVolumesUsageDescription` oraz `NSDesktopFolderUsageDescription`.
- **Stabilność serwera i eliminacja blokad Tokio (`handle_query` & `handle_read_file`)**:
  - Przeniesiono operacje czytania systemu plików z asynchronicznych wątków Tokio do puli `tokio::task::spawn_blocking`.
  - Wprowadzono 5-sekundowy limit czasu (timeout), zapobiegający blokowaniu serwera na poziomie jądra macOS (`__open_nocancel`) w przypadku braku zgody TCC lub uśpionego dysku.
  - Dodano bezpieczną obsługę błędów `PermissionDenied` na poziomie pojedynczych plików i woluminów bez przerywania całego żądania.
- **Klient mobilny (`apps/android`)**:
  - Przełączono zapytania eksploracji plików (`listFiles` i `readFile`) z 5-sekundowego `fastClient` na dedykowany `fileClient` z 60-sekundowym timeoutem.
  - Wprowadzono translację błędów `SocketTimeoutException` na czytelne komunikaty w języku polskim, wskazujące na potrzebę weryfikacji uprawnień macOS TCC.

## [1.0.0] - 2026-09-20

### Dodano
- **Nowy, dedykowany ekosystem DevLens**: Niezależna aplikacja mobilna (Android) oraz natywna aplikacja desktopowa w Rust (`apps/desktop`) do zdalnego przeglądania projektów i audytu maszyn.
- **Nowa tożsamość wizualna**: Dedykowane wektorowe i rastrowe logo oraz ikony aplikacji DevLens (apertura optyczna z akcentem kodu `{ }`) dla Androida (wszystkie gęstości mipmap, adaptive icons) i systemów desktopowych (.icns, .ico, PNG, surowy bufor RGBA do traya).
- **Aplikacja desktopowa (`apps/desktop`)**: Natywny daemon w Rust zintegrowany z zasobnikiem systemowym (Tray) na macOS, Windows i Linux, autostartem (`LaunchAgent`, Registry, systemd), profilem zasilania (`power.rs`) i panelem webowym na `http://localhost:8888`.
- **Eksplorator plików**: Zdalne przeglądanie drzewa katalogów, sortowanie, wyszukiwanie, breadcrumbs, pobieranie i wgrywanie plików ze strumieniowaniem.
- **DevLens Reader**: Pełne renderowanie Markdown, renderowanie formuł matematycznych KaTeX, diagramów architektonicznych Mermaid oraz podświetlanie składni dla ponad 20 języków programowania.
- **Centrum audytu i uprawnień**: Inspekcja uprawnień do plików systemowych i wrażliwych katalogów (.ssh, .aws, .config), otwartych portów i procesów węzła.
- **Zero-Touch LAN Pairing**: Automatyczne wykrywanie węzłów w sieci lokalnej i bezpieczne parowanie kodem PIN.
- **Wbudowany instalator aktualizacji**: Bezpośrednie pobieranie i instalacja aktualizacji APK z GitHub Releases (`DevLens-Android-UpdateCheck`).
- **Wieloplatformowe workflowy CI/CD**: Zautomatyzowane pipeline'y GitHub Actions dla testów jednostkowych i wydań (Android APK, macOS DMG, Windows EXE, Linux binary).

### Naprawiono
- **Mechanizm aktualizacji (`ReleaseUpdateChecker.kt`)**: Usunięcie odwołań do starego zewnętrznego gista oraz manifestu ze starą wersją 2.8.7.
- **Spójność marki**: Oczyszczenie wszelkich pozostałości po starym projekcie w nagłówkach okien dialogowych, stylach kart, nazwach pakietów i plikach konfiguracyjnych.

