#!/usr/bin/env python3
"""
Generuje ustrukturyzowane, profesjonalne Release Notes dla DevLens
na podstawie wpisu w CHANGELOG.md oraz tabeli instalatorów i sum kontrolnych.
"""
import sys
import os
import re

def generate_notes(repo: str, tag: str, changelog_path: str = "CHANGELOG.md") -> str:
    version = tag.lstrip("v")
    base_url = f"https://github.com/{repo}/releases/download/{tag}"
    changelog_url = f"https://github.com/{repo}/blob/{tag}/CHANGELOG.md"

    # Extract section from CHANGELOG.md
    highlights = []
    if os.path.exists(changelog_path):
        with open(changelog_path, "r", encoding="utf-8") as f:
            content = f.read()

        pattern = rf"## \[?{re.escape(version)}\]?[^\n]*\n(.*?)(?=\n## |\Z)"
        match = re.search(pattern, content, re.DOTALL)
        if match:
            raw_section = match.group(1).strip()
            for line in raw_section.splitlines():
                stripped = line.strip()
                if stripped.startswith("###"):
                    cat = stripped.lstrip("#").strip()
                    lower_cat = cat.lower()
                    icon = "🚀" if "dodano" in lower_cat or "feat" in lower_cat else (
                        "🛠️" if "naprawiono" in lower_cat or "fix" in lower_cat else (
                            "⚡" if "perf" in lower_cat else "📌"
                        )
                    )
                    highlights.append(f"\n#### {icon} {cat}")
                else:
                    highlights.append(line)

    if not highlights:
        highlights_block = f"Oficjalne wydanie DevLens v{version}."
    else:
        highlights_block = "\n".join(highlights).strip()

    notes = f"""# DevLens {tag} — Zdalny Eksplorator Kodu, Węzłów i Dokumentacji 🔍

**DevLens** to kompletny, wieloplatformowy ekosystem łączący zaawansowaną aplikację mobilną na system **Android** z natywnym agentem/daemonem w **Rust** wyposażonym w zasobnik systemowy (Tray), profilaktykę zasilania i autostart na **macOS**, **Windows** i **Linux**.

---

### 📦 Pakiety Instalacyjne i Binaria

| Platforma | Plik do pobrania | Opis |
| :--- | :--- | :--- |
| 📱 **Android** | [DevLens.apk]({base_url}/DevLens.apk) | Aplikacja mobilna (nowe logo, czytnik Markdown/KaTeX/Mermaid, audytor LAN) |
| 🍏 **macOS** | [DevLens.dmg]({base_url}/DevLens.dmg) | Instalator DMG z `DevLens.app` zintegrowany z zasobnikiem w pasku menu |
| 🍏 **macOS (CLI)** | [DevLens-macOS]({base_url}/DevLens-macOS) | Samodzielna binarka węzła (Apple Silicon / arm64) do uruchomienia z terminala |
| 🪟 **Windows** | [DevLens-Windows.exe]({base_url}/DevLens-Windows.exe) | Samodzielna aplikacja węzła w Rust z ikoną w trayu powiadomień |
| 🐧 **Linux** | [DevLens-Linux]({base_url}/DevLens-Linux) | Binarka węzła dla Linuksa (x86_64) z obsługą systemd i wskaźnika zasobnika |
| 📄 **Manifest** | [android-latest.json]({base_url}/android-latest.json) | Oficjalny manifest auto-aktualizacji dla aplikacji mobilnej |

---

### 🌟 Najważniejsze Zmiany i Nowości (v{version})

{highlights_block}

---

### 🛡️ Bezpieczeństwo i Architektura
* **Zero-Touch LAN Pairing:** Bezpieczna autoryzacja węzła za pomocą 4-cyfrowego kodu PIN lub tokenu.
* **Brak Zależności:** Serwer desktopowy i lekki runner Python nie wymagają instalacji zewnętrznych paczek pip ani ciężkich runtime'ów.
* **Prywatność:** Brak telemetrycznego śledzenia i brak zależności od zewnętrznych usług chmurowych – komunikacja odbywa się bezpośrednio w Twojej sieci lokalnej / VPN.

---

Pełna historia zmian projektu: [CHANGELOG.md]({changelog_url})
"""
    return notes

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: build-release-notes.py <repo> <tag> [changelog_path]")
        sys.exit(1)
    
    r = sys.argv[1]
    t = sys.argv[2]
    c = sys.argv[3] if len(sys.argv) > 3 else "CHANGELOG.md"
    print(generate_notes(r, t, c))
