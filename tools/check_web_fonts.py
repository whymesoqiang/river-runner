"""Fail before deployment if either bundled font lacks characters used by the app."""
from fontTools.ttLib import TTFont
from make_web_font import OUT, used_characters


def main() -> None:
    required = used_characters()
    failures = []
    for name in ("noto_sans_sc.ttf", "noto_sans_sc_bold.ttf"):
        with TTFont(OUT.with_name(name)) as font:
            cmap = font.getBestCmap()
            missing = sorted(c for c in required if ord(c) not in cmap or cmap[ord(c)] == ".notdef")
        if missing:
            failures.append(f"{name}: " + " ".join(f"{c} (U+{ord(c):04X})" for c in missing))
        else:
            print(f"{name}: all {len(required)} required characters covered")
    if failures:
        raise SystemExit("Missing web font glyphs. Regenerate both fonts with make_web_font.py and make_web_symbols.py:\n" + "\n".join(failures))


if __name__ == "__main__":
    main()
