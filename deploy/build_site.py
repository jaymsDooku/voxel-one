#!/usr/bin/env python3
"""Build the static website and navigation from the game's checked-in documentation."""
from pathlib import Path
import html, json, re, shutil

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'website/dist'
FAVICON = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Crect width=%2232%22 height=%2232%22 rx=%226%22 fill=%22%230b1015%22/%3E%3Cpath d=%22M16 5 27 11 16 17 5 11ZM5 13 15 19 15 28 5 22ZM17 19 27 13 27 22 17 28Z%22 fill=%22%2378e3c1%22/%3E%3C/svg%3E'

def page(title, description, current, body, script=''):
    links = ''.join('<a href="'+url+'"'+(' aria-current="page"' if current==key else '')+'>'+label+'</a>' for key,url,label in [('home','index.html','The game'),('docs','docs.html','Documentation'),('progress','progress.html','Development')])
    return '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0b1015"><meta name="description" content="'+html.escape(description,quote=True)+'"><title>'+html.escape(title)+' · Voxel One</title><link rel="icon" type="image/svg+xml" href="'+FAVICON+'"><link rel="stylesheet" href="site.css"></head><body><a class="skip button" href="#main">Skip to content</a><header><div class="bar"><a class="brand" href="index.html">VOXEL <span>ONE</span></a><nav aria-label="Main navigation">'+links+'</nav></div></header>'+body+'<footer><span>Voxel One · A voxel engine in active development</span><span><a href="https://github.com/jaymsDooku/voxel-one">Source</a><a href="https://github.com/jaymsDooku/voxel-one/releases/latest">Desktop releases</a></span></footer>'+script+'</body></html>'

def inline(text):
    tokens=[]
    def hold(value):tokens.append(value);return '\x00'+str(len(tokens)-1)+'\x00'
    text=re.sub(r'`([^`]+)`',lambda m:hold('<code>'+html.escape(m[1])+'</code>'),text)
    text=html.escape(text)
    text=re.sub(r'\[([^\]]+)\]\((https://[^\s)]+)\)',lambda m:hold('<a href="'+html.escape(html.unescape(m[2]),quote=True)+'">'+m[1]+'</a>'),text)
    text=re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',text)
    return re.sub('\x00(\d+)\x00',lambda m:tokens[int(m[1])],text)

def markdown(text):
    lines=text.splitlines();out=[];headings=[];i=0
    while i<len(lines):
        line=lines[i]
        if not line.strip():i+=1;continue
        if line.startswith('```'):
            code=[];i+=1
            while i<len(lines) and not lines[i].startswith('```'):code.append(lines[i]);i+=1
            out.append('<pre><code>'+html.escape('\n'.join(code))+'</code></pre>');i+=1;continue
        match=re.match(r'^(#{1,3}) (.+)$',line)
        if match:
            level=len(match[1]);name=match[2];anchor=re.sub(r'[^a-z0-9]+','-',name.lower()).strip('-')
            if level==2:headings.append((anchor,name))
            out.append('<h'+str(level)+' id="'+anchor+'">'+inline(name)+'</h'+str(level)+'>');i+=1;continue
        if line.startswith('|'):
            rows=[]
            while i<len(lines) and lines[i].startswith('|'):rows.append([c.strip() for c in lines[i].strip('|').split('|')]);i+=1
            out.append('<div class="table-wrap"><table><thead><tr>'+''.join('<th>'+inline(c)+'</th>' for c in rows[0])+'</tr></thead><tbody>')
            for row in rows[2:]:out.append('<tr>'+''.join('<td>'+inline(c)+'</td>' for c in row)+'</tr>')
            out.append('</tbody></table></div>');continue
        if line.startswith('- '):
            out.append('<ul>')
            while i<len(lines) and lines[i].startswith('- '):out.append('<li>'+inline(lines[i][2:])+'</li>');i+=1
            out.append('</ul>');continue
        paragraph=[line];i+=1
        while i<len(lines) and lines[i].strip() and not re.match(r'^(#|```|\||- )',lines[i]):paragraph.append(lines[i]);i+=1
        out.append('<p>'+inline(' '.join(paragraph))+'</p>')
    return ''.join(out),headings

def main():
    OUT.mkdir(parents=True,exist_ok=True);(OUT/'assets').mkdir(exist_ok=True)
    for name in ['player-nameplates-city.png','mayor-dashboard-overview.png','voxel-features-3d-created.png','voxel-lighting-placed-purple.png']:
        source=ROOT/'dashboard/evidence'/name
        if not source.exists():source=Path('/tmp')/name
        if source.exists():shutil.copy2(source,OUT/'assets'/name)
    shutil.copy2(ROOT/'dashboard/progress.json',OUT/'progress.json')
    body='''<main id="main"><section class="hero"><div><p class="eyebrow">Voxel City One · Built on the Voxel One engine</p><h1>Build a city.<br>Live in its world.</h1><p>Plan homes, shops and mines from the sky, then ride through the city you created. Follow citizens as they work, shop and return home. Build with friends in a shared voxel world.</p><div class="actions"><a class="button primary" href="https://github.com/jaymsDooku/voxel-one/releases/latest/download/Voxel-One-windows.zip">Download for Windows</a><a class="button" href="docs.html#build-and-download">Getting started</a></div><p class="timestamp" style="margin-top:16px">Desktop playtest · Java 17+ · OpenGL 3.3<br>Linux and Mac downloads available in the releases.</p></div><figure><img src="assets/player-nameplates-city.png" width="1280" height="720" alt="Voxel City One in isometric view, showing generated houses, a shop, a mine and dirt roads"><figcaption>The working city: polygon zones, generated buildings and a population simulation.</figcaption></figure></section><div class="strip"><span><strong>4</strong> procedural biomes</span><span><strong>36</strong> inventory slots</span><span><strong>95</strong> crafting recipes</span><span><strong>1/16</strong> world block size</span></div><section class="section"><h2>A city you can enter</h2><div class="features"><article class="feature"><h3>Draw the next neighbourhood</h3><p>Create convex polygon zones beside dirt roads. Act as mayor, budget for roads and designate land use. Private developers buy plots and fund houses, shops and mines.</p></article><article class="feature"><h3>Follow the people</h3><p>Twelve named citizens buy or rent homes and find company jobs, earn wages, get hungry and buy food. Open the mayor dashboard to monitor housing, jobs, hunger and savings across socioeconomic groups, then locate individual citizens.</p></article><article class="feature"><h3>Ride through your plans</h3><p>Leave the planning camera, right-click a horse and travel through the same streets. A configurable day and night cycle changes the city’s light.</p></article></div><p><a href="docs.html#voxel-city-one">Learn Voxel City One</a> · <a href="docs.html#mayor-dashboard">Explore the mayor dashboard</a></p></section><section class="section model-showcase"><div><p class="eyebrow">Know your city</p><h2>See how your citizens are doing.</h2><p class="muted">Monitor housing, employment, hunger and household savings. Compare socioeconomic groups, find people who need attention and keep track of the mayor’s treasury alongside private company finances.</p><a href="docs.html#mayor-dashboard">Open the dashboard with F9</a></div><img src="assets/mayor-dashboard-overview.png" width="1280" height="720" loading="lazy" alt="The mayor dashboard showing population, housing, employment, hunger, savings and needs attention metrics"></section><section class="section"><h2>An engine you can build with</h2><div class="features"><article class="feature"><h3>Explore together</h3><p>Walk, jump or fly through plains, forests, deserts and snowy mountains. The multiplayer server shares edits and saves your inventory between sessions.</p></article><article class="feature"><h3>Build at a smaller scale</h3><p>Cut materials into half, quarter, eighth or sixteenth cubes. Use precise shapes for constructions and combine the pieces again through crafting.</p></article><article class="feature"><h3>Make your own objects</h3><p>Sculpt coloured voxel models in 3D, from a tiny flower pot to a custom decoration. Publish a model as an item and place it in the shared world.</p></article></div></section><section class="section model-showcase"><div><p class="eyebrow">From an idea to an item</p><h2>Your model. Your colours.</h2><p class="muted">Add, paint, erase or pick colours directly on the model. Rotate the workspace, choose a brush size, and use any RGB colour. The layer editor stays available for precise slices.</p><a href="docs.html#tiny-voxel-models-and-the-editor">Learn the model editor</a></div><img src="assets/voxel-features-3d-created.png" width="1280" height="720" loading="lazy" alt="The 3D model editor showing a custom orange and blue voxel model and its editable layer"></section><section class="section model-showcase"><div><p class="eyebrow">Light changes the scene</p><h2>Colour your world with light.</h2><p class="muted">A visible sun casts shadows. Indirect light brings colour into shaded spaces, while surfaces reflect the sky. Craft LED blocks, pick any RGB colour, and light your builds with friends.</p><a href="docs.html#rendering-and-coloured-lights">Explore lighting and rendering</a></div><img src="assets/voxel-lighting-placed-purple.png" width="1280" height="720" loading="lazy" alt="A roofed gallery lit by red, green, blue and purple voxel LED blocks, with light colour spreading onto nearby surfaces"></section><section class="section"><h2>Follow the engine as it grows</h2><p class="muted">Voxel One is an active playtest. See what is complete, what is being built, and the evidence behind each change.</p><div class="actions"><a class="button" href="progress.html">Development progress</a><a class="button" href="docs.html">Feature documentation</a></div></section></main>'''
    (OUT/'index.html').write_text(page('Build, explore and sculpt','Explore Voxel One: a multiplayer voxel playground with procedural biomes, crafting and tiny custom models.','home',body))
    article,headings=markdown((ROOT/'README.md').read_text())
    nav=''.join('<a href="#'+a+'">'+html.escape(t)+'</a>' for a,t in headings)
    options=''.join('<option value="#'+a+'">'+html.escape(t)+'</option>' for a,t in headings)
    body='<main id="main" class="docs"><p class="eyebrow">Feature documentation</p><label for="docs-jump" class="docs-jump">Jump to a section</label><select id="docs-jump" class="docs-jump">'+options+'</select><div class="docs-layout"><aside class="docs-nav"><strong>ON THIS PAGE</strong><nav aria-label="Documentation sections" style="display:block">'+nav+'</nav></aside><article>'+article+'</article></div></main>'
    script='<script>document.getElementById("docs-jump").addEventListener("change",function(){location.hash=this.value;});</script>'
    (OUT/'docs.html').write_text(page('Documentation','Detailed Voxel One documentation: install, controls, multiplayer, worlds, crafting, models and server operation.','docs',body,script))
    body='''<main id="main"><div class="intro"><div><p class="eyebrow">Development record</p><h1>What’s happening in Voxel One</h1><p class="muted">Open a work item to inspect its implementation and testing evidence.</p></div><button id="refresh" type="button">Refresh progress</button></div><section class="questions" aria-labelledby="questions-title"><div class="question-top"><div><h2 id="questions-title">Questions for you</h2><p id="question-count" class="muted">Checking for questions…</p></div><button id="refresh-questions" type="button">Refresh questions</button></div><p id="question-state" role="status" aria-live="polite"></p><div id="question-list"></div></section><div class="metrics" aria-label="Progress summary"><div class="metric"><strong id="completed-count">—</strong><span>Completed</span></div><div class="metric"><strong id="active-count">—</strong><span>In progress</span></div><div class="metric"><strong id="queued-count">—</strong><span>Queued</span></div><div class="metric"><strong id="tests-count">—</strong><span id="test-label">Recorded tests</span></div></div><p id="updated-at" class="timestamp"></p><p id="load-state" role="status" aria-live="polite">Loading recorded progress…</p><div id="work"></div><noscript><p>Enable JavaScript to view the work items, or <a href="progress.json">open the saved progress record</a>.</p></noscript></main>'''
    (OUT/'progress.html').write_text(page('Development progress','Voxel One development progress with completed and current work items, build results, screenshots and testing evidence.','progress',body,'<script src="progress.js" defer></script><script src="questions.js" defer></script>'))
    print('Built landing page, documentation with '+str(len(headings))+' navigable sections, and development dashboard.')

if __name__=='__main__':main()
