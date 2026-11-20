"""Double the Palace study to approximately one metre per block and carve its wall faces."""
from pathlib import Path

from author_westminster import lines as study


def units(*coords):
    return " ".join(str(round(v*16)) for v in coords)


def facing(run, bounds, backing, joint=4):
    # A thin recessed mortar bed remains visible through the staggered joints.
    return ["box AIR "+units(*bounds), "box SAND "+units(*backing),
            f"masonry SAND {run} 48 16 {joint} 0 "+units(*bounds)]


def wall_details(part, words):
    if words[0:2] != ["box", "SAND"]:
        return []
    coords = tuple(int(v)/16 for v in words[2:])
    if part == "thames-range":
        if coords == (18,2,16,136,16,18):
            return facing("x",(36,4,32,272,32,32.25),(36,4,32.25,272,32,32.5),2)
        if coords == (18,2,35,136,16,37):
            return facing("x",(36,4,73.75,272,32,74),(36,4,73.5,272,32,73.75))
        if coords[2] == 13 and coords[5] == 17 and coords[4] == 17:
            left,right = coords[0]*2,coords[3]*2
            return facing("x",(left,2,26,right,34,26.25),(left,2,26.25,right,34,26.5),2)
    if part in ("elizabeth-tower","victoria-tower") and coords[1]==0:
        x,y,z,end,top,back = (n*2 for n in coords)
        return (["box AIR "+units(x+2,2,z+2,end-2,top-2,back-2)]
                +facing("x",(x,y,z,end,top,z+.25),(x,y,z+.25,end,top,z+.5))
                +facing("z",(x,y,z,x+.25,top,back),(x+.25,y,z,x+.5,top,back))
                +facing("z",(end-.25,y,z,end,top,back),(end-.5,y,z,end-.25,top,back))
                +facing("x",(x,y,back-.25,end,top,back),(x,y,back-.5,end,top,back-.25)))
    return []


def recipe():
    result=["building westminster-grand 4800 1632 2432 grid16 monument",
            "# Approximately one metre per block. Stone faces and carvings use grid16."]
    part=""
    for line in study[1:]:
        words=line.split()
        if not words or words[0].startswith("#"):
            continue
        if words[0]=="end":
            result.append("end")
            continue
        if words[0]=="part":
            part=words[1]
        result.append(" ".join(words[:2]+[str(2*int(v)) for v in words[2:]]))
        result.extend(wall_details(part,words))
    # A pierced quatrefoil frieze in two lengths between the projecting pavilions.
    for start,count in ((53,23),(164,23)):
        result.append("part frieze-"+str(start)+" "+units(start,25,30,start+count*4,28,32))
        result.append("row "+str(count)+" "+units(4,0,0))
        def carve(material, left, bottom, right, top, front):
            result.append("box "+material+" "+units(start+left,bottom,front,start+right,top,32))
        carve("SAND",0,25.5,4,27.5,30.75)
        carve("STONE",0,25,4,25.5,30.5)
        carve("SAND",0,27.5,4,28,30.5)
        carve("AIR",1.25,26.125,2.75,26.875,30.75)
        carve("AIR",1.625,25.75,2.375,27.25,30.75)
        # Small raised nailhead mouldings catch the light along the lower lip.
        carve("SAND",.25,25.125,.625,25.375,30.375)
        carve("SAND",.3125,25.1875,.5625,25.3125,30.3125)
        result.append("endrow")
        result.append("end")
    # Projecting jamb shafts, capitals and converging hood moulds flank the
    # recessed lancets. Each repeated bay has the same local carving sequence.
    for x in (60,170):
        result.extend(["part archivolts-"+str(x)+" "+units(x-1,15,30,x+85,25,33),
                       "row 9 "+units(10,0,0)])
        def mould(left,bottom,right,top,depth):
            result.append("box SAND "+units(x+left,bottom,depth,x+right,top,32.5))
        for left in (-.5,4):
            mould(left,16,left+.5,21,31.5)
            mould(left-.25,15.75,left+.75,16.25,31.25)
            mould(left-.25,21,left+.75,21.5,31.25)
        for step in range(5):
            offset=step*.5
            bottom=21.5+offset
            mould(-.5+offset,bottom,.25+offset,bottom+.5,31.25)
            mould(3.75-offset,bottom,4.5-offset,bottom+.5,31.25)
        mould(1.75,24,2.25,24.5,31)
        result.extend(["endrow","end"])
    return "\n".join(result)+"\n"


if __name__ == "__main__":
    path=Path(__file__).resolve().parents[1]/"assets/buildings/westminster-grand.vbuild"
    path.write_text(recipe(),encoding="utf-8",newline="\n")
