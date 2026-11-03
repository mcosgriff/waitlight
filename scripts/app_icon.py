# Writes the launcher icon's vectors (foreground, background, monochrome) from simple shapes.
# Run from the repo root: `uv run scripts/app_icon.py app/src/main/res`. No dependencies.

import sys, pathlib
res = pathlib.Path(sys.argv[1])

def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 z"

def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} a{r},{r} 0 0 1 {-r},{r} "
            f"h{-(w - 2 * r)} a{r},{r} 0 0 1 {-r},{-r} v{-(h - 2 * r)} a{r},{r} 0 0 1 {r},{-r} z")

# 108dp canvas; launchers may mask to a 66dp circle at the centre (radius 33), so everything stays inside it.
HOUSING = rrect(41, 26, 26, 56, 9)
CX, R = 54, 7.5
LIGHTS = {"red": (38, "#F2C7C3"), "amber": (54, "#F5E2A0"), "green": (70, "#1B7F3B")}
CHECK = f"M{CX - 3.6},{70.3} l2.5,2.5 l5,-5.2"

def path(d, fill=None, stroke=None, width=None, extra=""):
    attrs = [f'android:pathData="{d}"']
    if fill: attrs.append(f'android:fillColor="{fill}"')
    if stroke:
        attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{width}"',
                  'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    if extra: attrs.append(extra)
    return "    <path\n        " + "\n        ".join(attrs) + " />\n"

def vector(body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- {comment} -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n' + body + "</vector>\n")

fg = path(HOUSING, "#F6F7F4")
for cy, colour in LIGHTS.values():
    fg += path(circle(CX, cy, R), colour)
fg += path(CHECK, stroke="#FFFFFF", width=2.4)
(res / "drawable/ic_launcher_foreground.xml").write_text(vector(fg,
    "Waitlight's icon: a traffic light with the green light lit and checked (the emissions test passed). "
    "Kept inside the adaptive icon's 66dp safe circle."))

bg = path("M0,0h108v108h-108z", "#1F4E5F")
(res / "drawable/ic_launcher_background.xml").write_text(vector(bg, "The theme's primary, #1F4E5F."))

# Themed icons tint every opaque pixel one colour: the housing with its lights cut out, and the check.
holes = " ".join(circle(CX, cy, R) for cy, _ in LIGHTS.values())
mono = path(f"{HOUSING} {holes}", "#FFFFFF", extra='android:fillType="evenOdd"')
mono += path(CHECK, stroke="#FFFFFF", width=2.4)
(res / "drawable/ic_launcher_monochrome.xml").write_text(vector(mono,
    "The icon in one colour, for themed icons: the housing with the lights cut out, and the check."))
