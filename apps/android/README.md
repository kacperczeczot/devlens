[Strona główna](../../README.md) > [apps](../README.md) > [android](README.md)

---

# DevLens - Aplikacja Android 📱🔍

Natywna aplikacja na system Android (Kotlin + Jetpack Compose) umożliwiająca przeglądanie plików na zdalnych węzłach (Mac, Linux, Windows), czytanie dokumentacji (Markdown z KaTeX i Mermaid), przeglądanie kodu oraz uruchamianie audytów bezpieczeństwa i uprawnień.

---

## ✨ Funkcjonalności

1. **Zdalny Eksplorator Plików**:
   - Przeglądanie struktury folderów i plików na podłączonych komputerach.
   - Nawigacja po ścieżce, sortowanie, pobieranie i wgrywanie plików.
2. **DevLens Reader**:
   - Pełne renderowanie Markdown z matematyką KaTeX i diagramami Mermaid.
   - Kolorowanie składni kodu źródłowego (SyntaxHighlighter).
3. **Audyt Uprawnień & Bezpieczeństwa**:
   - Sprawdzanie uprawnień do kluczy SSH, profili chmurowych i konfiguracji.
4. **Auto-Aktualizacje**:
   - Wykrywanie i instalowanie aktualizacji bezpośrednio z GitHub Releases.

## 🛠️ Budowanie
```bash
./gradlew assembleDebug
```
