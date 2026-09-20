# DevLens 🔍

[![CI & Tests](https://github.com/kacperczeczot/devlens/actions/workflows/ci.yml/badge.svg)](https://github.com/kacperczeczot/devlens/actions/workflows/ci.yml)
[![Build & Release](https://github.com/kacperczeczot/devlens/actions/workflows/release.yml/badge.svg)](https://github.com/kacperczeczot/devlens/actions/workflows/release.yml)
[![Release](https://img.shields.io/github/v/release/kacperczeczot/devlens?color=00D2FF&label=Latest%20Release)](https://github.com/kacperczeczot/devlens/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**DevLens** to nowoczesny ekosystem do zdalnego przeglądania projektów, czytania dokumentacji i kodu (Markdown, KaTeX, Mermaid) oraz audytu uprawnień i bezpieczeństwa maszyn deweloperskich w sieci lokalnej (macOS, Linux, Windows), składający się z **aplikacji mobilnej Android** oraz **natywnej aplikacji desktopowej (Rust)** z zasobnikiem systemowym (Tray).

---

## 🌟 Główne Możliwości

* 📂 **Zdalny Eksplorator Plików & Drzewo Katalogów:**
  * Błyskawiczne przeglądanie projektów na dowolnym komputerze w sieci.
  * Nawigacja po ścieżce (breadcrumbs), sortowanie (nazwa, data, rozmiar), wyszukiwanie i filtrowanie.
  * Pobieranie plików, udostępnianie, wysyłanie (upload) oraz strumieniowanie multimediów.

* 📖 **Zaawansowany Czytnik Dokumentacji i Kodu (DevLens Reader):**
  * **Markdown:** Pełne wsparcie dla nagłówków, tabel, list zadań `[x]`, akordeonów `<details>` i alertów GFM (`[!NOTE]`, `[!TIP]`, `[!WARNING]`, `[!CAUTION]`).
  * **Wzory Matematyczne (KaTeX):** Renderowanie formuł \(\LaTeX\) w tekście i w blokach dedykowanych.
  * **Diagramy Architektury (Mermaid):** Wizualizacja diagramów przepływu (`flowchart`), sekwencji (`sequenceDiagram`), klas i baz danych (`erDiagram`).
  * **Kolorowanie Składni (SyntaxHighlighter):** Obsługa ponad 20 języków programowania (Kotlin, Python, Rust, Go, TypeScript, JavaScript, HTML, CSS, SQL, JSON, YAML itp.).

* 🛡️ **Audytor Uprawnień & Bezpieczeństwa (Security Auditor):**
  * Szybka inspekcja wrażliwych ścieżek (`.ssh`, `.aws`, `.config/gcloud`, `.devlens`).
  * Wykrywanie otwartych portów sieciowych, niebezpiecznych uprawnień do plików i katalogów.
  * Diagnostyka środowiska węzła (wersja OS, procesor, pamięć RAM, stan dysków, TCC/POSIX).

* 🖥️ **Natywna Aplikacja Desktopowa (`apps/desktop`):**
  * Zbudowana w Rust z zasobnikiem systemowym (Tray) na macOS, Windows i Linux.
  * Autostart przy logowaniu (`LaunchAgent`, Rejestr Windows, `.desktop` na Linux).
  * Profilaktyka usypiania systemu (`power.rs`) podczas aktywnych transferów i operacji węzła.
  * Wbudowany lokalny interfejs webowy (`http://localhost:8888`) ze statystykami węzła.

* ⚡ **Lekki Serwer Węzła Python (`apps/daemon`):**
  * Samodzielny serwer bez zewnętrznych bibliotek (Zero Dependencies) do szybkich wdrożeń w kontenerach lub środowiskach minimalistycznych.

* 🔄 **Wbudowany System Aktualizacji:**
  * Bezpośrednie sprawdzanie i instalacja aktualizacji APK oraz wydań desktopowych z GitHub Releases.

---

## 🏗️ Architektura

```mermaid
flowchart LR
    subgraph Host ["Komputer Roboczy (macOS / Windows / Linux)"]
        Tray["Zasobnik Systemowy (Tray) & Autostart"]
        D["devlens-desktop (Rust Daemon)"]
        Web["Web Dashboard (localhost:8888)"]
        FS["System Plików / Projekty"]
        Sec["Audyt Uprawnień & Bezpieczeństwa"]
        
        Tray --> D
        D --> Web
        D --> FS
        D --> Sec
    end

    subgraph Mobile ["Aplikacja Mobilna (DevLens Android)"]
        Dash["Dashboard: Węzły & Skaner LAN"]
        Exp["Eksplorator Plików"]
        Reader["Reader: Markdown + KaTeX + Mermaid + Kod"]
        Auditor["Centrum Audytu & Bezpieczeństwa"]

        Dash --> Exp
        Dash --> Auditor
        Exp --> Reader
    end

    Mobile <-->|"REST API / LAN (Zero-Touch Pairing)"| D
```

---

## 🚀 Szybki Start

### 1. Uruchomienie Aplikacji Desktopowej (Komputer)

Pobierz instalator dla swojego systemu z [sekcji wydań (Releases)](https://github.com/kacperczeczot/devlens/releases/latest):
* **macOS:** `DevLens.dmg` (lub binarka `DevLens-macOS`)
* **Windows:** `DevLens-Windows.exe`
* **Linux:** `DevLens-Linux`

Możesz także uruchomić ją ze źródeł:
```bash
cd apps/desktop
cargo run
```

Ikonka DevLens pojawi się w zasobniku systemowym (obok zegara). Kliknij na nią prawym przyciskiem myszy, aby skopiować PIN lub token parowania.

Alternatywnie możesz uruchomić lekki serwer w Pythonie:
```bash
python3 apps/daemon/server.py
```

### 2. Połączenie z telefonu (Android)

1. Zainstaluj plik **DevLens.apk** z [sekcji wydań (Releases)](https://github.com/kacperczeczot/devlens/releases/latest).
2. Upewnij się, że telefon i komputer są w tej samej sieci Wi-Fi.
3. Kliknij **„Skanuj i paruj w LAN”** na ekranie głównym aplikacji, aby automatycznie wykryć węzeł, lub dodaj go ręcznie podając IP i token/PIN.

---

## 🛠️ Budowanie Aplikacji ze Źródeł

Wymagany Android SDK oraz JDK 17:

```bash
cd apps/android
./gradlew assembleDebug
```

Wygenerowany plik APK znajdziesz w:
`apps/android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Licencja

Projekt udostępniany jest na warunkach licencji [MIT](LICENSE).
