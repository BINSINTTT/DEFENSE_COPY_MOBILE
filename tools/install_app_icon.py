"""Render the Android launcher icons from the original, uncropped in-app logo."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

res = Path(__file__).resolve().parents[1] / "app/src/main/res"
with Image.open(res / "drawable-nodpi/tomatoinsight_logo.png") as artwork:
    logo = artwork.convert("RGBA")

background = logo.getpixel((0, 0))
resampling = Image.Resampling.LANCZOS


def icon_canvas(size, artwork_width):
    canvas = Image.new("RGBA", (size, size), background)
    art = ImageOps.contain(logo, (artwork_width, artwork_width), resampling)
    canvas.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return canvas


# Android masks the center of a 108 dp adaptive layer. Keep the artwork
# within the central 66 dp so its leaf and magnifier survive each mask.
foreground = icon_canvas(432, 264)
foreground.save(res / "drawable/ic_launcher_foreground.png", optimize=True)

square = icon_canvas(576, 540)
circle = Image.new("L", square.size)
ImageDraw.Draw(circle).ellipse((0, 0, square.width - 1, square.height - 1), fill=255)
rounded = square.copy()
rounded.putalpha(circle)

for density, pixels in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    directory = res / f"mipmap-{density}"
    for image, name in [(square, "ic_launcher"), (rounded, "ic_launcher_round")]:
        image.resize((pixels, pixels), resampling).save(directory / f"{name}.png", optimize=True)
