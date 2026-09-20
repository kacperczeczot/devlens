#!/usr/bin/env python3
"""
Generate complete visual assets (icons, logos) for DevLens.
Outputs:
- Android mipmaps (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Android adaptive icon foreground/background
- Android drawable/ic_launcher.png (512x512)
- Desktop assets (256, 128, 64, 32, 16 px PNG, ICO, and ICNS via iconutil)
"""

import os
import math
import subprocess
from PIL import Image, ImageDraw, ImageFilter

def create_devlens_master(size=1024, is_foreground_only=False, is_round=False):
    # Supersampling 2x for ultra smooth rendering
    s = size * 2
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx, cy = s // 2, s // 2

    if not is_foreground_only:
        # Background: Deep Obsidian rounded squircle or circle
        if is_round:
            draw.ellipse([s * 0.04, s * 0.04, s * 0.96, s * 0.96], fill=(8, 11, 20, 255))
        else:
            radius = int(s * 0.22)
            draw.rounded_rectangle([s * 0.04, s * 0.04, s * 0.96, s * 0.96], radius=radius, fill=(8, 11, 20, 255))
            
            # Subtle gradient overlay / glow on the background
            glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
            gdraw = ImageDraw.Draw(glow)
            gdraw.ellipse([s * 0.15, s * 0.10, s * 0.85, s * 0.80], fill=(0, 210, 255, 35))
            glow = glow.filter(ImageFilter.GaussianBlur(s * 0.08))
            img = Image.alpha_composite(img, glow)
            draw = ImageDraw.Draw(img)

    # Outer Aperture / Optical Lens Ring
    # Gradient simulation from Electric Cyan (0, 210, 255) to Violet (139, 92, 246)
    ring_radius = int(s * 0.36)
    ring_width = int(s * 0.045)

    # Glow layer for aperture
    glow_ring = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_ring)
    gdraw.ellipse([cx - ring_radius, cy - ring_radius, cx + ring_radius, cy + ring_radius], outline=(0, 210, 255, 120), width=ring_width + int(s * 0.02))
    glow_ring = glow_ring.filter(ImageFilter.GaussianBlur(s * 0.025))
    img = Image.alpha_composite(img, glow_ring)
    draw = ImageDraw.Draw(img)

    # Draw segmented lens aperture arcs
    segments = 4
    for i in range(segments):
        start_angle = i * 90 + 12
        end_angle = (i + 1) * 90 - 12
        # Interpolate color from Cyan (top/left) to Indigo/Violet (bottom/right)
        t = i / segments
        r = int(0 * (1 - t) + 139 * t)
        g = int(210 * (1 - t) + 92 * t)
        b = int(255 * (1 - t) + 246 * t)
        draw.arc([cx - ring_radius, cy - ring_radius, cx + ring_radius, cy + ring_radius],
                 start=start_angle, end=end_angle, fill=(r, g, b, 255), width=ring_width)

    # Inner lens optics ring (subtle dashed/thin cyan circle)
    inner_radius = int(s * 0.26)
    inner_width = int(s * 0.015)
    draw.ellipse([cx - inner_radius, cy - inner_radius, cx + inner_radius, cy + inner_radius],
                 outline=(0, 210, 255, 180), width=inner_width)

    # Optical focus ticks at 0, 90, 180, 270 degrees
    tick_len = int(s * 0.04)
    tick_w = int(s * 0.018)
    for angle in [0, 90, 180, 270]:
        rad = math.radians(angle)
        x1 = cx + math.cos(rad) * (ring_radius + ring_width // 2 + int(s * 0.01))
        y1 = cy + math.sin(rad) * (ring_radius + ring_width // 2 + int(s * 0.01))
        x2 = cx + math.cos(rad) * (ring_radius + ring_width // 2 + tick_len)
        y2 = cy + math.sin(rad) * (ring_radius + ring_width // 2 + tick_len)
        draw.line([x1, y1, x2, y2], fill=(0, 210, 255, 230), width=tick_w)

    # Center Developer Symbol: Modern glowing '< / >' lens reticle
    # Left bracket '<'
    code_glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    cgdraw = ImageDraw.Draw(code_glow)

    b_width = int(s * 0.038)
    b_h = int(s * 0.12)
    b_w = int(s * 0.075)

    # Draw left chevron
    p_left_tip = (cx - int(s * 0.16), cy)
    p_left_top = (cx - int(s * 0.16) + b_w, cy - b_h)
    p_left_bot = (cx - int(s * 0.16) + b_w, cy + b_h)
    cgdraw.line([p_left_top, p_left_tip, p_left_bot], fill=(0, 210, 255, 100), width=b_width + int(s * 0.02), joint="round")
    draw.line([p_left_top, p_left_tip, p_left_bot], fill=(0, 210, 255, 255), width=b_width, joint="round")

    # Slash '/'
    slash_top = (cx + int(s * 0.025), cy - int(s * 0.14))
    slash_bot = (cx - int(s * 0.025), cy + int(s * 0.14))
    cgdraw.line([slash_top, slash_bot], fill=(99, 102, 241, 100), width=b_width + int(s * 0.02))
    draw.line([slash_top, slash_bot], fill=(129, 140, 248, 255), width=b_width)

    # Right chevron '>'
    p_right_tip = (cx + int(s * 0.16), cy)
    p_right_top = (cx + int(s * 0.16) - b_w, cy - b_h)
    p_right_bot = (cx + int(s * 0.16) - b_w, cy + b_h)
    cgdraw.line([p_right_top, p_right_tip, p_right_bot], fill=(139, 92, 246, 100), width=b_width + int(s * 0.02), joint="round")
    draw.line([p_right_top, p_right_tip, p_right_bot], fill=(168, 85, 247, 255), width=b_width, joint="round")

    code_glow = code_glow.filter(ImageFilter.GaussianBlur(s * 0.02))
    img = Image.alpha_composite(img, code_glow)

    # Downsample with Lanczos to requested size
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    return img

def create_adaptive_background(size=512):
    s = size * 2
    img = Image.new("RGBA", (s, s), (8, 11, 20, 255))
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([s * 0.2, s * 0.15, s * 0.8, s * 0.75], fill=(0, 210, 255, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(s * 0.1))
    img = Image.alpha_composite(img, glow)
    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    root = "/Volumes/MAC_STORAGE_APFS/Developer/GitHub/devlens"
    android_res = os.path.join(root, "apps/android/app/src/main/res")
    
    # 1. Android Mipmap densities
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    print("Generating Android mipmaps...")
    for folder, px in densities.items():
        dir_path = os.path.join(android_res, folder)
        os.makedirs(dir_path, exist_ok=True)
        
        # Standard icon
        icon = create_devlens_master(size=px, is_round=False)
        icon.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        
        # Round icon
        round_icon = create_devlens_master(size=px, is_round=True)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")

        # Foreground for adaptive icon (extra padding for safe zone: 108dp base)
        # For mdpi (108px), hdpi (162px), xhdpi (216px), xxhdpi (324px), xxxhdpi (432px)
        fg_px = int(px * 108 / 48)
        fg_icon = create_devlens_master(size=fg_px, is_foreground_only=True)
        fg_icon.save(os.path.join(dir_path, "ic_launcher_foreground.png"), "PNG")
        
        bg_icon = create_adaptive_background(size=fg_px)
        bg_icon.save(os.path.join(dir_path, "ic_launcher_background.png"), "PNG")

    # 2. Main drawable/ic_launcher.png (512x512)
    drawable_dir = os.path.join(android_res, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    master_512 = create_devlens_master(size=512, is_round=False)
    master_512.save(os.path.join(drawable_dir, "ic_launcher.png"), "PNG")
    print("Android assets generated successfully.")

    # 3. Desktop assets
    desktop_assets = os.path.join(root, "apps/desktop/assets")
    os.makedirs(desktop_assets, exist_ok=True)

    master_1024 = create_devlens_master(size=1024, is_round=False)
    master_256 = master_1024.resize((256, 256), Image.Resampling.LANCZOS)
    master_128 = master_1024.resize((128, 128), Image.Resampling.LANCZOS)
    master_64 = master_1024.resize((64, 64), Image.Resampling.LANCZOS)
    master_32 = master_1024.resize((32, 32), Image.Resampling.LANCZOS)
    master_16 = master_1024.resize((16, 16), Image.Resampling.LANCZOS)

    master_256.save(os.path.join(desktop_assets, "icon_256.png"), "PNG")
    master_128.save(os.path.join(desktop_assets, "icon_128.png"), "PNG")
    master_32.save(os.path.join(desktop_assets, "icon_32.png"), "PNG")
    master_16.save(os.path.join(desktop_assets, "icon_16.png"), "PNG")

    # Raw 32x32 RGBA bytes for tray_icon crate
    with open(os.path.join(desktop_assets, "icon_32.rgba"), "wb") as f:
        f.write(master_32.convert("RGBA").tobytes())

    # Windows .ico (multi-resolution)
    master_256.save(os.path.join(desktop_assets, "icon.ico"), format="ICO", sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])

    # macOS iconset & .icns
    iconset_dir = os.path.join(desktop_assets, "DevLens.iconset")
    os.makedirs(iconset_dir, exist_ok=True)
    master_16.save(os.path.join(iconset_dir, "icon_16x16.png"), "PNG")
    master_32.save(os.path.join(iconset_dir, "icon_16x16@2x.png"), "PNG")
    master_32.save(os.path.join(iconset_dir, "icon_32x32.png"), "PNG")
    master_64.save(os.path.join(iconset_dir, "icon_32x32@2x.png"), "PNG")
    master_128.save(os.path.join(iconset_dir, "icon_128x128.png"), "PNG")
    master_256.save(os.path.join(iconset_dir, "icon_128x128@2x.png"), "PNG")
    master_256.save(os.path.join(iconset_dir, "icon_256x256.png"), "PNG")
    master_512.save(os.path.join(iconset_dir, "icon_256x256@2x.png"), "PNG")
    master_512.save(os.path.join(iconset_dir, "icon_512x512.png"), "PNG")
    master_1024.save(os.path.join(iconset_dir, "icon_512x512@2x.png"), "PNG")

    try:
        subprocess.run(["iconutil", "-c", "icns", iconset_dir, "-o", os.path.join(desktop_assets, "AppIcon.icns")], check=True)
        print("Generated AppIcon.icns for macOS via iconutil.")
    except Exception as e:
        print("Warning: could not generate .icns:", e)

    print("All visual assets generated successfully!")

if __name__ == "__main__":
    main()
