# /// script
# dependencies = ["resvg-py", "pillow"]
# ///
"""`uv run scripts/app_icon_render.py app/src/main/res design/png/AppIcon.png`

Renders the launcher icon's vectors (Android VectorDrawable XML) to a PNG sheet: the icon in the masks
launchers use, and the themed one-colour version."""
import io, sys, re, pathlib
import xml.etree.ElementTree as ET
import resvg_py
from PIL import Image, ImageDraw, ImageFont

res, out = pathlib.Path(sys.argv[1]), sys.argv[2]
A = "{http://schemas.android.com/apk/res/android}"

def svg_paths(name, recolour=None):
    root = ET.parse(res / "drawable" / name).getroot()
    parts = []
    for p in root.iter("path"):
        d = p.get(A + "pathData")
        fill = recolour or p.get(A + "fillColor") or "none"
        stroke = p.get(A + "strokeColor")
        attrs = f'd="{d}" fill="{fill if p.get(A + "fillColor") else "none"}"'
        if p.get(A + "fillType") == "evenOdd":
            attrs += ' fill-rule="evenodd"'
        if stroke:
            attrs += (f' stroke="{recolour or stroke}" stroke-width="{p.get(A + "strokeWidth")}"'
                      ' stroke-linecap="round" stroke-linejoin="round"')
        parts.append(f"<path {attrs}/>")
    return "".join(parts)

# The visible 72dp of the 108dp canvas, as launchers show it (18dp cut from each side).
def icon(mask, background, foreground, size=360):
    clip = {
        "circle": '<circle cx="54" cy="54" r="36"/>',
        "squircle": '<path d="M54,18 C84,18 90,24 90,54 C90,84 84,90 54,90 C24,90 18,84 18,54 C18,24 24,18 54,18 z"/>',
        "rounded": '<rect x="18" y="18" width="72" height="72" rx="14"/>',
    }[mask]
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
           f'<defs><clipPath id="m">{clip}</clipPath></defs><g clip-path="url(#m)">{background}{foreground}</g></svg>')
    return Image.open(io.BytesIO(bytes(resvg_py.svg_to_bytes(svg_string=svg))))

colour_bg, colour_fg = svg_paths("ic_launcher_background.xml"), svg_paths("ic_launcher_foreground.xml")
# Themed icons: Android tints the monochrome layer; shown here as Material You might, light and dark.
themed = [("#D3E8EF", "#0E4A5C"), ("#16333C", "#A9D6E2")]
tiles = [(icon(m, colour_bg, colour_fg), label) for m, label in
         (("circle", "Circle"), ("squircle", "Squircle"), ("rounded", "Rounded square"))]
for bg, fg in themed:
    tiles.append((icon("circle", f'<rect x="0" y="0" width="108" height="108" fill="{bg}"/>',
                       svg_paths("ic_launcher_monochrome.xml", recolour=fg)), "Themed" + (" (dark)" if bg.startswith("#16") else "")))
small = icon("circle", colour_bg, colour_fg, size=96)

pad, size = 40, 360
sheet = Image.new("RGB", (pad + len(tiles) * (size + pad), size + 2 * pad + 50 + 140), "#F6F7F4")
draw = ImageDraw.Draw(sheet)
try:
    font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 26)
except OSError:
    font = ImageFont.load_default()
for i, (tile, label) in enumerate(tiles):
    x = pad + i * (size + pad)
    sheet.paste(tile, (x, pad), tile)
    w = draw.textlength(label, font=font)
    draw.text((x + (size - w) / 2, pad + size + 12), label, fill="#3E4843", font=font)
# Actual launcher size (about 48dp at xhdpi), on light and dark wallpapers.
y = pad + size + 60
for j, wall in enumerate(("#E3E6E1", "#0F1513")):
    x = pad + j * 260
    draw.rounded_rectangle((x, y, x + 220, y + 120), 16, fill=wall)
    sheet.paste(small, (x + 62, y + 12), small)
draw.text((pad + 2 * 260, y + 45), "At launcher size, on light and dark wallpapers", fill="#3E4843", font=font)
sheet.save(out, optimize=True)
