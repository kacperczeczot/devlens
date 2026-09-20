[Strona główna](../../README.md) > [Aplikacje](../README.md) > **Desktop Daemon**

---

# DevLens Desktop Daemon (`apps/desktop`) 🖥️

Natywna, wysokowydajna aplikacja węzła w Rust zintegrowana z zasobnikiem systemowym (Tray) oraz interfejsem webowym, dedykowana dla platform **macOS**, **Windows** i **Linux**.

## Kluczowe Funkcjonalności

* 📂 **Zdalny Eksplorator Plików:** Błyskawiczne przeglądanie katalogów roboczych, pobieranie strumieniowe, wgrywanie plików i odczyt z podświetlaniem składni.
* 🛡️ **Audytor Bezpieczeństwa:** Inspekcja uprawnień systemowych (TCC, ACL, POSIX), analiza portów sieciowych, weryfikacja procesów i autoryzacja zapytań PIN.
* 💻 **Zasobnik Systemowy (Tray):** Natywna ikonka w menu statusu z podglądem portu, generowaniem/resetowaniem kodu PIN, kopiowaniem tokenu oraz panelem Web Dashboard.
* 🚀 **Autostart w Systemie:** Wsparcie dla uruchamiania przy logowaniu użytkownika (`LaunchAgent` na macOS, Registry na Windows, `.desktop`/`systemd` na Linux).
* 🔋 **Zarządzanie Zasilaniem (`power.rs`):** Zapobieganie usypianiu systemu (`PreventUserIdleSystemSleep` / `ES_SYSTEM_REQUIRED`) podczas pracy węzła.

## Budowanie i Uruchomienie

```bash
# Uruchomienie deweloperskie
cargo run

# Uruchomienie bez zasobnika systemowego (tryb headless serwer)
cargo run -- --no-tray --port 8888

# Budowanie paczki produkcyjnej
cargo build --release
```
