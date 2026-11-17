package dev.raskol.hud.client.render;

import dev.raskol.hud.client.config.HudConfig;
import dev.raskol.hud.client.network.CrosshairSignalPayload;
import dev.raskol.hud.protocol.RaskolHudProtocol.CrosshairSignal;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Пиксельные классовые знаки вместо универсального стрелкового перекрестья.
 * Все элементы рисуются в GUI-пикселях и не требуют внешних текстур.
 */
public final class RaskolCrosshairRenderer {

    private static final int SHADOW = 0xC0000000;
    private static final int HIGHLIGHT = 0x45FFFFFF;

    private RaskolCrosshairRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, RaskolHudSnapshot state) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        float scale = (float) HudConfig.scale();
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx * (1.0F - scale), cy * (1.0F - scale));
        graphics.pose().scale(scale, scale);
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());
        CrosshairSignalPayload.Pulse pulse = CrosshairSignalPayload.current();
        CrosshairSignal signal = pulse.active() ? pulse.signal() : CrosshairSignal.NONE;
        int idle = theme.highlight();
        int active = pulse.active() ? pulseColor(signal) : idle;

        switch (state.classId().toLowerCase(java.util.Locale.ROOT)) {
            case "warlock" -> drawWarlock(graphics, cx, cy, idle, signal, pulse.strength());
            case "hunter" -> drawHunter(graphics, cx, cy, idle, active, signal, pulse.strength());
            case "warrior" -> drawWarrior(graphics, cx, cy, idle, signal, pulse.strength());
            case "priest" -> drawPriest(graphics, cx, cy, idle, active, signal, pulse.strength());
            case "mage" -> drawMage(graphics, cx, cy, idle, active, signal, pulse.strength());
            case "rogue" -> drawRogue(graphics, cx, cy, idle, active, signal, pulse.strength());
            default -> drawNeutral(graphics, cx, cy, idle);
        }
        graphics.pose().popMatrix();
    }

    private static void drawWarlock(
            GuiGraphicsExtractor g, int cx, int cy, int idle, CrosshairSignal signal, float strength) {
        drawLine(g, cx, cy, new int[][] {
            {-6,-6},{-5,-5},{-4,-4}, {6,-6},{5,-5},{4,-4},
            {-6,6},{-5,5},{-4,4}, {6,6},{5,5},{4,4}
        }, idle);
        drawLine(g, cx, cy, new int[][] {
            {-3,4},{-2,5},{-1,6},{0,7},{1,6},{2,5},{3,4}
        }, idle);
        if (signal == CrosshairSignal.WARLOCK_CURSE) {
            drawLine(g, cx, cy, new int[][] {
                {-8,-8},{8,-8},{-8,8},{8,8}
            }, pulseColor(signal, strength));
        }
    }

    private static void drawHunter(
            GuiGraphicsExtractor g, int cx, int cy, int idle, int active,
            CrosshairSignal signal, float strength) {
        boolean closing = signal == CrosshairSignal.HUNTER_MARK;
        int inset = closing ? 1 : 0;
        int color = closing ? active : idle;
        drawLine(g, cx, cy, new int[][] {
            {-7+inset,-6+inset},{-6+inset,-6+inset},{-7+inset,-5+inset},
            {-6+inset,-5+inset},{-5+inset,-4+inset},{-4+inset,-3+inset},
            {7-inset,-6+inset},{6-inset,-6+inset},{7-inset,-5+inset},
            {6-inset,-5+inset},{5-inset,-4+inset},{4-inset,-3+inset},
            {-7+inset,6-inset},{-6+inset,6-inset},{-7+inset,5-inset},
            {-6+inset,5-inset},{-5+inset,4-inset},{-4+inset,3-inset},
            {7-inset,6-inset},{6-inset,6-inset},{7-inset,5-inset},
            {6-inset,5-inset},{5-inset,4-inset},{4-inset,3-inset}
        }, color);
        pixel(g, cx, cy, 0, 0, color);
        if (closing && strength > 0.4F) {
            pixel(g, cx, cy, -8, 0, pulseColor(signal, strength));
            pixel(g, cx, cy, 8, 0, pulseColor(signal, strength));
        }
    }

    private static void drawWarrior(
            GuiGraphicsExtractor g, int cx, int cy, int idle, CrosshairSignal signal, float strength) {
        int spread = signal == CrosshairSignal.WARRIOR_HIT
                || signal == CrosshairSignal.WARRIOR_BLEED ? 1 : 0;
        block(g,cx,cy,-6-spread,-6-spread,2,idle);
        block(g,cx,cy,-4-spread,-4-spread,2,idle);
        block(g,cx,cy,5+spread,-6-spread,2,idle);
        block(g,cx,cy,3+spread,-4-spread,2,idle);
        block(g,cx,cy,-6-spread,5+spread,2,idle);
        block(g,cx,cy,-4-spread,3+spread,2,idle);
        block(g,cx,cy,5+spread,5+spread,2,idle);
        block(g,cx,cy,3+spread,3+spread,2,idle);
        if (signal == CrosshairSignal.WARRIOR_BLEED && strength > 0.15F) {
            pixel(g,cx,cy,0,0,pulseColor(signal,strength));
        }
    }

    private static void drawPriest(
            GuiGraphicsExtractor g, int cx, int cy, int idle, int active,
            CrosshairSignal signal, float strength) {
        int lift = signal == CrosshairSignal.PRIEST_BLESSING ? 1 : 0;
        int color = lift == 1 ? active : idle;
        drawLine(g,cx,cy,new int[][] {
            {-8,-1-lift},{-7,-2-lift},{-6,-3-lift},{-5,-3-lift},{-4,-2-lift},{-3,-1-lift},
            {-8,-lift},{-7,1-lift},{-6,1-lift},
            {8,-1-lift},{7,-2-lift},{6,-3-lift},{5,-3-lift},{4,-2-lift},{3,-1-lift},
            {8,-lift},{7,1-lift},{6,1-lift}
        },color);
        drawLine(g,cx,cy,new int[][] {{-2,4-lift},{-1,5-lift},{0,6-lift},{1,5-lift},{2,4-lift}},color);
        if (lift == 1 && strength > 0.35F) {
            pixel(g,cx,cy,-5,-5,pulseColor(signal,strength));
            pixel(g,cx,cy,5,-5,pulseColor(signal,strength));
        }
    }

    private static void drawMage(
            GuiGraphicsExtractor g, int cx, int cy, int idle, int active,
            CrosshairSignal signal, float strength) {
        boolean bloom = signal == CrosshairSignal.MAGE_FROST;
        int color = bloom ? active : idle;
        drawLine(g,cx,cy,new int[][] {
            {0,-7},{0,-6},{0,-5},{0,5},{0,6},{0,7},
            {-7,0},{-6,0},{-5,0},{5,0},{6,0},{7,0},
            {-6,-6},{-5,-5},{6,-6},{5,-5},
            {-6,6},{-5,5},{6,6},{5,5},
            {-4,-4},{4,-4},{-4,4},{4,4}
        },color);
        pixel(g,cx,cy,0,0,color);
        if (bloom && strength > 0.3F) {
            int flash=pulseColor(signal,strength);
            pixel(g,cx,cy,0,-8,flash);
            pixel(g,cx,cy,0,8,flash);
            pixel(g,cx,cy,-8,0,flash);
            pixel(g,cx,cy,8,0,flash);
            pixel(g,cx,cy,-7,-7,flash);
            pixel(g,cx,cy,7,-7,flash);
            pixel(g,cx,cy,-7,7,flash);
            pixel(g,cx,cy,7,7,flash);
        }
    }

    private static void drawRogue(
            GuiGraphicsExtractor g, int cx, int cy, int idle, int active,
            CrosshairSignal signal, float strength) {
        boolean closing = signal == CrosshairSignal.ROGUE_BACKSTAB
                || signal == CrosshairSignal.ROGUE_STEALTH;
        int inset = closing ? 1 : 0;
        int color = closing ? active : idle;
        drawLine(g,cx,cy,new int[][] {
            {-7+inset,7-inset},{-6+inset,6-inset},{-5+inset,5-inset},
            {-4+inset,4-inset},{-3+inset,3-inset},{-2+inset,2-inset},
            {7-inset,7-inset},{6-inset,6-inset},{5-inset,5-inset},
            {4-inset,4-inset},{3-inset,3-inset},{2-inset,2-inset}
        },color);
        pixel(g,cx,cy,0,0,color);
        if (closing && strength > 0.4F) {
            pixel(g,cx,cy,-1,1,pulseColor(signal,strength));
            pixel(g,cx,cy,1,1,pulseColor(signal,strength));
        }
    }

    private static void drawNeutral(GuiGraphicsExtractor g,int cx,int cy,int color) {
        pixel(g,cx,cy,0,-3,color); pixel(g,cx,cy,-3,0,color);
        pixel(g,cx,cy,3,0,color); pixel(g,cx,cy,0,3,color);
        pixel(g,cx,cy,0,0,color);
    }

    private static int pulseColor(CrosshairSignal signal) {
        return switch(signal) {
            case WARLOCK_CURSE -> 0xFFE4A16F;
            case HUNTER_MARK -> 0xFFFF934E;
            case WARRIOR_HIT -> 0xFFFFD2A0;
            case WARRIOR_BLEED -> 0xFFE95B68;
            case PRIEST_BLESSING -> 0xFFFFE7A9;
            case MAGE_FROST -> 0xFFBDEBFF;
            case ROGUE_BACKSTAB -> 0xFFFF4E66;
            case ROGUE_STEALTH -> 0xFFC69AF1;
            case NONE -> 0xFFE8DED2;
        };
    }

    private static int pulseColor(CrosshairSignal signal,float strength) {
        return withAlpha(pulseColor(signal),Math.max(110,Math.min(255,130+Math.round(125*strength))));
    }

    private static void drawLine(GuiGraphicsExtractor g,int cx,int cy,int[][] points,int color) {
        for(int[] point:points) pixel(g,cx,cy,point[0],point[1],color);
    }

    private static void pixel(GuiGraphicsExtractor g,int cx,int cy,int dx,int dy,int color) {
        g.fill(cx+dx-1,cy+dy-1,cx+dx,cy+dy,HIGHLIGHT);
        g.fill(cx+dx+1,cy+dy+1,cx+dx+2,cy+dy+2,SHADOW);
        g.fill(cx+dx,cy+dy,cx+dx+1,cy+dy+1,color);
    }

    private static void block(GuiGraphicsExtractor g,int cx,int cy,int dx,int dy,int size,int color) {
        g.fill(cx+dx-1,cy+dy-1,cx+dx,cy+dy,HIGHLIGHT);
        g.fill(cx+dx+1,cy+dy+1,cx+dx+size+1,cy+dy+size+1,SHADOW);
        g.fill(cx+dx,cy+dy,cx+dx+size,cy+dy+size,color);
    }

    private static int withAlpha(int color,int alpha) {
        return (color&0x00FFFFFF)|(Math.max(0,Math.min(255,alpha))<<24);
    }
}
