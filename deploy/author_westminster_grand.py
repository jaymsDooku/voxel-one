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


def roof_details():
    result=[]

    def box(material,*bounds):
        result.append("box "+material+" "+units(*bounds))

    def row(count,stride):
        result.append("row "+str(count)+" "+units(stride,0,0))

    # Both pitches meet at Z=53. Half-block rises preserve the slope at
    # pedestrian distance, while continuous eaves and the crest read from afar.
    result.append("part river-roof "+units(35,31,33,273,49,73))
    for step in range(20):
        left, right, y = 33+step, 73-step, 32+step*.5
        for z in (left,right-1):
            box("STONE",36,y,z,272,y+1,z+1)
            row(29,8)
            box("STONE",38,y+1,z,38.25,y+1.25,z+1)
            result.append("endrow")
    for z in (33,72):
        box("STONE",35.5,31.5,z,272.5,32.5,z+1)
    box("STONE",36,42.5,52.5,272,43,53.5)
    # Low open ridge rail with a taller spear and two shoulders every four blocks.
    box("WOOD",36,44,52.75,272,44.25,53.25)
    row(59,4)
    box("WOOD",36,43,52.75,36.5,45,53.25)
    box("SAND",36.125,45,52.875,36.375,45.75,53.125)
    box("WOOD",37.5,43,52.75,38,44,53.25)
    result.extend(["endrow","end"])

    result.append("part hall-roof "+units(7,19,113,83,40,137))
    for step in range(22):
        z=114+step*.5
        y=21+step*.5
        for face in (z,250-z-.5):
            box("STONE",8,y,face,82,y+1,face+.5)
            row(12,6)
            box("STONE",10,y+1,face,10.25,y+1.25,face+.5)
            result.append("endrow")
    box("STONE",7.75,32,124.5,82.25,33,125.5)
    # Two louvred ridge lanterns, with lead hips and a small gilded finial.
    for x in (30,60):
        box("STONE",x-3,31,122,x+4,32,128)
        box("WOOD",x-2,32,123,x+3,35,127)
        for y in (32.5,33.5,34.5):
            box("SAND",x-2.25,y,122.75,x+3.25,y+.25,127.25)
        for level in range(4):
            inset=level*.5
            box("STONE",x-3+inset,35+level*.5,122+inset,x+4-inset,35.5+level*.5,128-inset)
        box("SAND",x+.25,37,124.75,x+.75,39,125.25)
    result.append("end")
    # Shorter hipped ranges enclose the courts on the landward side.
    result.append("part courtyard-roofs "+units(54,30,72,270,42,114))
    for x0,z0,x1,z1 in ((56,106,270,114),(54,72,64,106),(136,72,158,106),(224,72,238,106)):
        courses=min(x1-x0,z1-z0)//2
        for inset in range(courses):
            box("STONE",x0+inset,30+inset*.5,z0+inset,
                x1-inset,30.5+inset*.5,z1-inset)
        top=30+courses*.5
        if x1-x0>z1-z0:
            box("STONE",x0+courses,top,(z0+z1)/2-.5,x1-courses,top+.5,(z0+z1)/2+.5)
        else:
            box("STONE",(x0+x1)/2-.5,top,z0+courses,(x0+x1)/2+.5,top+.5,z1-courses)
    for x in range(72,253,24):
        box("BRICKS",x,31,109,x+2,35,111)
        box("STONE",x-.25,35,108.75,x+2.25,35.5,111.25)
        box("WOOD",x+.5,35.5,109.5,x+1.5,36,110.5)
    result.append("end")
    return result


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
        if part=="thames-range" and words[:2]==["box","STONE"]:
            x,y,z,end,top,back=(int(v)/16 for v in words[2:])
            if x==18 and end==136 and 16<=y<20 and z==17+2*(y-16):
                continue
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
    result.extend(roof_details())
    return "\n".join(result)+"\n"


if __name__ == "__main__":
    path=Path(__file__).resolve().parents[1]/"assets/buildings/westminster-grand.vbuild"
    path.write_text(recipe(),encoding="utf-8",newline="\n")
