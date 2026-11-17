#!/usr/bin/env python3
"""Draws the Docker and deployment pictures for README.md into docs/assets.

    python3 tool/deploy_pictures.py

It borrows the colours, fonts and helpers from readme_assets.py (same folder), so these pictures look like the
others. tool/readme_assets.py calls run() at the end, so one command redraws everything. What the pictures say is
read from the repository where it can be (which deploy files exist), so they stay honest after a change.

  environments.svg   development, staging and production side by side        (auth, messenger)
  docker-dev.svg     what `docker compose up --build` starts on your computer (auth, messenger)
  pipeline.svg       from git push to a running service                       (auth, messenger)
  hosting.svg        which platform ships which file                          (auth, messenger)
  livekit.svg        setting up and starting LiveKit and the recorder         (messenger)
  whole-stack.svg    the whole system on one computer, in start order         (all three)
"""
import os
import sys
import textwrap


def run(H):
    """H is the module (or namespace) of readme_assets with the palette and helpers."""
    ROOT = H.ROOT
    repo = os.path.basename(ROOT.rstrip('/'))
    cfg = CONFIG.get(repo)
    if cfg:
        environments(H, cfg)
        docker_dev(H, cfg)
        pipeline(H, cfg)
        hosting(H, cfg)
        if cfg.get('livekit'):
            livekit_setup(H, cfg)
    whole_stack(H, repo)


CONFIG = {
    'premisave_auth_service': dict(
        title='auth-service', short='auth', port=8080, health='/health', repo='premisave_auth_service',
        dev_containers=['premisave-auth-dev', 'premisave-auth-dev-mongo', 'premisave-auth-dev-redis'],
        needs='MongoDB and Redis', note_dev='Email and profile pictures stay off until .env has the Gmail and Cloudinary values.'),
    'premisave-messenger-service': dict(
        title='messenger', short='messenger', port=8081, health='/health', repo='premisave-messenger-service',
        dev_containers=['premisave-messenger-dev', 'premisave-messenger-dev-mongo', 'premisave-messenger-dev-redis'],
        needs='MongoDB, Redis and the auth-service', livekit=True, note_dev='Start the auth-service first: sign in goes through it.'),
}

FONTS = ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')
RISE = ('.rise { animation: rise .7s cubic-bezier(.2,.8,.2,1) both; } '
        '@keyframes rise { from { opacity:0; transform: translateY(14px); } }\n')


def wrap(s, n):
    return textwrap.wrap(s, n, break_long_words=False, break_on_hyphens=False) or ['']


def text(H, x, y, s, size=13, cls='', style='', anchor=None, weight=None):
    a = f' text-anchor="{anchor}"' if anchor else ''
    w = f' font-weight="{weight}"' if weight else ''
    st = f' style="{style}"' if style else ''
    c = f' class="{cls}"' if cls else ''
    return f'<text{c} x="{x}" y="{y}" font-size="{size}"{a}{w}{st}>{H.esc(s)}</text>'


def lines(H, x, y, rows, size=12.5, gap=17, cls='', style=''):
    return ''.join(text(H, x, y + i * gap, r, size, cls, style) for i, r in enumerate(rows))


def arrow(H, x1, y1, x2, y2, col=None, dash=None, w=1.6, head=7):
    col = col or H.INK
    d = f' stroke-dasharray="{dash}"' if dash else ''
    import math
    ang = math.atan2(y2 - y1, x2 - x1)
    hx = [(x2, y2), (x2 - head * math.cos(ang - .45), y2 - head * math.sin(ang - .45)),
          (x2 - head * math.cos(ang + .45), y2 - head * math.sin(ang + .45))]
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2 - head * .6 * math.cos(ang):.1f}" y2="{y2 - head * .6 * math.sin(ang):.1f}" stroke="{col}" stroke-width="{w}"{d}/>'
            f'<polygon points="{" ".join(f"{px:.1f},{py:.1f}" for px, py in hx)}" fill="{col}"/>')


# ------------------------------------------------------------------------------------------ environments

def environments(H, c):
    w, h = 1200, 560
    cycle = 12.0
    s = c['short']
    p = c['port']
    cols = [
        ('development', 'dev', 'develop', 'development',
         ['docker compose up --build'],
         'a MongoDB container, throw-away', 'a Redis container', f'localhost:{p}'),
        ('staging', 'staging', 'staging', 'staging',
         ['docker compose', '--env-file deploy/env/staging.env', '-f deploy/docker-compose.staging.yml', 'up -d --build'],
         'a MongoDB container, its own database', 'a Redis container', f'127.0.0.1:{p}, then a proxy'),
        ('production', 'prod', 'main', 'production or 1.0.0',
         ['IMAGE_TAG=1.0.0 docker compose', '--env-file deploy/env/production.env', '-f deploy/docker-compose.production.yml', 'pull, then up -d'],
         'managed MongoDB (Atlas) with backups', 'a Redis container with a disk', f'127.0.0.1:{p}, then a proxy'),
    ]
    lx = 32
    lab = 150
    cw = (w - 64 - lab) / 3
    top = 96
    rows = [('profile', 62), ('branch and image tag', 62), ('start it with', 118), ('MongoDB', 62), ('Redis', 62), ('listens on', 62)]
    out = []
    y = top
    ys = []
    for name, hgt in rows:
        ys.append(y)
        out.append(f'<line x1="{lx}" y1="{y}" x2="{w - 32}" y2="{y}" stroke="{H.RULE}"/>')
        out.append(text(H, lx, y + 36, name, 14, 'cond', f'fill:{H.INK2}'))
        y += hgt
    end = y
    out.append(f'<line x1="{lx}" y1="{end}" x2="{w - 32}" y2="{end}" stroke="{H.INK}" stroke-width="1.5"/>')
    for i, (name, prof, br, tag, cmd, mongo, redis, listen) in enumerate(cols):
        x = lx + lab + i * cw + 8
        fill = H.INK if i == 2 else H.PAPER
        out.append(f'<g class="rise" style="animation-delay:{.1 + i * .12:.2f}s">')
        out.append(f'<text class="cond" x="{x}" y="{top - 14}" font-size="22">{name}</text>')
        out.append(f'<text class="mono" x="{x}" y="{ys[0] + 36}" font-size="14" font-weight="600" style="fill:{H.DATA}">SPRING_PROFILES_ACTIVE={prof}</text>')
        out.append(f'<text x="{x}" y="{ys[1] + 28}" font-size="13">branch <tspan class="mono" font-weight="600">{br}</tspan></text>')
        out.append(f'<text x="{x}" y="{ys[1] + 48}" font-size="13">tag <tspan class="mono" font-weight="600">{H.esc(tag)}</tspan></text>')
        out.append(lines(H, x, ys[2] + 30, cmd, 11, 17, 'mono'))
        out.append(lines(H, x, ys[3] + 36, [mongo], 13))
        out.append(lines(H, x, ys[4] + 36, [redis], 13))
        out.append(lines(H, x, ys[5] + 36, [listen], 13, 17, 'mono'))
        out.append('</g>')
    # the moving amber outline
    cwid = cw - 4
    out.append(f'<rect class="hi" x="{lx + lab + 2}" y="{top - 44}" width="{cwid}" height="{end - top + 50}" fill="none" stroke="{H.AMBER}" stroke-width="4"/>')
    css = RISE + f'.hi {{ opacity: 0; animation: hi {cycle}s infinite; }}\n' + H.band_kf('hi', cycle, 3, 3.6, cw, 'X')
    out.append(text(H, lx, end + 34, 'One image runs in all three. Only the variables change, above all SPRING_PROFILES_ACTIVE. Use a different database and different secrets in each.', 13.5))
    out.append(text(H, lx, end + 56, f'Copy deploy/env/<name>.env.example to deploy/env/<name>.env and fill it in. Filled-in env files are git-ignored.', 13.5, '', f'fill:{H.INK2}'))
    h = end + 78
    body = H.sheet(w, h, 'Three environments, one image', f'{c["title"]}') + ''.join(out)
    H.save('environments.svg', H.svg(w, h, body, css, '', f'{c["title"]} environments',
                                     'Development, staging and production run the same Docker image. They differ in the profile, the branch and image tag, the command, and where MongoDB lives.', FONTS))


# ------------------------------------------------------------------------------------------ docker-dev

def docker_dev(H, c):
    w, h = 1200, 480
    cycle = 10.0
    p = c['port']
    out = []
    # terminal
    tx, ty, tw, th = 32, 96, 470, 196
    out.append(f'<rect x="{tx}" y="{ty}" width="{tw}" height="{th}" fill="{H.INK}"/>')
    for i, col in enumerate((H.AMBER, H.SHEET_SOFT, H.SHEET_SOFT)):
        out.append(f'<circle cx="{tx + 20 + i * 18}" cy="{ty + 18}" r="5" fill="{col}"/>')
    cmds = [('cd ' + c['repo'], 0.2), ('docker compose up --build', 0.5), (f'curl localhost:{p}{c["health"]}', 0.8)]
    for i, (cmd, _) in enumerate(cmds):
        yy = ty + 62 + i * 44
        out.append(f'<text class="mono" x="{tx + 22}" y="{yy}" font-size="15" style="fill:{H.SHEET_SOFT}">$</text>'
                   f'<text class="mono" x="{tx + 42}" y="{yy}" font-size="15" font-weight="600" style="fill:{H.PAPER}">{H.esc(cmd)}</text>')
    out.append(f'<text class="mono" x="{tx + 42}" y="{ty + 62 + 3 * 44 - 12}" font-size="13" style="fill:{H.AMBER}">{{"status":"UP"}}</text>')
    out.append(text(H, tx, ty + th + 32, 'The first build takes a few minutes: Maven downloads the', 13.5))
    out.append(text(H, tx, ty + th + 50, 'dependencies once. After that it starts in about a minute.', 13.5))
    # containers
    cx = 600
    svc = (cx + 120, 96, 300, 112)
    mongo = (cx, 280, 250, 114)
    redis = (cx + 290, 280, 250, 114)
    boxes = [('service', svc, c['dev_containers'][0], f'Java 21, port {p}', 'published on this computer', H.INK),
             ('mongo', mongo, c['dev_containers'][1], 'mongo:7', 'not published, data in a volume', H.PAPER),
             ('redis', redis, c['dev_containers'][2], 'redis:7-alpine', 'not published, data in a volume', H.PAPER)]
    cyc = cycle
    for key, (x, y, bw, bh), name, l2, l3, fill in boxes:
        dark = fill == H.INK
        fg = H.PAPER if dark else H.INK
        out.append(f'<g class="box {key}"><rect x="{x}" y="{y}" width="{bw}" height="{bh}" fill="{fill}" stroke="{H.INK}" stroke-width="2"/>'
                   f'<text class="cond" x="{x + 18}" y="{y + 34}" font-size="20" style="fill:{fg}">{key if key != "service" else c["short"]}</text>'
                   f'<text class="mono" x="{x + 18}" y="{y + 58}" font-size="11" style="fill:{H.AMBER if dark else H.DATA}">{H.esc(name)}</text>'
                   f'<text x="{x + 18}" y="{y + 80}" font-size="12.5" style="fill:{H.SHEET_LINE if dark else H.INK2}">{H.esc(l2)}</text>'
                   f'<text x="{x + 18}" y="{y + 98}" font-size="12" style="fill:{H.SHEET_SOFT if dark else H.INK2}">{H.esc(l3)}</text></g>')
    sx, sy, sw, sh = svc
    mx, my, mw, mh = mongo
    rx, ry, rw, rh = redis
    out.append(arrow(H, sx + 60, sy + sh, mx + mw / 2 + 20, my, H.INK, None, 1.6))
    out.append(arrow(H, sx + sw - 60, sy + sh, rx + rw / 2 - 20, ry, H.INK, None, 1.6))
    # the outside world
    out.append(text(H, sx + sw + 24, sy + 40, 'your browser,', 13.5))
    out.append(text(H, sx + sw + 24, sy + 58, 'the app, curl', 13.5))
    out.append(arrow(H, sx + sw + 18, sy + 50, sx + sw + 2, sy + 50, H.AMBER, None, 2.2))
    out.append(text(H, 885, 252, 'waits until both answer', 12.5, '', f'fill:{H.INK2}', 'middle'))
    # animation: databases light first, then the service, then the answer
    css = RISE
    css += f'.mongo rect, .redis rect {{ animation: db {cyc}s infinite; }}\n'
    css += H.kf('db', cyc, [(0, f'stroke:{H.INK};stroke-width:2'), (1.0, f'stroke:{H.AMBER};stroke-width:5'), (3.4, f'stroke:{H.AMBER};stroke-width:5'),
                            (3.8, f'stroke:{H.INK};stroke-width:2'), (cyc, f'stroke:{H.INK};stroke-width:2')])
    css += f'.service rect {{ animation: sv {cyc}s infinite; }}\n'
    css += H.kf('sv', cyc, [(0, f'stroke:{H.INK};stroke-width:2'), (3.8, f'stroke:{H.INK};stroke-width:2'), (4.4, f'stroke:{H.AMBER};stroke-width:5'),
                            (8.0, f'stroke:{H.AMBER};stroke-width:5'), (8.4, f'stroke:{H.INK};stroke-width:2'), (cyc, f'stroke:{H.INK};stroke-width:2')])
    # a packet from the service to each database and back, while it runs
    for k, (x1, y1, x2, y2) in enumerate([(sx + 60, sy + sh, mx + mw / 2 + 20, my), (sx + sw - 60, sy + sh, rx + rw / 2 - 20, ry)]):
        nm = f'pk{k}'
        out.append(f'<circle class="{nm}" r="6" fill="{H.AMBER}" stroke="{H.INK}" stroke-width="1.5" cx="{x1}" cy="{y1}"/>')
        css += f'.{nm} {{ opacity:0; animation: {nm} {cyc}s infinite; }}\n'
        css += H.kf(nm, cyc, [(0, 'opacity:0;transform:translate(0,0)'), (5.0 + k * .5, 'opacity:0;transform:translate(0,0)'), (5.2 + k * .5, 'opacity:1;transform:translate(0,0)'),
                              (6.0 + k * .5, f'opacity:1;transform:translate({x2 - x1}px,{y2 - y1}px)'), (6.2 + k * .5, f'opacity:0;transform:translate({x2 - x1}px,{y2 - y1}px)'),
                              (cyc, 'opacity:0;transform:translate(0,0)')])
    # the stop strip
    sy2 = 420
    out.append(f'<line x1="32" y1="{sy2 - 14}" x2="{w - 32}" y2="{sy2 - 14}" stroke="{H.INK}" stroke-width="1.5"/>')
    items = [('Watch the log', 'docker compose logs -f'), ('Stop, keep your data', 'docker compose down'), ('Stop and wipe the data', 'docker compose down -v')]
    for i, (a, b) in enumerate(items):
        x = 32 + i * 380
        out.append(text(H, x, sy2 + 12, a, 14, 'cond', f'fill:{H.INK2}'))
        out.append(f'<text class="mono" x="{x}" y="{sy2 + 36}" font-size="14" font-weight="600" style="fill:{H.DATA if i < 2 else H.AMBER_INK}">{b}</text>')
    body = H.sheet(w, h, 'Run it with Docker on your computer', f'{c["title"]}, development') + ''.join(out)
    H.save('docker-dev.svg', H.svg(w, h, body, css, '', f'Running the {c["title"]} with Docker',
                                   f'docker compose up --build starts the {c["title"]} with its own MongoDB and Redis. It answers on port {p}. docker compose down stops it.', FONTS))


# ------------------------------------------------------------------------------------------ pipeline

def pipeline(H, c):
    w, h = 1200, 450
    cycle = 9.0
    img = f'peacemakerbill/{c["repo"]}'
    out = []
    n1 = (32, 104, 236, 150)
    n2 = (330, 104, 250, 150)
    n3 = (640, 96, 300, 166)
    t1 = (976, 80, 192, 112)
    t2 = (976, 208, 192, 112)

    def node(b, title, rows, dark=False, mono_rows=()):
        x, y, bw, bh = b
        fg = H.PAPER if dark else H.INK
        sub = H.SHEET_LINE if dark else H.INK2
        s = f'<rect x="{x}" y="{y}" width="{bw}" height="{bh}" fill="{H.INK if dark else H.PAPER}" stroke="{H.INK}" stroke-width="2"/>'
        s += f'<text class="cond" x="{x + 16}" y="{y + 32}" font-size="20" style="fill:{fg}">{H.esc(title)}</text>'
        for i, r in enumerate(rows):
            mono = i in mono_rows
            s += (f'<text class="{"mono" if mono else ""}" x="{x + 16}" y="{y + 58 + i * 21}" font-size="{11 if mono else 13}" '
                  f'style="fill:{(H.AMBER if dark else H.DATA) if mono else sub}">{H.esc(r)}</text>')
        return s
    out.append(node(n1, 'git push', ['develop    tag development', 'staging    tag staging', 'main       tag production', 'v1.2.3     tag 1.2.3'], False, (0, 1, 2, 3)))
    out.append(node(n2, 'GitHub Actions', ['.github/workflows/', 'docker-image.yml', 'builds the Dockerfile.', 'A pull request only', 'checks that it builds.'], True, (1,)))
    out.append(node(n3, 'ghcr.io', [img, 'tags: development, staging,', 'production, latest, 1.2.3,', 'and sha-<commit>'], False, (0,)))
    out.append(node(t1, 'Pull the image', ['Docker server,', 'Coolify, Fly.io,', 'Render image service']))
    out.append(node(t2, 'Build from source', ['Render Blueprint,', 'Railway, Heroku,', 'Coolify compose']))
    out.append(arrow(H, n1[0] + n1[2], 179, n2[0], 179, H.INK, None, 2))
    out.append(arrow(H, n2[0] + n2[2], 179, n3[0], 179, H.INK, None, 2))
    out.append(arrow(H, n3[0] + n3[2], 136, t1[0], 136, H.INK, None, 2))
    out.append(f'<path d="M{n1[0] + 118} {n1[1] + n1[3]}V372H{t2[0] + 96}V{t2[1] + t2[3] + 6}" fill="none" stroke="{H.INK}" stroke-width="1.5" stroke-dasharray="5 4"/>')
    out.append(f'<polygon points="{t2[0] + 96},{t2[1] + t2[3]} {t2[0] + 90},{t2[1] + t2[3] + 10} {t2[0] + 102},{t2[1] + t2[3] + 10}" fill="{H.INK}"/>')
    out.append(text(H, 34, 396, 'These platforms read the repository and build the Dockerfile themselves, so they do not need the published image.', 13.5, '', f'fill:{H.INK2}'))
    out.append(text(H, 34, 424, 'A new version: git tag v1.2.3 && git push origin v1.2.3. Production then runs IMAGE_TAG=1.2.3.', 13.5))
    css = RISE
    y1 = 179
    pts = [(n1[0] + n1[2] + 8, y1), (n2[0] - 8, y1), (n2[0] + n2[2] + 8, y1), (n3[0] - 8, y1), (n3[0] + n3[2] + 8, 136), (t1[0] - 8, 136)]
    x0, y0 = pts[0]
    out.append(f'<circle class="pk" r="7" fill="{H.AMBER}" stroke="{H.INK}" stroke-width="1.5" cx="{x0}" cy="{y0}"/>')
    steps = [(pts[0], pts[1], 0.6, 1.5), (pts[2], pts[3], 2.4, 3.9), (pts[4], pts[5], 4.8, 6.2)]
    frames = [(0, 'opacity:0')]
    for (a, bb, s0, s1) in steps:
        frames += [(s0, f'opacity:0;transform:translate({a[0] - x0}px,{a[1] - y0}px)'), (s0 + .15, f'opacity:1;transform:translate({a[0] - x0}px,{a[1] - y0}px)'),
                   (s1, f'opacity:1;transform:translate({bb[0] - x0}px,{bb[1] - y0}px)'), (s1 + .15, f'opacity:0;transform:translate({bb[0] - x0}px,{bb[1] - y0}px)')]
    frames.append((cycle, 'opacity:0'))
    css += f'.pk {{ opacity:0; animation: pk {cycle}s infinite; }}\n' + H.kf('pk', cycle, frames)
    body = H.sheet(w, h, 'From git push to a running service', f'{c["title"]}') + ''.join(out)
    H.save('pipeline.svg', H.svg(w, h, body, css, '', f'Publishing the {c["title"]} image',
                                 'A push to develop, staging or main, or a version tag, makes GitHub Actions build the Docker image and publish it to ghcr.io. A server or platform either pulls that image or builds from the Dockerfile.', FONTS))


# ------------------------------------------------------------------------------------------ hosting

def hosting(H, c):
    ROOT = H.ROOT
    w = 1200
    cycle = 12.0

    def has(*names):
        return all(os.path.exists(os.path.join(ROOT, n)) for n in names)
    plats = [
        ('Docker Compose', ['docker-compose.yml', '+ staging, production'], (has('docker-compose.yml'), has('deploy/docker-compose.staging.yml'), has('deploy/docker-compose.production.yml'))),
        ('Coolify', ['docker-compose', '.coolify.yml'], (has('deploy/docker-compose.coolify.yml'),) * 3),
        ('Render', ['render.yaml'], (has('deploy/render.yaml'),) * 3),
        ('Heroku', ['heroku.yml, app.json', '(repository root)'], (has('heroku.yml', 'app.json'),) * 3),
        ('Fly.io', ['fly.staging.toml', 'fly.production.toml'], (False, has('deploy/fly.staging.toml'), has('deploy/fly.production.toml'))),
        ('Railway', ['railway.json'], (has('deploy/railway.json'),) * 3),
    ]
    envs = [('development', 'dev'), ('staging', 'staging'), ('production', 'prod')]
    lx, colw, x0, top, rowh = 32, 166, 172, 176, 56
    out = [f'<text class="cond" x="{lx}" y="108" font-size="20">Environment</text>']
    for j, (name, files, _) in enumerate(plats):
        x = x0 + j * colw
        out.append(f'<text class="cond" x="{x + 6}" y="108" font-size="20">{name}</text>')
        for k, f in enumerate(files):
            out.append(f'<text class="mono soft" x="{x + 6}" y="{128 + k * 14}" font-size="10">{H.esc(f)}</text>')
    out.append(f'<line x1="{lx}" y1="{top - 14}" x2="{w - 32}" y2="{top - 14}" stroke="{H.INK}" stroke-width="1.5"/>')
    for i, (name, prof) in enumerate(envs):
        y = top + i * rowh
        out.append(f'<text class="cond" x="{lx}" y="{y + 20}" font-size="21">{name}</text>'
                   f'<text class="mono" x="{lx}" y="{y + 38}" font-size="11.5" style="fill:{H.DATA}">profile {prof}</text>'
                   f'<line x1="{lx}" y1="{y + rowh - 6}" x2="{w - 32}" y2="{y + rowh - 6}" stroke="{H.RULE}"/>')
        for j, (_, _, flags) in enumerate(plats):
            x = x0 + j * colw + 6
            if flags[i]:
                out.append(f'<rect x="{x}" y="{y + 8}" width="22" height="22" fill="{H.DATA}"/>'
                           f'<path d="M{x + 5} {y + 19}l5 5l8 -10" fill="none" stroke="{H.PAPER}" stroke-width="2.4"/>')
            else:
                out.append(f'<rect x="{x}" y="{y + 8}" width="22" height="22" fill="none" stroke="{H.RULE}" stroke-width="1.5"/>'
                           f'<path d="M{x + 6} {y + 19}h10" stroke="{H.RULE}" stroke-width="1.6"/>')
    css = RISE + f'.band {{ opacity: 0; animation: band {cycle}s infinite; }}\n' + H.band_kf('band', cycle, 3, 3.6, rowh, 'Y')
    out.append(f'<rect class="band" x="{lx - 6}" y="{top - 8}" width="{w - 52}" height="{rowh - 8}" fill="none" stroke="{H.AMBER}" stroke-width="4"/>')
    ey = top + 3 * rowh + 26
    out.append(text(H, lx, ey, 'A ticked box means the repository ships a ready file for that platform and stage. Every one of them runs the same Dockerfile.', 13.5))
    out.append(text(H, lx, ey + 22, 'The files are in the deploy folder, except the two Heroku reads from the repository root. Steps for each platform: deploy/README.md.', 13.5, '', f'fill:{H.INK2}'))
    h = ey + 46
    body = H.sheet(w, h, 'Where each environment can run', f'{c["title"]}') + ''.join(out)
    H.save('hosting.svg', H.svg(w, h, body, css, '', f'Where the {c["title"]} can be deployed',
                                'Docker Compose, Coolify, Render, Heroku, Fly.io and Railway each have a ready file in the repository.', FONTS))

# ------------------------------------------------------------------------------------------ livekit

def livekit_setup(H, c):
    w, h = 1200, 500
    cycle = 10.0
    out = []
    # the four commands, in order
    tx, ty, tw, th = 32, 96, 480, 236
    out.append(f'<rect x="{tx}" y="{ty}" width="{tw}" height="{th}" fill="{H.INK}"/>')
    for i, col in enumerate((H.AMBER, H.SHEET_SOFT, H.SHEET_SOFT)):
        out.append(f'<circle cx="{tx + 20 + i * 18}" cy="{ty + 18}" r="5" fill="{col}"/>')
    cmds = ['openssl rand -hex 32', 'mkdir -p recordings && chmod 777 recordings', 'docker compose --env-file ../../.env up -d',
            'curl localhost:7880']
    notes = ['1 the secret for .env', '2 where videos are saved', '3 LiveKit and the recorder', '4 answers OK']
    for i, cmd in enumerate(cmds):
        yy = ty + 58 + i * 44
        out.append(f'<text class="mono" x="{tx + 22}" y="{yy}" font-size="13" style="fill:{H.SHEET_SOFT}">$</text>'
                   f'<text class="mono" x="{tx + 40}" y="{yy}" font-size="13" font-weight="600" style="fill:{H.PAPER}">{H.esc(cmd)}</text>'
                   f'<text x="{tx + 40}" y="{yy + 17}" font-size="11.5" style="fill:{H.AMBER}">{H.esc(notes[i])}</text>')
    out.append(text(H, tx, ty + th + 30, 'Run 2 to 4 in deploy/livekit. The key and secret come from the', 13.5))
    out.append(text(H, tx, ty + th + 48, 'messenger .env, so both sides always agree.', 13.5))
    # the pieces
    bw, bh = 270, 112
    msg = (560, 96, 250, bh)
    lk = (900, 96, bw, bh)
    eg = (900, 280, bw, bh)
    fo = (560, 280, 250, bh)
    boxes = [
        ('msg', msg, 'messenger', 'port 8081', 'signs tokens, keeps records', 'reads the recordings folder', H.PAPER),
        ('lk', lk, 'LiveKit server', 'premisave-livekit', 'audio and video, port 7880', 'host network, UDP 50000-50200', H.INK),
        ('eg', eg, 'recorder', 'premisave-egress', 'LiveKit Egress, a headless', 'browser that saves an MP4', H.PAPER),
        ('fo', fo, 'recordings folder', 'CALLS_RECORDINGS_HOST_DIR', 'the same folder for the recorder', 'and the messenger', H.PAPER),
    ]
    for key, (x, y, bw_, bh_), title, mono, l2, l3, fill in boxes:
        dark = fill == H.INK
        fg = H.PAPER if dark else H.INK
        out.append(f'<g class="box {key}"><rect x="{x}" y="{y}" width="{bw_}" height="{bh_}" fill="{fill}" stroke="{H.INK}" stroke-width="2"/>'
                   f'<text class="cond" x="{x + 18}" y="{y + 34}" font-size="20" style="fill:{fg}">{H.esc(title)}</text>'
                   f'<text class="mono" x="{x + 18}" y="{y + 58}" font-size="11" style="fill:{H.AMBER if dark else H.DATA}">{H.esc(mono)}</text>'
                   f'<text x="{x + 18}" y="{y + 80}" font-size="12.5" style="fill:{H.SHEET_LINE if dark else H.INK2}">{H.esc(l2)}</text>'
                   f'<text x="{x + 18}" y="{y + 98}" font-size="12" style="fill:{H.SHEET_SOFT if dark else H.INK2}">{H.esc(l3)}</text></g>')
    # the links
    out.append(arrow(H, 810, 134, 900, 134, H.INK, None, 1.6))
    out.append(text(H, 855, 124, 'tokens', 12, '', f'fill:{H.INK2}', 'middle'))
    out.append(arrow(H, 900, 176, 810, 176, H.INK, '5 4', 1.6))
    out.append(text(H, 855, 194, 'webhook', 12, '', f'fill:{H.INK2}', 'middle'))
    out.append(arrow(H, 1035, 208, 1035, 280, H.INK, None, 1.6))
    out.append(text(H, 1047, 248, 'through Redis', 12, '', f'fill:{H.INK2}'))
    out.append(arrow(H, 900, 336, 810, 336, H.AMBER, None, 2.2))
    out.append(text(H, 855, 326, 'writes MP4', 12, '', f'fill:{H.INK2}', 'middle'))
    out.append(arrow(H, 685, 208, 685, 280, H.INK, None, 1.6))
    out.append(text(H, 697, 248, 'plays back', 12, '', f'fill:{H.INK2}'))
    # a packet from the recorder to the folder
    out.append(f'<circle class="pk" r="6" fill="{H.AMBER}" stroke="{H.INK}" stroke-width="1.5" cx="900" cy="336"/>')
    css = RISE
    base = f'stroke:{H.INK};stroke-width:2'
    on = f'stroke:{H.AMBER};stroke-width:5'
    for key, win in (('lk', (0.4, 3.0)), ('eg', (3.0, 5.6)), ('fo', (5.6, 8.0)), ('msg', (8.0, 9.6))):
        css += f'.{key} rect {{ animation: {key}lit {cycle}s infinite; }}\n'
        css += H.lit_kf(f'{key}lit', cycle, base, on, [win])
    css += f'.pk {{ opacity:0; animation: pk {cycle}s infinite; }}\n'
    css += H.kf('pk', cycle, [(0, 'opacity:0;transform:translateX(0)'), (5.0, 'opacity:0;transform:translateX(0)'), (5.2, 'opacity:1;transform:translateX(0)'),
                              (6.2, 'opacity:1;transform:translateX(-90px)'), (6.4, 'opacity:0;transform:translateX(-90px)'), (cycle, 'opacity:0;transform:translateX(0)')])
    # the everyday commands
    sy2 = 428
    out.append(f'<line x1="32" y1="{sy2 - 14}" x2="{w - 32}" y2="{sy2 - 14}" stroke="{H.INK}" stroke-width="1.5"/>')
    items = [('Is it running?', 'docker compose ps'), ('Read the log', 'docker compose logs -f livekit egress'),
             ('Stop it, videos stay', 'docker compose down')]
    for i, (a, b) in enumerate(items):
        x = 32 + i * 380
        out.append(text(H, x, sy2 + 12, a, 14, 'cond', f'fill:{H.INK2}'))
        out.append(f'<text class="mono" x="{x}" y="{sy2 + 36}" font-size="13" font-weight="600" style="fill:{H.DATA}">{H.esc(b)}</text>')
    body = H.sheet(w, h, 'Set up LiveKit for calls and recording', f'{c["title"]}, deploy/livekit') + ''.join(out)
    H.save('livekit.svg', H.svg(w, h, body, css, '', 'Setting up LiveKit',
                                'Make a secret, make a recordings folder, start LiveKit and the recorder with docker compose, then check port 7880. The messenger signs the tokens, LiveKit carries the media, and the recorder writes MP4 files to the folder the messenger plays back from.', FONTS))


# ------------------------------------------------------------------------------------------ whole stack

def whole_stack(H, repo):
    w = 1200
    cycle = 12.0
    steps = [
        ('1', 'auth-service', ':8080', ['premisave_auth_service'], 'docker compose up --build', 'identity, tokens, accounts', False),
        ('2', 'messenger', ':8081', ['premisave-messenger-service'], 'docker compose up --build', 'needs the auth-service', False),
        ('3', 'LiveKit', ':7880', ['premisave-messenger-service', '/deploy/livekit'], 'docker compose up -d', 'only for calls', True),
        ('4', 'Flutter app', 'your device', ['premisave_flutter_frontend'], './run.sh', 'the app you sign in with', False),
    ]
    bw, gap, top, bh = 250, 34, 100, 236
    out = []
    for i, (n, name, port, folder, cmd, note, optional) in enumerate(steps):
        x = 32 + i * (bw + gap)
        dark = i == 3
        fg = H.PAPER if dark else H.INK
        sub = H.SHEET_LINE if dark else H.INK2
        dash = ' stroke-dasharray="7 5"' if optional else ''
        out.append(f'<g class="rise" style="animation-delay:{.1 + i * .12:.2f}s"><g class="st s{i}">'
                   f'<rect x="{x}" y="{top}" width="{bw}" height="{bh}" fill="{H.INK if dark else H.PAPER}" stroke="{H.INK}" stroke-width="2"{dash}/></g>')
        out.append(H.num(x + 28, top + 34, n, 13, H.AMBER, H.INK))
        out.append(f'<text class="cond" x="{x + 54}" y="{top + 40}" font-size="23" style="fill:{fg}">{name}</text>')
        out.append(f'<text class="mono" x="{x + 18}" y="{top + 76}" font-size="14" font-weight="600" style="fill:{H.AMBER if dark else H.DATA}">{H.esc(port)}</text>')
        out.append(f'<text x="{x + 18}" y="{top + 100}" font-size="13" style="fill:{sub}">{H.esc(note)}</text>')
        yy = top + 138
        out.append(f'<text x="{x + 18}" y="{yy}" font-size="12" style="fill:{sub}">in the folder</text>')
        for j, row in enumerate(folder):
            out.append(f'<text class="mono" x="{x + 18}" y="{yy + 19 + j * 15}" font-size="10.5" style="fill:{fg}">{H.esc(row)}</text>')
        ry = yy + 19 + len(folder) * 15 + 14
        out.append(f'<text x="{x + 18}" y="{ry}" font-size="12" style="fill:{sub}">then run</text>')
        out.append(f'<text class="mono" x="{x + 18}" y="{ry + 21}" font-size="12.5" font-weight="600" style="fill:{H.AMBER if dark else H.INK}">{H.esc(cmd)}</text>')
        out.append('</g>')
        if i < 3:
            out.append(arrow(H, x + bw + 3, top + bh / 2, x + bw + gap - 3, top + bh / 2, H.INK, '5 4' if i == 1 else None, 2))
    py = top + bh + 30
    out.append(f'<line x1="32" y1="{py - 14}" x2="{w - 32}" y2="{py - 14}" stroke="{H.INK}" stroke-width="1.5"/>')
    facts = [('Order matters.', 'The messenger signs people in through the auth-service, so start that first.'),
             ('Calls are optional.', 'Calls stay off until LiveKit is running and its keys are in the messenger .env.'),
             ('Stop in any order.', 'docker compose down in each folder keeps the data. Add -v to wipe it.')]
    for i, (a_, b_) in enumerate(facts):
        y = py + 12 + i * 34
        out.append(text(H, 32, y, a_, 15, 'cond'))
        out.append(text(H, 190, y, b_, 13.5))
    css = RISE
    for i in range(4):
        css += f'.s{i} rect {{ animation: sa{i} {cycle}s infinite; }}\n'
        css += H.kf(f'sa{i}', cycle, [(0, f'stroke:{H.INK};stroke-width:2'), (0.4 + i * 2.2, f'stroke:{H.INK};stroke-width:2'), (0.8 + i * 2.2, f'stroke:{H.AMBER};stroke-width:6'),
                                      (2.6 + i * 2.2, f'stroke:{H.AMBER};stroke-width:6'), (3.0 + i * 2.2, f'stroke:{H.INK};stroke-width:2'), (cycle, f'stroke:{H.INK};stroke-width:2')])
    h = py + 12 + 3 * 34 + 6
    body = H.sheet(w, h, 'The whole system on one computer', 'start in this order') + ''.join(out)
    H.save('whole-stack.svg', H.svg(w, h, body, css, '', 'Running the whole Premisave system',
                                    'Start the auth-service, then the messenger, then LiveKit if you want calls, then the Flutter app. Each backend starts with its own database using docker compose up --build.', FONTS))


if __name__ == '__main__':
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import readme_assets as _H
    run(_H)
