# Elevators and More

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen.svg)](#)
[![NeoForge](https://img.shields.io/badge/Modloader-NeoForge-orange.svg)](#)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-blue.svg)](#)

**Elevators and More** brings realistic, smoothly animated, multi-floor industrial elevators and travel utilities to Minecraft 26.3 (NeoForge). Build custom moving elevator platforms of varying shapes and sizes, link call stations across dozens or hundreds of vertical blocks, name your floors, and travel between levels seamlessly with smooth entity physics.

---

## 🌟 Key Features

- **True Moving Platforms**: Elevators physically transition into moving carriage entities with real-time collision and smooth sub-tick camera interpolation for passengers.
- **Dynamic Platform Assembling**: Assemble platforms up to 49 blocks (configurable up to 121 blocks) using `Industrial Elevator Floor` blocks, with attached `Industrial Safety Railing` and `Elevator Control Pedestals`.
- **Intelligent Guide Rail Shafts**: A single vertical column of `Industrial Guide Rail` blocks powers the elevator shaft for up to 384+ blocks vertically.
- **Interactive Floor Stations**:
  - Place `Elevator Control Boards` at any floor along the shaft to act as elevator landings.
  - Automatic station discovery and docking detection (`DOCKED` indicator light).
  - Open the control GUI to inspect all discovered floors, call the elevator to your current level, or dispatch it to any designated floor.
  - Rename floors directly in the GUI (stored per station and synchronized with all passengers).
- **Onboard Pedestal Controls**:
  - Mount an `Elevator Control Pedestal` directly onto the moving platform.
  - Right-click mid-ride to open the elevator control menu or trigger the **Emergency Stop** to halt motion immediately.
- **Sound & Visual Feedback**: Heavy industrial mechanical sounds on departure, movement, and docking clunk when settling into floor alignment.
- **Server-Friendly & Multiplayer Safe**: Custom network payloads synchronize floor listings, naming, and carriage motion across clients and servers.

---

## 📦 Blocks & Items

| Item / Block | Description | Crafting Summary |
| :--- | :--- | :--- |
| **Industrial Guide Rail** | The structural spine of the elevator shaft. Placed vertically along the wall of the shaft. | 6x Iron Bars surrounded by Iron Ingots |
| **Industrial Elevator Floor** | The foundation block of the moving carriage. Connected floor blocks form the platform. | 4x Iron Ingots around Smooth Stone |
| **Industrial Safety Railing** | Protective edge railings that travel with the carriage to prevent riders from falling off. | 3x Iron Bars and 2x Yellow Dye |
| **Elevator Control Board** | Wall-mounted station placed at floor landings to call the elevator and rename floors. | Iron Ingots, Stone Button, Redstone Torches |
| **Elevator Control Pedestal** | Standalone control column placed on top of the moving platform for onboard control. | Stone Button, Redstone, Iron Ingot |

---

## 🛠️ How to Build an Elevator

1. **Erect the Guide Rail**:
   - Build a continuous vertical column of **Industrial Guide Rails** along your shaft from your lowest level to your highest floor.

2. **Construct the Platform**:
   - At your starting floor, place **Industrial Elevator Floor** blocks adjoining or within perimeter reach of the Guide Rail column (supports shapes up to 7x7 / 49 blocks by default).
   - Place **Industrial Safety Railings** along the platform edges for safety and industrial aesthetic.
   - Place an **Elevator Control Pedestal** on the platform floor so you can control the elevator while riding it.

3. **Set Up Floor Stations**:
   - On each floor landing, place an **Elevator Control Board** on a wall within 2 blocks horizontally of the platform perimeter.
   - Right-click the Control Board to open the GUI. Enter a custom floor name (e.g., *"Mining Level - Y:-58"*, *"Main Lobby"*, *"Penthouse"*) and click **Save**.

4. **Operate**:
   - **From a Landing**: Right-click the Control Board and press **Call Elevator Here** to summon the platform, or press **Go** next to any floor to dispatch it.
   - **While Riding**: Right-click the Control Pedestal on your platform to select a destination floor or hit **Emergency Stop** to halt at any height.

---

## ⚙️ Configuration

The mod generates a local configuration file at `.minecraft/config/elevatorsandmore-client.toml` / `elevatorsandmore-server.toml` (or inside your world `serverconfig`):

```toml
[industrial_elevator]
    # Maximum number of connected floor blocks that can make up an elevator platform (1 - 121)
    maxPlatformBlocks = 49

    # Maximum horizontal span (width or length) in blocks from the guide rail anchor (1 - 11)
    maxHorizontalSpan = 7

    # Maximum vertical distance in blocks that an elevator shaft can span (16 - 512)
    maxShaftHeight = 384

    # Travel speed of the elevator in blocks per tick (0.20 = 4 blocks per second)
    elevatorSpeed = 0.20
```

---

## 📋 Requirements & Compatibility

- **Minecraft**: `26.3`
- **Mod Loader**: `NeoForge` (version `26.3.0.48-beta` or higher recommended)
- **Dependencies**: No external library mods required.
- **Client & Server**: Required on both client and dedicated server.

---

## 📄 License

All Rights Reserved. Distributed for personal and server play with attribution.
