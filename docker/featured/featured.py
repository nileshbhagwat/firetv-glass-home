#!/usr/bin/env python3
"""
Featured titles for Glass Home.

For each streaming app in the dock, finds its most popular films and series in India this
month (data from TMDB's streaming-service filter), cuts the main character out of each title's
artwork with the ISNet matting model, blends it so it reads as a separate 3D layer, and writes
the images and index.json that scripts/push-featured.ps1 copies to the TV. All of this runs on
the laptop. On the TV, the home screen shows only the titles of the app focused in the dock.

    python featured.py --out /work/featured/out           # needs TMDB_TOKEN
    python featured.py --out /work/featured/out --demo    # generated demo artwork, no account

"Popular this month" means TMDB's popularity ranking, which follows the last few weeks of
interest, among films and series first released in the last year (two years for apps with
few titles).

Blending, so the cut-out looks like it stands in front of the picture rather than pasted on:
  * depth of field: the background plate is softened and dimmed slightly;
  * clean edges: the matte is tightened and feathered, and edge colours are rebuilt from the
    character's own colours so no halo of the old background remains;
  * light wrap: the scene's colour bleeds softly onto the character's edges;
  * rim light: a thin highlight on the side facing the brightest part of the scene;
  * contact shadow: a soft shadow cast away from the light, baked into the character layer.
"""
import argparse
import datetime
import http.client
import io
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

# TMDB's alternate API domain: some Indian internet providers cut connections to
# api.themoviedb.org during the TLS handshake (most requests fail), while api.tmdb.org, which
# TMDB provides for such networks, answers normally. Same API and token.
API = "https://api.tmdb.org/3"
IMAGES = "https://image.tmdb.org/t/p/original"
FULL = (1920, 1080)
CARD = (768, 432)

# Streaming services shown in the dock: (TMDB provider name pattern, app package, label).
PROVIDERS = [
    (r"^netflix", "com.netflix.ninja", "Netflix"),
    (r"prime video", "com.amazon.firebat", "Prime Video"),
    (r"hotstar", "in.startv.hotstar", "JioHotstar"),
    (r"zee5", "com.zee5.amazon", "Zee5"),
    (r"sony ?liv", "com.onemainstream.sonyliv.android", "SonyLIV"),
]


# ----------------------------------------------------------------------------- TMDB

class Tmdb:
    def __init__(self, token):
        self.token = token.strip()
        # A 32-character v3 API key goes in the query string; a v4 read token is a bearer token.
        self.v3 = re.fullmatch(r"[0-9a-fA-F]{32}", self.token) is not None

    def get(self, path, **params):
        if self.v3:
            params["api_key"] = self.token
        url = API + path + "?" + urllib.parse.urlencode(params)
        headers = {"Accept": "application/json", "User-Agent": "glass-home-featured/1"}
        if not self.v3:
            headers["Authorization"] = "Bearer " + self.token
        for attempt in range(5):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
                    return json.load(r)
            except urllib.error.HTTPError as e:
                if e.code == 401:
                    sys.exit("TMDB rejected the token (HTTP 401). Check TMDB_TOKEN in tv.env.")
                if e.code == 429 or e.code >= 500:
                    time.sleep(2 + attempt * 2)
                    continue
                raise
            except NETWORK_ERRORS:  # dropped or reset connections: try again
                time.sleep(2 + attempt * 2)
        raise RuntimeError("TMDB did not answer: " + path)


# Connection problems worth a retry (HTTP errors with a status code are handled separately).
NETWORK_ERRORS = (urllib.error.URLError, http.client.HTTPException, OSError)


def app_providers(tmdb, region):
    """[(package, label, TMDB provider ids)] for the dock's services offered in the region.

    One app can have several TMDB entries (for example a plan with adverts); all are used."""
    ids = {package: set() for _, package, _ in PROVIDERS}
    for media in ("movie", "tv"):
        for p in tmdb.get(f"/watch/providers/{media}", watch_region=region).get("results", []):
            name = p.get("provider_name", "").lower()
            for pattern, package, _ in PROVIDERS:
                if re.search(pattern, name):
                    ids[package].add(p["provider_id"])
    return [(package, label, sorted(ids[package])) for _, package, label in PROVIDERS if ids[package]]


# How far back a title may have been first released, in days: one year, widened to two for
# apps with too few titles. Older titles are left out so that long-running reruns and old
# favourites do not crowd out what is new.
WINDOWS = (365, 730)


def popular_ids(tmdb, media, region, provider_ids):
    """TMDB ids of one app's most popular films (media "movie") or series ("tv").

    Ranked by TMDB popularity, which follows viewing and interest over the last few weeks, so
    this month's big releases come first. Uses TMDB's streaming-service filter (watch_region +
    with_watch_providers), so every title streams on that app in the region. A series counts
    from its first episode: filtering on any recent episode would favour daily and weekly
    shows that have run for years."""
    today = datetime.date.today()
    date_field = "primary_release_date" if media == "movie" else "first_air_date"
    seen = set()
    for days in WINDOWS:
        params = {
            f"{date_field}.gte": (today - datetime.timedelta(days=days)).isoformat(),
            f"{date_field}.lte": today.isoformat(),
            "watch_region": region,
            "with_watch_providers": "|".join(str(i) for i in provider_ids),
            "with_watch_monetization_types": "flatrate|free|ads",
            "sort_by": "popularity.desc",
            "include_adult": "false",
        }
        for page in (1, 2):
            data = tmdb.get(f"/discover/{media}", page=page, **params)
            for r in data.get("results", []):
                if r.get("id") not in seen and not r.get("adult"):
                    seen.add(r["id"])
                    yield r["id"]
            if page >= data.get("total_pages", 0):
                break


def describe(tmdb, media, ident):
    """Title and the best textless backdrop for one film or series, or None."""
    d = tmdb.get(f"/{media}/{ident}", append_to_response="images", include_image_language="null")
    backdrops = [b for b in d.get("images", {}).get("backdrops", [])
                 if b.get("iso_639_1") is None and b.get("width", 0) >= 1280]
    if not backdrops:
        return None
    backdrops.sort(key=lambda b: (b.get("vote_average", 0), b.get("width", 0)), reverse=True)
    return {
        "title": d.get("title") or d.get("name") or "",
        "image": IMAGES + backdrops[0]["file_path"],
    }


def download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "glass-home-featured/1"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return Image.open(io.BytesIO(r.read())).convert("RGB")
        except urllib.error.HTTPError:
            raise
        except NETWORK_ERRORS:
            if attempt == 3:
                raise
            time.sleep(2 + attempt * 2)


# ----------------------------------------------------------------------------- matting

class Matter:
    """ISNet general-use background removal (1024 x 1024 input), as published with rembg."""

    def __init__(self, path):
        import onnxruntime as ort
        options = ort.SessionOptions()
        options.log_severity_level = 3
        self.session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
        self.input = self.session.get_inputs()[0].name

    def __call__(self, image):
        small = np.asarray(image.resize((1024, 1024), Image.LANCZOS), dtype=np.float32)
        small = small / max(float(small.max()), 1e-6) - 0.5
        out = self.session.run(None, {self.input: small.transpose(2, 0, 1)[None]})[0][0, 0]
        out = (out - out.min()) / max(float(out.max() - out.min()), 1e-6)
        mask = Image.fromarray((out * 255).astype(np.uint8)).resize(image.size, Image.LANCZOS)
        return np.asarray(mask, dtype=np.float32) / 255.0


# ----------------------------------------------------------------------------- image helpers

def box(a, r, axis):
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis)
    n = 2 * r + 1
    return (c[n:] - c[:-n]) / n if axis == 0 else (c[:, n:] - c[:, :-n]) / n


def blur(a, sigma):
    """Gaussian-like blur of a 2-D or (H, W, C) float array: three box passes."""
    r = max(1, int(round(sigma * 0.9)))
    for _ in range(3):
        a = box(box(a, r, 0), r, 1)
    return a


def shift(a, dx, dy):
    """Moves an array by whole pixels, filling with zeros."""
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    dx, dy = int(round(dx)), int(round(dy))
    xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
    ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
    out[yd, xd] = a[ys, xs]
    return out


def main_subject(mask):
    """Keeps the largest group of columns holding foreground; returns (mask, box) or None."""
    h, w = mask.shape
    solid = mask > 0.5
    cols = solid.mean(axis=0) > 0.01
    runs, start, gap = [], None, 0
    for x in range(w + 1):
        on = x < w and cols[x]
        if on:
            if start is None:
                start = x
            gap = 0
        elif start is not None:
            gap += 1
            if gap > w * 0.02 or x == w:
                runs.append((start, x - gap + 1))
                start, gap = None, 0
    if not runs:
        return None
    a, b = max(runs, key=lambda r: mask[:, r[0]:r[1]].sum())
    keep = np.zeros_like(mask)
    keep[:, a:b] = mask[:, a:b]
    rows = np.where((keep > 0.5).mean(axis=1) > 0.004)[0]
    coverage = float((keep > 0.5).mean())
    if len(rows) == 0:
        return None
    top, bottom = int(rows[0]), int(rows[-1]) + 1
    if coverage < 0.025 or coverage > 0.5 or bottom - top < 0.35 * h or b - a > 0.7 * w:
        return None
    return keep, (a, top, b, bottom)


def light_of(rgb, subject_box):
    """Direction (unit vector from the subject towards the light) and colour of the scene light."""
    lum = blur(rgb.mean(axis=2), 12)
    threshold = np.quantile(lum, 0.98)
    ys, xs = np.where(lum >= threshold)
    colour = rgb[ys, xs].mean(axis=0)
    colour = np.clip(colour / max(float(colour.max()), 1e-6), 0.35, 1.0)
    cx = (subject_box[0] + subject_box[2]) / 2
    cy = (subject_box[1] + subject_box[3]) / 2
    v = np.array([xs.mean() - cx, ys.mean() - cy])
    length = float(np.hypot(*v))
    if length < 40:
        v, length = np.array([-0.5, -0.85]), 1.0
    return v / length, colour


def blend_character(rgb, mask, subject_box):
    """Returns the character colour and alpha (float arrays) with edges, light and shadow."""
    # Tighten and feather the matte so no ring of the old background is kept.
    m = Image.fromarray((mask * 255).astype(np.uint8))
    a = np.asarray(m.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.0)), np.float32) / 255

    # Rebuild edge colours from confidently-inside pixels (colour decontamination).
    inner = (a > 0.95).astype(np.float32)
    est_w = blur(inner, 6)
    est = blur(rgb * inner[..., None], 6) / np.maximum(est_w, 1e-4)[..., None]
    edge = ((a > 0.01) & (a < 0.95)).astype(np.float32) * np.clip(est_w * 4, 0, 1)
    fg = rgb * (1 - edge[..., None]) + est * edge[..., None]

    # Light wrap: blurred scene colour (without the character) bleeds onto the outer edge.
    outside = 1 - a
    scene = blur(rgb * outside[..., None], 22) / np.maximum(blur(outside, 22), 1e-4)[..., None]
    band = np.clip(a * (1 - blur(a, 7)) * 2.2, 0, 1)
    fg = 1 - (1 - fg) * (1 - scene * band[..., None] * 0.45)

    # Rim light on the side facing the brightest part of the scene.
    direction, colour = light_of(rgb, subject_box)
    toward = shift(a, -direction[0] * 5, -direction[1] * 5)
    rim = blur(np.clip(a * (1 - toward), 0, 1), 1.2) * a
    fg = 1 - (1 - fg) * (1 - rim[..., None] * colour[None, None, :] * 0.9)

    # Contact shadow cast away from the light, under the character.
    shadow = shift(blur(a, 10), -direction[0] * 14, -direction[1] * 14 + 6) * 0.55
    alpha = a + shadow * (1 - a)
    colour_out = fg * (a / np.maximum(alpha, 1e-4))[..., None]  # the shadow is black
    return np.clip(colour_out, 0, 1), np.clip(alpha, 0, 1)


def background_plate(image):
    """Softened, slightly dimmed artwork: shallow depth of field behind the character."""
    return ImageEnhance.Brightness(image.filter(ImageFilter.GaussianBlur(2.2))).enhance(0.9)


def crop_rgba(colour, alpha, box_, scale):
    x0, y0, x1, y1 = box_
    rgba = np.dstack([colour[y0:y1, x0:x1], alpha[y0:y1, x0:x1]])
    image = Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA")
    if scale != 1:
        image = image.resize((max(1, round((x1 - x0) * scale)), max(1, round((y1 - y0) * scale))), Image.LANCZOS)
    return image


def accent(image):
    small = image.resize((64, 36))
    hsv = np.asarray(small.convert("HSV"), np.float32) / 255
    rgb = np.asarray(small, np.float32)
    vivid = (hsv[..., 1] > 0.35) & (hsv[..., 2] > 0.3)
    c = rgb[vivid].mean(axis=0) if vivid.sum() > 20 else rgb.reshape(-1, 3).mean(axis=0)
    return "#%02X%02X%02X" % tuple(int(v) for v in c)


def build(image, matter, out_dir, n):
    """Writes the card and full-screen layers for one title; returns the index.json fields."""
    full = ImageOps.fit(image, FULL, Image.LANCZOS, centering=(0.5, 0.4))
    rgb = np.asarray(full, np.float32) / 255
    plate = background_plate(full)
    fields = {}
    picked = main_subject(matter(full))

    window = (0, 0, FULL[0], FULL[1])
    if picked is not None:
        mask, sb = picked
        colour, alpha = blend_character(rgb, mask, sb)
        ys, xs = np.where(alpha > 0.01)
        cb = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
        crop_rgba(colour, alpha, cb, 1).save(os.path.join(out_dir, f"{n}-full-char.png"), optimize=True)
        fields["fullChar"] = f"{n}-full-char.png"
        fields["fullCharRect"] = [round(cb[0] / FULL[0], 4), round(cb[1] / FULL[1], 4),
                                  round((cb[2] - cb[0]) / FULL[0], 4), round((cb[3] - cb[1]) / FULL[1], 4)]
        # Card window: its top edge cuts through the character's upper body, so the head and
        # shoulders rise out of the card.
        cut = sb[1] + 0.30 * (sb[3] - sb[1])
        h = FULL[1] - cut
        if h >= 0.45 * FULL[1]:
            w = h * 16 / 9
            cx = (sb[0] + sb[2]) / 2
            x = min(max(cx - w / 2, 0), FULL[0] - w)
            window = (x, cut, x + w, cut + h)
        wx, wy, ww, wh = window[0], window[1], window[2] - window[0], window[3] - window[1]
        scale = CARD[0] / ww
        crop_rgba(colour, alpha, cb, scale).save(os.path.join(out_dir, f"{n}-card-char.png"), optimize=True)
        fields["cardChar"] = f"{n}-card-char.png"
        fields["cardCharRect"] = [round((cb[0] - wx) / ww, 4), round((cb[1] - wy) / wh, 4),
                                  round((cb[2] - cb[0]) / ww, 4), round((cb[3] - cb[1]) / wh, 4)]

    card = plate.crop(tuple(int(round(v)) for v in window)).resize(CARD, Image.LANCZOS)
    card.save(os.path.join(out_dir, f"{n}-card.jpg"), quality=88, optimize=True)
    plate.save(os.path.join(out_dir, f"{n}-full.jpg"), quality=86, optimize=True)
    fields.update({"card": f"{n}-card.jpg", "full": f"{n}-full.jpg", "accent": accent(card)})
    return fields


# ----------------------------------------------------------------------------- demo artwork

# One film and one series per app: (title, kind, app label, package, sky colours, sun x, figure x).
DEMO = [
    ("Saffron Skies", "movie", "Netflix", "com.netflix.ninja", ((42, 15, 30), (122, 46, 58), (240, 160, 64)), 0.28, 0.62),
    ("Ember Road", "tv", "Netflix", "com.netflix.ninja", ((26, 10, 6), (90, 30, 14), (255, 122, 42)), 0.25, 0.58),
    ("Midnight Harbor", "movie", "Prime Video", "com.amazon.firebat", ((8, 23, 42), (22, 58, 86), (184, 99, 58)), 0.72, 0.36),
    ("Tidewater", "tv", "Prime Video", "com.amazon.firebat", ((4, 18, 30), (18, 70, 96), (120, 196, 210)), 0.30, 0.66),
    ("The Last Monsoon", "movie", "JioHotstar", "in.startv.hotstar", ((6, 20, 28), (31, 74, 82), (111, 154, 138)), 0.66, 0.32),
    ("Kite Season", "tv", "JioHotstar", "in.startv.hotstar", ((30, 20, 8), (110, 80, 30), (250, 200, 90)), 0.70, 0.38),
    ("Glass City", "movie", "SonyLIV", "com.onemainstream.sonyliv.android", ((11, 11, 36), (58, 42, 120), (208, 90, 154)), 0.30, 0.64),
    ("Night Shift", "tv", "SonyLIV", "com.onemainstream.sonyliv.android", ((6, 8, 20), (30, 34, 80), (150, 120, 220)), 0.72, 0.34),
    ("Northbound", "movie", "Zee5", "com.zee5.amazon", ((10, 26, 42), (63, 111, 154), (216, 230, 240)), 0.70, 0.40),
    ("Paper Lanterns", "tv", "Zee5", "com.zee5.amazon", ((24, 6, 18), (96, 28, 52), (255, 150, 90)), 0.26, 0.60),
]


def demo_scene(spec):
    """A generated scene with a figure, used to try the styles without a TMDB account."""
    (top, mid, horizon), sun_x, fig_x = spec[4:]
    w, h = FULL
    y = np.linspace(0, 1, h)[:, None, None]
    top, mid, horizon = (np.array(c, np.float32) for c in (top, mid, horizon))
    sky = np.where(y < 0.5, top + (mid - top) * (y / 0.5), mid + (horizon - mid) * np.clip((y - 0.5) / 0.28, 0, 1))
    img = np.broadcast_to(sky, (h, w, 3)).copy()
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx - sun_x * w, yy - 0.6 * h) / h
    img += (np.clip(1 - d / 0.45, 0, 1) ** 2)[..., None] * np.array([255, 190, 120]) * 0.7
    image = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    draw = ImageDraw.Draw(image)
    far = [(0, h)] + [(int(x * w / 8), int(h * (0.6 + 0.08 * ((x * 37) % 5) / 5))) for x in range(9)] + [(w, h)]
    draw.polygon(far, fill=tuple(int(c * 0.35) for c in mid))
    near = [(0, h)] + [(int(x * w / 6), int(h * (0.74 + 0.06 * ((x * 53) % 4) / 4))) for x in range(7)] + [(w, h)]
    draw.polygon(near, fill=(12, 14, 18))

    # Figure: head, hair, neck and coat, lit from the sun's side.
    fig = Image.new("L", (w, h), 0)
    fd = ImageDraw.Draw(fig)
    cx, s = fig_x * w, h / 300
    fd.ellipse([cx - 33 * s, 39 * s, cx + 33 * s, 121 * s], fill=255)
    fd.ellipse([cx - 36 * s, 28 * s, cx + 36 * s, 92 * s], fill=255)
    fd.rectangle([cx - 14 * s, 112 * s, cx + 14 * s, 146 * s], fill=255)
    fd.polygon([(cx - 96 * s, h), (cx - 88 * s, 190 * s), (cx - 46 * s, 142 * s), (cx + 46 * s, 142 * s),
                (cx + 88 * s, 190 * s), (cx + 96 * s, h)], fill=255)
    fd.ellipse([cx - 96 * s, 140 * s, cx - 20 * s, 220 * s], fill=255)
    fd.ellipse([cx + 20 * s, 140 * s, cx + 96 * s, 220 * s], fill=255)
    fig = fig.filter(ImageFilter.GaussianBlur(1.2))
    shade = np.linspace(0, 1, w)[None, :] if sun_x > fig_x else np.linspace(1, 0, w)[None, :]
    body = np.zeros((h, w, 3), np.float32) + np.array([20, 22, 30], np.float32)
    body += shade[..., None] * np.array([40, 34, 30], np.float32)
    face = Image.new("L", (w, h), 0)
    ImageDraw.Draw(face).ellipse([cx - 26 * s, 58 * s, cx + 26 * s, 118 * s], fill=255)
    face = np.asarray(face.filter(ImageFilter.GaussianBlur(6)), np.float32)[..., None] / 255
    body = body * (1 - face * 0.8) + np.array([120, 92, 76], np.float32) * face * 0.8
    alpha = np.asarray(fig, np.float32)[..., None] / 255
    out = np.asarray(image, np.float32) * (1 - alpha) + body * alpha
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


# ----------------------------------------------------------------------------- main

def app_titles(tmdb, matter, staging, region, media, count, provider_ids, label, built):
    """Up to `count` prepared films or series for one app, those with a clear character first.

    `built` caches prepared artwork by title, so a title on two apps is processed once."""
    noun = "film" if media == "movie" else "series"
    chosen, spare, tries = [], [], 0
    for ident in popular_ids(tmdb, media, region, provider_ids):
        if len(chosen) >= count or tries >= count * 3:
            break
        key = f"{media}-{ident}"
        if key not in built:
            try:
                info = describe(tmdb, media, ident)
            except (RuntimeError,) + NETWORK_ERRORS as e:  # one title must not stop the rest
                print(f"  {label} {noun} {ident}: no answer from TMDB ({e}); skipped", flush=True)
                continue
            if info is None:
                built[key] = None  # no textless artwork
                continue
            tries += 1
            print(f"  {label} {noun}: {info['title']}", flush=True)
            try:
                built[key] = {"title": info["title"], **build(download(info["image"]), matter, staging, key)}
            except Exception as e:  # one bad image must not stop the rest
                print(f"    skipped: {e}", flush=True)
                built[key] = None
                continue
            if "cardChar" not in built[key]:
                print("    no clear character; kept as a spare", flush=True)
        if built[key] is not None:
            (chosen if "cardChar" in built[key] else spare).append({"id": key, "kind": media, **built[key]})
    return chosen + spare[:max(0, count - len(chosen))]


def interleave(films, series):
    """film, series, film, series, ...: the order the gallery ring shows them in."""
    out = []
    for i in range(max(len(films), len(series))):
        out.extend(x[i] for x in (films, series) if i < len(x))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--out", default="/work/featured/out")
    parser.add_argument("--films", type=int, default=6, help="films per app")
    parser.add_argument("--series", type=int, default=4, help="series per app")
    parser.add_argument("--region", default="IN")
    parser.add_argument("--demo", action="store_true", help="use generated demo artwork")
    args = parser.parse_args()

    staging = args.out.rstrip("/") + ".tmp"
    shutil.rmtree(staging, ignore_errors=True)
    os.makedirs(staging)
    matter = Matter(os.environ.get("MATTE_MODEL", "/opt/models/isnet-general-use.onnx"))
    items = []

    if args.demo:
        for n, spec in enumerate(DEMO, 1):
            title, kind, label, package = spec[:4]
            print(f"[{n}/{len(DEMO)}] {title} ({label}, demo)", flush=True)
            key = f"demo-{n}"
            fields = build(demo_scene(spec), matter, staging, key)
            items.append({"id": key, "kind": kind, "title": title, "provider": label, "package": package, **fields})
    else:
        token = os.environ.get("TMDB_TOKEN", "")
        if not token:
            sys.exit("TMDB_TOKEN is not set. Add TMDB_TOKEN=... to tv.env (see README).")
        tmdb = Tmdb(token)
        apps = app_providers(tmdb, args.region)
        if not apps:
            sys.exit("TMDB lists none of the dock's streaming services for region " + args.region)
        built = {}
        for package, label, ids in apps:
            print(f"{label} (TMDB provider {', '.join(map(str, ids))})", flush=True)
            try:
                films = app_titles(tmdb, matter, staging, args.region, "movie", args.films, ids, label, built)
                series = app_titles(tmdb, matter, staging, args.region, "tv", args.series, ids, label, built)
            except (RuntimeError,) + NETWORK_ERRORS as e:  # keep the other apps' titles
                print(f"  {label}: no answer from TMDB ({e}); this app is skipped this time", flush=True)
                continue
            print(f"  {len(films)} films, {len(series)} series", flush=True)
            items.extend({**t, "provider": label, "package": package} for t in interleave(films, series))

    if not items:
        sys.exit("No featured titles could be prepared.")
    used = {v for item in items for k, v in item.items() if k in ("card", "cardChar", "full", "fullChar")}
    for name in os.listdir(staging):
        if name not in used:
            os.remove(os.path.join(staging, name))
    index = {
        "version": 1,
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": "demo" if args.demo else "tmdb",
        "items": items,
    }
    with open(os.path.join(staging, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    shutil.rmtree(args.out, ignore_errors=True)
    os.replace(staging, args.out)
    print(f"Prepared {len(items)} featured titles in {args.out}")


if __name__ == "__main__":
    main()
