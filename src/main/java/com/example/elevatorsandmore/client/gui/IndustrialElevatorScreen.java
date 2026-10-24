package com.example.elevatorsandmore.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.network.ElevatorActionPayload;
import com.example.elevatorsandmore.network.ElevatorSyncPayload;
import com.example.elevatorsandmore.network.FloorInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@OnlyIn(Dist.CLIENT)
public class IndustrialElevatorScreen extends Screen {
    private static final int WINDOW_WIDTH = 240;
    private static final int WINDOW_HEIGHT = 220;

    private final BlockPos originPos;
    private final BlockPos railPos;
    private final List<FloorInfo> floors = new ArrayList<>();
    private final int currentElevatorY;
    private final boolean isMoving;

    private int leftPos;
    private int topPos;
    private int scrollOffset = 0;

    private @Nullable EditBox renameBox;
    private @Nullable Button callButton;
    private @Nullable Button stopButton;

    public IndustrialElevatorScreen(
            BlockPos originPos,
            BlockPos railPos,
            List<FloorInfo> floors,
            int currentElevatorY,
            boolean isMoving
    ) {
        super(Component.translatable("gui.elevatorsandmore.elevator_control.title"));
        this.originPos = originPos;
        this.railPos = railPos;
        this.floors.addAll(floors);
        this.currentElevatorY = currentElevatorY;
        this.isMoving = isMoving;
    }

    public static void open(ElevatorSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new IndustrialElevatorScreen(
                payload.originPos(),
                payload.railPos(),
                payload.floors(),
                payload.currentElevatorY(),
                payload.isMoving()
        ));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        int xo = this.leftPos;
        int yo = this.topPos;

        this.clearWidgets();

        // Floor action buttons (up to 4 floors visible per page)
        int maxVisible = 4;
        int startIdx = this.scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, this.floors.size());

        int rowY = yo + 48;
        for (int i = startIdx; i < endIdx; i++) {
            FloorInfo fi = this.floors.get(i);
            int targetY = fi.yLevel();

            // Destination Go Button
            this.addRenderableWidget(
                    Button.builder(Component.literal("Go"), btn -> {
                        ClientPacketDistributor.sendToServer(new ElevatorActionPayload(
                                this.originPos,
                                ElevatorActionPayload.ACTION_TRAVEL,
                                targetY,
                                ""
                        ));
                        this.onClose();
                    }).bounds(xo + WINDOW_WIDTH - 44, rowY - 1, 32, 18).build()
            );

            rowY += 22;
        }

        // Scroll Buttons if more than maxVisible floors
        if (this.floors.size() > maxVisible) {
            this.addRenderableWidget(
                    Button.builder(Component.literal("▲"), btn -> {
                        if (this.scrollOffset > 0) {
                            this.scrollOffset--;
                            this.init();
                        }
                    }).bounds(xo + WINDOW_WIDTH - 44, yo + 28, 16, 16).build()
            );

            this.addRenderableWidget(
                    Button.builder(Component.literal("▼"), btn -> {
                        if (this.scrollOffset + maxVisible < this.floors.size()) {
                            this.scrollOffset++;
                            this.init();
                        }
                    }).bounds(xo + WINDOW_WIDTH - 26, yo + 28, 16, 16).build()
            );
        }

        // Call Elevator Button
        this.callButton = this.addRenderableWidget(
                Button.builder(Component.literal("Call Elevator Here").withStyle(ChatFormatting.GOLD), btn -> {
                    ClientPacketDistributor.sendToServer(new ElevatorActionPayload(
                            this.originPos,
                            ElevatorActionPayload.ACTION_CALL,
                            this.originPos.getY(),
                            ""
                    ));
                    this.onClose();
                }).bounds(xo + 12, yo + 138, 120, 20).build()
        );

        // Emergency Stop Button
        this.stopButton = this.addRenderableWidget(
                Button.builder(Component.literal("Stop").withStyle(ChatFormatting.RED), btn -> {
                    ClientPacketDistributor.sendToServer(new ElevatorActionPayload(
                            this.originPos,
                            ElevatorActionPayload.ACTION_EMERGENCY_STOP,
                            0,
                            ""
                    ));
                    this.onClose();
                }).bounds(xo + 136, yo + 138, 92, 20).build()
        );

        // Floor Name EditBox
        String currentName = "";
        for (FloorInfo fi : this.floors) {
            if (fi.stationPos().equals(this.originPos)) {
                currentName = fi.floorName();
                break;
            }
        }
        if (currentName.isEmpty()) currentName = "Floor Y=" + (this.originPos.getY() - 2);

        this.renameBox = this.addRenderableWidget(
                new EditBox(this.font, xo + 12, yo + 166, 150, 18, Component.literal("Floor Name"))
        );
        this.renameBox.setMaxLength(32);
        this.renameBox.setValue(currentName);

        this.addRenderableWidget(
                Button.builder(Component.literal("Save"), btn -> {
                    if (this.renameBox != null) {
                        String name = this.renameBox.getValue().trim();
                        if (!name.isEmpty()) {
                            ClientPacketDistributor.sendToServer(new ElevatorActionPayload(
                                    this.originPos,
                                    ElevatorActionPayload.ACTION_RENAME,
                                    this.originPos.getY(),
                                    name
                            ));
                            this.onClose();
                        }
                    }
                }).bounds(xo + 166, yo + 166, 62, 18).build()
        );

        // Close Done Button
        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), btn -> this.onClose())
                        .bounds(xo + (WINDOW_WIDTH - 80) / 2, yo + 192, 80, 20)
                        .build()
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = this.leftPos;
        int yo = this.topPos;

        // Background panel
        graphics.fill(xo - 2, yo - 2, xo + WINDOW_WIDTH + 2, yo + WINDOW_HEIGHT + 2, 0xCC0D1117);
        graphics.fill(xo, yo, xo + WINDOW_WIDTH, yo + WINDOW_HEIGHT, 0xF21C2028);
        graphics.outline(xo, yo, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFF4B5362);

        // Hazard top bar (alternating yellow / black)
        for (int i = 0; i < (WINDOW_WIDTH - 2) / 8; i++) {
            int col = (i % 2 == 0) ? 0xFFF59E0B : 0xFF1F2937;
            graphics.fill(xo + 1 + i * 8, yo + 1, xo + 1 + (i + 1) * 8, yo + 4, col);
        }

        // Title
        graphics.text(this.font, Component.literal("Industrial Elevator").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), xo + 12, yo + 8, 0xFFFFFFFF, true);

        // Status Line
        Component statusText;
        if (this.isMoving) {
            statusText = Component.literal("Status: In Motion (Y=" + this.currentElevatorY + ")").withStyle(ChatFormatting.YELLOW);
        } else {
            statusText = Component.literal("Status: Docked (Y=" + this.currentElevatorY + ")").withStyle(ChatFormatting.GREEN);
        }
        graphics.text(this.font, statusText, xo + 12, yo + 22, 0xFFFFFFFF, false);

        // Floor List Header
        graphics.text(this.font, Component.literal("Available Shaft Floors:").withStyle(ChatFormatting.DARK_GRAY), xo + 12, yo + 34, 0xFFAAAAAA, false);

        // Floor Rows
        int maxVisible = 4;
        int startIdx = this.scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, this.floors.size());

        int rowY = yo + 48;
        for (int i = startIdx; i < endIdx; i++) {
            FloorInfo fi = this.floors.get(i);

            // Row background highlight
            int bgCol = fi.isElevatorDocked() ? 0x3310B981 : 0x22FFFFFF;
            graphics.fill(xo + 10, rowY - 2, xo + WINDOW_WIDTH - 48, rowY + 16, bgCol);

            String label = fi.floorName() + " (Y=" + fi.yLevel() + ")";
            ChatFormatting col = fi.isElevatorDocked() ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            graphics.text(this.font, Component.literal(label).withStyle(col), xo + 14, rowY + 2, 0xFFFFFFFF, false);

            if (fi.isElevatorDocked()) {
                graphics.text(this.font, Component.literal("● DOCKED").withStyle(ChatFormatting.DARK_GREEN), xo + 14, rowY + 10, 0xFF00FF00, false);
            }

            rowY += 22;
        }

        if (this.floors.isEmpty()) {
            graphics.text(this.font, Component.literal("No stations detected along rail shaft.").withStyle(ChatFormatting.RED), xo + 14, rowY + 6, 0xFFFF5555, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
