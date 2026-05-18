import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * ╔══════════════════════════════════════════╗
 * ║         SPACE SHOOTER  —  Java           ║
 * ║  Arrow Keys / WASD  →  Move              ║
 * ║  SPACE              →  Shoot             ║
 * ║  P                  →  Pause             ║
 * ║  R (Game Over)      →  Restart           ║
 * ╚══════════════════════════════════════════╝
 *
 * Compile:  javac SpaceShooter.java
 * Run:      java SpaceShooter
 */
public class SpaceShooter extends JPanel implements ActionListener, KeyListener {

    // ── Window ─────────────────────────────────────────────────────────────
    static final int W = 800, H = 700;

    // ── Game states ────────────────────────────────────────────────────────
    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEVEL_CLEAR }
    State state = State.MENU;

    // ── Timing ─────────────────────────────────────────────────────────────
    javax.swing.Timer timer;
    static final int FPS = 60;
    long frameCount = 0;

    // ── Input ──────────────────────────────────────────────────────────────
    boolean left, right, up, down, shooting;

    // ── Player ─────────────────────────────────────────────────────────────
    Player player;

    // ── Collections ────────────────────────────────────────────────────────
    List<Bullet>    playerBullets = new ArrayList<>();
    List<Bullet>    enemyBullets  = new ArrayList<>();
    List<Enemy>     enemies       = new ArrayList<>();
    List<Explosion> explosions    = new ArrayList<>();
    List<Star>      stars         = new ArrayList<>();
    List<PowerUp>   powerUps      = new ArrayList<>();

    // ── HUD / progression ─────────────────────────────────────────────────
    int score  = 0;
    int hiScore= 0;
    int level  = 1;
    int wave   = 0;           // wave within level (0-based)
    int totalWaves = 3;
    long shootCooldown = 0;
    long enemyShootTimer = 0;
    int levelClearTimer = 0;

    // ── Screen-shake ──────────────────────────────────────────────────────
    int shakeFrames = 0;
    Random rng = new Random();

    // ── Colors & fonts ────────────────────────────────────────────────────
    static final Color COL_BG      = new Color(4, 8, 20);
    static final Color COL_ACCENT  = new Color(0, 230, 255);
    static final Color COL_WARN    = new Color(255, 80, 80);
    static final Color COL_GOLD    = new Color(255, 215, 0);
    static final Color COL_GREEN   = new Color(60, 255, 100);
    static final Font  FONT_HUD    = new Font("Monospaced", Font.BOLD, 14);
    static final Font  FONT_BIG    = new Font("Monospaced", Font.BOLD, 38);
    static final Font  FONT_MED    = new Font("Monospaced", Font.BOLD, 20);
    static final Font  FONT_SM     = new Font("Monospaced", Font.PLAIN, 13);

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════
    public SpaceShooter() {
        setPreferredSize(new Dimension(W, H));
        setBackground(COL_BG);
        setFocusable(true);
        addKeyListener(this);
        generateStars(120);
        timer = new javax.swing.Timer(1000 / FPS, this);
        timer.start();
    }

    void startGame() {
        score  = 0;
        level  = 1;
        wave   = 0;
        player = new Player(W / 2, H - 90);
        playerBullets.clear();
        enemyBullets.clear();
        explosions.clear();
        powerUps.clear();
        spawnWave();
        state = State.PLAYING;
    }

    void generateStars(int n) {
        stars.clear();
        for (int i = 0; i < n; i++) stars.add(new Star());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WAVE / LEVEL SPAWNING
    // ══════════════════════════════════════════════════════════════════════
    void spawnWave() {
        enemies.clear();
        enemyBullets.clear();
        int cols = 8 + wave + (level - 1) * 2;
        int rows = 2 + wave + (level - 1);
        cols = Math.min(cols, 12);
        rows = Math.min(rows, 5);

        int startX = (W - cols * 60) / 2 + 25;
        int startY = 70;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int type = (r == 0) ? 2 : (r == 1 ? 1 : 0);
                enemies.add(new Enemy(startX + c * 60, startY + r * 55, type, level));
            }
        }
    }

    void nextWave() {
        wave++;
        if (wave >= totalWaves) {
            wave = 0;
            level++;
        }
        levelClearTimer = FPS * 2;
        state = State.LEVEL_CLEAR;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GAME LOOP
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void actionPerformed(ActionEvent e) {
        frameCount++;
        if (state == State.PLAYING) updateGame();
        if (state == State.LEVEL_CLEAR) {
            levelClearTimer--;
            updateStars();
            if (levelClearTimer <= 0) { spawnWave(); state = State.PLAYING; }
        }
        repaint();
    }

    void updateGame() {
        updateStars();
        updatePlayer();
        updateBullets();
        updateEnemies();
        updateExplosions();
        updatePowerUps();
        checkCollisions();
        if (shakeFrames > 0) shakeFrames--;
    }

    void updateStars() {
        for (Star s : stars) s.update();
    }

    void updatePlayer() {
        int spd = player.boosted ? 7 : 4;
        if (left  && player.x > 20)       player.x -= spd;
        if (right && player.x < W - 20)   player.x += spd;
        if (up    && player.y > H/2)       player.y -= spd;
        if (down  && player.y < H - 20)   player.y += spd;

        if (shooting && frameCount >= shootCooldown) {
            int cd = player.rapidFire ? 6 : 14;
            shootCooldown = frameCount + cd;
            spawnPlayerBullets();
        }

        // timed power-up expiry
        if (player.boosted   && frameCount > player.boostEnd)    player.boosted   = false;
        if (player.rapidFire && frameCount > player.rapidEnd)    player.rapidFire = false;
        if (player.spread    && frameCount > player.spreadEnd)   player.spread    = false;
        if (player.shield    && frameCount > player.shieldEnd)   player.shield    = false;
    }

    void spawnPlayerBullets() {
        if (player.spread) {
            playerBullets.add(new Bullet(player.x, player.y - 20, 0, -14, true));
            playerBullets.add(new Bullet(player.x, player.y - 20, -4, -12, true));
            playerBullets.add(new Bullet(player.x, player.y - 20,  4, -12, true));
        } else {
            playerBullets.add(new Bullet(player.x, player.y - 20, 0, -14, true));
        }
    }

    void updateBullets() {
        playerBullets.removeIf(b -> { b.update(); return b.y < -10; });
        enemyBullets.removeIf(b -> { b.update(); return b.y > H + 10; });

        // enemy shooting
        if (!enemies.isEmpty() && frameCount >= enemyShootTimer) {
            int cd = Math.max(20, 80 - level * 8 - wave * 5);
            enemyShootTimer = frameCount + cd;
            Enemy shooter = enemies.get(rng.nextInt(enemies.size()));
            enemyBullets.add(new Bullet(shooter.x, shooter.y + 18, 0, 5 + level, false));
        }
    }

    void updateEnemies() {
        if (enemies.isEmpty()) { nextWave(); return; }
        boolean hitEdge = false;
        for (Enemy en : enemies) {
            en.update(frameCount);
            if (en.x < 22 || en.x > W - 22) hitEdge = true;
        }
        if (hitEdge) {
            for (Enemy en : enemies) { en.dirX *= -1; en.y += 18; }
        }
        // enemy reaches bottom → game over
        for (Enemy en : enemies) {
            if (en.y > H - 60) { triggerGameOver(); return; }
        }
    }

    void updateExplosions() {
        explosions.removeIf(ex -> !ex.alive());
        for (Explosion ex : explosions) ex.update();
    }

    void updatePowerUps() {
        powerUps.removeIf(p -> { p.update(); return p.y > H + 20 || !p.alive; });
    }

    void checkCollisions() {
        // player bullets ↔ enemies
        Iterator<Bullet> bi = playerBullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            Iterator<Enemy> ei = enemies.iterator();
            while (ei.hasNext()) {
                Enemy en = ei.next();
                if (dist(b.x, b.y, en.x, en.y) < 22) {
                    en.hp--;
                    bi.remove();
                    if (en.hp <= 0) {
                        score += en.points;
                        if (score > hiScore) hiScore = score;
                        explosions.add(new Explosion(en.x, en.y, en.type == 2 ? 20 : 14));
                        shakeFrames = 6;
                        maybeDrop(en.x, en.y);
                        ei.remove();
                    }
                    break;
                }
            }
        }

        // enemy bullets ↔ player
        if (!player.invincible) {
            Iterator<Bullet> ebi = enemyBullets.iterator();
            while (ebi.hasNext()) {
                Bullet b = ebi.next();
                if (dist(b.x, b.y, player.x, player.y) < 20) {
                    ebi.remove();
                    if (player.shield) { player.shield = false; }
                    else               { hitPlayer(); }
                }
            }
        }

        // power-ups ↔ player
        Iterator<PowerUp> pi = powerUps.iterator();
        while (pi.hasNext()) {
            PowerUp p = pi.next();
            if (dist(p.x, p.y, player.x, player.y) < 25) {
                applyPowerUp(p.type);
                pi.remove();
            }
        }
    }

    void hitPlayer() {
        player.lives--;
        shakeFrames = 14;
        explosions.add(new Explosion(player.x, player.y, 18));
        player.invincible = true;
        // brief invincibility
        new javax.swing.Timer(2000, ev -> { player.invincible = false; ((javax.swing.Timer)ev.getSource()).stop(); }).start();
        if (player.lives <= 0) triggerGameOver();
    }

    void triggerGameOver() {
        if (score > hiScore) hiScore = score;
        state = State.GAME_OVER;
    }

    void maybeDrop(int x, int y) {
        if (rng.nextInt(100) < 18) {
            powerUps.add(new PowerUp(x, y, rng.nextInt(4)));
        }
    }

    void applyPowerUp(int type) {
        int dur = FPS * 8;
        switch (type) {
            case 0: player.rapidFire = true; player.rapidEnd  = frameCount + dur; break;
            case 1: player.spread    = true; player.spreadEnd = frameCount + dur; break;
            case 2: player.shield    = true; player.shieldEnd = frameCount + dur; break;
            case 3: player.boosted   = true; player.boostEnd  = frameCount + dur; break;
        }
    }

    double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDERING
    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // screen shake
        if (shakeFrames > 0) {
            g.translate(rng.nextInt(7) - 3, rng.nextInt(7) - 3);
        }

        drawBackground(g);

        switch (state) {
            case MENU:       drawMenu(g);      break;
            case PLAYING:    drawGame(g);      break;
            case PAUSED:     drawGame(g); drawPause(g); break;
            case GAME_OVER:  drawGame(g); drawGameOver(g); break;
            case LEVEL_CLEAR:drawGame(g); drawLevelClear(g); break;
        }
    }

    void drawBackground(Graphics2D g) {
        g.setColor(COL_BG);
        g.fillRect(0, 0, W, H);
        for (Star s : stars) s.draw(g);
    }

    void drawGame(Graphics2D g) {
        // power-ups
        for (PowerUp p : powerUps)     p.draw(g);
        // bullets
        for (Bullet b : playerBullets) b.draw(g, true);
        for (Bullet b : enemyBullets)  b.draw(g, false);
        // entities
        for (Enemy en : enemies)        en.draw(g);
        // player
        if (player != null) {
            if (!player.invincible || (frameCount / 4) % 2 == 0) player.draw(g);
        }
        // explosions
        for (Explosion ex : explosions) ex.draw(g);
        // HUD
        drawHUD(g);
    }

    void drawHUD(Graphics2D g) {
        g.setFont(FONT_HUD);
        // score
        g.setColor(COL_ACCENT);
        g.drawString("SCORE  " + String.format("%07d", score), 14, 22);
        g.setColor(COL_GOLD);
        g.drawString("BEST   " + String.format("%07d", hiScore), 14, 40);
        // level / wave
        g.setColor(Color.WHITE);
        g.drawString("LEVEL " + level + "  WAVE " + (wave + 1) + "/" + totalWaves,
                     W / 2 - 90, 22);
        // lives
        g.setColor(COL_GREEN);
        g.drawString("LIVES:", W - 140, 22);
        for (int i = 0; i < player.lives; i++) {
            drawMiniShip(g, W - 80 + i * 22, 14);
        }
        // active power-ups
        int py = 58;
        if (player.rapidFire) { g.setColor(COL_GOLD);   g.drawString("⚡ RAPID FIRE", 14, py); py += 16; }
        if (player.spread)    { g.setColor(COL_ACCENT);  g.drawString("↔ SPREAD", 14, py);     py += 16; }
        if (player.shield)    { g.setColor(new Color(100,180,255)); g.drawString("🛡 SHIELD", 14, py); py += 16; }
        if (player.boosted)   { g.setColor(COL_GREEN);   g.drawString("▶ BOOST", 14, py); }

        // enemy count
        g.setColor(new Color(200, 200, 200));
        g.setFont(FONT_SM);
        g.drawString("ENEMIES: " + enemies.size(), W - 130, 40);
    }

    void drawMiniShip(Graphics2D g, int x, int y) {
        g.setColor(COL_GREEN);
        int[] px = {x, x - 6, x + 6};
        int[] py = {y, y + 10, y + 10};
        g.fillPolygon(px, py, 3);
    }

    void drawMenu(Graphics2D g) {
        // title glow
        g.setFont(FONT_BIG);
        String title = "SPACE SHOOTER";
        FontMetrics fm = g.getFontMetrics();
        int tx = (W - fm.stringWidth(title)) / 2;
        // glow layers
        for (int r = 12; r >= 0; r -= 3) {
            float alpha = 0.04f * (12 - r + 1);
            g.setColor(new Color(0f, 0.9f, 1f, alpha));
            g.drawString(title, tx, 240 + r/2);
            g.drawString(title, tx, 240 - r/2);
        }
        g.setColor(COL_ACCENT);
        g.drawString(title, tx, 240);

        g.setFont(FONT_MED);
        g.setColor(Color.WHITE);
        center(g, "PRESS  ENTER  TO  START", 310);

        g.setFont(FONT_SM);
        g.setColor(new Color(150, 200, 255));
        center(g, "ARROWS / WASD  →  MOVE", 370);
        center(g, "SPACE  →  SHOOT       ", 390);
        center(g, "P  →  PAUSE           ", 410);

        g.setFont(FONT_HUD);
        g.setColor(COL_GOLD);
        center(g, "HI-SCORE  " + String.format("%07d", hiScore), 460);

        // decorative demo ships
        drawPlayerShipAt(g, W / 2, 560, 1.6f);
        drawEnemyShipAt(g, W / 2 - 100, 480, 0, 1.4f);
        drawEnemyShipAt(g, W / 2,       480, 1, 1.4f);
        drawEnemyShipAt(g, W / 2 + 100, 480, 2, 1.4f);
    }

    void drawPause(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, W, H);
        g.setFont(FONT_BIG);
        g.setColor(COL_GOLD);
        center(g, "PAUSED", H / 2 - 20);
        g.setFont(FONT_MED);
        g.setColor(Color.WHITE);
        center(g, "P  to resume", H / 2 + 30);
    }

    void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, H);
        g.setFont(FONT_BIG);
        g.setColor(COL_WARN);
        center(g, "GAME  OVER", H / 2 - 50);
        g.setFont(FONT_MED);
        g.setColor(Color.WHITE);
        center(g, "SCORE  " + String.format("%07d", score), H / 2 + 10);
        g.setColor(COL_GOLD);
        center(g, "BEST   " + String.format("%07d", hiScore), H / 2 + 38);
        g.setFont(FONT_SM);
        g.setColor(new Color(180, 180, 180));
        center(g, "PRESS  R  TO  RESTART   |   ESC  TO  QUIT", H / 2 + 90);
    }

    void drawLevelClear(Graphics2D g) {
        g.setFont(FONT_MED);
        g.setColor(COL_GREEN);
        String msg = (wave == 0 && level > 1) ? "LEVEL " + (level - 1) + " CLEARED!" : "WAVE " + wave + " CLEARED!";
        center(g, msg, H / 2 - 10);
        g.setFont(FONT_SM);
        g.setColor(Color.WHITE);
        center(g, "GET READY...", H / 2 + 24);
    }

    void center(Graphics2D g, String s, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHIP PLACEHOLDERS  (replace these drawXxxShipAt methods with sprites)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * PLACEHOLDER – Player Ship
     * Replace this method with your actual sprite / Image drawing code.
     * The ship is drawn centred on (cx, cy) and scaled by `scale`.
     */
    static void drawPlayerShipAt(Graphics2D g, int cx, int cy, float scale) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.scale(scale, scale);

        // engine glow
        g2.setColor(new Color(0, 160, 255, 80));
        g2.fillOval(-10, 14, 20, 14);

        // main body
        int[] bx = {0, -14, -10, 0, 10, 14};
        int[] by = {-22, 8, 18, 14, 18, 8};
        g2.setColor(new Color(40, 220, 255));
        g2.fillPolygon(bx, by, 6);

        // cockpit
        g2.setColor(new Color(10, 30, 60));
        g2.fillOval(-6, -12, 12, 14);
        g2.setColor(new Color(120, 220, 255, 180));
        g2.fillOval(-4, -10, 8, 10);

        // wings
        int[] lx = {-10, -28, -14, -8};
        int[] ly = {6,   14,  18,  4};
        g2.setColor(new Color(0, 160, 220));
        g2.fillPolygon(lx, ly, 4);

        int[] rx = {10, 28, 14, 8};
        int[] ry = {6,  14, 18, 4};
        g2.fillPolygon(rx, ry, 4);

        // engine flame
        long t = System.currentTimeMillis();
        int flicker = (int)(Math.sin(t * 0.02) * 3);
        g2.setColor(new Color(0, 200, 255, 200));
        g2.fillOval(-5, 18, 10, 6 + flicker);
        g2.setColor(new Color(255, 255, 255, 160));
        g2.fillOval(-2, 20, 4, 3 + flicker);

        g2.dispose();
    }

    /**
     * PLACEHOLDER – Enemy Ship (3 types: 0=grunt, 1=cruiser, 2=commander)
     * Replace this method with your actual sprite / Image drawing code.
     * The ship is drawn centred on (cx, cy) and scaled by `scale`.
     */
    static void drawEnemyShipAt(Graphics2D g, int cx, int cy, int type, float scale) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.scale(scale, scale);

        switch (type) {
            case 0: { // ── GRUNT  (small, green saucer) ──────────────────
                g2.setColor(new Color(40, 200, 80));
                g2.fillOval(-14, -7, 28, 14);
                g2.setColor(new Color(20, 100, 40));
                g2.fillOval(-8, -12, 16, 12);
                g2.setColor(new Color(120, 255, 150, 160));
                g2.fillOval(-4, -9, 8, 7);
                break;
            }
            case 1: { // ── CRUISER  (medium, purple angular) ─────────────
                int[] px = {0, -18, -12, 0, 12, 18};
                int[] py = {-16, 2, 14, 10, 14, 2};
                g2.setColor(new Color(160, 60, 220));
                g2.fillPolygon(px, py, 6);
                g2.setColor(new Color(80, 20, 120));
                g2.fillOval(-6, -8, 12, 12);
                g2.setColor(new Color(220, 140, 255, 180));
                g2.fillOval(-3, -5, 6, 6);
                // cannons
                g2.setColor(new Color(200, 80, 255));
                g2.fillRect(-14, 10, 4, 8);
                g2.fillRect(10,  10, 4, 8);
                break;
            }
            case 2: { // ── COMMANDER  (large, red angular boss-style) ────
                int[] px = {0, -22, -18, -6, 6, 18, 22};
                int[] py = {-20, 4, 16, 12, 12, 16, 4};
                g2.setColor(new Color(220, 40, 40));
                g2.fillPolygon(px, py, 7);
                // bridge
                g2.setColor(new Color(120, 0, 0));
                g2.fillOval(-8, -12, 16, 16);
                g2.setColor(new Color(255, 120, 120, 200));
                g2.fillOval(-4, -8, 8, 8);
                // triple cannon
                g2.setColor(new Color(255, 80, 80));
                g2.fillRect(-2, 12, 4, 10);
                g2.fillRect(-12, 14, 4, 8);
                g2.fillRect( 8,  14, 4, 8);
                // wing accents
                g2.setColor(new Color(255, 160, 40));
                g2.fillOval(-20, 6, 6, 6);
                g2.fillOval(14,  6, 6, 6);
                break;
            }
        }
        g2.dispose();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INNER CLASSES
    // ══════════════════════════════════════════════════════════════════════

    class Player {
        int x, y, lives = 3;
        boolean invincible = false;
        boolean rapidFire, spread, boosted, shield;
        long rapidEnd, spreadEnd, boostEnd, shieldEnd;

        Player(int x, int y) { this.x = x; this.y = y; }

        void draw(Graphics2D g) {
            if (shield) {
                g.setColor(new Color(80, 180, 255, 60));
                g.fillOval(x - 28, y - 28, 56, 56);
                g.setColor(new Color(100, 200, 255, 120));
                g.drawOval(x - 28, y - 28, 56, 56);
            }
            drawPlayerShipAt(g, x, y, 1f);
        }
    }

    class Enemy {
        int x, y, type, hp, points;
        float dirX = 1;
        int baseX;

        Enemy(int x, int y, int type, int lvl) {
            this.x = x; this.y = y; this.type = type;
            this.baseX = x;
            switch (type) {
                case 0: hp = 1; points = 100; break;
                case 1: hp = 2; points = 200; break;
                case 2: hp = 3; points = 350; break;
            }
            hp += lvl - 1;
        }

        void update(long frame) {
            float spd = 0.8f + level * 0.2f + (1f - (float)enemies.size() / 60f) * 1.2f;
            x += dirX * spd;
        }

        void draw(Graphics2D g) {
            drawEnemyShipAt(g, x, y, type, 1f);
            // HP bar for multi-hp enemies
            if (hp > 1) {
                int maxHp = type + 1 + level - 1;
                int bw = 28;
                g.setColor(new Color(60, 60, 60, 180));
                g.fillRect(x - bw/2, y - 26, bw, 4);
                g.setColor(COL_WARN);
                g.fillRect(x - bw/2, y - 26, (int)(bw * hp / (float)maxHp), 4);
            }
        }
    }

    static class Bullet {
        float x, y, vx, vy;
        boolean fromPlayer;

        Bullet(float x, float y, float vx, float vy, boolean fromPlayer) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.fromPlayer = fromPlayer;
        }

        void update() { x += vx; y += vy; }

        void draw(Graphics2D g, boolean player) {
            if (player) {
                g.setColor(new Color(0, 255, 220));
                g.fillRect((int)x - 2, (int)y - 8, 4, 14);
                g.setColor(new Color(180, 255, 255, 140));
                g.fillRect((int)x - 1, (int)y - 8, 2, 14);
            } else {
                g.setColor(new Color(255, 80, 80));
                g.fillOval((int)x - 4, (int)y - 4, 8, 8);
                g.setColor(new Color(255, 180, 180, 140));
                g.fillOval((int)x - 2, (int)y - 2, 4, 4);
            }
        }
    }

    class Explosion {
        int x, y, maxR, frame = 0;
        static final int LIFE = 20;

        Explosion(int x, int y, int maxR) { this.x = x; this.y = y; this.maxR = maxR; }

        void update() { frame++; }
        boolean alive() { return frame < LIFE; }

        void draw(Graphics2D g) {
            float t = (float) frame / LIFE;
            int r = (int)(maxR * t);
            int alpha = (int)(255 * (1 - t));
            // outer ring
            g.setColor(new Color(255, 140, 0, alpha));
            g.drawOval(x - r, y - r, r*2, r*2);
            // inner flash
            int ir = (int)(maxR * 0.5f * (1 - t));
            g.setColor(new Color(255, 255, 180, Math.min(255, alpha * 2)));
            g.fillOval(x - ir, y - ir, ir*2, ir*2);
            // sparks
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + t * 2;
                int sx = (int)(x + Math.cos(ang) * r * 1.2);
                int sy = (int)(y + Math.sin(ang) * r * 1.2);
                g.setColor(new Color(255, 200, 50, alpha / 2));
                g.fillOval(sx - 2, sy - 2, 4, 4);
            }
        }
    }

    class Star {
        float x, y, spd, brightness;
        int size;

        Star() { reset(rng.nextInt(H)); }

        void reset(int startY) {
            x = rng.nextInt(W);
            y = startY;
            spd = 0.4f + rng.nextFloat() * 1.8f;
            brightness = 0.3f + rng.nextFloat() * 0.7f;
            size = rng.nextInt(2) + 1;
        }

        void update() { y += spd; if (y > H) reset(0); }

        void draw(Graphics2D g) {
            int c = (int)(brightness * 200);
            g.setColor(new Color(c, c, c + 30));
            g.fillRect((int)x, (int)y, size, size);
        }
    }

    static class PowerUp {
        int x; float y;
        int type; // 0=rapid 1=spread 2=shield 3=boost
        boolean alive = true;
        float vy = 1.8f;
        long birth = System.currentTimeMillis();

        PowerUp(int x, int y, int type) { this.x = x; this.y = y; this.type = type; }

        void update() { y += vy; }

        void draw(Graphics2D g) {
            double t = (System.currentTimeMillis() - birth) * 0.005;
            int iy = (int)y + (int)(Math.sin(t) * 3);
            Color c; String label;
            switch (type) {
                case 0: c = COL_GOLD;                   label = "R"; break;
                case 1: c = COL_ACCENT;                 label = "S"; break;
                case 2: c = new Color(100, 180, 255);   label = "⬡"; break;
                default:c = COL_GREEN;                  label = "B"; break;
            }
            // glow
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
            g.fillOval(x - 14, iy - 14, 28, 28);
            // gem
            g.setColor(c);
            int[] px = {x, x+10, x, x-10};
            int[] py = {iy-12, iy, iy+12, iy};
            g.fillPolygon(px, py, 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.drawString(label, x - 4, iy + 4);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  KEY INPUT
    // ══════════════════════════════════════════════════════════════════════
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (state == State.MENU && k == KeyEvent.VK_ENTER)     startGame();
        if (state == State.GAME_OVER && k == KeyEvent.VK_R)    startGame();
        if (state == State.GAME_OVER && k == KeyEvent.VK_ESCAPE) System.exit(0);
        if ((state == State.PLAYING || state == State.PAUSED) && k == KeyEvent.VK_P)
            state = (state == State.PAUSED) ? State.PLAYING : State.PAUSED;
        if (state != State.PLAYING) return;
        if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) left  = true;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
        if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) up    = true;
        if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) down  = true;
        if (k == KeyEvent.VK_SPACE)                        shooting = true;
    }

    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) left  = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
        if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) up    = false;
        if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) down  = false;
        if (k == KeyEvent.VK_SPACE)                        shooting = false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ══════════════════════════════════════════════════════════════════════
    //  MAIN
    // ══════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Space Shooter");
            SpaceShooter game = new SpaceShooter();
            frame.add(game);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}