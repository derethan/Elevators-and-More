import os
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

REF_DIR = 'Elevators and More/src/main/resources/assets/elevatorsandmore/sample_ref'
OUT_DIR = 'Elevators and More/src/main/resources/assets/elevatorsandmore/textures/block'
SIZE = 1024

os.makedirs(OUT_DIR, exist_ok=True)

# 1. Load base seed textures from the Unbelievable Definition pack
base_iron = np.array(Image.open(os.path.join(REF_DIR, 'iron_block.png')).convert('RGBA'), dtype=np.float32)
base_iron_n = np.array(Image.open(os.path.join(REF_DIR, 'iron_block_n.png')).convert('RGBA'), dtype=np.float32)
base_iron_s = np.array(Image.open(os.path.join(REF_DIR, 'iron_block_s.png')).convert('RGBA'), dtype=np.float32)

def clamp255(arr):
    return np.clip(arr, 0, 255).astype(np.uint8)

def compute_normal_map(height_map, scale=2.0, base_n=None):
    H = height_map.astype(np.float32)
    gx = np.zeros_like(H)
    gy = np.zeros_like(H)
    
    gx[:, 1:-1] = (H[:, 2:] - H[:, :-2]) * 0.5
    gx[:, 0] = H[:, 1] - H[:, 0]
    gx[:, -1] = H[:, -1] - H[:, -2]
    
    gy[1:-1, :] = (H[2:, :] - H[:-2, :]) * 0.5
    gy[0, :] = H[1, :] - H[0, :]
    gy[-1, :] = H[-1, :] - H[-2, :]
    
    dx = -gx * scale / 128.0
    dy = -gy * scale / 128.0
    dz = np.ones_like(H)
    
    length = np.sqrt(dx*dx + dy*dy + dz*dz)
    nx = dx / length
    ny = dy / length
    nz = dz / length
    
    r = (nx * 0.5 + 0.5) * 255.0
    g = (ny * 0.5 + 0.5) * 255.0
    b = (nz * 0.5 + 0.5) * 255.0
    
    if base_n is not None:
        bnx = (base_n[:, :, 0] / 255.0) * 2.0 - 1.0
        bny = (base_n[:, :, 1] / 255.0) * 2.0 - 1.0
        
        nx_comb = np.clip(nx + bnx * 0.35, -1.0, 1.0)
        ny_comb = np.clip(ny + bny * 0.35, -1.0, 1.0)
        nz_comb = np.sqrt(np.maximum(0.01, 1.0 - nx_comb*nx_comb - ny_comb*ny_comb))
        
        r = (nx_comb * 0.5 + 0.5) * 255.0
        g = (ny_comb * 0.5 + 0.5) * 255.0
        b = (nz_comb * 0.5 + 0.5) * 255.0
        
    normal_img = np.zeros((SIZE, SIZE, 4), dtype=np.float32)
    normal_img[:, :, 0] = r
    normal_img[:, :, 1] = g
    normal_img[:, :, 2] = b
    normal_img[:, :, 3] = H
    return clamp255(normal_img)

def compute_specular_map(smoothness, metalness=235, emission=0):
    s_img = np.zeros((SIZE, SIZE, 4), dtype=np.float32)
    if np.isscalar(smoothness):
        s_img[:, :, 0] = smoothness
    else:
        s_img[:, :, 0] = smoothness
        
    if np.isscalar(metalness):
        s_img[:, :, 1] = metalness
    else:
        s_img[:, :, 1] = metalness
        
    if np.isscalar(emission):
        s_img[:, :, 2] = emission
    else:
        s_img[:, :, 2] = emission
        
    s_img[:, :, 3] = 255
    return clamp255(s_img)

def draw_rivet(height_arr, color_arr, cx, cy, radius=14):
    y_min = max(0, int(cy - radius - 3))
    y_max = min(SIZE, int(cy + radius + 4))
    x_min = max(0, int(cx - radius - 3))
    x_max = min(SIZE, int(cx + radius + 4))
    
    for y in range(y_min, y_max):
        for x in range(x_min, x_max):
            dist = math.hypot(x - cx, y - cy)
            if dist <= radius:
                val = math.sqrt(max(0.0, 1.0 - (dist / radius)**2))
                h_boost = val * 55.0
                height_arr[y, x] = min(255.0, height_arr[y, x] + h_boost)
                shade = 1.0 + (-(x - cx) - (y - cy)) / (radius * 2.6)
                color_arr[y, x, :3] = np.clip(color_arr[y, x, :3] * shade, 0, 255)
            elif dist <= radius + 2.5:
                height_arr[y, x] = max(0.0, height_arr[y, x] - 20.0)
                color_arr[y, x, :3] = color_arr[y, x, :3] * 0.65

def save_pbr_set(base_name, color, normal, spec):
    Image.fromarray(color).save(os.path.join(OUT_DIR, f'{base_name}.png'))
    Image.fromarray(normal).save(os.path.join(OUT_DIR, f'{base_name}_n.png'))
    Image.fromarray(spec).save(os.path.join(OUT_DIR, f'{base_name}_s.png'))
    print(f'Saved PBR set: {base_name}')

# =========================================================================
# CONTROL PEDESTAL TOP (OPERATOR INTERFACE CONSOLE)
# Mapped directly to the square top face of the console head [0, 0, 16, 16]
# =========================================================================
def make_pedestal_top():
    color = base_iron.copy()
    color[:, :, :3] *= [0.45, 0.48, 0.55]
    height = np.full((SIZE, SIZE), 140.0, dtype=np.float32)
    smoothness = np.full((SIZE, SIZE), 160.0, dtype=np.float32)
    metalness = np.full((SIZE, SIZE), 240.0, dtype=np.float32)
    emission = np.zeros((SIZE, SIZE), dtype=np.float32)

    # Outer beveled chassis rim (64px border on all sides)
    b = 64
    height[:b, :] += 30.0
    height[-b:, :] += 30.0
    height[:, :b] += 30.0
    height[:, -b:] += 30.0
    color[:b, :, :3] *= 1.2
    color[-b:, :, :3] *= 1.2
    color[:, :b, :3] *= 1.2
    color[:, -b:, :3] *= 1.2

    # Chassis Allen bolts at 4 corners
    for cx in [48, SIZE - 48]:
        for cy in [48, SIZE - 48]:
            draw_rivet(height, color, cx, cy, 15)

    # Inner groove slit
    for i in range(b - 3, b + 1):
        height[i, b:-b] -= 25.0
        height[-i-1, b:-b] -= 25.0
        height[b:-b, i] -= 25.0
        height[b:-b, -i-1] -= 25.0
        color[i, b:-b, :3] *= 0.6
        color[-i-1, b:-b, :3] *= 0.6
        color[b:-b, i, :3] *= 0.6
        color[b:-b, -i-1, :3] *= 0.6

    # ================= Telemetry Screen (Y: 96..530, X: 96..928) =================
    sx0, sy0, sx1, sy1 = 96, 96, 928, 530
    height[sy0:sy1, sx0:sx1] -= 15.0  # Recessed glass
    color[sy0:sy1, sx0:sx1, :3] = [8, 22, 34]
    metalness[sy0:sy1, sx0:sx1] = 15.0
    smoothness[sy0:sy1, sx0:sx1] = 245.0
    emission[sy0:sy1, sx0:sx1] = 45.0

    # Draw telemetry graphics with PIL
    im_c = Image.fromarray(clamp255(color))
    draw = ImageDraw.Draw(im_c)

    cyan = (0, 230, 255, 255)
    dim_cyan = (0, 115, 135, 255)
    white = (240, 250, 255, 255)
    amber = (255, 185, 20, 255)

    # Outer screen border
    draw.rectangle([sx0 + 6, sy0 + 6, sx1 - 6, sy1 - 6], outline=cyan, width=4)
    # Header bar
    draw.rectangle([sx0 + 6, sy0 + 6, sx1 - 6, sy0 + 60], fill=(0, 65, 85, 255))
    draw.text((sx0 + 20, sy0 + 18), "INDUSTRIAL ELEVATOR CARRIAGE OS v2.0", fill=cyan)

    # Left panel: Floor & Status
    draw.rectangle([sx0 + 24, sy0 + 80, sx0 + 380, sy1 - 24], outline=dim_cyan, width=3)
    draw.text((sx0 + 40, sy0 + 100), "CURRENT FLOOR", fill=dim_cyan)
    draw.text((sx0 + 40, sy0 + 150), "FL  01", fill=white)
    draw.text((sx0 + 40, sy0 + 250), "STATUS: DOCKED", fill=cyan)
    draw.text((sx0 + 40, sy0 + 300), "SPEED : 0.00 M/S", fill=dim_cyan)
    draw.text((sx0 + 40, sy0 + 340), "MODE  : AUTOMATIC", fill=dim_cyan)

    # Right panel: Shaft diagram
    draw.rectangle([sx1 - 390, sy0 + 80, sx1 - 24, sy1 - 24], outline=dim_cyan, width=3)
    draw.text((sx1 - 370, sy0 + 100), "SHAFT DIAGRAM", fill=dim_cyan)
    # Vertical rail
    draw.line([(sx1 - 210, sy0 + 140), (sx1 - 210, sy1 - 40)], fill=cyan, width=6)
    # Stations
    for ny in [sy0 + 160, sy0 + 240, sy0 + 320]:
        draw.ellipse([sx1 - 220, ny - 10, sx1 - 200, ny + 10], fill=(0, 230, 255, 255))
    # Elevator car
    draw.rectangle([sx1 - 250, sy0 + 300, sx1 - 170, sy0 + 340], fill=(40, 255, 120, 255), outline=white, width=3)

    # ================= Lower Controls (Y: 570..940) =================
    # Left: Illuminated Green CALL/GO Button (Center: X=320, Y=750, r=105)
    gx, gy, gr = 320, 750, 105
    for y in range(gy - gr - 16, gy + gr + 17):
        for x in range(gx - gr - 16, gx + gr + 17):
            dist = math.hypot(x - gx, y - gy)
            if dist <= gr + 16 and dist > gr:
                # Chrome bezel
                height[y, x] += 35.0
                color[y, x, :3] = [190, 195, 205]
                metalness[y, x] = 245.0
                smoothness[y, x] = 230.0
            elif dist <= gr and dist > gr - 6:
                # Dark recess
                height[y, x] -= 25.0
                color[y, x, :3] *= 0.3
            elif dist <= gr - 6:
                dome = math.sqrt(max(0.0, 1.0 - (dist / (gr - 6))**2))
                height[y, x] += dome * 35.0
                color[y, x, :3] = np.clip(np.array([30.0, 220.0, 85.0]) * dome + np.array([140.0, 255.0, 180.0]) * (dome**2), 0, 255)
                metalness[y, x] = 30.0
                smoothness[y, x] = 225.0
                emission[y, x] = 200.0

    # Right: Emergency Stop Mushroom Palm Button (Center: X=704, Y=750, r=120)
    rx, ry, rr = 704, 750, 120
    # Yellow backing disc (radius 138)
    for y in range(ry - 145, ry + 146):
        for x in range(rx - 145, rx + 146):
            dist = math.hypot(x - rx, y - ry)
            if dist <= 138 and dist > rr:
                height[y, x] += 15.0
                color[y, x, :3] = [235, 185, 25]
                metalness[y, x] = 30.0
                smoothness[y, x] = 140.0
            elif dist <= rr and dist > rr - 6:
                height[y, x] -= 20.0
                color[y, x, :3] *= 0.25
            elif dist <= rr - 6:
                # Red mushroom dome
                dome = math.sqrt(max(0.0, 1.0 - (dist / (rr - 6))**2))
                height[y, x] += 30.0 + dome * 65.0
                shade = 1.0 + (-(x - rx) - (y - ry)) / ((rr - 6) * 3.2)
                color[y, x, :3] = np.clip(np.array([220.0, 25.0, 25.0]) * (dome * shade), 0, 255)
                metalness[y, x] = 40.0
                smoothness[y, x] = 210.0

    # Center Plaque between buttons (X: 460..564, Y: 680..820)
    color[680:820, 460:564, :3] = base_iron[680:820, 460:564, :3] * 0.9
    height[680:820, 460:564] += 15.0
    metalness[680:820, 460:564] = 245.0
    for cx in [476, 548]:
        for cy in [696, 804]:
            draw_rivet(height, color, cx, cy, 8)

    # Stamp button labels with PIL
    im_c = Image.fromarray(clamp255(color))
    draw = ImageDraw.Draw(im_c)
    draw.text((gx - 32, gy - 12), "CALL", fill=(255, 255, 255, 255))
    draw.text((rx - 70, ry - 16), "EMERGENCY", fill=(255, 255, 255, 255))
    draw.text((rx - 36, ry + 12), "STOP", fill=(255, 255, 255, 255))
    draw.text((472, 742), "AUTO", fill=(240, 190, 20, 255))
    color = np.array(im_c, dtype=np.float32)

    norm = compute_normal_map(height, scale=2.2, base_n=base_iron_n)
    spec = compute_specular_map(smoothness, metalness, emission)
    save_pbr_set('industrial_control_pedestal', clamp255(color), norm, spec)

# =========================================================================
# CONTROL PEDESTAL SIDE (VERTICAL CASING WALLS WITH COOLING LOUVERS)
# Mapped to north, south, east, west faces of the console head
# =========================================================================
def make_pedestal_side():
    color = base_iron.copy()
    color[:, :, :3] *= [0.50, 0.53, 0.60]
    height = np.full((SIZE, SIZE), 135.0, dtype=np.float32)
    smoothness = np.full((SIZE, SIZE), 150.0, dtype=np.float32)
    metalness = np.full((SIZE, SIZE), 240.0, dtype=np.float32)
    emission = np.zeros((SIZE, SIZE), dtype=np.float32)

    # Top & bottom beveled lips
    height[:80, :] += 30.0
    height[944:, :] += 30.0
    color[:80, :, :3] *= 1.2
    color[944:, :, :3] *= 1.15

    # Corner Allen screws
    for cx in [64, SIZE - 64]:
        for cy in [64, SIZE - 64]:
            draw_rivet(height, color, cx, cy, 16)

    # Left and right vertical corner chamfers
    height[:, :48] += 20.0
    height[:, -48:] += 20.0

    # Horizontal cooling louvers (heat vents) in center (X: 160..864, Y: 200..840)
    louver_y_starts = [220, 300, 380, 460, 540, 620, 700, 780]
    for ly in louver_y_starts:
        # Louver blade height: 48px
        for dy in range(48):
            frac = dy / 48.0
            # Angled blade profile
            h_blade = 145.0 + frac * 40.0
            shade = 0.85 + frac * 0.4
            height[ly + dy, 160:864] = h_blade
            color[ly + dy, 160:864, :3] = (base_iron[ly + dy, 160:864, :3] * 0.6) * shade
        # Recessed dark shadow slot under blade
        height[ly + 48:ly + 56, 160:864] = 60.0
        color[ly + 48:ly + 56, 160:864, :3] *= 0.25

    norm = compute_normal_map(height, scale=2.0, base_n=base_iron_n)
    spec = compute_specular_map(smoothness, metalness, emission)
    save_pbr_set('industrial_control_pedestal_side', clamp255(color), norm, spec)

if __name__ == '__main__':
    print("Generating dedicated Pedestal Top & Side HD Textures...")
    make_pedestal_top()
    make_pedestal_side()
    print("Done!")
