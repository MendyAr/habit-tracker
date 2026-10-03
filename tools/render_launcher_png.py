#!/usr/bin/env python3
"""Renders legacy (pre-Android 8) launcher PNGs that mirror the adaptive icons.

Usage: tools/render_launcher_png.py   (requires `pip install cairosvg`)
"""
import pathlib
import cairosvg

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
BRAND = "#1E8C7A"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

CHECK = f"""
<circle cx="54" cy="54" r="25" fill="none" stroke="#fff" stroke-opacity="0.55" stroke-width="3"/>
<circle cx="54" cy="54" r="17" fill="#fff"/>
<path d="M46.5,54.5L51.5,59.5L61.5,49.5" fill="none" stroke="{BRAND}" stroke-width="4"
      stroke-linecap="round" stroke-linejoin="round"/>"""

STATS = """
<path fill="#fff" d="M38,58h7a2,2 0,0 1,2 2v10a2,2 0,0 1,-2 2h-7a2,2 0,0 1,-2 -2v-10a2,2 0,0 1,2 -2zM50.5,40h7a2,2 0,0 1,2 2v28a2,2 0,0 1,-2 2h-7a2,2 0,0 1,-2 -2v-28a2,2 0,0 1,2 -2zM63,49h7a2,2 0,0 1,2 2v19a2,2 0,0 1,-2 2h-7a2,2 0,0 1,-2 -2v-19a2,2 0,0 1,2 -2z"/>"""


def svg(glyph, round_shape):
    # Legacy icons are 48dp with ~2dp padding; the 108dp adaptive canvas is cropped to its centre 76dp.
    shape = ('<circle cx="54" cy="54" r="36" fill="%s"/>' % BRAND if round_shape
             else '<rect x="18" y="18" width="72" height="72" rx="16" fill="%s"/>' % BRAND)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="16 16 76 76">{shape}{glyph}</svg>'


def main():
    for density, px in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        for name, glyph, rnd in [("ic_launcher", CHECK, False), ("ic_launcher_round", CHECK, True),
                                 ("ic_launcher_stats", STATS, True)]:
            cairosvg.svg2png(bytestring=svg(glyph, rnd).encode(), write_to=str(out / f"{name}.png"),
                             output_width=px, output_height=px)
    print("done")


if __name__ == "__main__":
    main()
