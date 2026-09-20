[Strona główna](../../README.md) > [Reguły Projektu](project.md)

---

# Reguły Projektowe DevLens (AI Instructions) 🤖

Projekt podlega regułom inżynieryjnym określonym w **DevEx Standards** (`devex-standards`).

## Główne Zasady
1. **Model Monorepo**: Moduły wykonawcze znajdują się w `apps/` (`apps/android`, `apps/daemon`), a współdzielone paczki w `packages/`.
2. **Czystość Root**: Dozwolone są wyłącznie katalogi ze standardowego słownika (`apps`, `packages`, `docs`, `data`, `scripts`, `.agents`, `.github`).
3. **Brak zależności od czatu**: DevLens to wyłącznie zdalna przeglądarka plików, czytnik dokumentacji/kodu (Markdown, KaTeX, Mermaid) oraz audytor uprawnień. Nie implementować funkcji LLM chat / prompt streaming.
4. **Weryfikacja**: Przed commitem należy uruchomić `python3 scripts/check-standards.py`.
