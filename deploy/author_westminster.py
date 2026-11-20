"""Write the plan-led Palace of Westminster recipe in grid16 coordinates."""
from contextlib import contextmanager
from math import cos, pi, sin, sqrt
from pathlib import Path

WIDTH, HEIGHT, DEPTH = 150, 51, 76
lines = [f"building westminster {WIDTH * 16} {HEIGHT * 16} {DEPTH * 16} grid16",
         "# Approximate 2 metres per block. North is low X. The Thames is low Z."]


def grid(values):
    coordinates = [round(value * 16) for value in values]
    assert all(unit / 16 == value for unit, value in zip(coordinates, values))
    return " ".join(str(unit) for unit in coordinates)


@contextmanager
def part(name, x0, y0, z0, x1, y1, z1):
    lines.append(f"\npart {name} {grid((x0, y0, z0, x1, y1, z1))}")
    yield
    lines.append("end")


def box(material, x0, y0, z0, x1, y1, z1):
    lines.append(f"box {material} {grid((x0, y0, z0, x1, y1, z1))}")


def lantern(x, y, z):
    box("SAND", x-1, y, z-1, x+1, y+1, z+1)
    box("WOOD", x, y+1, z, x+.5, y+3, z+.5)


def front_lancet(x, y, z):
    # Recessed jambs and a stepped pointed head.
    box("AIR", x, y, z, x+2, y+3, z+1)
    box("AIR", x+.5, y+3, z, x+1.5, y+4, z+1)
    box("WOOD", x, y, z+.5, x+2, y+3, z+1)
    box("WOOD", x+.5, y+3, z+.5, x+1.5, y+4, z+1)
    box("SAND", x+.875, y, z+.375, x+1.125, y+3.5, z+.625)
    box("STONE", x-.25, y-.5, z-.5, x+2.25, y, z+1)
    box("SAND", x, y+1.75, z+.25, x+2, y+2, z+.5)
    for step in range(3):
        offset = step*.25
        box("SAND", x+offset, y+2.5+offset, z+.25, x+.25+offset, y+2.75+offset, z+.5)
        box("SAND", x+1.75-offset, y+2.5+offset, z+.25, x+2-offset, y+2.75+offset, z+.5)


with part("river-terrace", 3, 0, 8, 147, 3, 16):
    box("STONE", 3, 0, 8, 147, 1, 16)
    box("SAND", 3, 1, 8, 147, 1.5, 9)
    for x in range(8, 147, 4):
        box("SAND", x, 1.5, 8, x+.5, 2, 9)
    for x in range(4, 146, 2):
        box("SAND", x, 1.5, 8, x+.5, 2.5, 8.5)
    box("SAND", 3, 2.5, 8, 147, 2.75, 8.5)

with part("thames-range", 16, 0, 12, 137, 25, 37):
    box("STONE", 18, 0, 16, 136, 2, 37)
    box("SAND", 18, 2, 16, 136, 16, 18)
    box("SAND", 18, 2, 35, 136, 16, 37)
    box("SAND", 18, 2, 18, 20, 16, 35)
    box("SAND", 134, 2, 18, 136, 16, 35)
    for y in (6, 12, 15):
        box("SAND", 18, y, 15.5, 136, y+.5, 17)
    for x in range(20, 131, 5):
        front_lancet(x, 8, 16)
        box("WOOD", x+.5, 3, 15.875, x+1.5, 5, 16)
        box("WOOD", x+.5, 13, 15.875, x+1.5, 14.5, 16)
        box("WOOD", x+.5, 7, 36.75, x+1.5, 11, 37)
        box("SAND", x+3, 2, 15, x+3.5, 16, 17)
        box("SAND", x+3, 16, 16, x+4, 17, 17)
    for level in range(4):
        box("STONE", 18, 16+level, 17+2*level, 136, 17+level, 36-2*level)
    for x in (19, 42, 65, 88, 111, 134):
        box("SAND", x-1, 15, 16, x+2, 19, 20)
        lantern(x, 19, 18)
    # Three projecting pavilions interrupt the long run of recessed window bays.
    for x in (22, 77, 132):
        box("SAND", x-4, 1, 13, x+4, 17, 17)
        for y in (3, 9):
            for jamb in (x-3, x+.5):
                front_lancet(jamb, y, 13)
        for step in range(7):
            inset = step*.5
            box("STONE", x-4+inset, 17+step, 13, x+4-inset, 18+step, 22)
            box("SAND", x-4+inset, 17+step, 12.75, x+4-inset, 18+step, 13.5)
        front_lancet(x-1, 17, 13)
        # Recess through the projecting gable edging, revealing the tracery.
        box("AIR", x-1, 17, 12.75, x+1, 20, 13)
        for pier in (x-4, x+3):
            box("SAND", pier, 1, 12, pier+1, 20, 14)
            lantern(pier+1, 20, 14)
    for x in range(29, 129, 10):
        box("SAND", x, 17, 18, x+3, 20, 21)
        box("WOOD", x+1, 18, 17.75, x+2, 19.5, 18)
        box("STONE", x-.5, 20, 17.5, x+3.5, 20.5, 21.5)
        box("STONE", x+.5, 20.5, 18, x+2.5, 21.5, 21)
        for flue in (x, x+2):
            box("BRICKS", flue, 18, 29, flue+1, 22, 30)
            box("STONE", flue-.25, 22, 28.75, flue+1.25, 22.5, 30.25)

with part("landward-and-cross-wings", 12, 0, 22, 144, 18, 58):
    for x0, z0, x1, z1 in ((28,53,135,57), (27,36,32,53), (68,36,79,53),
                          (112,36,119,53), (13,22,21,34), (129,34,140,57)):
        box("STONE", x0, 0, z0, x1, 1, z1)
        box("SAND", x0, 1, z0, x1, 13, z1)
        box("STONE", x0, 13, z0, x1, 15, z1)
    for x in range(30, 131, 4):
        box("WOOD", x, 5, 52.75, x+1, 9, 53)
        box("WOOD", x, 5, 57, x+1, 9, 57.25)
    # Actual passage from Hall toward Commons Court.
    box("AIR", 29, 1, 53, 32, 5, 57)

with part("commons-and-royal-courts", 32, 0, 37, 112, 1, 53):
    box("STONE", 32, 0, 37, 68, .5, 53)
    box("STONE", 79, 0, 37, 112, .5, 53)
    for x in (33, 64, 80, 108):
        box("SAND", x, .5, 38, x+2, 1, 40)

# Shallow vaulted walks face the open courts. Build the arch heads in silhouette
# under a continuous gallery and carry the spring line across the pier capitals.
with part("court-galleries", 32, 0, 49, 112, 10, 53):
    for start, end in ((32, 68), (79, 112)):
        box("STONE", start, 8, 49, end, 9, 53)
        box("SAND", start, 7, 49, end, 8, 50)
        for x in range(start, end-3, 4):
            box("SAND", x, .5, 49, x+1, 6, 50)
            box("STONE", x, .5, 49, x+1.5, 1, 50.5)
            box("SAND", x, 5.5, 49, x+1.5, 6, 50.5)
            for rise in range(3):
                span = .5+rise*.5
                bottom = 6+rise*.5
                box("SAND", x, bottom, 49, x+span, bottom+.5, 50)
                box("SAND", x+4-span, bottom, 49, x+4, bottom+.5, 50)

with part("westminster-hall", 3, 0, 56, 42, 18, 69):
    box("STONE", 4, 0, 57, 41, 1, 68)
    box("SAND", 4, 1, 57, 41, 10, 58)
    box("SAND", 4, 1, 67, 41, 10, 68)
    box("SAND", 4, 1, 58, 5, 10, 67)
    box("SAND", 40, 1, 58, 41, 10, 67)
    for x in range(6, 40, 5):
        front_lancet(x, 4, 57)
        box("SAND", x+2, 1, 68, x+3, 8, 69)
        box("WOOD", x, 8, 58, x+.5, 9, 61)
        box("WOOD", x, 8, 64, x+.5, 9, 67)
        for step in range(4):
            box("WOOD", x, 9+step, 58+step, x+.5, 10+step, 59+step)
            box("WOOD", x, 9+step, 66-step, x+.5, 10+step, 67-step)
        box("WOOD", x, 13, 62, x+.5, 14, 63)
    for level in range(6):
        low, high = 57+level, 68-level
        box("STONE", 4, 10+level, low, 41, 11+level, low+1)
        if low+1 < high:
            box("STONE", 4, 10+level, high-1, 41, 11+level, high)
        box("SAND", 4, 10+level, low, 5, 11+level, high)
        box("SAND", 40, 10+level, low, 41, 11+level, high)
    box("AIR", 4, 1, 61, 5, 5, 64)
    box("AIR", 29, 1, 57, 32, 5, 58)
    for z in (60.5, 62, 63.5):
        box("WOOD", 3.875, 6, z, 4, 11, z+.75)
        box("SAND", 3.75, 7, z+.3125, 4, 10, z+.4375)
    for x in (15, 30):
        box("SAND", x, 16, 62, x+1, 17, 63)

with part("victoria-tower", 133, 0, 43, 150, 49, 60):
    box("SAND", 134, 0, 44, 149, 43, 59)
    for x in (134, 148):
        for z in (44, 58):
            box("SAND", x-.5, 2, z-.5, x+1.5, 42, z+1.5)
    for y in (6, 14, 25, 35, 42):
        box("SAND", 133.5, y, 43.5, 149.5, y+.5, 59.5)
    for x in (136, 140, 144):
        for y in (17, 27, 36):
            front_lancet(x, y, 44)
    # Galleries repeat on both side elevations and the landward face.
    for bottom in (16, 26, 36):
        for z in (46, 50, 54):
            for face in (134, 149):
                plane = face-.25 if face == 134 else face
                box("WOOD", plane, bottom, z, plane+.25, bottom+4, z+2)
                box("WOOD", plane, bottom+4, z+.5, plane+.25, bottom+5, z+1.5)
                box("SAND", plane, bottom, z+.75, plane+.25, bottom+4, z+1)
                box("SAND", plane, bottom+2, z, plane+.25, bottom+2.25, z+2)
        for x in (136, 140, 144):
            box("WOOD", x, bottom, 59, x+2, bottom+4, 59.25)
            box("WOOD", x+.5, bottom+4, 59, x+1.5, bottom+5, 59.25)
            box("SAND", x+.75, bottom, 59, x+1, bottom+4, 59.25)
    for x in (134, 146):
        for z in (44, 56):
            box("SAND", x, 41, z, x+3, 47, z+3)
            box("STONE", x+.5, 47, z+.5, x+2.5, 48, z+2.5)
            box("SAND", x+1, 48, z+1, x+2, 49, z+2)
    for i in range(137, 146, 2):
        box("SAND", i, 43, 44, i+1, 45, 45)
        box("SAND", i, 43, 58, i+1, 45, 59)
        box("SAND", 134, 43, i-90, 135, 45, i-89)
        box("SAND", 148, 43, i-90, 149, 45, i-89)
    box("AIR", 138, 0, 53, 145, 6, 59)
    box("AIR", 139, 6, 53, 144, 8, 59)
    box("AIR", 140, 8, 53, 143, 9, 59)
    box("WOOD", 138, 0, 52, 145, 6, 53)

with part("central-tower", 67, 0, 30, 82, 46, 45):
    box("SAND", 68, 1, 31, 81, 25, 44)
    for x in (68, 79):
        for z in (31, 42):
            box("SAND", x, 20, z, x+2, 29, z+2)
    for x in (71, 75):
        front_lancet(x, 20, 31)
    for y in range(25, 45):
        radius = max(1, 6-(y-25)//3)
        shoulder = max(.5, radius-1)
        box("SAND", 74.5-radius, y, 37.5-shoulder, 74.5+radius, y+1, 37.5+shoulder)
        box("SAND", 74.5-shoulder, y, 37.5-radius, 74.5+shoulder, y+1, 37.5+radius)
    box("STONE", 74.25, 45, 37.25, 74.75, 46, 37.75)


def clock(plane, level, center, outward):
    # Clock dials and hands use the same local profile on all four elevations.
    def panel(material, u0, y0, u1, y1, inset, thickness):
        a = outward + level*inset
        b = a + level*thickness
        # Local U points right as seen by a viewer standing outside each face.
        if (plane == "z" and level < 0) or (plane == "x" and level > 0):
            u0, u1 = -u1, -u0
        if plane == "z":
            box(material, center+u0, y0, min(a,b), center+u1, y1, max(a,b))
        else:
            box(material, min(a,b), y0, center+u0, max(a,b), y1, center+u1)

    mid = 27.5
    panel("STONE", -4, mid-4, 4, mid+4, 0, .25)
    for material, radius, inset in (("SAND", 3.75, .25), ("SNOW", 3.375, .5)):
        for row in range(round(radius*8)):
            bottom = row*.25-radius
            width = round(sqrt(radius**2-(bottom+.125)**2)*8)/8
            panel(material, -width, mid+bottom, width, mid+bottom+.25, inset, .25)
    # Each Roman numeral is built from sixteenth-block strokes. Quarter hours
    # use the same glyphs as the intermediate hours on all four elevations.
    glyphs = {"I": ("1",)*5, "V": ("101","101","101","101","010"),
              "X": ("101","101","010","101","101")}
    for hour, numeral in enumerate(("XII","I","II","III","IV","V","VI","VII","VIII","IX","X","XI")):
        angle = hour*pi/6
        label_width = sum(len(glyphs[c][0])+1 for c in numeral)-1
        left = round(16*2.625*sin(angle)-label_width)
        top = round(16*(mid+2.625*cos(angle)))+5
        for char in numeral:
            for row, pixels in enumerate(glyphs[char]):
                for col, pixel in enumerate(pixels):
                    if pixel == "1":
                        u, y = (left+2*col)/16, (top-2*row)/16
                        panel("WOOD", u, y-.125, u+.125, y, .75, .0625)
            left += 2*(len(glyphs[char][0])+1)
    for minute in range(60):
        angle = minute*pi/30
        u, y = round(50*sin(angle))/16, mid+round(50*cos(angle))/16
        panel("WOOD", u, y, u+.0625, y+.125, .75, .0625)
    # Set each dial to 10:10. A separate hub covers the junction of the hands.
    for direction, reach in ((-1, 11), (1, 16)):
        for step in range(reach):
            u = direction*step*.125
            y = mid+round(step*.125*.58*16)/16
            panel("WOOD", u-.125, y-.0625, u+.125, y+.125, .75, .1875)
    panel("SAND", -.1875, mid-.1875, .1875, mid+.1875, .9375, .0625)
    for shield in range(6):
        u = -3.5+shield*1.25
        panel("SNOW", u, 31.5, u+.75, 32.25, .25, .25)
        panel("BRICKS", u+.25, 31.5, u+.5, 32.25, .5, .125)
        panel("BRICKS", u, 31.875, u+.75, 32, .5, .125)


with part("elizabeth-tower", 2, 0, 13, 15, 48, 26):
    box("SAND", 3, 0, 14, 14, 34, 25)
    for y in (3, 18, 23, 30, 33):
        box("SAND", 2.5, y, 13.5, 14.5, y+.5, 25.5)
    for x in (4, 7, 10):
        front_lancet(x, 11, 14)
        box("WOOD", x, 31, 13.75, x+1, 33, 14)
        box("WOOD", x, 31, 25, x+1, 33, 25.25)
    for z in (16, 19, 22):
        box("WOOD", 2.75, 31, z, 3, 33, z+1)
        box("WOOD", 14, 31, z, 14.25, 33, z+1)
    for args in (("z",-1,8.5,14), ("z",1,8.5,25), ("x",-1,19.5,3), ("x",1,19.5,14)):
        clock(*args)
    for x in (3, 13):
        for z in (14, 24):
            box("SAND", x, 33, z, x+1, 38, z+1)
    for level in range(12):
        width = max(.5, 5-level*.5)
        box("STONE", 8.5-width, 34+level, 19.5-width, 8.5+width, 35+level, 19.5+width)
        for x in (8.5-width, 8+width):
            for z in (19.5-width, 19+width):
                box("SAND", x, 34+level, z, x+.5, 35+level, z+.5)
    box("SAND", 7.5, 42, 18.5, 9.5, 42.5, 20.5)
    box("SNOW", 8, 42.5, 19, 9, 44, 20)
    box("STONE", 7.75, 44, 18.75, 9.25, 44.5, 20.25)
    box("SAND", 8.25, 46, 19.25, 8.75, 48, 19.75)

if __name__ == "__main__":
    out = Path(__file__).resolve().parents[1] / "assets/buildings/westminster.vbuild"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
