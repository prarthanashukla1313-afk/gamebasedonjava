import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class EscapeToGoogle extends JPanel implements ActionListener, KeyListener, MouseMotionListener, MouseListener {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 650;
    private static final double FOV = 620.0;

    // 3-Floor House Map Constants
    // 0 = Floor, 1 = Wall, 2 = Locked DSA Door, 3 = Google Exit Gate, 4 = Stairs UP, 5 = Stairs DOWN
    private static final int FLOORS = 3;
    private static final int MAP_SIZE = 18;
    private int[][][] houseMap = new int[FLOORS][MAP_SIZE][MAP_SIZE];

    // Player "PRASHANT" State
    private final String PLAYER_NAME = "PRASHANT";
    private int pFloor = 0; // 0 = Ground, 1 = 1st Floor, 2 = 2nd Floor
    private double px = 8.5, py = 0.65, pz = 8.5;
    private double camYaw = 0.0, camPitch = -0.06, bodyYaw = 0.0;
    private boolean isTPP = true;
    private double walkCycle = 0.0;
    private int stairCooldown = 0;

    // Player Stuck State (From 2 LPA Traps - 2 Seconds = 120 frames)
    private int playerStuckTimer = 0;

    // Stun Gun State (Found behind a locked door on Ground Floor)
    private boolean hasStunGun = false;
    private boolean stunGunPicked = false;
    private final int stunGunFloor = 0;
    private final double stunGunX = 2.5, stunGunZ = 15.5; // Inside Bottom-Left Locked Room
    private int stunCooldown = 0;   // Reload timer
    private int shootLaserTimer = 0; // Visual laser beam timer

    // Enemy "2 LPA THREAT" State
    private int eFloor = 0;
    private double ex = 3.5, ez = 3.5;
    private double enemyAngle = 0.5;
    private int alertTimer = 0;
    private int enemyStunTimer = 0; // 3 Seconds Stun = 180 frames!
    private int enemyStairCooldown = 0;

    // 2 LPA Traps (Randomly placed by 2 LPA Threat)
    static class Trap3D {
        int floor;
        double x, z;
        Trap3D(int floor, double x, double z) { this.floor = floor; this.x = x; this.z = z; }
    }
    private List<Trap3D> traps = new ArrayList<>();

    // Touchpad Lock (ENTER key toggle)
    private boolean touchpadLocked = false;
    private Robot robot;
    private Cursor blankCursor;
    private int lastMouseX = -1, lastMouseY = -1;

    // Input Flags
    private boolean w, a, s, d, leftArrow, rightArrow, upArrow, downArrow, shift, freeLookAlt;
    private double stamina = 100.0;

    // Real-Time Quiz Overlay
    private boolean quizActive = false;
    private Question currentQuestion = null;
    private int doorGridX = -1, doorGridZ = -1;

    // Collectibles & Questions
    private List<Item3D> items = new ArrayList<>();
    private List<String> collected = new ArrayList<>();
    private List<Question> questionBank = new ArrayList<>();
    private Random random = new Random();
    private double spinAngle = 0;

    // Game Loop
    private javax.swing.Timer timer;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private String banner = "3-FLOOR HOUSE: Find Stun Gun & 4 Essentials! Watch out for 2 LPA & Traps!";
    private int bannerTimer = 450;

    static class Poly3D {
        int[] sx, sy;
        int n;
        Color color, border;
        double depth;
        String tag;
        int tagX, tagY;

        Poly3D(int[] sx, int[] sy, int n, Color color, Color border, double depth) {
            this.sx = sx; this.sy = sy; this.n = n;
            this.color = color; this.border = border; this.depth = depth;
        }
    }

    public EscapeToGoogle() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        try {
            robot = new Robot();
            blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
        } catch (Exception ignored) {}

        initHouseAndData();
        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    private void buildHouseMaps() {
        // Template layout for each floor of the 3-Story House
        int[][] baseFloor = {
            {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,1,2,1,1,1,0,0,0,0,0,0,1,1,1,2,1,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,1,1,0,1,1,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,1,1,0,1,1,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,1,2,1,1,1,0,0,0,0,0,0,1,1,1,2,1,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,0,0,0,0,2,0,0,0,0,0,0,2,0,0,0,0,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1},
            {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
        };

        for (int f = 0; f < FLOORS; f++) {
            for (int r = 0; r < MAP_SIZE; r++) {
                System.arraycopy(baseFloor[r], 0, houseMap[f][r], 0, MAP_SIZE);
            }
        }

        // Ground Floor (Floor 0): Google Exit Gate at East Wall + Stairs UP to Floor 1
        houseMap[0][8][17] = 3;
        houseMap[0][9][17] = 3;
        houseMap[0][2][8] = 4; // Stairs UP (0 -> 1)

        // 1st Floor (Floor 1): Stairs DOWN to Floor 0 + Stairs UP to Floor 2
        houseMap[1][2][8] = 5;  // Stairs DOWN (1 -> 0)
        houseMap[1][15][8] = 4; // Stairs UP (1 -> 2)

        // 2nd Floor (Floor 2 / Top Attic): Stairs DOWN to Floor 1
        houseMap[2][15][8] = 5; // Stairs DOWN (2 -> 1)
    }

    private void initHouseAndData() {
        buildHouseMaps();
        items.clear();
        collected.clear();
        traps.clear();

        hasStunGun = false;
        stunGunPicked = false;
        stunCooldown = 0;
        playerStuckTimer = 0;
        enemyStunTimer = 0;

        // Place 4 Essentials across all 3 Floors of the House!
        items.add(new Item3D("DSA Mastery", 0, 15.5, 2.5, Color.CYAN));       // Ground Floor Room
        items.add(new Item3D("Java Core", 1, 2.5, 2.5, Color.ORANGE));        // 1st Floor North-West Room
        items.add(new Item3D("Final Project", 1, 15.5, 15.5, Color.MAGENTA)); // 1st Floor South-East Room
        items.add(new Item3D("B.Tech Degree", 2, 2.5, 15.5, Color.YELLOW));   // 2nd Floor Top Room

        // Initial traps on different floors
        traps.add(new Trap3D(0, 6.5, 6.5));
        traps.add(new Trap3D(1, 8.5, 6.5));
        traps.add(new Trap3D(2, 6.5, 11.5));

        if (questionBank.isEmpty()) {
            questionBank.add(new Question("Worst-case time complexity of Binary Search?",
                    new String[]{"1: O(n)", "2: O(log n)", "3: O(n log n)", "4: O(1)"}, 1));
            questionBank.add(new Question("Which data structure strictly follows LIFO?",
                    new String[]{"1: Queue", "2: Heap", "3: Stack", "4: Graph"}, 2));
            questionBank.add(new Question("Average time complexity of HashMap get()?",
                    new String[]{"1: O(1)", "2: O(n)", "3: O(log n)", "4: O(n^2)"}, 0));
            questionBank.add(new Question("Which sorting algorithm guarantees O(n log n) worst-case?",
                    new String[]{"1: Quick Sort", "2: Bubble Sort", "3: Merge Sort", "4: Selection Sort"}, 2));
            questionBank.add(new Question("Java keyword that prevents class inheritance?",
                    new String[]{"1: static", "2: final", "3: super", "4: abstract"}, 1));
            questionBank.add(new Question("Which BST traversal prints elements in sorted order?",
                    new String[]{"1: Preorder", "2: Postorder", "3: Level-order", "4: Inorder"}, 3));
        }
    }

    private void toggleTouchpadLock() {
        touchpadLocked = !touchpadLocked;
        if (touchpadLocked) {
            if (blankCursor != null) setCursor(blankCursor);
            centerCursorOnScreen();
            showBanner("🔒 TOUCHPAD LOCKED! Slide for 360° Look | Press F/Space to Fire Stun Gun!");
        } else {
            setCursor(Cursor.getDefaultCursor());
            lastMouseX = -1;
            lastMouseY = -1;
            showBanner("🔓 TOUCHPAD UNLOCKED! Press ENTER to lock again.");
        }
    }

    private void centerCursorOnScreen() {
        if (robot != null && isShowing()) {
            Point p = getLocationOnScreen();
            robot.mouseMove(p.x + WIDTH / 2, p.y + HEIGHT / 2);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameOver && !gameWon) {
            spinAngle += 0.05;
            if (stairCooldown > 0) stairCooldown--;
            if (enemyStairCooldown > 0) enemyStairCooldown--;
            if (stunCooldown > 0) stunCooldown--;
            if (shootLaserTimer > 0) shootLaserTimer--;

            updatePlayerAndCamera();
            updateEnemy2LPA();
            checkGameEvents();
            if (bannerTimer > 0) bannerTimer--;
        }
        repaint();
    }

    private void fireStunGun() {
        if (!hasStunGun) {
            showBanner("❌ You don't have the Stun Gun yet! Unlock the Ground Floor South-West Room!");
            return;
        }
        if (stunCooldown > 0) {
            showBanner("⏳ Stun Gun recharging... (" + String.format("%.1f", stunCooldown / 60.0) + "s)");
            return;
        }

        stunCooldown = 300; // 5 sec cooldown
        shootLaserTimer = 15; // Laser effect

        if (pFloor == eFloor) {
            double dx = ex - px;
            double dz = ez - pz;
            double dist = Math.hypot(dx, dz);
            double angleToEnemy = Math.atan2(dx, dz);
            double diff = Math.abs( normalizeAngle(angleToEnemy - camYaw) );

            // Hit if within 9 tiles and roughly in front of crosshair
            if (dist < 9.5 && diff < 0.48) {
                enemyStunTimer = 180; // 3 SECONDS STUN (180 frames at 60fps)!
                Toolkit.getDefaultToolkit().beep();
                showBanner("⚡ HIT! 2 LPA THREAT STUNNED FOR 3 SECONDS! RUN!");
                return;
            }
        }
        showBanner("⚡ Stun Gun fired, but missed 2 LPA Threat!");
    }

    private double normalizeAngle(double a) {
        while (a < -Math.PI) a += 2 * Math.PI;
        while (a > Math.PI) a -= 2 * Math.PI;
        return a;
    }

    private void updatePlayerAndCamera() {
        // Camera Rotation Always Works
        double turnSpeed = 0.052;
        if (leftArrow) {
            camYaw -= turnSpeed;
            if (!freeLookAlt) bodyYaw -= turnSpeed;
        }
        if (rightArrow) {
            camYaw += turnSpeed;
            if (!freeLookAlt) bodyYaw += turnSpeed;
        }
        if (upArrow) camPitch = Math.min(0.55, camPitch + 0.03);
        if (downArrow) camPitch = Math.max(-0.55, camPitch - 0.03);

        // Check if Prashant is stuck in a 2 LPA Trap!
        if (playerStuckTimer > 0) {
            playerStuckTimer--;
            walkCycle = 0;
            return; // Cannot walk for 2 seconds!
        }

        double moveYaw = freeLookAlt ? bodyYaw : camYaw;
        double inForward = (w ? 1 : 0) - (s ? 1 : 0);
        double inRight = (d ? 1 : 0) - (a ? 1 : 0);
        double mag = Math.hypot(inForward, inRight);

        if (mag > 0) {
            inForward /= mag;
            inRight /= mag;

            double speed = 0.075;
            if (shift && stamina > 5) {
                speed = 0.13;
                stamina -= 0.65;
            } else {
                stamina = Math.min(100.0, stamina + 0.35);
            }

            double vx = (Math.sin(moveYaw) * inForward + Math.cos(moveYaw) * inRight) * speed;
            double vz = (Math.cos(moveYaw) * inForward - Math.sin(moveYaw) * inRight) * speed;

            double targetBody = Math.atan2(vx, vz);
            bodyYaw = lerpAngle(bodyYaw, targetBody, 0.3);
            walkCycle += (shift ? 0.38 : 0.24);

            if (canMoveTo(px + vx, pz)) px += vx;
            if (canMoveTo(px, pz + vz)) pz += vz;
        } else {
            stamina = Math.min(100.0, stamina + 0.45);
            walkCycle = 0;
            if (!freeLookAlt) {
                bodyYaw = lerpAngle(bodyYaw, camYaw, 0.25);
            }
        }
    }

    private double lerpAngle(double a, double b, double t) {
        double diff = normalizeAngle(b - a);
        return a + diff * t;
    }

    private boolean canMoveTo(double nx, double nz) {
        double r = 0.25;
        int[][] corners = {
            {(int)(nx - r), (int)(nz - r)},
            {(int)(nx + r), (int)(nz - r)},
            {(int)(nx - r), (int)(nz + r)},
            {(int)(nx + r), (int)(nz + r)}
        };
        for (int[] c : corners) {
            int gx = c[0], gz = c[1];
            if (gx < 0 || gx >= MAP_SIZE || gz < 0 || gz >= MAP_SIZE) return false;
            int tile = houseMap[pFloor][gz][gx];
            if (tile == 1) return false;
            if (tile == 2) {
                if (!quizActive) {
                    quizActive = true;
                    doorGridX = gx;
                    doorGridZ = gz;
                    currentQuestion = questionBank.get(random.nextInt(questionBank.size()));
                    showBanner("LOCKED DOOR! Press 1, 2, 3, or 4 to solve DSA MCQ!");
                }
                return false;
            }
            if (tile == 3) {
                if (collected.size() >= 4) gameWon = true;
                else showBanner("GOOGLE GATE LOCKED! Collect all 4 items across the 3 Floors (" + collected.size() + "/4)");
                return false;
            }
            if (tile == 4 && stairCooldown == 0 && pFloor < FLOORS - 1) {
                pFloor++;
                stairCooldown = 70;
                quizActive = false;
                showBanner("🔼 Climbed UP to Floor " + pFloor + "!");
                return true;
            }
            if (tile == 5 && stairCooldown == 0 && pFloor > 0) {
                pFloor--;
                stairCooldown = 70;
                quizActive = false;
                showBanner("🔽 Went DOWN to Floor " + pFloor + "!");
                return true;
            }
        }
        return true;
    }

    private void answerQuiz(int idx) {
        if (!quizActive || currentQuestion == null) return;
        if (idx == currentQuestion.correctIndex) {
            houseMap[pFloor][doorGridZ][doorGridX] = 0;
            quizActive = false;
            currentQuestion = null;
            showBanner("✅ DOOR UNLOCKED!");
        } else {
            Toolkit.getDefaultToolkit().beep();
            alertTimer = 360;
            eFloor = pFloor; // 2 LPA Threat hears alarm and comes to your floor!
            currentQuestion = questionBank.get(random.nextInt(questionBank.size()));
            showBanner("🚨 WRONG! Alarm triggered — 2 LPA Threat is hunting you on Floor " + pFloor + "!");
        }
    }

    private void updateEnemy2LPA() {
        // If hit by Stun Gun, freeze in place for 3 seconds!
        if (enemyStunTimer > 0) {
            enemyStunTimer--;
            return;
        }

        // Randomly place a "2 LPA Bond Trap" on the floor (max 8 active traps)
        if (random.nextInt(260) == 0 && traps.size() < 8) {
            int gx = (int) ex, gz = (int) ez;
            if (houseMap[eFloor][gz][gx] == 0 && Math.hypot(px - ex, pz - ez) > 2.0) {
                traps.add(new Trap3D(eFloor, ex, ez));
            }
        }

        // Occasionally use stairs to follow player's floor
        if (eFloor != pFloor && enemyStairCooldown == 0 && random.nextInt(140) == 0) {
            eFloor = pFloor;
            ex = 8.5;
            ez = (eFloor == 1) ? 3.5 : 13.5;
            enemyStairCooldown = 180;
            showBanner("👣 Footsteps on the stairs... 2 LPA Threat entered Floor " + eFloor + "!");
        }

        boolean sameFloor = (eFloor == pFloor);
        double dist = sameFloor ? Math.hypot(px - ex, pz - ez) : 999;
        boolean chasing = sameFloor && (dist < (shift ? 11.0 : 8.0) || alertTimer > 0);
        if (alertTimer > 0) alertTimer--;

        double speed = (alertTimer > 0) ? 0.066 : (chasing ? 0.052 : 0.036);

        if (chasing) {
            double ang = Math.atan2(px - ex, pz - ez);
            double nx = ex + Math.sin(ang) * speed;
            double nz = ez + Math.cos(ang) * speed;
            if (enemyCanMove(nx, ez)) ex = nx;
            if (enemyCanMove(ex, nz)) ez = nz;
        } else {
            double nx = ex + Math.sin(enemyAngle) * speed;
            double nz = ez + Math.cos(enemyAngle) * speed;
            if (!enemyCanMove(nx, nz) || random.nextInt(100) == 0) {
                enemyAngle = random.nextDouble() * Math.PI * 2;
            } else {
                ex = nx; ez = nz;
            }
        }
    }

    private boolean enemyCanMove(double nx, double nz) {
        double r = 0.25;
        int[][] corners = {
            {(int)(nx - r), (int)(nz - r)}, {(int)(nx + r), (int)(nz - r)},
            {(int)(nx - r), (int)(nz + r)}, {(int)(nx + r), (int)(nz + r)}
        };
        for (int[] c : corners) {
            int gx = c[0], gz = c[1];
            if (gx < 0 || gx >= MAP_SIZE || gz < 0 || gz >= MAP_SIZE) return false;
            int t = houseMap[eFloor][gz][gx];
            if (t == 1 || t == 2 || t == 3) return false;
        }
        return true;
    }

    private void checkGameEvents() {
        if (quizActive && Math.hypot(px - (doorGridX + 0.5), pz - (doorGridZ + 0.5)) > 2.3) {
            quizActive = false;
            currentQuestion = null;
        }

        // Check Stun Gun Pickup (Inside Ground Floor Room)
        if (!stunGunPicked && pFloor == stunGunFloor) {
            if (Math.hypot(px - stunGunX, pz - stunGunZ) < 0.85) {
                stunGunPicked = true;
                hasStunGun = true;
                showBanner("⚡ STUN GUN ACQUIRED! Press F, Space, or Left-Click to stun 2 LPA Threat for 3s!");
            }
        }

        // Check Essential Collectibles Pickup
        Iterator<Item3D> it = items.iterator();
        while (it.hasNext()) {
            Item3D item = it.next();
            if (item.floor == pFloor && Math.hypot(px - item.x, pz - item.z) < 0.8) {
                collected.add(item.name);
                showBanner("⭐ " + PLAYER_NAME + " collected " + item.name + "! (" + collected.size() + "/4)");
                it.remove();
            }
        }

        // Check if Prashant stepped on a 2 LPA Trap!
        Iterator<Trap3D> trapIt = traps.iterator();
        while (trapIt.hasNext()) {
            Trap3D tr = trapIt.next();
            if (tr.floor == pFloor && Math.hypot(px - tr.x, pz - tr.z) < 0.55) {
                playerStuckTimer = 120; // STUCK FOR 2 SECONDS (120 frames)!
                alertTimer = 240;       // Alerts 2 LPA Threat!
                Toolkit.getDefaultToolkit().beep();
                showBanner("🪤 TRAPPED BY 2 LPA BOND TRAP! Stuck for 2 Seconds!");
                trapIt.remove();
                break;
            }
        }

        // Check if 2 LPA Threat caught Prashant (Only if enemy is NOT stunned!)
        if (enemyStunTimer == 0 && pFloor == eFloor && Math.hypot(px - ex, pz - ez) < 0.62) {
            gameOver = true;
            if (touchpadLocked) toggleTouchpadLock();
        }
        if (gameWon && touchpadLocked) {
            toggleTouchpadLock();
        }
    }

    private void showBanner(String msg) {
        banner = msg;
        bannerTimer = 280;
    }

    // ==================== 3D PROJECTION ENGINE ====================

    private double[] toCameraSpace(double wx, double wy, double wz, double camX, double camY, double camZ) {
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;

        double sinY = Math.sin(camYaw);
        double cosY = Math.cos(camYaw);

        double rx = dx * cosY - dz * sinY;
        double rz = dx * sinY + dz * cosY;

        double cosP = Math.cos(camPitch), sinP = Math.sin(camPitch);
        double ry = dy * cosP - rz * sinP;
        double finalZ = dy * sinP + rz * cosP;

        return new double[]{rx, ry, finalZ};
    }

    private Point project(double cx, double cy, double cz) {
        if (cz <= 0.08) cz = 0.08;
        int sx = (int) (WIDTH / 2.0 + (cx * FOV) / cz);
        int sy = (int) (HEIGHT / 2.0 - (cy * FOV) / cz);
        return new Point(sx, sy);
    }

    private void addQuad3D(List<Poly3D> list, double[][] pts3D, Color baseColor, Color border,
                           double camX, double camY, double camZ, String label) {
        double[][] camPts = new double[4][3];
        int behindCount = 0;
        double avgZ = 0;

        for (int i = 0; i < 4; i++) {
            camPts[i] = toCameraSpace(pts3D[i][0], pts3D[i][1], pts3D[i][2], camX, camY, camZ);
            if (camPts[i][2] <= 0.12) behindCount++;
            avgZ += camPts[i][2];
        }
        if (behindCount == 4) return;
        avgZ /= 4.0;
        if (avgZ > 14.0) return;

        int[] sx = new int[4];
        int[] sy = new int[4];
        for (int i = 0; i < 4; i++) {
            double cz = Math.max(0.12, camPts[i][2]);
            Point p = project(camPts[i][0], camPts[i][1], cz);
            sx[i] = p.x;
            sy[i] = p.y;
        }

        float shade = (float) Math.max(0.10, Math.min(1.0, 1.0 - (avgZ / 13.5)));
        Color shaded = new Color(
                (int) (baseColor.getRed() * shade),
                (int) (baseColor.getGreen() * shade),
                (int) (baseColor.getBlue() * shade)
        );
        Color shadedBorder = (border == null) ? null : new Color(
                (int) (border.getRed() * shade),
                (int) (border.getGreen() * shade),
                (int) (border.getBlue() * shade)
        );

        Poly3D poly = new Poly3D(sx, sy, 4, shaded, shadedBorder, avgZ);
        if (label != null) {
            poly.tag = label;
            poly.tagX = (sx[0] + sx[1]) / 2;
            poly.tagY = Math.min(sy[0], sy[1]) - 8;
        }
        list.add(poly);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int horizonY = (int) (HEIGHT / 2.0 + Math.tan(camPitch) * FOV);
        g2.setColor(new Color(8, 10, 16));
        g2.fillRect(0, 0, WIDTH, Math.max(0, Math.min(HEIGHT, horizonY)));
        g2.setColor(new Color(14, 16, 22));
        g2.fillRect(0, Math.max(0, Math.min(HEIGHT, horizonY)), WIDTH, HEIGHT);

        double camX = px, camY = py, camZ = pz;
        if (isTPP) {
            double targetDist = 1.85;
            double safeDist = 0.0;
            for (double d = 0.1; d <= targetDist; d += 0.1) {
                double tx = px - Math.sin(camYaw) * d;
                double tz = pz - Math.cos(camYaw) * d;
                int t = houseMap[pFloor][(int) tz][(int) tx];
                if (t == 0 || t == 4 || t == 5) safeDist = d;
                else break;
            }
            camX = px - Math.sin(camYaw) * safeDist;
            camZ = pz - Math.cos(camYaw) * safeDist;
            camY = 0.98;
        }

        List<Poly3D> renderQueue = new ArrayList<>();
        int viewRange = 12;
        int minGX = Math.max(0, (int) camX - viewRange);
        int maxGX = Math.min(MAP_SIZE - 1, (int) camX + viewRange);
        int minGZ = Math.max(0, (int) camZ - viewRange);
        int maxGZ = Math.min(MAP_SIZE - 1, (int) camZ + viewRange);

        double wallH = 1.35;

        // Floor color theme varies slightly per floor of the house!
        Color floorEven = (pFloor == 0) ? new Color(36, 40, 48) : (pFloor == 1) ? new Color(46, 36, 34) : new Color(32, 44, 38);
        Color floorOdd  = (pFloor == 0) ? new Color(28, 32, 39) : (pFloor == 1) ? new Color(38, 28, 26) : new Color(25, 36, 30);

        for (int gz = minGZ; gz <= maxGZ; gz++) {
            for (int gx = minGX; gx <= maxGX; gx++) {
                int tile = houseMap[pFloor][gz][gx];

                if (tile == 0 || tile == 4 || tile == 5) {
                    double[][] floorQuad = {
                        {gx, 0, gz}, {gx + 1, 0, gz},
                        {gx + 1, 0, gz + 1}, {gx, 0, gz + 1}
                    };
                    Color tileCol = ((gx + gz) % 2 == 0) ? floorEven : floorOdd;
                    String stairTag = null;
                    if (tile == 4) {
                        tileCol = new Color(40, 140, 220);
                        stairTag = "🔼 STAIRS UP (Floor " + (pFloor + 1) + ")";
                    } else if (tile == 5) {
                        tileCol = new Color(180, 90, 220);
                        stairTag = "🔽 STAIRS DOWN (Floor " + (pFloor - 1) + ")";
                    }
                    addQuad3D(renderQueue, floorQuad, tileCol, new Color(55, 62, 74), camX, camY, camZ, stairTag);

                    // Draw 3D Step Blocks on Stairs
                    if (tile == 4 || tile == 5) {
                        buildRotatedBox(renderQueue, gx + 0.5, 0.12, gz + 0.5, 0.35, 0.12, 0.35, 0, tileCol.brighter(), camX, camY, camZ, null);
                    }
                } else {
                    Color wallCol = (pFloor == 0) ? new Color(85, 95, 115) : (pFloor == 1) ? new Color(110, 85, 75) : new Color(75, 105, 90);
                    Color edgeCol = new Color(40, 45, 55);
                    if (tile == 2) {
                        wallCol = new Color(215, 130, 30);
                        edgeCol = Color.YELLOW;
                    } else if (tile == 3) {
                        wallCol = (collected.size() >= 4) ? new Color(45, 180, 85) : new Color(90, 90, 100);
                        edgeCol = Color.GREEN;
                    }

                    if (gz > 0 && isWalkableTile(houseMap[pFloor][gz - 1][gx]) && camZ < gz) {
                        addQuad3D(renderQueue, new double[][]{{gx, wallH, gz}, {gx + 1, wallH, gz}, {gx + 1, 0, gz}, {gx, 0, gz}},
                                wallCol, edgeCol, camX, camY, camZ, null);
                    }
                    if (gz < MAP_SIZE - 1 && isWalkableTile(houseMap[pFloor][gz + 1][gx]) && camZ > gz + 1) {
                        addQuad3D(renderQueue, new double[][]{{gx + 1, wallH, gz + 1}, {gx, wallH, gz + 1}, {gx, 0, gz + 1}, {gx + 1, 0, gz + 1}},
                                wallCol, edgeCol, camX, camY, camZ, null);
                    }
                    if (gx > 0 && isWalkableTile(houseMap[pFloor][gz][gx - 1]) && camX < gx) {
                        addQuad3D(renderQueue, new double[][]{{gx, wallH, gz + 1}, {gx, wallH, gz}, {gx, 0, gz}, {gx, 0, gz + 1}},
                                wallCol.darker(), edgeCol, camX, camY, camZ, null);
                    }
                    if (gx < MAP_SIZE - 1 && isWalkableTile(houseMap[pFloor][gz][gx + 1]) && camX > gx + 1) {
                        addQuad3D(renderQueue, new double[][]{{gx + 1, wallH, gz}, {gx + 1, wallH, gz + 1}, {gx + 1, 0, gz + 1}, {gx + 1, 0, gz}},
                                wallCol.darker(), edgeCol, camX, camY, camZ, null);
                    }
                }
            }
        }

        // Render Stun Gun pickup if on Ground Floor & not picked yet
        if (!stunGunPicked && pFloor == stunGunFloor) {
            build3DCube(renderQueue, stunGunX, 0.42 + Math.sin(spinAngle * 3) * 0.06, stunGunZ, 0.20, -spinAngle * 2,
                    new Color(0, 230, 255), camX, camY, camZ, "⚡ STUN GUN (Pick Up)");
        }

        // Render 2 LPA Traps on current floor
        for (Trap3D tr : traps) {
            if (tr.floor == pFloor) {
                buildRotatedBox(renderQueue, tr.x, 0.04, tr.z, 0.24, 0.03, 0.24, spinAngle,
                        new Color(220, 20, 60), camX, camY, camZ, "🪤 2 LPA TRAP");
            }
        }

        // Render Collectibles on current floor
        for (Item3D item : items) {
            if (item.floor == pFloor) {
                build3DCube(renderQueue, item.x, 0.45 + Math.sin(spinAngle * 2) * 0.08, item.z, 0.22, spinAngle,
                        item.color, camX, camY, camZ, item.name);
            }
        }

        // Render PRASHANT in TPP
        if (isTPP) {
            Color pBodyCol = (playerStuckTimer > 0) ? Color.ORANGE : new Color(50, 120, 230);
            String pTag = (playerStuckTimer > 0) ? "PRASHANT [STUCK!]" : PLAYER_NAME;
            build3DCharacter(renderQueue, px, pz, bodyYaw, walkCycle,
                    pBodyCol, new Color(235, 190, 150), pTag, camX, camY, camZ, hasStunGun);
        }

        // Render 2 LPA THREAT if on the same floor
        if (eFloor == pFloor) {
            double monsterYaw = Math.atan2(px - ex, pz - ez);
            boolean stunned = enemyStunTimer > 0;
            Color mCol = stunned ? new Color(0, 200, 255) : new Color(185, 20, 20);
            String mTag = stunned
                    ? "⚡ 2 LPA STUNNED (" + String.format("%.1f", enemyStunTimer / 60.0) + "s) ⚡"
                    : "⚠ 2 LPA THREAT ⚠";
            build3DCharacter(renderQueue, ex, ez, monsterYaw, stunned ? 0 : spinAngle * 3,
                    mCol, Color.BLACK, mTag, camX, camY, camZ, false);
        }

        renderQueue.sort((p1, p2) -> Double.compare(p2.depth, p1.depth));

        for (Poly3D p : renderQueue) {
            g2.setColor(p.color);
            g2.fillPolygon(p.sx, p.sy, p.n);
            if (p.border != null) {
                g2.setColor(p.border);
                g2.drawPolygon(p.sx, p.sy, p.n);
            }
            if (p.tag != null) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.drawString(p.tag, p.tagX - 38, p.tagY);
            }
        }

        // Stun Gun Laser Beam Visual Effect
        if (shootLaserTimer > 0) {
            g2.setColor(new Color(0, 255, 255, 200));
            g2.setStroke(new BasicStroke(4));
            g2.drawLine(WIDTH / 2 + 40, HEIGHT - 80, WIDTH / 2, HEIGHT / 2);
            g2.drawOval(WIDTH / 2 - 14, HEIGHT / 2 - 14, 28, 28);
        }

        // Crosshair
        g2.setColor(hasStunGun && stunCooldown == 0 ? Color.CYAN : new Color(255, 255, 255, 150));
        g2.drawLine(WIDTH / 2 - 8, HEIGHT / 2, WIDTH / 2 + 8, HEIGHT / 2);
        g2.drawLine(WIDTH / 2, HEIGHT / 2 - 8, WIDTH / 2, HEIGHT / 2 + 8);

        drawHUD(g2);
        drawMiniMap(g2);
        if (quizActive && currentQuestion != null) drawLiveQuizOverlay(g2);

        // Stuck Warning Overlay
        if (playerStuckTimer > 0) {
            g2.setColor(new Color(220, 120, 0, 90));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("SansSerif", Font.BOLD, 24));
            g2.drawString("🪤 STUCK IN 2 LPA TRAP! (" + String.format("%.1f", playerStuckTimer / 60.0) + "s)", WIDTH / 2 - 190, HEIGHT / 2 - 60);
        }

        if (gameOver) {
            drawEndScreen(g2, new Color(140, 0, 0, 235),
                    "GAME OVER: " + PLAYER_NAME + " CAUGHT BY 2 LPA THREAT!",
                    "Signed a 2.0 LPA Bond with ₹15,000/month salary! Press R to Retry.");
        } else if (gameWon) {
            drawEndScreen(g2, new Color(15, 115, 55, 235),
                    "🎉 GOOGLE OFFER LETTER: " + PLAYER_NAME + "! 🎉",
                    "Escaped the 3-Floor House & defeated the 2 LPA Threat! Press R to Play Again.");
        }
    }

    private boolean isWalkableTile(int t) {
        return t == 0 || t == 4 || t == 5;
    }

    private void build3DCube(List<Poly3D> q, double cx, double cy, double cz, double r, double rot,
                             Color col, double camX, double camY, double camZ, String label) {
        double c = Math.cos(rot), s = Math.sin(rot);
        double[][] local = {
            {-r, r, -r}, {r, r, -r}, {r, r, r}, {-r, r, r},
            {-r, -r, -r}, {r, -r, -r}, {r, -r, r}, {-r, -r, r}
        };
        double[][] world = new double[8][3];
        for (int i = 0; i < 8; i++) {
            world[i][0] = cx + local[i][0] * c - local[i][2] * s;
            world[i][1] = cy + local[i][1];
            world[i][2] = cz + local[i][0] * s + local[i][2] * c;
        }
        int[][] faces = {{0,1,5,4}, {1,2,6,5}, {2,3,7,6}, {3,0,4,7}, {0,3,2,1}};
        for (int f = 0; f < faces.length; f++) {
            double[][] quad = {world[faces[f][0]], world[faces[f][1]], world[faces[f][2]], world[faces[f][3]]};
            addQuad3D(q, quad, (f % 2 == 0) ? col : col.darker(), Color.WHITE, camX, camY, camZ, (f == 0) ? label : null);
        }
    }

    private void build3DCharacter(List<Poly3D> q, double x, double z, double facing, double anim,
                                  Color bodyCol, Color headCol, String nameTag, double camX, double camY, double camZ, boolean holdingGun) {
        double swing = Math.sin(anim) * 0.14;
        double sinF = Math.sin(facing), cosF = Math.cos(facing);

        double lx = x - cosF * 0.08 + sinF * swing;
        double lz = z + sinF * 0.08 + cosF * swing;
        double rx = x + cosF * 0.08 - sinF * swing;
        double rz = z - sinF * 0.08 - cosF * swing;

        buildRotatedBox(q, lx, 0.18, lz, 0.055, 0.18, 0.055, facing, Color.DARK_GRAY, camX, camY, camZ, null);
        buildRotatedBox(q, rx, 0.18, rz, 0.055, 0.18, 0.055, facing, Color.DARK_GRAY, camX, camY, camZ, null);
        buildRotatedBox(q, x, 0.52, z, 0.15, 0.18, 0.09, facing, bodyCol, camX, camY, camZ, null);

        if (holdingGun) {
            double gx = x + cosF * 0.16 + sinF * 0.18;
            double gz = z - sinF * 0.16 + cosF * 0.18;
            buildRotatedBox(q, gx, 0.56, gz, 0.04, 0.04, 0.14, facing, Color.CYAN, camX, camY, camZ, null);
        }

        buildRotatedBox(q, x, 0.80, z, 0.10, 0.10, 0.10, facing, headCol, camX, camY, camZ, nameTag);
    }

    private void buildRotatedBox(List<Poly3D> q, double cx, double cy, double cz, double rx, double ry, double rz,
                                 double rotY, Color col, double camX, double camY, double camZ, String label) {
        double c = Math.cos(rotY), s = Math.sin(rotY);
        double[][] local = {
            {-rx, ry, -rz}, {rx, ry, -rz}, {rx, ry, rz}, {-rx, ry, rz},
            {-rx, -ry, -rz}, {rx, -ry, -rz}, {rx, -ry, rz}, {-rx, -ry, rz}
        };
        double[][] v = new double[8][3];
        for (int i = 0; i < 8; i++) {
            v[i][0] = cx + local[i][0] * c + local[i][2] * s;
            v[i][1] = cy + local[i][1];
            v[i][2] = cz - local[i][0] * s + local[i][2] * c;
        }
        int[][] faces = {{0,1,5,4}, {1,2,6,5}, {2,3,7,6}, {3,0,4,7}, {0,3,2,1}};
        for (int f = 0; f < faces.length; f++) {
            double[][] quad = {v[faces[f][0]], v[faces[f][1]], v[faces[f][2]], v[faces[f][3]]};
            addQuad3D(q, quad, (f % 2 == 0) ? col : col.darker(), Color.BLACK, camX, camY, camZ, (f == 4) ? label : null);
        }
    }

    private void drawHUD(Graphics2D g2) {
        g2.setColor(new Color(10, 12, 18, 215));
        g2.fillRoundRect(12, 12, 680, 95, 12, 12);
        g2.setColor(touchpadLocked ? Color.GREEN : new Color(70, 85, 110));
        g2.drawRoundRect(12, 12, 680, 95, 12, 12);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        String floorName = (pFloor == 0) ? "GROUND FLOOR (0)" : (pFloor == 1) ? "1st FLOOR (1)" : "2nd FLOOR (2)";
        g2.drawString("PLAYER: " + PLAYER_NAME + " | HOUSE: " + floorName + " | 2 LPA ON FLOOR: " + eFloor, 24, 32);

        g2.setColor(Color.WHITE);
        g2.drawString("ESSENTIALS (" + collected.size() + "/4): " + collected, 24, 54);

        String gunStatus = !hasStunGun
                ? "LOCKED (Find in Ground Floor SW Room)"
                : (stunCooldown == 0 ? "⚡ READY [Press F / Space / Click]" : "Recharging (" + (stunCooldown / 60 + 1) + "s)");
        g2.setColor(hasStunGun ? Color.YELLOW : Color.ORANGE);
        g2.drawString("STUN GUN: " + gunStatus, 24, 76);

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString("ENTER: Lock Touchpad | WASD: Move | F/Space: Shoot Stun Gun | V: FPP/TPP | Shift: Sprint", 24, 97);

        if (bannerTimer > 0) {
            g2.setColor(new Color(0, 0, 0, 210));
            g2.fillRect(0, HEIGHT - 34, WIDTH, 34);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString(banner, 20, HEIGHT - 12);
        }
    }

    private void drawMiniMap(Graphics2D g2) {
        int scale = 7;
        int ox = WIDTH - (MAP_SIZE * scale) - 18;
        int oy = 18;

        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(ox - 4, oy - 4, MAP_SIZE * scale + 8, MAP_SIZE * scale + 24);

        for (int r = 0; r < MAP_SIZE; r++) {
            for (int c = 0; c < MAP_SIZE; c++) {
                int t = houseMap[pFloor][r][c];
                if (t == 1) g2.setColor(new Color(80, 90, 105));
                else if (t == 2) g2.setColor(Color.ORANGE);
                else if (t == 3) g2.setColor(Color.GREEN);
                else if (t == 4) g2.setColor(new Color(40, 150, 255));
                else if (t == 5) g2.setColor(new Color(190, 90, 255));
                else g2.setColor(new Color(20, 22, 26));
                g2.fillRect(ox + c * scale, oy + r * scale, scale, scale);
            }
        }

        // Stun gun icon on minimap
        if (!stunGunPicked && pFloor == stunGunFloor) {
            g2.setColor(Color.CYAN);
            g2.fillRect(ox + (int)(stunGunX * scale) - 3, oy + (int)(stunGunZ * scale) - 3, 6, 6);
        }

        // Traps on current floor
        for (Trap3D tr : traps) {
            if (tr.floor == pFloor) {
                g2.setColor(Color.PINK);
                g2.fillOval(ox + (int)(tr.x * scale) - 2, oy + (int)(tr.z * scale) - 2, 4, 4);
            }
        }

        // Items on current floor
        for (Item3D item : items) {
            if (item.floor == pFloor) {
                g2.setColor(item.color);
                g2.fillOval(ox + (int)(item.x * scale) - 3, oy + (int)(item.z * scale) - 3, 6, 6);
            }
        }

        // 2 LPA Threat on current floor
        if (eFloor == pFloor) {
            g2.setColor(enemyStunTimer > 0 ? Color.CYAN : Color.RED);
            g2.fillOval(ox + (int)(ex * scale) - 3, oy + (int)(ez * scale) - 3, 7, 7);
        }

        // Player on Minimap
        int mpx = ox + (int)(px * scale);
        int mpz = oy + (int)(pz * scale);
        g2.setColor(Color.WHITE);
        g2.fillOval(mpx - 3, mpz - 3, 6, 6);
        g2.drawLine(mpx, mpz, mpx + (int)(Math.sin(camYaw) * 10), mpz + (int)(Math.cos(camYaw) * 10));

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("FLOOR " + pFloor + " MAP (Blue/Purple = Stairs)", ox, oy + MAP_SIZE * scale + 14);
    }

    private void drawLiveQuizOverlay(Graphics2D g2) {
        int boxW = 660, boxH = 165;
        int bx = (WIDTH - boxW) / 2;
        int by = HEIGHT - boxH - 45;

        g2.setColor(new Color(18, 20, 28, 242));
        g2.fillRoundRect(bx, by, boxW, boxH, 16, 16);
        g2.setColor(Color.ORANGE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(bx, by, boxW, boxH, 16, 16);

        g2.setColor(Color.RED);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("⚠ REAL-TIME LOCK: SOLVE QUICKLY — 2 LPA THREAT IS MOVING! (Press 1, 2, 3, or 4)", bx + 20, by + 24);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString(currentQuestion.prompt, bx + 20, by + 54);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString(currentQuestion.options[0] + "      " + currentQuestion.options[1], bx + 25, by + 95);
        g2.drawString(currentQuestion.options[2] + "      " + currentQuestion.options[3], bx + 25, by + 130);
    }

    private void drawEndScreen(Graphics2D g2, Color bg, String title, String subtitle) {
        g2.setColor(bg);
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 28));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (WIDTH - fm.stringWidth(title)) / 2, HEIGHT / 2 - 20);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        fm = g2.getFontMetrics();
        g2.drawString(subtitle, (WIDTH - fm.stringWidth(subtitle)) / 2, HEIGHT / 2 + 25);
    }

    private void resetGame() {
        pFloor = 0; px = 8.5; pz = 8.5; camYaw = 0; camPitch = -0.06; bodyYaw = 0;
        eFloor = 0; ex = 3.5; ez = 3.5; alertTimer = 0;
        quizActive = false; gameOver = false; gameWon = false;

        initHouseAndData();
        showBanner("NEW RUN: Explore the 3-Floor House, find Stun Gun & 4 Essentials!");
    }

    private void handleTouchpadLook(MouseEvent e) {
        if (touchpadLocked && robot != null && isShowing()) {
            Point origin = getLocationOnScreen();
            int centerScreenX = origin.x + WIDTH / 2;
            int centerScreenY = origin.y + HEIGHT / 2;

            int dx = e.getXOnScreen() - centerScreenX;
            int dy = e.getYOnScreen() - centerScreenY;

            if (dx != 0 || dy != 0) {
                camYaw += dx * 0.004;
                camPitch = Math.max(-0.55, Math.min(0.55, camPitch - dy * 0.0035));
                if (!freeLookAlt) bodyYaw = camYaw;
                centerCursorOnScreen();
            }
        } else {
            int x = e.getX(), y = e.getY();
            if (lastMouseX != -1 && lastMouseY != -1) {
                int dx = x - lastMouseX, dy = y - lastMouseY;
                if (Math.abs(dx) < 120 && Math.abs(dy) < 120) {
                    camYaw += dx * 0.0055;
                    camPitch = Math.max(-0.55, Math.min(0.55, camPitch - dy * 0.0045));
                    if (!freeLookAlt) bodyYaw = camYaw;
                }
            }
            lastMouseX = x; lastMouseY = y;
        }
    }

    @Override public void mouseMoved(MouseEvent e) { handleTouchpadLook(e); }
    @Override public void mouseDragged(MouseEvent e) { handleTouchpadLook(e); }
    @Override public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (SwingUtilities.isLeftMouseButton(e) && hasStunGun) {
            fireStunGun();
        }
        lastMouseX = e.getX(); lastMouseY = e.getY();
    }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); }
    @Override public void mouseEntered(MouseEvent e) { lastMouseX = e.getX(); lastMouseY = e.getY(); }
    @Override public void mouseExited(MouseEvent e) { if (!touchpadLocked) { lastMouseX = -1; lastMouseY = -1; } }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER) { toggleTouchpadLock(); return; }
        if (code == KeyEvent.VK_ESCAPE && touchpadLocked) { toggleTouchpadLock(); return; }

        if (code == KeyEvent.VK_W) w = true;
        if (code == KeyEvent.VK_S) s = true;
        if (code == KeyEvent.VK_A) a = true;
        if (code == KeyEvent.VK_D) d = true;
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_Q) leftArrow = true;
        if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_E) rightArrow = true;
        if (code == KeyEvent.VK_UP) upArrow = true;
        if (code == KeyEvent.VK_DOWN) downArrow = true;
        if (code == KeyEvent.VK_SHIFT) shift = true;
        if (code == KeyEvent.VK_ALT) { freeLookAlt = true; e.consume(); }

        // Shoot Stun Gun with F or SPACE
        if (code == KeyEvent.VK_F || code == KeyEvent.VK_SPACE) {
            fireStunGun();
        }

        if (code == KeyEvent.VK_V) {
            isTPP = !isTPP;
            showBanner("3D View: " + (isTPP ? "TPP (Follow Camera)" : "FPP (First-Person)"));
        }
        if (quizActive) {
            if (code == KeyEvent.VK_1 || code == KeyEvent.VK_NUMPAD1) answerQuiz(0);
            if (code == KeyEvent.VK_2 || code == KeyEvent.VK_NUMPAD2) answerQuiz(1);
            if (code == KeyEvent.VK_3 || code == KeyEvent.VK_NUMPAD3) answerQuiz(2);
            if (code == KeyEvent.VK_4 || code == KeyEvent.VK_NUMPAD4) answerQuiz(3);
        }
        if (code == KeyEvent.VK_R && (gameOver || gameWon)) resetGame();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W) w = false;
        if (code == KeyEvent.VK_S) s = false;
        if (code == KeyEvent.VK_A) a = false;
        if (code == KeyEvent.VK_D) d = false;
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_Q) leftArrow = false;
        if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_E) rightArrow = false;
        if (code == KeyEvent.VK_UP) upArrow = false;
        if (code == KeyEvent.VK_DOWN) downArrow = false;
        if (code == KeyEvent.VK_SHIFT) shift = false;
        if (code == KeyEvent.VK_ALT) freeLookAlt = false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    static class Item3D {
        String name; int floor; double x, z; Color color;
        Item3D(String name, int floor, double x, double z, Color color) {
            this.name = name; this.floor = floor; this.x = x; this.z = z; this.color = color;
        }
    }

    static class Question {
        String prompt; String[] options; int correctIndex;
        Question(String prompt, String[] options, int correctIndex) {
            this.prompt = prompt; this.options = options; this.correctIndex = correctIndex;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Escape 3-Floor House: PRASHANT vs 2 LPA THREAT");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new EscapeToGoogle());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}