#!/usr/bin/env python3
"""
Builds browser/dist/cognisense-preview.html, a single self-contained file:
  1. compiles the shared Kotlin core (android/core/src/main/kotlin, unchanged) plus browser/src
     to JavaScript with kotlinc-js (two steps: sources -> klib -> JS);
  2. inlines that JavaScript, browser/web/app.js and style.css, the fonts in browser/web/fonts, and the exact bytes of
     config/tasks.json and config/strings.json into browser/web/index.template.html.
Usage: python3 browser/build.py --kotlin-home /path/to/kotlinc   (Kotlin 2.0.21 compiler zip)
"""
import argparse, base64, hashlib, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B = os.path.join(ROOT, 'browser')


def compile_core(kotlin_home):
    out = os.path.join(B, 'build')
    os.makedirs(out, exist_ok=True)
    kjs = os.path.join(kotlin_home, 'bin', 'kotlinc-js')
    stdlib = os.path.join(kotlin_home, 'lib', 'kotlin-stdlib-js.klib')
    klib_dir = os.path.join(out, 'klib')
    js_dir = os.path.join(out, 'js')
    sources = [os.path.join(ROOT, 'android', 'core', 'src', 'main', 'kotlin'), os.path.join(B, 'src')]
    subprocess.run([kjs, '-Xir-produce-klib-file', '-ir-output-dir', klib_dir, '-ir-output-name', 'cognisense-core',
                    '-libraries', stdlib, *sources], check=True)
    # Absolute output paths are required: kotlinc-js silently writes nothing for some relative paths.
    subprocess.run([kjs, '-Xir-produce-js', '-Xir-dce', '-ir-output-dir', js_dir, '-ir-output-name', 'cognisense-core',
                    '-module-kind', 'umd', '-libraries', stdlib,
                    '-Xinclude=' + os.path.join(klib_dir, 'cognisense-core.klib')], check=True)
    js = os.path.join(js_dir, 'cognisense-core.js')
    if not os.path.exists(js):
        sys.exit('kotlinc-js produced no output')
    return js


def read(p, binary=False):
    with open(p, 'rb' if binary else 'r', encoding=None if binary else 'utf-8') as f:
        return f.read()


FONTS = [  # (family, weight, file) - SIL Open Font License 1.1, see web/fonts/OFL.txt
    ('Atkinson Hyperlegible', 400, 'atkinson-hyperlegible-400.woff2'),
    ('Atkinson Hyperlegible', 700, 'atkinson-hyperlegible-700.woff2'),
    ('Noto Nastaliq Urdu', 400, 'noto-nastaliq-urdu-400.woff2'),
]


def font_faces():
    """@font-face rules with the fonts inlined as data: URIs, so the page needs no network (CSP font-src data:)."""
    rules = []
    for family, weight, name in FONTS:
        data = base64.b64encode(read(os.path.join(B, 'web', 'fonts', name), True)).decode('ascii')
        rules.append(f"@font-face {{ font-family: '{family}'; font-style: normal; font-weight: {weight}; "
                     f"font-display: block; src: url(data:font/woff2;base64,{data}) format('woff2'); }}")
    return '\n'.join(rules)


SOURCES = ['android/core/src/main/kotlin', 'browser/src', 'browser/web', 'config/tasks.json', 'config/strings.json']


def source_fingerprint():
    """SHA-256 over every input of the demo (paths and bytes, in a fixed order).
    Embedded in the built file so CI can check that the committed demo was built from the current
    sources. Compiler output itself can differ between Java versions, so the output bytes are not compared."""
    files = []
    for rel in SOURCES:
        p = os.path.join(ROOT, rel)
        if os.path.isfile(p):
            files.append(rel)
        else:
            for dp, _, fn in os.walk(p):
                files += [os.path.relpath(os.path.join(dp, f), ROOT).replace(os.sep, '/') for f in fn]
    h = hashlib.sha256()
    for rel in sorted(files):
        h.update(rel.encode() + b'\0' + read(os.path.join(ROOT, rel), True) + b'\0')
    return h.hexdigest()


def check_current():
    dist = os.path.join(B, 'dist', 'cognisense-preview.html')
    m = re.search(r'<meta name="cognisense-sources" content="([0-9a-f]{64})">', read(dist))
    want = source_fingerprint()
    if not m or m.group(1) != want:
        sys.exit(f'browser/dist/cognisense-preview.html was not built from the current sources '
                 f'(embedded {m.group(1) if m else "none"}, current {want}). Run browser/build.py and commit the result.')
    print('the committed web demo was built from the current sources (' + want[:12] + ')')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--kotlin-home', default=os.environ.get('KOTLIN_HOME'))
    ap.add_argument('--core-js', help='use an already compiled core bundle instead of compiling')
    ap.add_argument('--check-current', action='store_true', help='only check that the committed demo matches the current sources')
    a = ap.parse_args()
    if a.check_current:
        return check_current()
    core_js = a.core_js or compile_core(a.kotlin_home or sys.exit('--kotlin-home or KOTLIN_HOME is required'))
    tasks_bytes = read(os.path.join(ROOT, 'config', 'tasks.json'), True)
    parts = {
        '@STYLE@': font_faces() + '\n' + read(os.path.join(B, 'web', 'style.css')),
        '@TASKS_JSON@': tasks_bytes.decode('utf-8'),
        '@STRINGS_JSON@': read(os.path.join(ROOT, 'config', 'strings.json')),
        '@CORE_JS@': read(core_js),
        '@APP_JS@': read(os.path.join(B, 'web', 'app.js')),
        '@SOURCES@': source_fingerprint(),
    }
    html = read(os.path.join(B, 'web', 'index.template.html'))
    for k, v in parts.items():
        if '</script' in v.lower() or '<!--' in v:
            sys.exit(f'{k} contains a sequence that would break inline embedding')
        if html.count(k) != 1:
            sys.exit(f'template must contain {k} exactly once')
        html = html.replace(k, v)
    os.makedirs(os.path.join(B, 'dist'), exist_ok=True)
    dist = os.path.join(B, 'dist', 'cognisense-preview.html')
    with open(dist, 'w', encoding='utf-8', newline='\n') as f:
        f.write(html)
    print(f'wrote {dist} ({len(html.encode()) // 1024} KB); embedded tasks.json sha256 {hashlib.sha256(tasks_bytes).hexdigest()}')


if __name__ == '__main__':
    main()
