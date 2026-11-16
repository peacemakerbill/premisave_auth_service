#!/usr/bin/env python3
"""Draws the pictures used by the README files into docs/assets.

    python3 tool/readme_assets.py

Only the Python standard library is needed. The pictures are plain SVG with CSS animation, so GitHub
shows them as they are, with no scripts and no outside files. The type is embedded from tool/fonts (small
subsets of IBM Plex Sans, IBM Plex Sans Condensed and JetBrains Mono, all under the SIL Open Font License, the
same fonts as the Premisave app), so the lettering looks the same on every computer.

The endpoint counts, the access table, the code statistics, the risk weights and the library versions are read
from the real source files, so run this again after a big change to refresh them.
"""
import base64
import math
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'docs', 'assets')
FONTS = os.path.join(ROOT, 'tool', 'fonts')

# The look: a drafting sheet. Cool paper, green black ink, one amber for the thing that matters.
PAPER = '#F4F6F3'
PAPER2 = '#E8EDE9'
INK = '#0F2A22'
INK2 = '#4C655B'
RULE = '#BAC7C0'
DATA = '#1F6B55'      # the one colour the charts use
AMBER = '#E3A72F'
AMBER_INK = '#8A5F0A'  # amber that stays readable as text on paper
SHEET = '#0B2F25'      # the dark sheet, banner only
SHEET_LINE = '#DCE8E1'
SHEET_SOFT = '#7FA797'

SANS = "'PS Sans','IBM Plex Sans','Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif"
COND = "'PS Cond','IBM Plex Sans Condensed','Arial Narrow','Helvetica Neue',Arial,sans-serif"
MONO = "'PS Mono','JetBrains Mono',ui-monospace,SFMono-Regular,Menlo,Consolas,monospace"

FACES = {
    'cond7': ('PS Cond', 700, 'plex-cond-700.woff2'),
    'cond6': ('PS Cond', 600, 'plex-cond-600.woff2'),
    'sans4': ('PS Sans', 400, 'plex-400.woff2'),
    'sans6': ('PS Sans', 600, 'plex-600.woff2'),
    'mono4': ('PS Mono', 400, 'mono-400.woff2'),
    'mono6': ('PS Mono', 600, 'mono-600.woff2'),
}
_cache = {}


def font_css(*keys):
    out = []
    for k in keys:
        fam, weight, fname = FACES[k]
        if fname not in _cache:
            with open(os.path.join(FONTS, fname), 'rb') as f:
                _cache[fname] = base64.b64encode(f.read()).decode('ascii')
        out.append(f"@font-face{{font-family:'{fam}';font-weight:{weight};src:url(data:font/woff2;base64,{_cache[fname]}) format('woff2');}}")
    return ''.join(out)


BASE_CSS = f"""
text {{ font-family: {SANS}; fill: {INK}; }}
.cond {{ font-family: {COND}; font-weight: 700; }}
.mono {{ font-family: {MONO}; }}
.soft {{ fill: {INK2}; }}
@media (prefers-reduced-motion: reduce) {{ * {{ animation: none !important; }} }}
"""


def esc(s):
    return str(s).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def svg(width, height, body, css='', defs='', title='', desc='', fonts=('cond7', 'sans4', 'sans6')):
    head = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" role="img" aria-labelledby="t d">'
    meta = f'<title id="t">{esc(title)}</title><desc id="d">{esc(desc or title)}</desc>'
    return f'{head}{meta}<defs>{defs}</defs><style>{font_css(*fonts)}{BASE_CSS}{css}</style>{body}</svg>\n'


def save(name, content):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        f.write(content)
    print('wrote', name, len(content) // 1024, 'KB')


def sheet(w, h, title, note=None):
    """A light drawing sheet: paper, an inset border, a heading and the rule under it."""
    out = (f'<rect width="{w}" height="{h}" fill="{PAPER}"/>'
           f'<rect x="8.5" y="8.5" width="{w - 17}" height="{h - 17}" fill="none" stroke="{INK}" stroke-width="1"/>'
           f'<text class="cond" x="32" y="52" font-size="26">{esc(title)}</text>')
    if note:
        out += f'<text class="soft" x="{w - 32}" y="50" text-anchor="end" font-size="13.5">{esc(note)}</text>'
    out += f'<line x1="32" y1="68" x2="{w - 32}" y2="68" stroke="{INK}" stroke-width="1.5"/>'
    return out


def num(x, y, n, r=11, fill=INK, ink=PAPER):
    """A numbered callout, the circle architects use to point at a detail."""
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}"/>'
            f'<text x="{x}" y="{y + 4.4}" text-anchor="middle" font-size="12.5" font-weight="600" style="fill:{ink}" class="mono">{n}</text>')


# ---------------------------------------------------------------------------------------- motion helpers
#
# Every loop is plain CSS, so a device that asks for reduced motion stops all of it (BASE_CSS). The rule that keeps
# that safe: an element's own style is always its finished, readable state, and the keyframes only add the motion.

def pc(t, cycle):
    return f'{min(max(t / cycle * 100, 0), 100):.3f}%'


def kf(name, cycle, frames):
    """Keyframes from [(seconds, 'declarations')], written as percentages of the cycle."""
    frames = sorted(frames, key=lambda f: f[0])
    return f'@keyframes {name} {{ ' + ' '.join(f'{pc(t, cycle)} {{ {s} }}' for t, s in frames) + ' }\n'


def lit_kf(name, cycle, base, on, wins, fade=0.25):
    """Keyframes that sit on `base` and switch to `on` during each (start, end) window, in seconds."""
    frames = []
    for s, e in wins:
        frames += [(s, base), (s + fade, on), (e, on), (e + fade, base)]
    if not frames or frames[0][0] > 0:
        frames.insert(0, (0, base))
    if frames[-1][0] < cycle:
        frames.append((cycle, base))
    return kf(name, cycle, [(min(t, cycle), s) for t, s in frames])


def band_kf(name, cycle, n, hold, pitch, axis='Y', glide=0.4):
    """A highlight band that rests on item after item (n items, `pitch` px apart) and then fades."""
    def at(k, op):
        return f'opacity:{op};transform:translate{axis}({k * pitch}px)'
    frames = [(0, at(0, 0)), (0.3, at(0, 1))]
    for k in range(n):
        frames += [(k * hold + (0.3 if k == 0 else 0), at(k, 1)), ((k + 1) * hold - glide, at(k, 1))]
    frames += [(n * hold - glide + 0.3, at(n - 1, 0)), (cycle, at(n - 1, 0))]
    return kf(name, cycle, frames)


def logo_mark(x, y, s):
    """The Premisave mark: a cream house beside an amber house, on a 1024 unit grid."""
    wins = ''.join(f'<rect class="lgw" style="--d:{1.1 + k * 0.12:.2f}s" x="{wx}" y="{wy}" width="48" height="48" fill="{SHEET}"/>'
                   for k, (wx, wy) in enumerate(((582, 541), (649, 541), (582, 608), (649, 608))))
    return (f'<g transform="translate({x} {y}) scale({s})">'
            f'<path class="lg1" d="M282 742V474L512 282V742H445V617A48 48 0 0 0 349 617V742Z" fill="{SHEET_LINE}"/>'
            f'<path class="lg2" d="M536 742V302L742 474V742Z" fill="{AMBER}"/>{wins}</g>')


# ---------------------------------------------------------------------------------------- source readers

JAVA = os.path.join('src', 'main', 'java', 'com', 'premisave', 'auth')


def read(path):
    with open(os.path.join(ROOT, path), encoding='utf-8') as f:
        return f.read()


def java_files(sub=''):
    base = os.path.join(ROOT, JAVA, sub)
    out = []
    for dirpath, _dirs, files in os.walk(base):
        for name in sorted(files):
            if name.endswith('.java'):
                out.append(os.path.join(dirpath, name))
    return sorted(out)


def code_lines(path):
    """Lines of Java that are neither blank nor comments."""
    n, in_block = 0, False
    with open(path, encoding='utf-8') as f:
        for line in f:
            t = line.strip()
            if in_block:
                if '*/' in t:
                    in_block = False
                continue
            if not t or t.startswith('//'):
                continue
            if t.startswith('/*'):
                if '*/' not in t:
                    in_block = True
                continue
            n += 1
    return n


# What each controller is called in the pictures and the docs.
AREAS = [
    ('Sign in and accounts', ['AuthController', 'SecurityActionController']),
    ('Profiles and views', ['ProfileController', 'ProfileViewController']),
    ('Social', ['SocialController']),
    ('Location', ['LocationController']),
    ('Places search', ['PlacesController']),
    ('Devices and sessions', ['SecurityController']),
    ('Home Owner applications', ['HomeOwnerApplicationController']),
    ('Staff review', ['StaffApplicationController']),
    ('User administration', ['UserManagementController']),
    ('Admin tools', ['AdminApplicationController', 'AdminSecurityController']),
    ('Internal, for services', ['InternalUserValidationController', 'InternalSessionController']),
    ('Contact and health', ['ContactController', 'HomeController']),
]


def endpoint_counts():
    """Number of HTTP endpoints per area, counted from the mapping annotations in the controllers."""
    per_class = {}
    for path in java_files('controller'):
        src = open(path, encoding='utf-8').read()
        per_class[os.path.basename(path)[:-5]] = len(re.findall(r'@(?:Get|Post|Put|Delete|Patch)Mapping', src))
    rows = [(label, sum(per_class.get(c, 0) for c in classes)) for label, classes in AREAS]
    return rows, sum(v for _, v in rows)


PACKAGES = [
    ('Controllers', ['controller']),
    ('Sign in, sessions and alerts', ['service/security']),
    ('Home Owner applications', ['service/application']),
    ('Other services', ['service']),
    ('Entities and repositories', ['entity', 'repository']),
    ('Request and response types', ['dto', 'enums']),
    ('Filters, configuration and errors', ['security', 'config', 'exception', 'util']),
]


def package_lines():
    """Lines of Java per group of packages. service/security and service/application are counted apart from service."""
    totals = {label: 0 for label, _ in PACKAGES}
    for path in java_files():
        rel = os.path.relpath(path, os.path.join(ROOT, JAVA)).replace(os.sep, '/')
        n = code_lines(path)
        placed = False
        for label, prefixes in PACKAGES:
            if label == 'Other services':
                continue
            if any(rel.startswith(p + '/') for p in prefixes):
                totals[label] += n
                placed = True
                break
        if not placed:
            if rel.startswith('service/'):
                totals['Other services'] += n
            else:
                totals['Filters, configuration and errors'] += n
    return sorted(totals.items(), key=lambda kv: -kv[1]), len(java_files())


def access_rules():
    """The route rules in SecurityConfig, as (who, [paths]) in the order they are written."""
    src = read(os.path.join(JAVA, 'config', 'SecurityConfig.java'))
    body = src[src.index('authorizeHttpRequests'):src.index('sessionManagement')]
    rules = []
    for m in re.finditer(r'\.requestMatchers\(([^)]*)\)\s*\.(permitAll|authenticated|hasRole|hasAnyRole)\(([^)]*)\)', body, re.S):
        paths = re.findall(r'"([^"]+)"', m.group(1))
        roles = re.findall(r'"([^"]+)"', m.group(3))
        rules.append((m.group(2), roles, paths))
    return rules


def risk_weights():
    src = read(os.path.join(JAVA, 'service', 'security', 'SuspiciousActivityService.java'))
    w = {k: int(v) for k, v in re.findall(r'static final int ([A-Z_]+) = (\d+);', src)}
    yml = read(os.path.join('src', 'main', 'resources', 'application.yml'))
    m = re.search(r'risk-alert-threshold: \$\{[A-Z_]+:(\d+)\}', yml)
    return w, int(m.group(1)) if m else 50


def pom():
    src = read('pom.xml')
    boot = re.search(r'spring-boot-starter-parent</artifactId>\s*<version>([^<]+)</version>', src).group(1)
    java = re.search(r'<java.version>([^<]+)</java.version>', src).group(1)

    def ver(artifact):
        m = re.search(r'<artifactId>' + re.escape(artifact) + r'</artifactId>\s*<version>([^<]+)</version>', src)
        return m.group(1) if m else ''
    return {'boot': boot, 'java': java, 'jjwt': ver('jjwt-api'), 'cloudinary': ver('cloudinary-http44'), 'bucket4j': ver('bucket4j_jdk17-core'),
            'google': ver('google-api-client')}


def settings_count():
    """How many environment variables application.yml reads, so the docs can say it."""
    yml = read(os.path.join('src', 'main', 'resources', 'application.yml'))
    return len(set(re.findall(r'\$\{([A-Z0-9_]+)', yml)))


def required_settings():
    """Environment variables application.yml reads with no default, so the service cannot start without them."""
    yml = read(os.path.join('src', 'main', 'resources', 'application.yml'))
    return sorted(set(re.findall(r'\$\{([A-Z0-9_]+)\}', yml)))
# ---------------------------------------------------------------------------------------- banner

def banner():
    w, h = 1280, 420
    g = 350  # ground line
    lines, lit = [], []

    def draw(d, delay, width=1.6, colour=SHEET_LINE, dash=None):
        extra = f' stroke-dasharray="{dash}"' if dash else ''
        cls = '' if dash else ' class="dr" pathLength="1"'
        lines.append(f'<path{cls} d="{d}" fill="none" stroke="{colour}" stroke-width="{width}" stroke-linejoin="miter"{extra} style="animation-delay:{delay:.2f}s"/>')

    # ground and its hatch
    draw(f'M650 {g}H1262', 0.2, 2)
    hatch = ''.join(f'M{x} {g}l-8 9' for x in range(660, 1262, 11))
    lines.append(f'<path d="{hatch}" fill="none" stroke="{SHEET_SOFT}" stroke-width="1" opacity=".55" class="fade" style="animation-delay:.9s"/>')

    # the gatehouse: the auth service. Walls, cornice, plaque, arched opening, two windows
    draw(f'M700 {g}V196H900V{g}', 0.5)
    draw('M690 196H910', 0.7)
    draw('M770 166h60v24h-60z', 0.9, 1.2)
    draw(f'M772 {g}V292a28 28 0 0 1 56 0V{g}', 1.0)
    draw(f'M800 264V{g}', 1.2, 1.1, SHEET_SOFT)
    for i, x in enumerate((720, 848)):
        draw(f'M{x} 226h32v34h-32z', 1.2 + i * .15, 1.2)
        draw(f'M{x + 16} 226v34M{x} 243h32', 1.3 + i * .15, .8, SHEET_SOFT)

    # three sibling services, plain houses that trust the token
    houses = [(950, 1030, 290, 258), (1060, 1140, 270, 236), (1170, 1250, 300, 270)]
    names = ['wallet', 'property', 'booking']
    centres = []
    for k, (x0, x1, top, peak) in enumerate(houses):
        cx = (x0 + x1) / 2
        centres.append(cx)
        d0 = 1.4 + k * .2
        draw(f'M{x0} {g}V{top}H{x1}V{g}', d0)
        draw(f'M{x0 - 8} {top + 2}L{cx} {peak}L{x1 + 8} {top + 2}', d0 + .2)
        draw(f'M{cx - 8} {g}V{g - 26}h16V{g}', d0 + .3, 1.2)
        draw(f'M{cx - 12} {top + 14}h24v20h-24z', d0 + .35, 1.2)
        lit.append(f'<rect class="svc sv{k}" x="{cx - 12}" y="{top + 14}" width="24" height="20" fill="{AMBER}"/>')
    # the gate's windows light up on a slow breathing loop
    glow = ''.join(f'<rect class="lit" x="{x}" y="226" width="32" height="34" fill="{AMBER}" style="--d:{2.8 + i * .5:.2f}s;--p:{5.1 + i * .9:.2f}s"/>' for i, x in enumerate((720, 848)))
    lock = (f'<circle class="lock" cx="800" cy="318" r="9" fill="{AMBER}"/>'
            f'<circle cx="800" cy="316" r="3.4" fill="{SHEET}"/><path d="M798.4 318h3.2l1.2 12h-5.6z" fill="{SHEET}"/>')

    # axes with bubbles, and a dimension string under the ground
    ax = ''
    for x, letter in ((800, 'A'), (990, 'B'), (1210, 'C')):
        ax += (f'<line class="march" x1="{x}" y1="86" x2="{x}" y2="388" stroke="{SHEET_SOFT}" stroke-width="1" stroke-dasharray="10 4 2 4" opacity=".6"/>'
               f'<circle cx="{x}" cy="68" r="13" fill="none" stroke="{SHEET_LINE}" stroke-width="1.2"/>'
               f'<text x="{x}" y="72.4" text-anchor="middle" font-size="12.5" font-weight="600" style="fill:{SHEET_LINE}" class="mono">{letter}</text>')
    dy = 402
    dims = (f'<g class="fade" style="animation-delay:2.2s"><path d="M800 {dy}H1210M800 {dy - 6}v12M1210 {dy - 6}v12" stroke="{SHEET_LINE}" stroke-width="1.1" fill="none"/>'
            f'<path d="M796 {dy + 4}l8 -8M1206 {dy + 4}l8 -8" stroke="{SHEET_LINE}" stroke-width="1.6"/>'
            f'<text x="1005" y="{dy - 9}" text-anchor="middle" font-size="12" style="fill:{SHEET_LINE}" class="mono">one signature, three services</text></g>')
    labels = ''.join(f'<text x="{c}" y="{g + 22}" text-anchor="middle" font-size="12" class="mono" style="fill:{SHEET_SOFT}">{n}</text>' for c, n in zip(centres, names))

    # tokens leave the gate, one for each service, and the service window lights when it arrives
    cycle = 9.0
    css_tok = ''
    tokens = ''
    for k, c in enumerate(centres):
        t0 = 1.0 + k * 2.6
        dist = c - 908
        a, b = t0 / cycle * 100, (t0 + 1.6) / cycle * 100
        css_tok += (f'@keyframes tk{k} {{ 0%, {a:.2f}% {{ opacity: 0; transform: translateX(0px); }} {a + 1:.2f}% {{ opacity: 1; transform: translateX(0px); animation-timing-function: cubic-bezier(.55,0,.35,1); }} '
                    f'{b:.2f}% {{ opacity: 1; transform: translateX({dist:.0f}px); }} {b + 1.5:.2f}%, 100% {{ opacity: 0; transform: translateX({dist:.0f}px); }} }}\n')
        css_tok += lit_kf(f'sv{k}', cycle, 'opacity:.12', 'opacity:.95', [(t0 + 1.5, t0 + 2.7)], 0.3)
        css_tok += f'.sv{k} {{ animation: sv{k} {cycle}s infinite; }}\n'
        tokens += (f'<g class="tok" style="animation: tk{k} {cycle}s infinite"><rect x="900" y="297" width="16" height="11" fill="{AMBER}" stroke="{SHEET}" stroke-width="1.2"/>'
                   f'<path d="M903 302.5h10" stroke="{SHEET}" stroke-width="1.4"/></g>')
    css_tok += lit_kf('lockpulse', cycle, 'opacity:.4', 'opacity:1', [(0.6 + k * 2.6, 1.8 + k * 2.6) for k in range(3)], 0.25)
    track = f'<path d="M908 303H1262" stroke="{SHEET_SOFT}" stroke-width="1" stroke-dasharray="3 5" opacity=".55" class="fade" style="animation-delay:1.8s"/>'

    css = f"""
.dr {{ stroke-dasharray: 1; animation: draw 1.5s cubic-bezier(.5,0,.2,1) both; }}
@keyframes draw {{ from {{ stroke-dashoffset: 1; }} to {{ stroke-dashoffset: 0; }} }}
.fade {{ animation: fadein 1s both; }} @keyframes fadein {{ from {{ opacity: 0; }} }}
.lit {{ opacity: .92; animation: on 1.4s var(--d) ease-out both, breathe var(--p) calc(var(--d) + 2.4s) ease-in-out infinite; }}
@keyframes on {{ from {{ opacity: 0; }} 40% {{ opacity: .35; }} to {{ opacity: .92; }} }}
@keyframes breathe {{ 0%, 100% {{ opacity: .92; }} 50% {{ opacity: .45; }} }}
.svc {{ opacity: .35; }} .tok {{ opacity: 0; }}
.lock {{ animation: lockpulse {cycle}s infinite; }}
.in1 {{ animation: slide .9s .1s cubic-bezier(.2,.7,.2,1) both; }} .in2 {{ animation: slide .9s .35s cubic-bezier(.2,.7,.2,1) both; }}
@keyframes slide {{ from {{ opacity: 0; transform: translateY(10px); }} }}
.lg1, .lg2 {{ animation: lgrise 1s cubic-bezier(.2,.8,.2,1) both; }} .lg2 {{ animation-delay: .25s; }}
@keyframes lgrise {{ from {{ opacity: 0; transform: translateY(90px); }} }}
.lgw {{ transform-box: fill-box; transform-origin: center; animation: pop .5s var(--d) cubic-bezier(.3,1.5,.5,1) both; }}
@keyframes pop {{ from {{ transform: scale(0); }} }}
.march {{ animation: march 3s linear infinite; }} @keyframes march {{ to {{ stroke-dashoffset: -20; }} }}
.plat {{ animation: tick 7.5s calc(3.4s + var(--i) * 1.25s) infinite; }}
@keyframes tick {{ 0%, 100% {{ fill: {SHEET_SOFT}; }} 2%, 14% {{ fill: {AMBER}; }} 17% {{ fill: {SHEET_SOFT}; }} }}
{css_tok}text {{ fill: {SHEET_LINE}; }}
"""
    gridlines = ''.join(f'M{x} 0V{h}' for x in range(40, w, 40)) + ''.join(f'M0 {y}H{w}' for y in range(40, h, 40))
    stack = ['Spring Boot', 'Java 21', 'MongoDB', 'Redis', 'JWT', 'Cloudinary']
    px, plat = 64, ''
    for k, p in enumerate(stack):
        plat += f'<text x="{px}" y="318" font-size="14.5" class="mono plat" style="fill:{SHEET_SOFT};--i:{k}">{p}</text>'
        px += len(p) * 8.7 + 22
        plat += f'<path d="M{px - 11} 306v14" stroke="{SHEET_SOFT}" stroke-width="1" opacity=".6"/>'
    plat = plat.rsplit('<path', 1)[0]
    body = (f'<rect width="{w}" height="{h}" fill="{SHEET}"/>'
            f'<path d="{gridlines}" stroke="#fff" stroke-width="1" opacity=".045" fill="none"/>'
            f'{ax}{"".join(lines)}{track}{"".join(lit)}{glow}{lock}{labels}{dims}{tokens}'
            f'<g class="in1">{logo_mark(7.6, 186 - 742 * 0.2, 0.2)}<text class="cond" x="176" y="186" font-size="84" letter-spacing="-1.2">Auth Service</text></g>'
            f'<g class="in2"><text x="66" y="244" font-size="25" style="fill:{SHEET_LINE}">Identity, sessions and accounts for Premisave.</text>'
            f'<text x="66" y="276" font-size="16" style="fill:{SHEET_SOFT}">It signs people in and issues the tokens other services trust.</text>'
            f'{plat}</g>')
    save('banner.svg', svg(w, h, body, css, '', 'Premisave Auth Service',
                           'The Premisave logo and the words Auth Service beside an architectural elevation of a gatehouse that sends signed tokens to three sibling services.'))


def divider():
    w, h = 1200, 22
    ticks = ''.join(f'M{x} 11v{-7 if x % 50 == 0 else -4}' for x in range(8, 1193, 10))
    body = (f'<line x1="8" y1="11" x2="1192" y2="11" stroke="#8FA39A" stroke-width="1.2"/>'
            f'<path d="{ticks}" stroke="#8FA39A" stroke-width="1" fill="none"/>'
            f'<rect class="mk" x="595" y="5" width="10" height="12" fill="{AMBER}"/>')
    css = ('.mk { animation: slide 18s ease-in-out infinite alternate; } '
           '@keyframes slide { from { transform: translateX(-560px); } to { transform: translateX(560px); } }')
    save('divider.svg', svg(w, h, body, css, '', 'Divider', fonts=()))


# ---------------------------------------------------------------------------------------- architecture

def architecture():
    rows, total = endpoint_counts()
    controllers = len(java_files('controller'))
    w, h = 1200, 520
    nw, nh, ny = 200, 150, 116
    xs = [36 + i * 234 for i in range(5)]
    mains = [
        ('Callers', 'HTTPS and JSON', ('the Flutter app, a browser,', 'or another service')),
        ('Filters', 'security/', ('client context, JWT check,', 'API key on /internal')),
        ('Controllers', 'controller/', (f'{controllers} classes with', f'{total} endpoints')),
        ('Services', 'service/', ('sign in, sessions, alerts,', 'applications, profiles')),
        ('Data', 'MongoDB and Redis', ('users, sessions, applications,', 'blacklist and throttles')),
    ]
    out = []
    for i, (title, path, note) in enumerate(mains):
        x = xs[i]
        solid = i == 4
        out.append(f'<g class="rise" style="animation-delay:{i * 0.12:.2f}s">'
                   f'<rect x="{x}" y="{ny}" width="{nw}" height="{nh}" fill="{INK if solid else PAPER}" stroke="{INK}" stroke-width="2"/>'
                   + ('' if solid else f'<rect x="{x + 5}" y="{ny + 5}" width="{nw - 10}" height="{nh - 10}" fill="none" stroke="{INK}" stroke-width="0.8"/>') +
                   f'<text class="cond" x="{x + 18}" y="{ny + 58}" font-size="23" style="fill:{PAPER if solid else INK}">{title}</text>'
                   f'<text class="mono" x="{x + 18}" y="{ny + 82}" font-size="11.5" style="fill:{AMBER if solid else DATA}">{path}</text>'
                   f'<text x="{x + 18}" y="{ny + 108}" font-size="12.5" style="fill:{SHEET_LINE if solid else INK2}">{esc(note[0])}</text>'
                   f'<text x="{x + 18}" y="{ny + 125}" font-size="12.5" style="fill:{SHEET_LINE if solid else INK2}">{esc(note[1])}</text>'
                   + num(x + 18, ny, i + 1, 12, AMBER, INK) + '</g>')
    links, packets = [], []
    ymid = ny + nh / 2
    cycle = 11.0
    css = '.rise { animation: rise .7s cubic-bezier(.2,.8,.2,1) both; } @keyframes rise { from { opacity:0; transform: translateY(14px); } }\n'
    css += '.pk { opacity: 0; } .pl { opacity: 0; } .march { animation: march 1.4s linear infinite; } @keyframes march { to { stroke-dashoffset: -6; } }\n'

    def hop(name, t0, dist):
        a, b = t0 / cycle * 100, (t0 + 1.0) / cycle * 100
        return (f'@keyframes {name} {{ 0%, {a:.3f}% {{ opacity: 0; transform: translateX(0px); }} '
                f'{a + 0.4:.3f}% {{ opacity: 1; transform: translateX(0px); animation-timing-function: cubic-bezier(.55,0,.35,1); }} '
                f'{b - 0.4:.3f}% {{ opacity: 1; transform: translateX({dist:.1f}px); }} '
                f'{b:.3f}%, 100% {{ opacity: 0; transform: translateX({dist:.1f}px); }} }}\n')
    for i in range(4):
        x1, x2 = xs[i] + nw, xs[i + 1]
        links.append(f'<path d="M{x1} {ymid - 14}H{x2 - 2}" stroke="{INK}" stroke-width="1.5"/><path d="M{x2 - 9} {ymid - 19}L{x2 - 1} {ymid - 14}L{x2 - 9} {ymid - 9}Z" fill="{INK}"/>')
        links.append(f'<path d="M{x2} {ymid + 14}H{x1 + 2}" stroke="{INK2}" stroke-width="1.2" stroke-dasharray="5 4"/><path d="M{x1 + 9} {ymid + 9}L{x1 + 1} {ymid + 14}L{x1 + 9} {ymid + 19}Z" fill="{INK2}"/>')
        dist = x2 - x1 - 16
        packets.append(f'<rect class="pk" x="{x1 + 4}" y="{ymid - 18}" width="8" height="8" fill="{AMBER}" stroke="{INK}" stroke-width="1" style="animation: rq{i} {cycle}s infinite"/>')
        css += hop(f'rq{i}', i, dist)
        back = 5 + (3 - i)
        packets.append(f'<rect class="pk" x="{x2 - 12}" y="{ymid + 10}" width="8" height="8" fill="{PAPER}" stroke="{INK}" stroke-width="1.4" style="animation: rs{i} {cycle}s infinite"/>')
        css += hop(f'rs{i}', back, -dist)
    wins = {n: [(n, n + 1.2), (9 - n, 10.2 - n)] for n in range(5)}
    wins[0] = [(0, 1.2), (9, 10.4)]
    wins[4] = [(4, 5.4)]
    pulses = []
    for n in range(5):
        x = xs[n]
        pulses.append(f'<rect class="pl" x="{x - 3}" y="{ny - 3}" width="{nw + 6}" height="{nh + 6}" fill="none" stroke="{AMBER}" stroke-width="6" style="animation: pl{n} {cycle}s infinite"/>')
        css += lit_kf(f'pl{n}', cycle, 'opacity:0', 'opacity:1', wins[n])
    caption = [((0, 1), 'A caller sends a request'), ((1, 4), 'Each layer checks it and hands it on'), ((4, 5.4), 'MongoDB and Redis answer'),
               ((5.4, 9), 'The answer returns the same way'), ((9, 11), 'The caller gets JSON')]
    cap = ''
    for k, ((t0, t1), text) in enumerate(caption):
        cap += f'<text class="cap" x="36" y="{ny + nh + 38}" font-size="14" font-weight="600" style="fill:{AMBER_INK};animation: cp{k} {cycle}s infinite">{esc(text)}</text>'
        css += lit_kf(f'cp{k}', cycle, 'opacity:0', 'opacity:1', [(t0, t1 - 0.2)], 0.2)
    css += '.cap { opacity: 0; }\n'
    sy, sh = 360, 84
    sup = [(1, 'Route rules', 'public, signed in, staff, admin'), (2, 'Rate limiter', 'Bucket4j on the sensitive paths'),
           (3, 'One error shape', 'timestamp, status, message'), (4, 'Outside services', 'Gmail, Cloudinary, Google')]
    sparts = []
    for col, title, note in sup:
        x = xs[col]
        sparts.append(f'<path class="march" d="M{x + nw / 2} {sy}V{ny + nh + 10}" stroke="{INK2}" stroke-width="1.1" stroke-dasharray="2 4"/>'
                      f'<circle cx="{x + nw / 2}" cy="{ny + nh + 10}" r="3" fill="{INK2}"/>'
                      f'<rect x="{x}" y="{sy}" width="{nw}" height="{sh}" fill="none" stroke="{INK}" stroke-width="1.2"/>'
                      f'<rect class="pl" x="{x - 2}" y="{sy - 2}" width="{nw + 4}" height="{sh + 4}" fill="none" stroke="{AMBER}" stroke-width="3" style="animation: pl{col} {cycle}s infinite"/>'
                      f'<text class="cond" x="{x + 16}" y="{sy + 34}" font-size="18">{title}</text>'
                      f'<text x="{x + 16}" y="{sy + 58}" font-size="12" class="soft">{note}</text>')
    legend = (f'<g transform="translate({xs[0]} {sy + 6})"><rect x="0" y="0" width="8" height="8" fill="{AMBER}" stroke="{INK}"/><text x="18" y="9" font-size="12.5">request</text>'
              f'<rect x="0" y="26" width="8" height="8" fill="none" stroke="{INK}" stroke-width="1.2"/><text x="18" y="35" font-size="12.5">response</text>'
              f'<text x="0" y="68" font-size="12.5" class="soft">Every caller takes this path.</text></g>')
    body = (sheet(w, h, 'How a request is handled', 'one direction in, the same way back') +
            ''.join(links) + ''.join(pulses) + ''.join(out) + ''.join(packets) + ''.join(sparts) + legend + cap +
            f'<text x="32" y="{sy - 18}" font-size="13.5" font-weight="600">Shared by the layers above</text>'
            f'<line x1="32" y1="{sy - 8}" x2="{xs[4] - 20}" y2="{sy - 8}" stroke="{RULE}"/>')
    save('architecture.svg', svg(w, h, body, css, '', 'Architecture of the auth-service',
                                 'A request moves from the caller through the filters, controllers and services to MongoDB and Redis, and the answer returns the same way.',
                                 ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


# ---------------------------------------------------------------------------------------- sign in and refresh

def session_flow():
    w, h = 1200, 730
    lanes = [('Client', 190), ('auth-service', 610), ('MongoDB and Redis', 1010)]
    thr = risk_weights()[1]
    steps = [
        (0, 1, 'POST /auth/signin', 'email and password, with the device headers', False),
        (1, 1, 'throttle, password, risk score', f'locks grow with each failure, an alert goes out at {thr}', False),
        (1, 2, 'save the session', 'a session record in MongoDB, generation 1', False),
        (1, 0, 'access token and refresh token', 'both carry the session id', True),
        (0, 1, 'POST /auth/refresh', 'the refresh token, generation 1', False),
        (1, 2, 'still generation 1? raise it to 2', 'one atomic update in MongoDB, so only one caller wins', False),
        (1, 0, 'new access token and refresh token', 'generation 2, the old refresh token is spent', True),
    ]
    top, gap = 168, 74
    cycle = 17.0
    css, drawn, marks = '.cur, .pk { opacity: 0; }\n', [], []
    lane_svg = ''
    for n, x in lanes:
        lane_svg += (f'<text class="cond" x="{x}" y="104" text-anchor="middle" font-size="21">{n}</text>'
                     f'<line x1="{x - 80}" y1="116" x2="{x + 80}" y2="116" stroke="{INK}" stroke-width="1.5"/>'
                     f'<line x1="{x}" y1="116" x2="{x}" y2="{h - 96}" stroke="{INK2}" stroke-width="1" stroke-dasharray="3 5"/>')
    k = len(steps) + 0.6
    for i, (a, b, label, note, back) in enumerate(steps):
        y = top + i * gap
        xa, xb = lanes[a][1], lanes[b][1]
        if a == b:
            body = (f'<rect x="{xa - 6}" y="{y - 26}" width="12" height="60" fill="{PAPER}" stroke="{INK}" stroke-width="1.4"/>'
                    f'<path d="M{xa + 6} {y - 4}h26v26h-26" fill="none" stroke="{INK}" stroke-width="1.4"/><path d="M{xa + 12} {y + 17}l-6 5" stroke="{INK}" stroke-width="1.4"/>'
                    f'<text class="mono" x="{xa + 44}" y="{y + 2}" font-size="13" font-weight="600">{esc(label)}</text>'
                    f'<text x="{xa + 44}" y="{y + 22}" font-size="12.5" class="soft">{esc(note)}</text>')
        else:
            d = 1 if xb > xa else -1
            mid = (xa + xb) / 2
            dash = ' stroke-dasharray="6 4"' if back else ''
            body = (f'<path d="M{xa + 8 * d} {y}H{xb - 12 * d}" stroke="{INK}" stroke-width="1.6"{dash}/>'
                    f'<path d="M{xb - 14 * d} {y - 6}L{xb - 2 * d} {y}L{xb - 14 * d} {y + 6}" fill="none" stroke="{INK}" stroke-width="1.6"/>'
                    f'<text class="mono" x="{mid}" y="{y - 10}" text-anchor="middle" font-size="13" font-weight="600">{esc(label)}</text>'
                    f'<text x="{mid}" y="{y + 22}" text-anchor="middle" font-size="12.5" class="soft">{esc(note)}</text>')
        drawn.append(f'<g class="s{i}">{num(40, y, i + 1)}{body}</g>')
        on = i / k * 100
        css += f'.s{i} {{ animation: st{i} {cycle}s infinite; }}\n@keyframes st{i} {{ 0%,{on:.1f}% {{ opacity: 0; }} {on + 3.5:.1f}%,93% {{ opacity: 1; }} 98%,100% {{ opacity: 0; }} }}\n'
        nxt = (i + 1) / k * 100 if i + 1 < len(steps) else 96.0
        marks.append(f'<g class="cur" style="animation: cur{i} {cycle}s infinite">{num(40, y, i + 1, 11, AMBER, INK)}</g>')
        css += f'@keyframes cur{i} {{ 0%,{on:.2f}% {{ opacity: 0; }} {on + 1.5:.2f}%,{nxt:.2f}% {{ opacity: 1; }} {nxt + 1.5:.2f}%,100% {{ opacity: 0; }} }}\n'
        if a != b:
            d = 1 if xb > xa else -1
            dist = (xb - 12 * d) - (xa + 8 * d)
            marks.append(f'<rect class="pk" x="{xa + 8 * d - 4}" y="{y - 4}" width="8" height="8" fill="{AMBER}" stroke="{INK}" stroke-width="1" style="animation: apk{i} {cycle}s infinite"/>')
            css += (f'@keyframes apk{i} {{ 0%,{on + 2:.2f}% {{ opacity: 0; transform: translateX(0px); }} '
                    f'{on + 2.4:.2f}% {{ opacity: 1; transform: translateX(0px); animation-timing-function: cubic-bezier(.55,0,.35,1); }} '
                    f'{on + 7:.2f}% {{ opacity: 1; transform: translateX({dist}px); }} {on + 7.4:.2f}%,100% {{ opacity: 0; transform: translateX({dist}px); }} }}\n')
    foot = (f'<line x1="32" y1="{h - 78}" x2="{w - 32}" y2="{h - 78}" stroke="{INK}" stroke-width="1.5"/>'
            f'<text x="32" y="{h - 50}" font-size="14.5">A refresh token used a second time is treated as theft: the session is revoked and a critical alert is raised.</text>'
            f'<text x="32" y="{h - 28}" font-size="14.5" class="soft">Every request is checked against its session, so a revoked session stops at once. See docs/AUTHENTICATION.md.</text>')
    body = sheet(w, h, 'Signing in and staying signed in', 'one session, two tokens') + lane_svg + ''.join(drawn) + ''.join(marks) + foot
    save('session-flow.svg', svg(w, h, body, css, '', 'Sign in and token refresh',
                                 'A sign in creates a session and returns an access token and a refresh token. Each refresh raises the session generation, so a refresh token works once.',
                                 ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


# ---------------------------------------------------------------------------------------- who can reach what

def access():
    rules = access_rules()
    keyed_src = read(os.path.join(JAVA, 'security', 'ApiKeyFilter.java'))
    keyed_paths = re.findall(r'"(/[^"]*)"', keyed_src[keyed_src.index('PROTECTED_PATHS'):keyed_src.index('};')])
    public, keyed, signed, staff, admin = [], [], [], [], []
    staff_roles = []
    for kind, roles, paths in rules:
        for p in paths:
            if kind == 'permitAll':
                (keyed if any(p.startswith(k) or p.rstrip('/*') == k.rstrip('/') for k in keyed_paths) else public).append(p)
            elif kind == 'authenticated':
                signed.append(p)
            elif kind == 'hasAnyRole':
                staff.append(p)
                staff_roles = roles
            else:
                admin.append(p)
    signed.append('everything else')
    # route groups: (name, paths, who may call). Columns: no token, signed in, staff, admin, service key.
    groups = [
        ('Public', public, (1, 1, 1, 1, 1)),
        ('Service key', keyed, (0, 0, 0, 0, 1)),
        ('Signed in', signed, (0, 1, 1, 1, 0)),
        ('Staff', staff, (0, 0, 1, 1, 0)),
        ('Admin', admin, (0, 0, 0, 1, 0)),
    ]
    cols = ['No token', 'Signed in', 'Staff role', 'Admin role', 'X-API-Key']
    w, rh, top = 1200, 92, 150
    h = top + len(groups) * rh + 96
    cx0, cw = 640, 110
    out = [sheet(w, h, 'Who can reach what', 'read from SecurityConfig and ApiKeyFilter')]
    css = ('.row { animation: fade .5s both; } @keyframes fade { from { opacity: 0; } }\n'
           '.cell { transform-box: fill-box; transform-origin: center; animation: pop .45s var(--d) cubic-bezier(.3,1.4,.5,1) both; }\n'
           '@keyframes pop { from { transform: scale(0); opacity: 0; } }\n'
           '.band { opacity: 0; }\n')
    cycle = 3.0 * len(groups) + 1.5
    css += band_kf('band', cycle, len(groups), 3.0, rh, 'Y') + f'.band {{ animation: band {cycle}s infinite; }}\n'
    for j, c in enumerate(cols):
        x = cx0 + j * cw + cw / 2
        out.append(f'<text class="cond" x="{x}" y="{top - 24}" text-anchor="middle" font-size="16">{c}</text>')
    out.append(f'<line x1="32" y1="{top - 10}" x2="{w - 32}" y2="{top - 10}" stroke="{INK}" stroke-width="1.5"/>')
    out.append(f'<rect class="band" x="24" y="{top}" width="{w - 48}" height="{rh}" fill="{AMBER}" opacity="0" style="mix-blend-mode:multiply" fill-opacity=".16"/>')
    for i, (name, paths, who) in enumerate(groups):
        y = top + i * rh
        txt = ', '.join(paths)
        # wrap the path list into lines of about 62 characters
        lines, cur = [], ''
        for p in [s + ',' for s in paths]:
            if len(cur) + len(p) + 1 > 62 and cur:
                lines.append(cur.rstrip())
                cur = ''
            cur += p + ' '
        if cur:
            lines.append(cur.rstrip().rstrip(','))
        out.append(f'<g class="row" style="animation-delay:{i * 0.08:.2f}s"><line x1="32" y1="{y + rh}" x2="{w - 32}" y2="{y + rh}" stroke="{RULE}"/>'
                   f'<text class="cond" x="32" y="{y + 34}" font-size="22">{name}</text>')
        for li, ln in enumerate(lines[:3]):
            out.append(f'<text class="mono" x="32" y="{y + 56 + li * 16}" font-size="11.5" style="fill:{INK2}">{esc(ln)}</text>')
        for j, ok in enumerate(who):
            x = cx0 + j * cw + cw / 2
            if ok:
                out.append(f'<rect class="cell" x="{x - 9}" y="{y + 28}" width="18" height="18" fill="{DATA}" style="--d:{0.3 + i * 0.1 + j * 0.05:.2f}s"/>')
            else:
                out.append(f'<path d="M{x - 6} {y + 37}h12" stroke="{RULE}" stroke-width="2"/>')
        out.append('</g>')
    sx = cx0 - 14
    out.append(f'<line x1="{sx}" y1="{top - 10}" x2="{sx}" y2="{top + len(groups) * rh}" stroke="{INK}" stroke-width="1.2"/>')
    fy = top + len(groups) * rh
    out.append(f'<line x1="32" y1="{fy}" x2="{w - 32}" y2="{fy}" stroke="{INK}" stroke-width="1.5"/>'
               f'<text x="32" y="{fy + 28}" font-size="14">Staff means {", ".join(staff_roles[:-1])} or {staff_roles[-1]}. Admin is ADMIN only. Public means no token is needed to reach the route.</text>'
               f'<text x="32" y="{fy + 50}" font-size="14" class="soft">A few public routes, such as logout and change-password, read the token themselves. Individual endpoints can also check more, such as re-authentication.</text>'
               f'<text x="32" y="{fy + 72}" font-size="14" class="soft">The service key is the X-API-Key header that other Premisave services send to /internal/**. Counted from the source by tool/readme_assets.py.</text>')
    save('access.svg', svg(w, h, ''.join(out), css, '', 'Who can reach which routes',
                           'A matrix of route groups against the kinds of caller that may reach them: no token, signed in, staff, admin and service key.',
                           ('cond7', 'sans4', 'sans6', 'mono4')))


# ---------------------------------------------------------------------------------------- applications

def status_labels():
    src = read(os.path.join(JAVA, 'enums', 'ApplicationStatus.java'))
    return dict(re.findall(r'^\s{4}([A-Z_]+)\("([^"]+)"', src, re.M))


def review_transitions():
    src = read(os.path.join(JAVA, 'service', 'application', 'ApplicationReviewService.java'))
    out = {}
    for m in re.finditer(r'TRANSITIONS\.put\(ApplicationStatus\.([A-Z_]+),\s*EnumSet\.of\(([^)]*)\)\)', src, re.S):
        out[m.group(1)] = re.findall(r'ApplicationStatus\.([A-Z_]+)', m.group(2))
    return out


def applications():
    lab = status_labels()
    trans = review_transitions()
    yml = read(os.path.join('src', 'main', 'resources', 'application.yml'))
    m = re.search(r'max-reapplications: \$\{[A-Z_]+:(\d+)\}', yml)
    reapply = m.group(1) if m else '3'
    w, h = 1200, 640
    nw, nh = 150, 64
    ax = [36 + i * 235 for i in range(5)]
    ay, by = 126, 326
    row_a = ['DRAFT', 'SUBMITTED', 'PROCESSING', 'VERIFIED', 'ACCEPTED']
    pos = {s: (ax[i], ay) for i, s in enumerate(row_a)}
    pos['WITHDRAWN'] = (ax[0], by)
    pos['INFO_REQUESTED'] = (388, by)
    pos['REJECTED'] = (ax[3], by)
    final = {'ACCEPTED', 'REJECTED', 'WITHDRAWN'}
    out, css = [], ''
    cycle = 9.0
    css += '.pl { opacity: 0; } .pk { opacity: 0; } .rise { animation: rise .6s cubic-bezier(.2,.8,.2,1) both; } @keyframes rise { from { opacity: 0; transform: translateY(10px); } }\n'
    order = ['DRAFT', 'SUBMITTED', 'PROCESSING', 'VERIFIED', 'ACCEPTED']
    for s, (x, y) in pos.items():
        solid = s == 'ACCEPTED'
        i = list(pos).index(s)
        out.append(f'<g class="rise" style="animation-delay:{i * 0.07:.2f}s">'
                   f'<rect x="{x}" y="{y}" width="{nw}" height="{nh}" fill="{INK if solid else PAPER}" stroke="{INK}" stroke-width="{2 if s in final or solid else 1.4}"/>'
                   + ('' if solid else f'<rect x="{x + 4}" y="{y + 4}" width="{nw - 8}" height="{nh - 8}" fill="none" stroke="{INK}" stroke-width=".7"/>') +
                   f'<text class="cond" x="{x + 14}" y="{y + 30}" font-size="19" style="fill:{PAPER if solid else INK}">{esc(lab[s])}</text>'
                   f'<text class="mono" x="{x + 14}" y="{y + 49}" font-size="10.5" style="fill:{AMBER if solid else DATA}">{s}</text></g>')

    def arrow(x1, y1, x2, y2, colour=INK, dash=None, sw=1.6, head='r'):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        heads = {'r': f'M{x2 - 9} {y2 - 5}L{x2} {y2}L{x2 - 9} {y2 + 5}Z', 'l': f'M{x2 + 9} {y2 - 5}L{x2} {y2}L{x2 + 9} {y2 + 5}Z',
                 'd': f'M{x2 - 5} {y2 - 9}L{x2} {y2}L{x2 + 5} {y2 - 9}Z', 'u': f'M{x2 - 5} {y2 + 9}L{x2} {y2}L{x2 + 5} {y2 + 9}Z'}
        return f'<path d="M{x1} {y1}L{x2} {y2}" fill="none" stroke="{colour}" stroke-width="{sw}"{d}/><path d="{heads[head]}" fill="{colour}"/>'

    def elbow(pts, colour=INK, dash=None, sw=1.6, head='d'):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        x2, y2 = pts[-1]
        heads = {'r': f'M{x2 - 9} {y2 - 5}L{x2} {y2}L{x2 - 9} {y2 + 5}Z', 'l': f'M{x2 + 9} {y2 - 5}L{x2} {y2}L{x2 + 9} {y2 + 5}Z',
                 'd': f'M{x2 - 5} {y2 - 9}L{x2} {y2}L{x2 + 5} {y2 - 9}Z', 'u': f'M{x2 - 5} {y2 + 9}L{x2} {y2}L{x2 + 5} {y2 + 9}Z'}
        path = 'M' + 'L'.join(f'{px} {py}' for px, py in pts)
        return f'<path d="{path}" fill="none" stroke="{colour}" stroke-width="{sw}"{d}/><path d="{heads[head]}" fill="{colour}"/>'

    links = []
    mid_y = ay + nh / 2
    who = ['applicant', 'reviewer', 'reviewer', 'admin']
    verb = ['submits', 'starts', 'verifies', 'promotes']
    for i in range(4):
        x1, x2 = ax[i] + nw, ax[i + 1]
        links.append(arrow(x1 + 2, mid_y, x2 - 2, mid_y, AMBER_INK if i in (0, 3) else INK, None, 2.2))
        cxm = (x1 + x2) / 2
        links.append(f'<text x="{cxm}" y="{mid_y - 20}" text-anchor="middle" font-size="11.5" class="soft">{who[i]}</text>'
                     f'<text x="{cxm}" y="{mid_y - 7}" text-anchor="middle" font-size="11.5" font-weight="600">{verb[i]}</text>')
    s_c, p_c, v_c = ax[1] + nw / 2, ax[2] + nw / 2, ax[3] + nw / 2
    i_c = pos['INFO_REQUESTED'][0] + nw / 2
    bus = ay + nh + 56
    links.append(elbow([(s_c + 24, ay + nh), (s_c + 24, bus), (i_c + 12, bus), (i_c + 12, by)]))
    links.append(elbow([(p_c - 24, ay + nh), (p_c - 24, bus), (i_c + 12, bus)], head='d'))
    links.append(f'<text x="{(s_c + p_c) / 2 + 12}" y="{bus - 8}" text-anchor="middle" font-size="11.5" class="soft">reviewer asks for changes</text>')
    links.append(elbow([(pos['INFO_REQUESTED'][0], by + nh / 2), (s_c - 30, by + nh / 2), (s_c - 30, ay + nh)], AMBER_INK, None, 2, 'u'))
    links.append(f'<text x="{s_c - 38}" y="{by - 22}" text-anchor="end" font-size="11.5" class="soft">applicant fixes</text>'
                 f'<text x="{s_c - 38}" y="{by - 9}" text-anchor="end" font-size="11.5" font-weight="600">and resubmits</text>')
    links.append(arrow(pos['INFO_REQUESTED'][0] + nw + 2, by + nh / 2, pos['REJECTED'][0] - 2, by + nh / 2, INK2, '6 4', 1.4))
    links.append(f'<text x="{(pos["INFO_REQUESTED"][0] + nw + pos["REJECTED"][0]) / 2}" y="{by + nh / 2 - 8}" text-anchor="middle" font-size="11.5" class="soft">reviewer rejects</text>')
    links.append(arrow(v_c, ay + nh + 2, v_c, by - 2, INK2, '6 4', 1.4, 'd'))
    links.append(f'<text x="{v_c + 10}" y="{(ay + nh + by) / 2 + 4}" font-size="11.5" class="soft">reviewer rejects</text>')
    d_c = ax[0] + nw / 2
    links.append(arrow(d_c, ay + nh + 2, d_c, by - 2, INK2, '6 4', 1.4, 'd'))
    links.append(f'<text x="{d_c + 10}" y="{(ay + nh + by) / 2 - 2}" font-size="11.5" class="soft">applicant withdraws</text>'
                 f'<text x="{d_c + 10}" y="{(ay + nh + by) / 2 + 12}" font-size="11.5" class="soft">from any open state</text>')
    ry = by + nh + 34
    links.append(elbow([(v_c, by + nh), (v_c, ry), (i_c - 12, ry), (i_c - 12, by + nh)], AMBER_INK, None, 2, 'u'))
    links.append(f'<text x="{(v_c + i_c) / 2}" y="{ry + 16}" text-anchor="middle" font-size="11.5" class="soft">applicant corrects and reapplies, up to {reapply} times unless the reviewer closed it</text>')
    # a token walks the main path
    tok, dist = [], ax[1] - ax[0]
    css += '.tk { opacity: 0; }\n'
    kfs = [(0, 'opacity:0;transform:translateX(0px)'), (0.3, 'opacity:1;transform:translateX(0px)')]
    stay, trav = 1.2, 0.8
    t = 0.3
    for i in range(5):
        px = i * dist
        kfs += [(t, f'opacity:1;transform:translateX({px}px)'), (t + stay, f'opacity:1;transform:translateX({px}px)')]
        css += lit_kf(f'ap{i}', cycle, 'opacity:0', 'opacity:1', [(t, t + stay + 0.3)], 0.2)
        out.append(f'<rect class="pl" x="{ax[i] - 3}" y="{ay - 3}" width="{nw + 6}" height="{nh + 6}" fill="none" stroke="{AMBER}" stroke-width="5" style="animation: ap{i} {cycle}s infinite"/>')
        t += stay + trav
    kfs += [(cycle - 0.4, f'opacity:1;transform:translateX({4 * dist}px)'), (cycle, f'opacity:0;transform:translateX({4 * dist}px)')]
    css += kf('tkm', cycle, kfs) + f'.tk {{ animation: tkm {cycle}s infinite; }}\n'
    tok.append(f'<g class="tk"><rect x="{ax[0] + nw - 6}" y="{mid_y - 5}" width="11" height="11" fill="{AMBER}" stroke="{INK}" stroke-width="1.2"/></g>')
    # the table of reviewer moves, read from the code
    ty = 470
    rows = [(k, v) for k, v in trans.items()]
    out_t = [f'<text class="cond" x="32" y="{ty}" font-size="17">Moves a reviewer can make</text>'
             f'<text x="{w - 32}" y="{ty}" text-anchor="end" font-size="12.5" class="soft">read from ApplicationReviewService</text>'
             f'<line x1="32" y1="{ty + 10}" x2="{w - 32}" y2="{ty + 10}" stroke="{INK}" stroke-width="1.2"/>']
    for i, (a, bs) in enumerate(rows):
        y = ty + 34 + i * 24
        out_t.append(f'<text class="mono" x="32" y="{y}" font-size="12" font-weight="600">{esc(lab[a])}</text>'
                     f'<text x="190" y="{y}" font-size="13" class="soft">to</text>'
                     f'<text x="224" y="{y}" font-size="13">{esc(", ".join(lab[b] for b in bs))}</text>')
    out_t.append(f'<line x1="32" y1="{h - 40}" x2="{w - 32}" y2="{h - 40}" stroke="{INK}" stroke-width="1.5"/>'
                 f'<text x="32" y="{h - 16}" font-size="13" class="soft">Asking for changes or rejecting needs a message. Only an admin promotes, and only from Verified. One open application per person.</text>')
    body = sheet(w, h, 'Life of a Home Owner application', 'eight states') + ''.join(links) + ''.join(out) + ''.join(tok) + ''.join(out_t)
    save('applications.svg', svg(w, h, body, css, '', 'Home Owner application states',
                                 'An application moves from draft to submitted, in review and verified, and an admin then promotes the user. Reviewers can ask for changes or reject, applicants can fix and resubmit, reapply or withdraw.',
                                 ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


# ---------------------------------------------------------------------------------------- charts

def stats():
    rows, total = endpoint_counts()
    items = [(k, v, f'{v * 100 / total:.0f}%') for k, v in sorted(rows, key=lambda kv: -kv[1])]
    bars('stats-endpoints.svg', 'Endpoints by area', f'{total} endpoints in {len(java_files("controller"))} controllers', items, total, 220,
         'HTTP endpoints counted from the mapping annotations in each controller. The percentage is the share of all endpoints.', gap=0.2)
    pk, files = package_lines()
    lines_total = sum(v for _, v in pk)
    items = [(k, v, f'{v * 100 / lines_total:.1f}%') for k, v in pk]
    bars('stats-code.svg', 'Code by area', f'{files} Java files, {lines_total:,} lines', items, lines_total, 290,
         'Lines of Java under src/main, blank lines and comments not counted. The percentage is the share of all lines.', gap=0.3)
    return total, lines_total, files


RISK_LABEL = {
    'COUNTRY_CHANGE': 'Country changed within the window',
    'TOKEN_REUSE': 'Refresh token replayed in the last day',
    'NEW_DEVICE_ADMIN': 'New device on an administrator account',
    'RECENT_FAILURES': 'Failed attempts just before sign in',
    'MANY_ADDRESSES': 'Many different network addresses',
    'HIGH_FREQUENCY': 'Many sign-ins in a short time',
    'NEW_DEVICE': 'New device',
}


def risk():
    weights, threshold = risk_weights()
    items = sorted(((RISK_LABEL[k], v) for k, v in weights.items() if k in RISK_LABEL), key=lambda kv: (-kv[1], kv[0]))
    w, rh, top, label_w = 1000, 34, 108, 320
    x0 = 32 + label_w
    plot_w = w - 32 - x0 - 70
    top_axis = math.ceil((threshold + 10) / 10) * 10
    h = top + len(items) * rh + 96
    out = [sheet(w, h, 'Sign-in risk score', f'an alert goes out at {threshold} or more')]
    for v in range(0, top_axis + 1, 10):
        x = x0 + plot_w * v / top_axis
        out.append(f'<line x1="{x:.1f}" y1="{top - 12}" x2="{x:.1f}" y2="{top + len(items) * rh}" stroke="{RULE}" stroke-width="1"/>'
                   f'<text class="mono" x="{x:.1f}" y="{top - 20}" text-anchor="middle" font-size="11.5" style="fill:{INK2}">{v}</text>')
    for i, (k, v) in enumerate(items):
        y = top + i * rh
        bw = plot_w * v / top_axis
        out.append(f'<g class="row" style="animation-delay:{i * 0.05:.2f}s">'
                   f'<text x="{x0 - 14}" y="{y + 22}" text-anchor="end" font-size="14">{esc(k)}</text>'
                   f'<rect class="grow" x="{x0}" y="{y + 10}" width="{bw:.1f}" height="14" fill="{DATA}" style="--d:{i * 0.05:.2f}s;--w:{i * 0.25 + 3.2:.2f}s"/>'
                   f'<text class="mono ride" x="{x0 + bw + 10:.1f}" y="{y + 22}" font-size="13" font-weight="600" style="--d:{i * 0.05:.2f}s;--bw:{bw:.1f}px">{v}</text></g>')
    tx = x0 + plot_w * threshold / top_axis
    ybot = top + len(items) * rh
    out.append(f'<line class="thr" x1="{tx:.1f}" y1="{top - 14}" x2="{tx:.1f}" y2="{ybot + 6}" stroke="{AMBER}" stroke-width="3"/>'
               f'<rect x="{tx - 62:.1f}" y="{ybot + 12}" width="124" height="24" fill="{AMBER}"/>'
               f'<text class="mono" x="{tx:.1f}" y="{ybot + 29}" text-anchor="middle" font-size="12.5" font-weight="600" style="fill:{INK}">alert at {threshold}</text>')
    out.append(f'<line x1="{x0}" y1="{top - 12}" x2="{x0}" y2="{ybot}" stroke="{INK}" stroke-width="1.5"/>')
    out.append(f'<line x1="32" y1="{h - 36}" x2="{w - 32}" y2="{h - 36}" stroke="{INK}" stroke-width="1.5"/>'
               f'<text x="32" y="{h - 14}" font-size="13" class="soft">No single signal reaches {threshold}, so an alert takes two. The score only sends an email. Nothing is blocked. Weights read from SuspiciousActivityService.</text>')
    css = ('.row { animation: fade .5s both; } @keyframes fade { from { opacity: 0; } }\n'
           '.grow { transform-box: fill-box; transform-origin: left center; animation: grow 1s var(--d) cubic-bezier(.2,.8,.2,1) both, wave 10s var(--w) infinite; }\n'
           '@keyframes grow { from { transform: scaleX(0); } }\n'
           '.ride { animation: ride 1s var(--d) cubic-bezier(.2,.8,.2,1) both; }\n'
           '@keyframes ride { from { opacity: 0; transform: translateX(calc(var(--bw) * -1)); } }\n'
           f'@keyframes wave {{ 0%, 12%, 100% {{ fill: {DATA}; }} 4%, 6% {{ fill: {AMBER}; }} }}\n'
           '.thr { animation: thr 2.8s ease-in-out infinite alternate; } '
           '@keyframes thr { from { opacity: 1; } to { opacity: .45; } }\n')
    save('stats-risk.svg', svg(w, h, ''.join(out), css, '', 'Sign-in risk score', f'The weight of each suspicious sign-in signal against the alert threshold of {threshold}.',
                               ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


# ---------------------------------------------------------------------------------------- stack and setup

def stack():
    v = pom()
    left = [('Spring Boot', v['boot'], 'Web, validation and the application core'), ('Java', v['java'], 'The language level the build targets'),
            ('Spring Security', 'with Boot', 'Stateless filters, route rules and roles'), ('MongoDB', 'with Boot', 'Users, sessions, applications and the audit trail'),
            ('Redis', 'with Boot', 'Token blacklist, session cache and sign-in throttles')]
    right = [('JJWT', v['jjwt'], 'Signs and checks the HS256 tokens'), ('Bucket4j', v['bucket4j'], 'Rate limit on the sensitive paths'),
             ('Cloudinary', v['cloudinary'], 'Profile pictures'), ('Google API client', v['google'], 'Checks Google ID tokens'),
             ('Gmail SMTP', 'with Boot', 'Activation, reset, invitation and alert emails')]
    w, rh, top = 1200, 66, 92
    h = top + 5 * rh + 28
    out = [sheet(w, h, 'Built with', 'versions read from pom.xml')]
    css = ('.row { animation: fade .5s both; } @keyframes fade { from { opacity: 0; } }\n'
           '.hop { opacity: 0; animation: hop 10s infinite; } @keyframes hop { 0% { opacity: 0; } 1.5%, 8.5% { opacity: 1; } 10%, 100% { opacity: 0; } }\n')
    for col, items in enumerate((left, right)):
        x = 32 + col * 584
        for i, (n, ver, role) in enumerate(items):
            y = top + i * rh
            k = col * 5 + i
            css += lit_kf(f'sv{k}', 10.0, f'fill:{INK2}', f'fill:{AMBER_INK}', [(k, k + 1)], 0.2) + f'.sv{k} {{ animation: sv{k} 10s infinite; }}\n'
            out.append(f'<rect class="hop" x="{x - 15}" y="{y + 9}" width="4" height="{rh - 18}" fill="{AMBER}" style="animation-delay:{k}s"/>')
            out.append(f'<g class="row" style="animation-delay:{k * 0.05:.2f}s"><line x1="{x}" y1="{y}" x2="{x + 552}" y2="{y}" stroke="{RULE}"/>'
                       f'<text class="cond" x="{x}" y="{y + 30}" font-size="20">{esc(n)}</text>'
                       f'<text class="mono sv{k}" x="{x + 552}" y="{y + 28}" text-anchor="end" font-size="13" style="fill:{INK2}">{esc(ver)}</text>'
                       f'<text x="{x}" y="{y + 52}" font-size="13.5" class="soft">{esc(role)}</text></g>')
    out.append(f'<line x1="32" y1="{top + 5 * rh}" x2="{w - 32}" y2="{top + 5 * rh}" stroke="{INK}" stroke-width="1.5"/>')
    save('stack.svg', svg(w, h, ''.join(out), css, '', 'Tech stack', 'The main libraries the auth-service is built with and what each is used for.',
                          ('cond7', 'sans4', 'sans6', 'mono4')))


def setup():
    words = {4: 'four', 5: 'five', 6: 'six', 7: 'seven', 8: 'eight'}
    need = len(required_settings())
    steps = [('Install', ['Java 21,', 'MongoDB, Redis']), ('Clone', ['git clone', 'cd premisave_auth_service']), ('Configure', ['cp .env.example .env', f'fill the {words.get(need, need)} required keys']),
             ('Run', ['sh ./mvnw', 'spring-boot:run']), ('Check', ['GET /health', 'on port 8080']), ('Sign in', ['open the activation', 'link from the email'])]
    w, h = 1200, 250
    n = len(steps)
    x0, x1 = 100, w - 100
    gapx = (x1 - x0) / (n - 1)
    out = [sheet(w, h, 'From clone to running', 'the full walk through is in docs/SETUP.md')]
    stay, travel = 1.3, 1.5
    cyc = n * stay + (n - 1) * travel + 1.2
    arrive = [k * (stay + travel) for k in range(n)]
    span = x1 - x0
    css = '.mk, .act, .done, .glow { opacity: 0; } .wipe { transform-box: fill-box; transform-origin: left center; }\n'
    mk = [(0, 'opacity:0;transform:translateX(0px)'), (0.2, 'opacity:1;transform:translateX(0px)')]
    pg = [(0, 'transform:scaleX(0)')]
    for k in range(n):
        px = k * gapx
        mk += [(arrive[k], f'opacity:1;transform:translateX({px:.1f}px)'), (arrive[k] + stay, f'opacity:1;transform:translateX({px:.1f}px)')]
        pg += [(arrive[k], f'transform:scaleX({px / span:.4f})'), (arrive[k] + stay, f'transform:scaleX({px / span:.4f})')]
    mk += [(cyc - 0.4, f'opacity:1;transform:translateX({span:.1f}px)'), (cyc, f'opacity:0;transform:translateX({span:.1f}px)')]
    pg += [(cyc - 0.4, 'transform:scaleX(1)'), (cyc, 'transform:scaleX(1)')]
    css += kf('mk', cyc, mk) + kf('pg', cyc, pg)
    css += f'.mk {{ animation: mk {cyc:.1f}s infinite; }} .wipe {{ animation: pg {cyc:.1f}s infinite; }}\n'
    out.append(f'<line x1="{x0}" y1="112" x2="{x1}" y2="112" stroke="{RULE}" stroke-width="1.5"/>')
    out.append(f'<rect class="wipe" x="{x0}" y="110.5" width="{span}" height="3" fill="{DATA}"/>')
    for i, (t, cmd) in enumerate(steps):
        x = x0 + i * gapx
        t_in = arrive[i]
        out.append(num(x, 112, i + 1, 15))
        css += lit_kf(f'ac{i}', cyc, 'opacity:0', 'opacity:1', [(t_in, t_in + stay)], 0.2)
        css += kf(f'dn{i}', cyc, [(0, 'opacity:0'), (t_in + stay, 'opacity:0'), (t_in + stay + 0.3, 'opacity:1'), (cyc - 0.5, 'opacity:1'), (cyc, 'opacity:0')])
        css += kf(f'tx{i}', cyc, [(0, 'opacity:.5'), (t_in, 'opacity:.5'), (t_in + 0.3, 'opacity:1'), (cyc - 0.5, 'opacity:1'), (cyc, 'opacity:.5')])
        css += (f'.ac{i} {{ animation: ac{i} {cyc:.1f}s infinite; }} .dn{i} {{ animation: dn{i} {cyc:.1f}s infinite; }} .tx{i} {{ animation: tx{i} {cyc:.1f}s infinite; }}\n')
        out.append(f'<circle class="dn{i} done" cx="{x}" cy="112" r="15" fill="{DATA}"/>'
                   f'<text x="{x}" y="116.4" text-anchor="middle" font-size="12.5" font-weight="600" style="fill:{PAPER}" class="mono dn{i} done">{i + 1}</text>')
        out.append(f'<circle class="ac{i} act" cx="{x}" cy="112" r="19" fill="none" stroke="{AMBER}" stroke-width="3"/>')
        out.append(f'<g class="tx{i}"><text class="cond" x="{x}" y="156" text-anchor="middle" font-size="19">{t}</text>')
        for j, c in enumerate(cmd):
            out.append(f'<text class="mono" x="{x}" y="{178 + j * 17}" text-anchor="middle" font-size="11.5" style="fill:{INK2}">{esc(c)}</text>')
        out.append('</g>')
    out.append(f'<g class="mk"><rect x="{x0 - 5}" y="107" width="10" height="10" fill="{AMBER}" stroke="{INK}" stroke-width="1.2"/></g>')
    save('setup.svg', svg(w, h, ''.join(out), css, '', 'Setup steps', 'Six steps from installing the tools to signing in to the running service.',
                          ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


# ---------------------------------------------------------------------------------------- icons and header bands

def circ(cx, cy, r):
    return f'M{cx - r} {cy}a{r} {r} 0 1 0 {2 * r} 0a{r} {r} 0 1 0 {-2 * r} 0'


# Each icon is a few strokes on a 64 unit grid. The second value marks the stroke drawn in amber.
ICONS = {
    'docs': [('M8 12H28Q32 12 32 16V54Q32 50 28 50H8Z', 0), ('M56 12H36Q32 12 32 16V54Q32 50 36 50H56Z', 0), ('M32 16V54', 1)],
    'setup': [('M6 54H22V42H36V30H50V18H58', 0), ('M50 18V5', 1), ('M50 5l11 4.5-11 4.5', 1)],
    'architecture': [('M32 8L58 20L32 32L6 20Z', 1), ('M6 31L32 43L58 31', 0), ('M6 42L32 54L58 42', 0)],
    'authentication': [(circ(18, 32, 11), 1), ('M29 32H59', 0), ('M51 32v10M43 32v8', 0), (circ(18, 32, 3), 0)],
    'social-sign-in': [(circ(32, 13, 7), 1), (circ(12, 50, 7), 0), (circ(52, 50, 7), 0), ('M29 19L15 44M35 19L49 44M19 50H45', 0)],
    'api': [('M6 22H48M40 13l9 9-9 9', 1), ('M58 44H16M24 35l-9 9 9 9', 0)],
    'configuration': [('M6 16H18M30 16H58', 0), (circ(24, 16, 6), 0), ('M6 32H34M46 32H58', 0), (circ(40, 32, 6), 1), ('M6 48H14M26 48H58', 0), (circ(20, 48, 6), 0)],
    'security': [('M32 5L55 13V31C55 45 45 54 32 59C19 54 9 45 9 31V13Z', 0), ('M21 32l8 8 15-16', 1)],
    'home-owner': [('M6 31L32 8L58 31', 1), ('M13 26V57H51V26', 0), ('M26 57V41H38V57', 0)],
    'storage': [('M12 15a20 8 0 1 0 40 0a20 8 0 1 0 -40 0', 0), ('M12 15V49a20 8 0 0 0 40 0V15', 0), ('M12 32a20 8 0 0 0 40 0', 1)],
    'integration': [('M5 20H25V44H5Z', 0), ('M39 20H59V44H39Z', 0), ('M25 28H39M33 23l6 5-6 5', 1), ('M39 38H25M31 33l-6 5 6 5', 1)],
    'deployment': [('M8 40H56V57H8Z', 0), ('M32 35V6M19 19L32 6L45 19', 1), ('M16 48.5h8', 0)],
    'development': [('M20 17L6 32L20 47', 0), ('M44 17L58 32L44 47', 0), ('M37 11L27 53', 1)],
    'troubleshooting': [(circ(26, 26, 17), 0), ('M39 39L58 58', 1), ('M26 17V28M26 34v1', 0)],
    'console': [('M5 11H59V53H5Z', 0), ('M5 21H59', 0), ('M13 33l8 6-8 6', 1), ('M27 45H41', 1)],
    'oauth-test': [('M24 7H40', 0), ('M28 7V26L11 51Q8 57 15 57H49Q56 57 53 51L36 26V7', 0), ('M19 43H45', 1)],
}

# What each band says. (icon, file shown at the right, one line, chips)
HEADS = {
    'docs': ('docs/README.md', 'Thirteen guides, one for each job you will have with this service.',
             ['Set it up', 'Build on it', 'Run it', 'Fix it']),
    'setup': ('docs/SETUP.md', 'From nothing to a signed in account in about fifteen minutes.',
              ['Java 21', 'MongoDB', 'Redis', 'Gmail App Password']),
    'architecture': ('docs/ARCHITECTURE.md', 'How a request travels, and why the service is built this way.',
                     ['Filters', 'Controllers', 'Services', 'MongoDB', 'Redis']),
    'authentication': ('docs/AUTHENTICATION.md', 'Two tokens, with one server side session behind them.',
                       ['Access token', 'Refresh rotation', 'Devices', 'Throttling']),
    'social-sign-in': ('docs/SOCIAL_SIGN_IN.md', 'Google, Facebook and GitHub, verified on the server.',
                       ['ID token', 'Authorization code', 'Account linking']),
    'api': ('docs/API.md', 'Every endpoint, who may call it, and what it answers.',
            ['{endpoints} endpoints', 'JSON', 'Bearer JWT', 'X-API-Key']),
    'configuration': ('docs/CONFIGURATION.md', 'Every setting, with its default and what it does.',
                      ['{settings} settings', '.env or environment', 'read at startup']),
    'security': ('docs/ADMIN_AND_ACCOUNT_SECURITY.md', 'Administrators, sign in alerts, and the limits of both.',
                 ['Invitations', 'Risk score', 'Audit trail', 'Retention']),
    'home-owner': ('docs/HOME_OWNER_APPLICATIONS.md', 'From a draft to a verified Home Owner, step by step.',
                   ['Form', 'Documents', 'Review', 'Promotion']),
    'storage': ('docs/STORAGE.md', 'Identity documents stay on a disk you control.',
                ['Local folder', 'NAS share', 'Never a public link']),
    'integration': ('docs/INTEGRATION.md', 'What another service needs to trust these tokens.',
                    ['JWT key', 'X-API-Key', 'Session check']),
    'deployment': ('docs/DEPLOYMENT.md', 'A checklist for going live, and the limits to plan around.',
                   ['HTTPS', 'Short tokens', 'Reverse proxy', 'Backups']),
    'development': ('docs/DEVELOPMENT.md', 'House rules, tests, and how to add things safely.',
                    ['Maven wrapper', 'Add an endpoint', 'Tests']),
    'troubleshooting': ('docs/TROUBLESHOOTING.md', 'Find the symptom, read the cause, apply the fix.',
                        ['Startup', 'Mail', 'Sign in', 'Tokens', 'Uploads']),
    'console': ('test-client/README.md', 'Try every endpoint from one HTML page.',
                ['No install', 'Tokens stay in the browser']),
    'oauth-test': ('oauth-test/README.md', 'Test Google, Facebook and GitHub sign in against your own service.',
                   ['No install', 'Shows the raw provider token']),
}


def icon_group(name, x, y, s, colour, accent, width=1.7, animate=True, delay0=0.3):
    out = []
    for i, (d, acc) in enumerate(ICONS[name]):
        cls = ' class="dr"' if animate else ''
        pl = ' pathLength="1"' if animate else ''
        dl = f' style="animation-delay:{delay0 + i * 0.22:.2f}s"' if animate else ''
        out.append(f'<path{cls}{pl} d="{d}" fill="none" stroke="{accent if acc else colour}" stroke-width="{width}" '
                   f'stroke-linecap="round" stroke-linejoin="round"{dl}/>')
    return f'<g transform="translate({x} {y}) scale({s})">{"".join(out)}</g>'


def dochead(slug):
    path, line, chips = HEADS[slug]
    rows, total = endpoint_counts()
    chips = [c.format(endpoints=total, settings=settings_count()) for c in chips]
    w, h = 1200, 190
    size = 32
    while len(line) * size * 0.43 > 930:
        size -= 1
    grid = ''.join(f'M{x} 0V{h}' for x in range(40, w, 40)) + ''.join(f'M0 {y}H{w}' for y in range(40, h, 40))
    corners = ''.join(f'<path d="{d}" stroke="{SHEET_SOFT}" stroke-width="1.2" fill="none" opacity=".7"/>' for d in (
        'M14 28V14H28', f'M{w - 28} 14H{w - 14}V28', f'M14 {h - 28}V{h - 14}H28', f'M{w - 28} {h - 14}H{w - 14}V{h - 28}'))
    ring = (f'<rect x="52" y="30" width="130" height="130" fill="none" stroke="{SHEET_SOFT}" stroke-width="1" opacity=".55" stroke-dasharray="6 4" class="march"/>'
            f'<circle cx="52" cy="30" r="3.2" fill="{AMBER}"/>')
    ico = icon_group(slug, 66, 44, 1.6, SHEET_LINE, AMBER, 1.9)
    cx, parts = 236, []
    for i, c in enumerate(chips):
        cw = len(c) * 8.4 + 26
        parts.append(f'<g class="fade" style="animation-delay:{1.1 + i * .15:.2f}s"><rect x="{cx}" y="118" width="{cw:.0f}" height="28" rx="14" fill="none" stroke="{SHEET_SOFT}" stroke-width="1.1"/>'
                     f'<text x="{cx + cw / 2:.0f}" y="136.5" text-anchor="middle" font-size="13" class="mono" style="fill:{SHEET_LINE}">{esc(c)}</text></g>')
        cx += cw + 10
    css = f"""
.dr {{ stroke-dasharray: 1; animation: draw 1.3s cubic-bezier(.5,0,.2,1) both; }}
@keyframes draw {{ from {{ stroke-dashoffset: 1; }} to {{ stroke-dashoffset: 0; }} }}
.fade {{ animation: fadein .9s both; }} @keyframes fadein {{ from {{ opacity: 0; }} }}
.march {{ animation: march 4s linear infinite; }} @keyframes march {{ to {{ stroke-dashoffset: -20; }} }}
.in1 {{ animation: slide .9s .2s cubic-bezier(.2,.7,.2,1) both; }}
@keyframes slide {{ from {{ opacity: 0; transform: translateY(10px); }} }}
.mk {{ animation: mk 9s ease-in-out infinite alternate; }} @keyframes mk {{ from {{ transform: translateX(0px); }} to {{ transform: translateX(920px); }} }}
text {{ fill: {SHEET_LINE}; }}
"""
    body = (f'<rect width="{w}" height="{h}" fill="{SHEET}"/><path d="{grid}" stroke="#fff" stroke-width="1" opacity=".045" fill="none"/>'
            f'{corners}{ring}{ico}'
            f'<g class="in1"><text class="cond" x="236" y="92" font-size="{size}" letter-spacing="-.4">{esc(line)}</text></g>'
            f'{"".join(parts)}'
            f'<text x="{w - 34}" y="46" text-anchor="end" font-size="13" class="mono" style="fill:{SHEET_SOFT}">{esc(path)}</text>'
            f'<line x1="236" y1="168" x2="{w - 34}" y2="168" stroke="{SHEET_SOFT}" stroke-width="1" opacity=".6"/>'
            f'<rect class="mk" x="236" y="162" width="10" height="12" fill="{AMBER}"/>')
    save(f'head-{slug}.svg', svg(w, h, body, css, '', f'{path}', f'Header band for {path} with a drawn icon and the topics it covers.',
                                 ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))


def icon_file(slug):
    """A small static icon for tables: ink strokes on a pale tile, one amber stroke."""
    body = (f'<rect x="0" y="0" width="64" height="64" rx="12" fill="{PAPER2}"/>'
            + icon_group(slug, 10, 10, 0.69, INK, '#C98A0B', 3.0, False))
    os.makedirs(os.path.join(OUT, 'icons'), exist_ok=True)
    content = svg(64, 64, body, '', '', slug.replace('-', ' '), f'Icon for {slug.replace("-", " ")}', ())
    with open(os.path.join(OUT, 'icons', f'{slug}.svg'), 'w', encoding='utf-8') as f:
        f.write(content)


def docs_heads():
    for slug in HEADS:
        dochead(slug)
        icon_file(slug)
# ---------------------------------------------------------------------------------------- shared drawing helpers for the guides

FULL = ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')
BASE_MOTION = (".rise { animation: rise .7s cubic-bezier(.2,.8,.2,1) both; } @keyframes rise { from { opacity:0; transform: translateY(14px); } }\n"
               ".march { animation: march 1.4s linear infinite; } @keyframes march { to { stroke-dashoffset: -6; } }\n"
               ".pk, .pl { opacity: 0; }\n")


def box(x, y, w, h, title, lines=(), i=0, solid=False, tsize=20, mono=None, dashed=False):
    """A drawing box. `mono` is an optional line of code under the title. Entrance is staggered by `i`."""
    fg, sub = (PAPER, SHEET_LINE) if solid else (INK, INK2)
    dash = ' stroke-dasharray="7 5"' if dashed else ''
    out = [f'<g class="rise" style="animation-delay:{i * 0.09:.2f}s">',
           f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{INK if solid else PAPER}" stroke="{INK}" stroke-width="{1.4 if dashed else 2}"{dash}/>']
    if not solid and not dashed:
        out.append(f'<rect x="{x + 4}" y="{y + 4}" width="{w - 8}" height="{h - 8}" fill="none" stroke="{INK}" stroke-width=".8"/>')
    ty = y + 14 + tsize
    out.append(f'<text class="cond" x="{x + 16}" y="{ty}" font-size="{tsize}" style="fill:{fg}">{esc(title)}</text>')
    cy = ty + 6
    if mono:
        cy += 16
        out.append(f'<text class="mono" x="{x + 16}" y="{cy}" font-size="11.5" style="fill:{AMBER if solid else DATA}">{esc(mono)}</text>')
    for ln in lines:
        cy += 18
        out.append(f'<text x="{x + 16}" y="{cy}" font-size="12.5" style="fill:{sub}">{esc(ln)}</text>')
    out.append('</g>')
    return ''.join(out)


def arrow(x1, y1, x2, y2, dash=False, col=INK, head=8, width=1.5):
    ang = math.atan2(y2 - y1, x2 - x1)
    bx, by = x2 - head * math.cos(ang), y2 - head * math.sin(ang)
    px, py = -math.sin(ang) * head * 0.55, math.cos(ang) * head * 0.55
    d = ' stroke-dasharray="5 4"' if dash else ''
    return (f'<path d="M{x1:.1f} {y1:.1f}L{bx:.1f} {by:.1f}" stroke="{col}" stroke-width="{width}" fill="none"{d}/>'
            f'<path d="M{x2:.1f} {y2:.1f}L{bx + px:.1f} {by + py:.1f}L{bx - px:.1f} {by - py:.1f}Z" fill="{col}"/>')


def poly(pts, dash=False, col=INK, head=8, width=1.5):
    """A path through several points, with an arrowhead on the last leg."""
    d = ' stroke-dasharray="5 4"' if dash else ''
    body = ''.join(f'L{x:.1f} {y:.1f}' for x, y in pts[1:-1])
    x0, y0 = pts[0]
    (xa, ya), (xb, yb) = pts[-2], pts[-1]
    ang = math.atan2(yb - ya, xb - xa)
    bx, by = xb - head * math.cos(ang), yb - head * math.sin(ang)
    px, py = -math.sin(ang) * head * 0.55, math.cos(ang) * head * 0.55
    return (f'<path d="M{x0:.1f} {y0:.1f}{body}L{bx:.1f} {by:.1f}" stroke="{col}" stroke-width="{width}" fill="none"{d}/>'
            f'<path d="M{xb:.1f} {yb:.1f}L{bx + px:.1f} {by + py:.1f}L{bx - px:.1f} {by - py:.1f}Z" fill="{col}"/>')


def packet(name, cycle, pts, t0, dur, fill=AMBER, size=10):
    """A small marker that travels along `pts` (a list of (x, y)) starting at second t0 and taking `dur` seconds."""
    seg = [math.dist(pts[k], pts[k + 1]) for k in range(len(pts) - 1)]
    total = sum(seg) or 1
    at = lambda p, op: f'opacity:{op};transform:translate({p[0]:.1f}px,{p[1]:.1f}px)'
    frames = [(0, at(pts[0], 0)), (t0, at(pts[0], 0)), (t0 + 0.12, at(pts[0], 1))]
    run = 0
    for k, p in enumerate(pts[1:], 1):
        run += seg[k - 1]
        frames.append((t0 + 0.12 + dur * run / total, at(p, 1)))
    end = t0 + 0.12 + dur
    frames += [(end + 0.25, at(pts[-1], 0)), (cycle, at(pts[-1], 0))]
    frames = [(min(t, cycle), s) for t, s in frames]
    css = kf(name, cycle, frames) + f'.{name} {{ animation: {name} {cycle}s linear infinite; }}\n'
    half = size / 2
    el = (f'<rect class="pk {name}" x="{-half}" y="{-half}" width="{size}" height="{size}" fill="{fill}" stroke="{INK}" stroke-width="1.2"/>')
    return el, css


def glow(name, cycle, x, y, w, h, windows, fade=0.25):
    css = lit_kf(name, cycle, 'opacity:0', 'opacity:1', windows, fade) + f'.{name} {{ animation: {name} {cycle}s infinite; }}\n'
    return f'<rect class="pl {name}" x="{x - 3}" y="{y - 3}" width="{w + 6}" height="{h + 6}" fill="none" stroke="{AMBER}" stroke-width="5"/>', css


def finish(name, w, h, parts, css, title, desc):
    save(name, svg(w, h, ''.join(parts), BASE_MOTION + css, '', title, desc, FULL))


# ---------------------------------------------------------------------------------------- social sign in

def social_flows():
    w, h, cycle = 1200, 640, 15.0
    bw, bh = 196, 112
    xs = [32 + i * 234 for i in range(5)]
    laneA = [('Your app', (), 'runs the provider sign in', ('on the device or in the page',)),
             ('Provider', (), 'signs the person in', ('and returns an ID token',)),
             ('Post the token', 'POST /auth/google', 'body: { token }', ('no secret on the device',)),
             ('Auth service', (), 'asks the provider: valid,', ('for this app, verified',)),
             ('Session', (), 'finds or creates the user', ('issues both tokens',))]
    laneB = [('Browser', (), 'the app opens the', ('provider sign in page',)),
             ('Redirect', (), 'the provider returns a', ('code to a listed address',)),
             ('Post the code', 'POST /auth/github', 'token is the code,', ('plus the redirectUri',)),
             ('Auth service', (), 'swaps the code for a', ('token using the secret',)),
             ('Session', (), 'finds or creates the user', ('issues both tokens',))]
    parts, css = [sheet(w, h, 'Two ways to sign in with a provider', 'both end in the same session')], ''
    rows = [(124, laneA, 'With the provider sign in', 'web, Android and iOS', 0.8),
            (322, laneB, 'With an authorization code', 'desktop, and GitHub everywhere', 5.6)]
    for r, (y, lane, head, sub, t0) in enumerate(rows):
        parts.append(f'<text class="cond" x="32" y="{y - 16}" font-size="19">{head}</text>'
                     f'<text class="soft" x="{32 + len(head) * 9.6 + 14:.0f}" y="{y - 16}" font-size="13">{sub}</text>')
        ym = y + bh / 2
        for i, (t, mono, l1, l2) in enumerate(lane):
            parts.append(box(xs[i], y, bw, bh, t, (l1,) + l2, i + r, solid=(i == 4), tsize=19, mono=mono or None))
            if i < 4:
                parts.append(arrow(xs[i] + bw + 2, ym, xs[i + 1] - 2, ym))
        dur = 4.2
        pk, c = packet(f'pa{r}', cycle, [(xs[0] + bw / 2, y + bh + 14), (xs[4] + bw / 2, y + bh + 14)], t0, dur)
        parts.append(pk)
        css += c
        for i in range(5):
            tt = t0 + 0.12 + dur * i / 4
            g, c = glow(f'gl{r}{i}', cycle, xs[i], y, bw, bh, [(tt - 0.3, tt + 0.7)])
            parts.append(g)
            css += c
    parts.append(f'<text class="cond" x="32" y="{488 - 16}" font-size="19">Which account is it</text>'
                 f'<text class="soft" x="196" y="{488 - 16}" font-size="13">the service tries these in order</text>')
    cases = [('1  Provider id already linked', 'signs in. A missing name or picture is filled in.'),
             ('2  Same email found', 'links it. An unverified one loses its password.'),
             ('3  Nobody yet', 'creates a verified CLIENT account.')]
    for k, (t, d) in enumerate(cases):
        x = 32 + k * 388
        parts.append(box(x, 488, 372, 96, t, (d,), 6 + k, tsize=19))
        if k < 2:
            parts.append(f'<path d="M{x + 376} 536h10" stroke="{INK2}" stroke-width="1.5" stroke-dasharray="3 3"/>')
        tt = 11.4 + k * 1.2
        g, c = glow(f'gc{k}', cycle, x, 488, 372, 96, [(tt, tt + 1.1)])
        parts.append(g)
        css += c
    finish('social-flows.svg', w, h, parts, css, 'Two ways to sign in with a provider',
           'A token flow for web, Android and iOS, and an authorization code flow for desktop and GitHub, both ending in a session, then the three rules that decide which account is used.')


# ---------------------------------------------------------------------------------------- configuration map

GROUPS = [
    ('Required', 'JWT_SECRET', ['JWT_SECRET', 'API_KEY', 'GMAIL_PASSWORD', 'CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET']),
    ('Addresses', 'FRONTEND_URL', ['FRONTEND_URL', 'BACKEND_URL', 'SECURITY_PAGE_PATH', 'HOME_OWNER_FRONTEND_PATH']),
    ('Data stores', 'MONGODB_URI', ['MONGODB_URI', 'REDIS_HOST', 'REDIS_PORT']),
    ('Mail', 'GMAIL_USERNAME', ['MAIL_HOST', 'MAIL_PORT', 'GMAIL_USERNAME', 'SUPPORT_EMAIL']),
    ('Tokens', 'SECURITY_ACCESS_TOKEN_MINUTES', ['JWT_EXPIRATION', 'SECURITY_ACCESS_TOKEN_MINUTES', 'SECURITY_LEGACY_REFRESH_ENABLED', 'SECURITY_SESSION_TTL_DAYS',
                                                  'SECURITY_REAUTH_WINDOW_MINUTES', 'SECURITY_HIDE_EXISTING_ACCOUNTS']),
    ('Social sign in', 'GOOGLE_CLIENT_ID', ['OAUTH_ALLOWED_REDIRECT_URIS']),
    ('Administrators', 'INITIAL_ADMIN_EMAILS', ['INITIAL_ADMIN_EMAILS', 'AUTO_ADMIN_EMAILS', 'SECURITY_INVITATION_TTL_HOURS']),
    ('Sessions and devices', 'SECURITY_DEVICE_COOKIE_SECURE', ['SECURITY_DEVICE_TRUST_ENABLED', 'SECURITY_SESSION_REVOCATION_ENABLED', 'SECURITY_DEVICE_COOKIE_NAME',
                                                               'SECURITY_DEVICE_COOKIE_SECURE', 'SECURITY_DEVICE_COOKIE_SAME_SITE', 'SECURITY_DEVICE_COOKIE_MAX_AGE_DAYS',
                                                               'SECURITY_MAX_DEVICES_PER_USER']),
    ('Alerts', 'SECURITY_NEW_DEVICE_ALERTS', ['SECURITY_LOGIN_EMAIL_NOTIFICATIONS', 'SECURITY_NEW_DEVICE_ALERTS', 'SECURITY_SUSPICIOUS_LOGIN_ALERTS',
                                              'SECURITY_DEVICE_ALERT_COOLDOWN_HOURS', 'SECURITY_ALERT_FIRST_DEVICE', 'SECURITY_DISPLAY_ZONE', 'SECURITY_ACTION_TOKEN_TTL_MINUTES']),
    ('Throttling', 'SECURITY_LOGIN_MAX_FAILURES', ['SECURITY_LOGIN_MAX_FAILURES', 'SECURITY_LOGIN_FAILURE_WINDOW_MINUTES', 'SECURITY_LOGIN_LOCKOUT_MINUTES',
                                                   'SECURITY_LOGIN_MAX_LOCKOUT_MINUTES', 'SECURITY_IP_MAX_FAILURES', 'SECURITY_IP_MAX_DISTINCT_ACCOUNTS',
                                                   'SECURITY_IP_WINDOW_MINUTES', 'RATE_LIMIT_REQUESTS_PER_MINUTE']),
    ('Risk scoring', 'SECURITY_RISK_ALERT_THRESHOLD', ['SECURITY_RISK_ALERT_THRESHOLD', 'SECURITY_LOCATION_CHANGE_WINDOW_HOURS', 'SECURITY_FREQUENCY_THRESHOLD',
                                                       'SECURITY_FREQUENCY_WINDOW_MINUTES', 'SECURITY_DISTINCT_IP_THRESHOLD', 'SECURITY_DISTINCT_IP_WINDOW_MINUTES']),
    ('Network and location', 'SECURITY_TRUSTED_PROXIES', ['SECURITY_TRUSTED_PROXIES', 'SECURITY_IP_GEOLOCATION_ENABLED', 'SECURITY_IP_GEOLOCATION_URL',
                                                          'SECURITY_IP_GEOLOCATION_TIMEOUT_MS']),
    ('Retention', 'SECURITY_AUDIT_RETENTION_DAYS', ['SECURITY_AUDIT_RETENTION_DAYS', 'SECURITY_DEVICE_RETENTION_DAYS', 'SECURITY_RETENTION_CRON']),
    ('Home Owner', 'HOME_OWNER_MAX_REAPPLICATIONS', ['HOME_OWNER_MAX_FILE_SIZE_MB', 'HOME_OWNER_MAX_DOCUMENTS', 'HOME_OWNER_REVIEW_SLA_DAYS', 'HOME_OWNER_MAX_REAPPLICATIONS']),
    ('Document storage', 'STORAGE_NETWORK_REQUIRE_MOUNT', []),
    ('Places', 'GOOGLE_MAPS_API_KEY', ['GOOGLE_MAPS_API_KEY', 'PLACES_TIMEOUT_MS', 'PLACES_REQUESTS_PER_USER_PER_MINUTE']),
]


def config_counts():
    yml = read('src/main/resources/application.yml')
    names = set(re.findall(r'\$\{([A-Z0-9_]+)', yml))
    counts, used = [], set()
    for title, ex, members in GROUPS:
        got = {n for n in names if n in members}
        if title == 'Document storage':
            got = {n for n in names if n.startswith('STORAGE_')}
        elif title == 'Administrators':
            got |= {n for n in names if re.match(r'ADMINS?\d?_', n)}
        elif title == 'Social sign in':
            got |= {n for n in names if n.startswith(('GOOGLE_CLIENT', 'GOOGLE_CODE', 'FACEBOOK_', 'GITHUB_'))}
        used |= got
        counts.append((title, ex, len(got)))
    rest = names - used
    if rest:
        print('config groups: not placed:', sorted(rest))
        counts.append(('Other', sorted(rest)[0], len(rest)))
    return counts, len(names)


def config_map():
    counts, total = config_counts()
    cols = 4
    rows = math.ceil(len(counts) / cols)
    w, h = 1200, 100 + rows * 112 + 44
    parts = [sheet(w, h, 'Where every setting lives', f'{total} settings in {len(counts)} groups, all read from the environment')]
    css, cycle = '', len(counts) * 0.55 + 2.5
    for k, (title, ex, n) in enumerate(counts):
        c, r = k % cols, k // cols
        x, y = 32 + c * 290, 92 + r * 112
        solid = title == 'Required'
        fg, sub = (PAPER, SHEET_LINE) if solid else (INK, INK2)
        parts.append(f'<g class="rise" style="animation-delay:{k * .05:.2f}s">'
                     f'<rect x="{x}" y="{y}" width="268" height="96" fill="{INK if solid else PAPER}" stroke="{INK}" stroke-width="2"/>'
                     + ('' if solid else f'<rect x="{x + 4}" y="{y + 4}" width="260" height="88" fill="none" stroke="{INK}" stroke-width=".8"/>') +
                     f'<text class="cond" x="{x + 16}" y="{y + 54}" font-size="44" style="fill:{AMBER if solid else DATA}">{n}</text>'
                     f'<text class="cond" x="{x + 82}" y="{y + 38}" font-size="19" style="fill:{fg}">{esc(title)}</text>'
                     f'<text x="{x + 82}" y="{y + 56}" font-size="12.5" style="fill:{sub}">{"setting" if n == 1 else "settings"}{" with no default" if solid else ""}</text>'
                     f'<text class="mono" x="{x + 16}" y="{y + 82}" font-size="10.5" style="fill:{sub}">{esc(ex)}</text></g>')
        tt = 0.6 + k * 0.55
        g, cs = glow(f'cg{k}', cycle, x, y, 268, 96, [(tt, tt + 0.9)])
        parts.append(g)
        css += cs
    parts.append(f'<text class="soft" x="32" y="{h - 20}" font-size="13">Each variable, its default and what it does is in the tables of this guide. Settings are read once, at startup.</text>')
    finish('config-map.svg', w, h, parts, css, 'Settings by group',
           f'{total} settings grouped by what they control, with the six that have no default highlighted.')


# ---------------------------------------------------------------------------------------- storage

def storage():
    w, h, cycle = 1200, 560, 12.0
    parts = [sheet(w, h, 'Where an uploaded document goes', 'never into the database, never to a third party')]
    css = ''
    parts.append(box(32, 100, 220, 140, 'Upload', ('PDF, JPG, PNG or WEBP', 'from the applicant'), 0, mono='POST .../documents'))
    parts.append(box(300, 100, 270, 140, 'Checks', ('content, not the file name', 'size and a duplicate SHA-256', 'free space, and the mount'), 1))
    parts.append(box(660, 92, 250, 108, 'Local folder', ('one server, one disk',), 2, mono='STORAGE_LOCAL_PATH'))
    parts.append(box(660, 232, 250, 126, 'Network share', ('mounted by the operating', 'system, SMB or NFS'), 3, mono='/mnt/premisave-nas'))
    parts.append(box(960, 232, 210, 126, 'NAS', ('Synology or any server', 'the login stays in', 'the mount, not here'), 4, solid=True))
    parts.append(arrow(254, 170, 298, 170))
    parts.append(poly([(572, 170), (616, 170), (616, 146), (658, 146)]))
    parts.append(poly([(616, 170), (616, 295), (658, 295)]))
    parts.append(arrow(912, 295, 958, 295, True))
    parts.append(f'<g class="rise" style="animation-delay:.5s"><path d="M616 218l11 -11 11 11 -11 11z" fill="{AMBER}" stroke="{INK}" stroke-width="1.4"/>'
                 f'<text class="mono" x="642" y="222" font-size="10.5" style="fill:{INK2}">mounted?</text></g>')
    p1, c1 = packet('st1', cycle, [(254, 170), (616, 170), (616, 146), (656, 146)], 0.8, 3.4)
    p2, c2 = packet('st2', cycle, [(254, 170), (616, 170), (616, 295), (656, 295)], 5.6, 3.8)
    parts += [p1, p2]
    css += c1 + c2
    for i, (x, y, bw, bh, win) in enumerate([(300, 100, 270, 140, [(1.6, 2.6), (6.4, 7.4)]), (660, 92, 250, 108, [(4.2, 5.2)]),
                                             (660, 232, 250, 126, [(8.4, 9.4)]), (960, 232, 210, 126, [(9.8, 10.8)])]):
        g, c = glow(f'sg{i}', cycle, x, y, bw, bh, win)
        parts.append(g)
        css += c
    parts.append(box(32, 392, 520, 128, 'Names on disk', ('one folder for each application, one file for each document',), 5,
                     mono='HOA-2026-000123 Ada Lovelace/'))
    parts.append(f'<text class="mono" x="48" y="{392 + 100}" font-size="11.5" style="fill:{DATA}">  TITLE_DEED_20260115-093012.pdf</text>')
    parts.append(box(600, 392, 570, 128, 'Served only through the API', ('owner or staff may download, anyone else gets 404',
                                                                         'the file is streamed, there is no public address'), 6,
                     mono='GET .../documents/{id}/download'))
    finish('storage.svg', w, h, parts, css, 'Document storage',
           'An upload is checked, then written to a local folder or to a network share that the operating system has mounted, and is only ever served through the API.')


# ---------------------------------------------------------------------------------------- integration

def integration():
    w, h, cycle = 1200, 660, 12.0
    parts = [sheet(w, h, 'How other services trust this one', 'a signature for people, a key for services')]
    css = ''
    parts.append(box(32, 160, 220, 100, 'Flutter app', ('sends the access token',), 0))
    parts.append(box(32, 290, 220, 100, 'Browsers', ('and any other client',), 1))
    parts.append(box(470, 250, 260, 170, 'Auth service', ('signs in, issues tokens,', 'keeps the sessions', 'answers /internal/**'), 2, solid=True, tsize=24))
    ys = (150, 280, 410)
    for i, (n, y) in enumerate((('Wallet', ys[0]), ('Listing', ys[1]), ('Property', ys[2]))):
        parts.append(box(940, y, 230, 96, n + ' service', ('verifies the token itself',), 3 + i, tsize=19))
    parts.append(arrow(254, 215, 468, 300))
    parts.append(arrow(468, 372, 254, 372, True))
    parts.append(f'<text class="mono" x="262" y="236" font-size="10.5" style="fill:{INK2}">POST /auth/signin</text>')
    parts.append(f'<text class="mono" x="262" y="392" font-size="10.5" style="fill:{INK2}">access + refresh token</text>')
    parts.append(poly([(142, 158), (142, 124), (1055, 124), (1055, 148)], col=DATA, width=1.8))
    parts.append(f'<text class="cond" x="400" y="98" font-size="16" style="fill:{DATA}">Authorization: Bearer access token</text>')
    parts.append(f'<text class="soft" x="400" y="115" font-size="12.5">checked with the shared JWT_SECRET, no call to the auth service</text>')
    mids = [y + 48 for y in ys]
    parts.append(f'<path d="M940 {mids[0]}H840M940 {mids[1]}H840M940 {mids[2]}H840M840 {mids[0]}V{mids[2]}" stroke="{INK}" stroke-width="1.5" fill="none"/>')
    parts.append(arrow(840, 335, 732, 335))
    parts.append(f'<text class="cond" x="750" y="312" font-size="16">X-API-Key</text>')
    parts.append(f'<text class="mono" x="750" y="362" font-size="10.5" style="fill:{INK2}">/internal/users</text>')
    parts.append(f'<text class="mono" x="750" y="376" font-size="10.5" style="fill:{INK2}">/internal/sessions</text>')
    parts.append(box(470, 546, 700, 96, 'Developer service', ('keeps its own accounts and its own DEVELOPER_JWT_SECRET, so it does not accept these tokens.',
                                                               'It shares only the INTERNAL_API_KEY with the wallet.'), 6, dashed=True, tsize=19))
    parts.append(box(32, 546, 400, 96, 'Two secrets to keep equal', ('JWT_SECRET: every service that checks tokens', 'API_KEY: the callers of /internal/**'), 7, tsize=19))
    p1, c1 = packet('ig1', cycle, [(142, 124), (1055, 124), (1055, 146)], 0.8, 4.2, DATA)
    p2, c2 = packet('ig2', cycle, [(938, 198), (840, 198), (840, 335), (736, 335)], 6.0, 3.6)
    parts += [p1, p2]
    css += c1 + c2
    g, c = glow('ig_a', cycle, 470, 250, 260, 170, [(9.6, 11)])
    parts.append(g)
    css += c
    for i, y in enumerate(ys):
        g, c = glow(f'ig_s{i}', cycle, 940, y, 230, 96, [(4.4 + i * .2, 5.2 + i * .2)])
        parts.append(g)
        css += c
    finish('integration.svg', w, h, parts, css, 'Integration',
           'People send a signed access token that other services verify with the shared secret. Services call the internal endpoints with an API key. The developer service is separate.')


# ---------------------------------------------------------------------------------------- deployment

def deployment():
    w, h, cycle = 1200, 600, 13.0
    parts = [sheet(w, h, 'A production layout', 'what the checklist protects')]
    css = ''
    parts.append(box(32, 250, 170, 110, 'People', ('browsers and apps',), 0, tsize=19))
    parts.append(box(32, 100, 170, 90, 'Other services', ('wallet, listing',), 1, tsize=19, dashed=True))
    parts.append(box(250, 230, 210, 170, 'Reverse proxy', ('terminates HTTPS', 'sets X-Forwarded-For', 'allows /internal only', 'from your network'), 2, tsize=21, solid=True))
    parts.append(box(520, 110, 210, 130, 'auth-service 1', ('same JWT_SECRET', 'and API_KEY'), 3, tsize=19, mono='SECURITY_TRUSTED_PROXIES'))
    parts.append(box(520, 370, 210, 130, 'auth-service 2', ('optional, any number', 'of instances'), 4, tsize=19))
    parts.append(box(800, 100, 170, 84, 'MongoDB', ('private network',), 5, tsize=19))
    parts.append(box(800, 214, 170, 84, 'Redis', ('private network',), 6, tsize=19))
    parts.append(box(800, 328, 170, 112, 'Documents', ('a share mounted at', 'the same path'), 7, tsize=19))
    for i, (n, d) in enumerate((('Gmail', 'SMTP'), ('Cloudinary', 'pictures'), ('Google', 'sign in, places'))):
        parts.append(box(1020, 100 + i * 96, 150, 78, n, (d,), 8 + i, tsize=18))
    parts.append(arrow(204, 305, 248, 305))
    parts.append(poly([(117, 192), (117, 214), (330, 214), (330, 228)], True))
    parts.append(f'<text class="mono" x="130" y="207" font-size="10.5" style="fill:{INK2}">/internal, X-API-Key</text>')
    parts.append(poly([(462, 270), (490, 270), (490, 175), (518, 175)]))
    parts.append(poly([(462, 340), (490, 340), (490, 435), (518, 435)]))
    parts.append(arrow(732, 142, 798, 142))
    parts.append(arrow(732, 190, 798, 256, True))
    parts.append(arrow(732, 435, 798, 395))
    parts.append(arrow(972, 142, 1018, 142, True))
    parts.append(f'<text class="soft" x="250" y="428" font-size="12.5">Port 8080 is never exposed.</text>')
    parts.append(f'<text class="soft" x="250" y="446" font-size="12.5">Only the proxy is.</text>')
    parts.append(f'<g class="rise" style="animation-delay:.9s"><rect x="32" y="528" width="1138" height="44" fill="none" stroke="{INK}" stroke-width="1.2" stroke-dasharray="7 5"/>'
                 f'<text x="48" y="555" font-size="13.5" font-weight="600">Before it goes live:</text>'
                 f'<text x="196" y="555" font-size="13.5" class="soft">fresh secrets, short access tokens, HTTPS, CORS limited, logging at INFO, no debugMessage, backups of data and documents.</text></g>')
    p1, c1 = packet('dp1', cycle, [(118, 292), (246, 292), (490, 292), (490, 160), (700, 160), (790, 150)], 0.6, 5.2)
    p2, c2 = packet('dp2', cycle, [(118, 160), (118, 214), (330, 214), (330, 240), (490, 240), (490, 420), (700, 420), (790, 385)], 6.6, 5.2, DATA)
    parts += [p1, p2]
    css += c1 + c2
    for i, (x, y, bw, bh, win) in enumerate([(250, 230, 210, 170, [(1.0, 2.0), (7.0, 8.0)]), (520, 110, 210, 130, [(3.2, 4.2)]),
                                             (800, 100, 170, 84, [(5.2, 6.0)]), (520, 370, 210, 130, [(9.6, 10.6)]), (800, 328, 170, 112, [(11.6, 12.4)])]):
        g, c = glow(f'dg{i}', cycle, x, y, bw, bh, win)
        parts.append(g)
        css += c
    finish('deployment.svg', w, h, parts, css, 'Production layout',
           'People and other services reach the auth-service through a reverse proxy over HTTPS. One or more instances share MongoDB, Redis, a document share and the outside services.')


# ---------------------------------------------------------------------------------------- development steps

def dev_steps():
    w, h = 1200, 330
    steps = [('Service', ('the rule, and', 'ApiException')), ('DTOs', ('Jakarta', 'Validation')), ('Controller', ('validate, call,', 'return a DTO')),
             ('Route rule', ('SecurityConfig:', 'who may call')), ('Rate limit', ('WebConfig, if it', 'sends mail')), ('Audit', ('a SecurityEvent', 'for changes')),
             ('Docs', ('API.md and the', 'Postman file')), ('Pictures', ('python3 tool/', 'readme_assets.py'))]
    parts = [sheet(w, h, 'Adding an endpoint, in order', 'each layer stays in its lane')]
    bw, gap = 124, 20
    hold, cycle = 1.4, 8 * 1.4 + 1.2
    css = band_kf('bd', cycle, 8, hold, bw + gap, 'X') + '.bd { animation: bd %.1fs infinite; opacity: 0; }\n' % cycle
    for i, (t, ls) in enumerate(steps):
        x = 34 + i * (bw + gap)
        parts.append(box(x, 108, bw, 132, t, ls, i, tsize=18, solid=(i in (0, 2))))
        parts.append(num(x + 18, 108, i + 1, 12, AMBER, INK))
    parts.append(f'<rect class="bd" x="31" y="105" width="{bw + 6}" height="138" fill="none" stroke="{AMBER}" stroke-width="5"/>')
    parts.append(f'<text class="soft" x="32" y="278" font-size="13.5">Controllers validate and call. Services decide. Only services touch repositories, Redis, mail, storage and providers.</text>')
    parts.append(f'<text class="soft" x="32" y="300" font-size="13.5">Anything a person can cause throws an ApiException with a message that is safe to show.</text>')
    finish('dev-steps.svg', w, h, parts, css, 'Adding an endpoint',
           'Eight steps in order: service, request types, controller, route rule, rate limit, audit event, documentation and pictures.')


# ---------------------------------------------------------------------------------------- troubleshooting

def triage():
    cards = [('It will not start', "placeholder 'JWT_SECRET'", ('a missing setting, a wrong', 'folder, a port in use')),
             ('Email does not arrive', '535 not accepted', ('App Password, sender', 'address, FRONTEND_URL')),
             ('Sign in fails', '401 Invalid email or password.', ('verification, deactivation,', 'throttle, device cookie')),
             ('Tokens and sessions', 'Your session has ended.', ('refresh used twice, key', 'mismatch, role not applied')),
             ('Social sign in', 'redirect address not allowed', ('allow list, audience,', 'unverified provider email')),
             ('Browsers and CORS', 'blocked by CORS policy', ('headers, file:// origin,', 'cookie not stored')),
             ('Home Owner uploads', '503 on upload', ('storage, file type,', 'size, incomplete form')),
             ('Other services', '401 from /internal/**', ('X-API-Key, localhost in', 'a container')),]
    w, h = 1200, 440
    parts = [sheet(w, h, 'Start with the symptom', 'each card is a section of TROUBLESHOOTING.md')]
    css, cycle = '', len(cards) * 0.9 + 2
    for k, (t, ex, ls) in enumerate(cards):
        c, r = k % 4, k // 4
        x, y = 32 + c * 290, 96 + r * 170
        parts.append(box(x, y, 268, 150, t, ls, k, tsize=20, mono=ex))
        g, cs = glow(f'tg{k}', cycle, x, y, 268, 150, [(0.5 + k * 0.9, 1.4 + k * 0.9)])
        parts.append(g)
        css += cs
    finish('triage.svg', w, h, parts, css, 'Troubleshooting map',
           'Eight groups of symptoms, each with an example message and its usual causes.')


# ---------------------------------------------------------------------------------------- application requirements

def requirements():
    w, h = 1200, 470
    cols = [('Individual', 520), ('Company', 720), ('Property manager', 920)]
    rows = [('Identity', ('ID card front and back, or a passport', 'photo page, or a driving licence'), (1, 1, 1)),
            ('Proof of ownership', ('a title deed, sale agreement, head', 'lease or allotment letter'), (1, 1, 1)),
            ('Company registration', ('certificate of incorporation',), (0, 1, 0)),
            ('Authority to manage', ('power of attorney or a signed', 'management agreement'), (0, 0, 1))]
    parts = [sheet(w, h, 'Which documents each owner must upload', 'a requirement is met by any one of its combinations')]
    css, cycle = '', 3 * 1.6 + 1.2
    css += band_kf('rq', cycle, 3, 1.6, 200, 'X') + '.rq { animation: rq %.1fs infinite; opacity: 0; }\n' % cycle
    for name, cx in cols:
        parts.append(f'<text class="cond" x="{cx}" y="104" text-anchor="middle" font-size="19">{name}</text>')
    parts.append(f'<line x1="32" y1="116" x2="{w - 32}" y2="116" stroke="{INK}" stroke-width="1.2"/>')
    for r, (label, ls, need) in enumerate(rows):
        y = 134 + r * 62
        parts.append(f'<g class="rise" style="animation-delay:{r * .1:.2f}s"><text class="cond" x="32" y="{y + 20}" font-size="18">{label}</text>'
                     + ''.join(f'<text x="32" y="{y + 38 + j * 15}" font-size="12" class="soft">{esc(l)}</text>' for j, l in enumerate(ls)))
        for (name, cx), n in zip(cols, need):
            if n:
                parts.append(f'<circle cx="{cx}" cy="{y + 24}" r="14" fill="{DATA}"/><path d="M{cx - 6} {y + 24}l4.5 4.5 8 -9" stroke="{PAPER}" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')
            else:
                parts.append(f'<path d="M{cx - 8} {y + 24}h16" stroke="{RULE}" stroke-width="2.4" stroke-linecap="round"/>')
        parts.append(f'<line x1="32" y1="{y + 56}" x2="{w - 32}" y2="{y + 56}" stroke="{RULE}" stroke-width="1"/></g>')
    parts.append(f'<rect class="rq" x="425" y="86" width="190" height="318" fill="none" stroke="{AMBER}" stroke-width="5"/>')
    parts.append(f'<text class="soft" x="32" y="{h - 34}" font-size="13">Optional for everyone, and they speed up review: a tax ID document, a selfie holding the ID, proof of address and a tax compliance certificate.</text>')
    parts.append(f'<text class="soft" x="32" y="{h - 14}" font-size="13">The tax ID number on the form is required. Files are PDF, JPG, PNG or WEBP, up to 8 MB each, 25 per application.</text>')
    finish('requirements.svg', w, h, parts, css, 'Required documents by owner type',
           'Identity and proof of ownership are required for every owner. A company also needs a certificate of incorporation, and a property manager a power of attorney.')


# ---------------------------------------------------------------------------------------- administrator provisioning

def admin_paths():
    w, h, cycle = 1200, 560, 15.0
    lanes = [('INITIAL_ADMIN_EMAILS', ('checked at startup',), 'Does a verified account exist?',
              ('yes: it is promoted to ADMIN', 'no: one is created unverified and an', 'invitation email is sent'), 'Promoted, or invited', ('the owner picks their own password',)),
             ('AUTO_ADMIN_EMAILS', ('checked at sign up, sign in,', 'and social sign in'), 'Verified, active and not archived?',
              ('yes: the account becomes ADMIN', 'no: nothing happens'), 'ADMIN at verification', ('no password is ever shared',)),
             ('ADMIN1 to ADMIN3', ('created at startup', 'from the .env file'), 'Is there an account with that email?',
              ('no: created, verified and active', 'yes: left alone, unless promoting', 'is on and it is verified'), 'Created, never overwritten', ('its password is hashed, then cleared',))]
    parts = [sheet(w, h, 'Three ways an administrator comes to exist', 'every grant is audited and the owner is emailed')]
    css = ''
    for r, (src, sl, rule, rl, res, resl) in enumerate(lanes):
        y = 96 + r * 138
        parts.append(box(32, y, 270, 118, src, sl, r * 3, tsize=18, solid=True, mono=None))
        parts.append(box(356, y, 440, 118, rule, rl, r * 3 + 1, tsize=18))
        parts.append(box(850, y, 320, 118, res, resl, r * 3 + 2, tsize=18))
        parts.append(arrow(304, y + 59, 354, y + 59))
        parts.append(arrow(798, y + 59, 848, y + 59))
        pk, c = packet(f'ad{r}', cycle, [(60, y + 100), (1140, y + 100)], 0.6 + r * 4.4, 3.6)
        parts.append(pk)
        css += c
        for i, (x, bw) in enumerate(((32, 270), (356, 440), (850, 320))):
            tt = 0.6 + r * 4.4 + 0.12 + 3.6 * (x + bw / 2 - 60) / 1080
            g, cs = glow(f'ag{r}{i}', cycle, x, y, bw, 118, [(tt - 0.4, tt + 0.7)])
            parts.append(g)
            css += cs
    parts.append(f'<text class="soft" x="32" y="{h - 40}" font-size="13">Removing an address from a list never demotes anyone. Roles change through PUT /admin/users/change-role/{{id}}, and the last administrator cannot be demoted.</text>')
    parts.append(f'<text class="soft" x="32" y="{h - 20}" font-size="13">An unverified, deactivated or archived account never receives ADMIN, whatever the lists say.</text>')
    finish('admin-paths.svg', w, h, parts, css, 'Administrator provisioning',
           'Three configuration routes to an administrator: an invitation list, an automatic list that applies when the email is verified, and up to three accounts created from the environment file.')
def bars(name, title, note, items, total, label_w, unit_note, gap=0.12):
    """Horizontal bars on a shared axis, sorted, labelled at the end of each bar."""
    mx = max(v for _, v, _x in items)
    step = next(c for c in (5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000) if mx / c <= 6)
    top_axis = math.ceil(mx * 1.12 / step) * step
    has_extra = any(x for _, _v, x in items)
    w, rh, top = 1000, 32, 108
    x0 = 32 + label_w
    plot_w = w - 32 - x0 - (160 if has_extra else 90)
    h = top + len(items) * rh + 56
    out = [sheet(w, h, title, note)]
    for v in range(0, top_axis + 1, step):
        x = x0 + plot_w * v / top_axis
        out.append(f'<line x1="{x:.1f}" y1="{top - 12}" x2="{x:.1f}" y2="{top + len(items) * rh}" stroke="{RULE}" stroke-width="1"{"" if v else f" stroke-dasharray=\"0\""}/>'
                   f'<text class="mono" x="{x:.1f}" y="{top - 20}" text-anchor="middle" font-size="11.5" style="fill:{INK2}">{v:,}</text>')
    for i, (k, v, extra) in enumerate(items):
        y = top + i * rh
        bw = plot_w * v / top_axis
        out.append(f'<g class="row" style="animation-delay:{i * 0.05:.2f}s">'
                   f'<text x="{x0 - 14}" y="{y + 21}" text-anchor="end" font-size="14">{esc(k)}</text>'
                   f'<rect class="grow" x="{x0}" y="{y + 9}" width="{bw:.1f}" height="14" fill="{DATA}" style="--d:{i * 0.05:.2f}s;--w:{i * gap + 3.2:.2f}s"/>'
                   f'<text class="mono ride" x="{x0 + bw + 10:.1f}" y="{y + 21}" font-size="13" font-weight="600" style="--d:{i * 0.05:.2f}s;--bw:{bw:.1f}px">{v:,}</text>'
                   + (f'<text class="mono" x="{w - 32}" y="{y + 21}" text-anchor="end" font-size="12.5" style="fill:{INK2}">{extra}</text>' if extra else '') + '</g>')
    out.append(f'<line x1="{x0}" y1="{top - 12}" x2="{x0}" y2="{top + len(items) * rh}" stroke="{INK}" stroke-width="1.5"/>')
    out.append(f'<line x1="32" y1="{h - 36}" x2="{w - 32}" y2="{h - 36}" stroke="{INK}" stroke-width="1.5"/>'
               f'<text x="32" y="{h - 14}" font-size="13" class="soft">{esc(unit_note)}</text>')
    css = ('.row { animation: fade .5s both; } @keyframes fade { from { opacity: 0; } }\n'
           '.grow { transform-box: fill-box; transform-origin: left center; '
           'animation: grow 1s var(--d) cubic-bezier(.2,.8,.2,1) both, wave 9s var(--w) infinite; }\n'
           '@keyframes grow { from { transform: scaleX(0); } }\n'
           '.ride { animation: ride 1s var(--d) cubic-bezier(.2,.8,.2,1) both; }\n'
           '@keyframes ride { from { opacity: 0; transform: translateX(calc(var(--bw) * -1)); } }\n'
           f'@keyframes wave {{ 0%, 12%, 100% {{ fill: {DATA}; }} 4%, 6% {{ fill: {AMBER}; }} }}\n')
    save(name, svg(w, h, ''.join(out), css, '', title, note, ('cond7', 'sans4', 'sans6', 'mono4', 'mono6')))




if __name__ == '__main__':
    banner()
    divider()
    architecture()
    session_flow()
    access()
    applications()
    stats()
    risk()
    stack()
    setup()
    docs_heads()
    social_flows()
    config_map()
    storage()
    integration()
    deployment()
    dev_steps()
    triage()
    requirements()
    admin_paths()
