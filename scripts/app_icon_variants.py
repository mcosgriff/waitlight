# /// script
# dependencies = ["resvg-py", "pillow"]
# ///
import io, sys
"""Sketches of app-icon ideas, side by side: A the traffic light alone (shipped), B a car waiting at the light
with exhaust from its tailpipe, C the exhaust as a checked puff (reads as a thought bubble; dropped).
`uv run scripts/app_icon_variants.py design/png/AppIcon-variants.png`"""
import resvg_py
from PIL import Image, ImageDraw, ImageFont

BG, LIGHT, DIM_R, DIM_A, GREEN, DARK = "#1F4E5F", "#F6F7F4", "#F2C7C3", "#F5E2A0", "#1B7F3B", "#12303A"

def circle(cx, cy, r, fill): return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"/>'
def rrect(x, y, w, h, r, fill): return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"/>'
def check(cx, cy, s, colour, width):
    return (f'<path d="M{cx - s},{cy + s * 0.05} l{s * 0.7},{s * 0.7} l{s * 1.4},{-s * 1.45}" fill="none" '
            f'stroke="{colour}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')

def light(x, y, w, h, r_lamp, gap):
    cx = x + w / 2
    top = y + h / 2 - gap
    return (rrect(x, y, w, h, w * 0.35, LIGHT) + circle(cx, top, r_lamp, DIM_R) + circle(cx, top + gap, r_lamp, DIM_A)
            + circle(cx, top + 2 * gap, r_lamp, GREEN) + check(cx, top + 2 * gap, r_lamp * 0.5, "#FFFFFF", r_lamp * 0.32))

def car(x, base, colour, k=1.0, pipe=True):  # side view, facing right; x = rear bumper, base = wheel centre line
    def X(v): return x + v * k
    def Y(v): return base - v * k
    body = rrect(X(0), Y(10), 44 * k, 10 * k, 4 * k, colour)
    cabin = (f'<path d="M{X(8)},{Y(9)} L{X(14)},{Y(18)} L{X(30)},{Y(18)} L{X(37)},{Y(9)} z" fill="{colour}" '
             f'stroke="{colour}" stroke-width="{2 * k}" stroke-linejoin="round"/>')
    windows = (f'<path d="M{X(12)},{Y(10.5)} L{X(15.5)},{Y(15.5)} L{X(21)},{Y(15.5)} L{X(21)},{Y(10.5)} z" fill="{BG}"/>'
               f'<path d="M{X(23.5)},{Y(10.5)} L{X(23.5)},{Y(15.5)} L{X(29)},{Y(15.5)} L{X(33.5)},{Y(10.5)} z" fill="{BG}"/>')
    wheels = "".join(circle(X(wx), base, 5 * k, DARK) + circle(X(wx), base, 2 * k, colour) for wx in (10, 34))
    tail = rrect(X(-3), Y(4.5), 5 * k, 2.6 * k, 1.3 * k, "#A9D6E2") if pipe else ""
    return tail + body + cabin + windows + wheels

# 1. Current: just the light.
current = light(41, 26, 26, 56, 7.5, 16)

# 2. A car waiting at the light, side by side; a little exhaust from its tailpipe.
car_at_light = (light(59, 28, 17, 38, 4.2, 10.5)
                + car(29, 74, LIGHT, k=0.66)
                + circle(25, 66.5, 2.2, "#A9D6E2") + circle(27.5, 61, 1.6, "#A9D6E2"))

# 3. The car's exhaust, checked: a puff from the tailpipe with a green check in it.
puff = (circle(33, 47, 9.5, LIGHT) + circle(43.5, 42, 10, LIGHT) + circle(51, 49, 7.5, LIGHT) + rrect(24.5, 46, 33, 12, 6, LIGHT))
exhaust_check = (car(44, 74, LIGHT, k=0.82)
                 + circle(39.5, 64.5, 2.2, LIGHT) + circle(37, 60.5, 1.6, LIGHT)
                 + puff + check(40, 49.5, 5.6, GREEN, 3.4))

def tile(fg, size=300, r=36):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
           f'<defs><clipPath id="m"><circle cx="54" cy="54" r="{r}"/></clipPath></defs>'
           f'<g clip-path="url(#m)"><rect width="108" height="108" fill="{BG}"/>{fg}</g></svg>')
    return Image.open(io.BytesIO(bytes(resvg_py.svg_to_bytes(svg_string=svg))))

variants = [("A  Now: the light", current), ("B  A car at the light", car_at_light), ("C  Exhaust, checked", exhaust_check)]
pad, size = 40, 300
sheet = Image.new("RGB", (pad + len(variants) * (size + pad), size + 3 * pad + 40 + 96), "#F6F7F4")
d = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 24)
for i, (label, fg) in enumerate(variants):
    x = pad + i * (size + pad)
    t = tile(fg); sheet.paste(t, (x, pad), t)
    d.text((x + (size - d.textlength(label, font=font)) / 2, pad + size + 10), label, fill="#3E4843", font=font)
    small = tile(fg, 96)
    d.rounded_rectangle((x + 40, pad + size + 50, x + 260, pad + size + 50 + 110), 14, fill="#0F1513")
    sheet.paste(small, (x + 102, pad + size + 57), small)
sheet.save(sys.argv[1])
