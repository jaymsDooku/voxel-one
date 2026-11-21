"""Rebuild the double-size palace from the checked-in grand palace recipe."""
from pathlib import Path


BUILDINGS = Path(__file__).resolve().parents[1] / "assets" / "buildings"


def doubled_recipe(source):
    result = []
    for line in source.splitlines():
        words = line.partition("#")[0].split()
        if not words:
            continue
        operation = words[0]
        if operation == "building":
            if words[1] != "westminster-grand" or words[5:] != ["grid16", "monument"]:
                raise ValueError("Expected the grid16 Westminster grand monument")
            result.append("building westminster-double " + " ".join(
                str(2 * int(n)) for n in words[2:5]) + " grid16 monument large")
            result.append("# Each axis is twice the size of westminster-grand. Grid units are 1/16 block.")
            continue
        if operation in ("end", "endrow"):
            result.append(operation)
            continue
        # Part and box bounds, row strides, and masonry dimensions and phase
        # are lengths. IDs, materials, directions and repetition counts stay fixed.
        first_length = {"part": 2, "box": 2, "row": 2, "masonry": 3}[operation]
        result.append(" ".join(words[:first_length] + [
            str(2 * int(n)) for n in words[first_length:]]))
    return "\n".join(result) + "\n"


if __name__ == "__main__":
    source = (BUILDINGS / "westminster-grand.vbuild").read_text(encoding="utf-8")
    (BUILDINGS / "westminster-double.vbuild").write_text(
        doubled_recipe(source), encoding="utf-8", newline="\n")
