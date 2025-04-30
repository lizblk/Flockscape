import greenfoot.*;  // (World, Actor, GreenfootImage, and Greenfoot)
import java.util.List;

/**
 * A boid represents one member of a flock. It follows several steering behaviors
 * (cohesion, separation, alignment, wall avoidance) to produce emergent flocking.
 * It also responds to audio cues (beat flashes or mic input) when in FILE or MIC mode.
 *
 * Steering rules are applied via a functional pipeline for modularity.
 *
 * @author Poul Henriksen, Liz Black
 * @version 3.0
 */
public class Boid extends SmoothActor {

    //─── Behavioral constants ─────────────────────────────────────
    private static final int WALL_DIST    = 50;    // start wall avoidance within this px
    private static final int WALL_FORCE   = 200;   // maximum wall avoidance force

    /** Public audio modes set by Sky world */
    public enum AudioMode { NONE, MIC, FILE }

    // ───────── audio state (pushed from Sky) ─────────────────────
    public static AudioMode mode       = AudioMode.NONE; // current audio mode
    public static double    audioLevel = 0;              // mic loudness 0-1
    public static double    audioTone  = 0;              // pitch ratio 0-1
    public static double    speedMul   = 1;              // multiplier for flock speed
    public static boolean   beatActive = false;          // true for a few frames on beat

    // ───────── visual constants ──────────────────────────────────
    private static final greenfoot.Color[] BASE = {
        new greenfoot.Color(250,204,204), // blush
        new greenfoot.Color(255,229,180), // peach
        new greenfoot.Color(255,251,215), // butter
        new greenfoot.Color(210,242,238), // mint
        new greenfoot.Color(208,224,255)  // baby-blue
    };

    //─── constants (can be removed) ─────────────────────────
    private static final int MAX_SPEED     = 200;
    private static final int MIN_SPEED     = 50;
    private static final int SPEED_DIVIDER = 15;

    //─── Flocking distances ───────────────────────────────────────
    private static final int REPULSE_DIST  = 40;  // separation radius
    private static final int ALIGN_DIST    = 150; // alignment radius
    private static final int ATTRACT_DIST  = 150; // cohesion radius

    //─── Image cache for pulse & glow performance ─────────────────
    private int lastSize  = -1;       // last scaled size
    private int lastAlpha = -1;       // last transparency
    private GreenfootImage cached     = null; // cache of last built frame

    //─── Target-pose state ────────────────────────────────────────
    private Vector target      = null;  // current pose target position
    private int    targetTicks = 0;     // frames to hold pose
    private boolean settled    = false; // arrived & jitter state

    //─── Seek behavior constants ─────────────────────────────────
    private static final int    ARRIVAL_RADIUS = 2;  // snap distance
    private static final double SEEK_DIVISOR   = 4;  // steering strength divisor
    private static final int    SLOW_RADIUS    = 30; // begin slowdown

    //─── Master sprite copy for scaling each tick ────────────────
    private GreenfootImage baseSprite;

    //─── Functional steering-rule pipeline ───────────────────────
    private static final List<SteeringRule> RULES = List.of(
        // cohesion: steer toward nearby center of mass
        (self, flock) -> self.getFlockAttraction(ATTRACT_DIST).divide(7.5),
        // separation: repel if too close
        (self, flock) -> self.getFlockRepulsion(REPULSE_DIST).multiply(1.0),
        // alignment: match velocity of neighbors
        (self, flock) -> self.getFlockAlignment(ALIGN_DIST).divide(8.0),
        // wall avoidance: keep inside world bounds
        (self, flock) -> self.getWallForce()
    );

    /**
     * Constructor: initializes sprite, color tint, and random velocity.
     */
    public Boid() {
        GreenfootImage img = new GreenfootImage("bird4.png");
        img.scale(32, 32 * img.getHeight() / img.getWidth());
        img.rotate(180);
        img = tintOpaquePixels(img, BASE[Greenfoot.getRandomNumber(BASE.length)]);
        img.setTransparency(250);
        setImage(img);
        baseSprite = new GreenfootImage(img); // keep original for scaling

        // random initial velocity
        Vector v = new Vector();
        v.setDirection(Math.random() * 360);
        v.setLength(50);
        setVelocity(v);
        setMinimumSpeed(MIN_SPEED);
        setMaximumSpeed(MAX_SPEED);
        setSpeedDivider(SPEED_DIVIDER);
    }

    /**
     * Tint opaque pixels of the sprite with a pastel color.
     */
    private GreenfootImage tintOpaquePixels(GreenfootImage src, greenfoot.Color tint) {
        GreenfootImage img = new GreenfootImage(src);
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                greenfoot.Color base = img.getColorAt(x, y);
                if (base.getAlpha() == 0) continue;
                greenfoot.Color tinted = new greenfoot.Color(
                    tint.getRed(), tint.getGreen(), tint.getBlue(), base.getAlpha()
                );
                img.setColorAt(x, y, tinted);
            }
        }
        return img;
    }

    /**
     * Called once per frame: updates sprite (pulse & glow), then moves.
     */
    @Override
    public void act() {
        // compute pulse and transparency based on audio mode
        double pulse = 1.0;
        int alpha = 220;
        if (mode == AudioMode.FILE) {
            pulse += beatActive ? 0.35 : 0.0;
            alpha = beatActive ? 255 : 200;
        } else if (mode == AudioMode.MIC) {
            pulse += 0.6 * audioLevel;
            alpha = 180 + (int)(75 * audioLevel);
        } else {
            alpha = 240; // default glow
        }

        // rebuild or reuse cached scaled image
        int w = (int)(baseSprite.getWidth() * pulse);
        int h = (int)(baseSprite.getHeight() * pulse);
        if (w != lastSize || alpha != lastAlpha) {
            cached = new GreenfootImage(baseSprite);
            cached.scale(w, h);
            cached.setTransparency(alpha);
            lastSize = w;
            lastAlpha = alpha;
        }
        if (cached != null) setImage(cached);

        // movement: pose or flocking
        if (targetTicks > 0 && target != null) {
            poseStep();
        } else {
            applyFlocking();
            super.act();
        }
    }

    /**
     * Handles motion toward a static target (letter pose) and jitter on arrival.
     */
    private void poseStep() {
        setMinimumSpeed(0);
        Vector to = target.copy().subtract(getLocation());
        double d = to.getLength();
        if (d > ARRIVAL_RADIUS) {
            Vector desired = (d < SLOW_RADIUS) ? to.setLength(d) : to;
            setAccelaration(desired.subtract(getVelocity()).divide(SEEK_DIVISOR));
            super.act();
            return;
        }
        // arrived: jitter
        settled = true;
        Vector jitter = new Vector(Math.random()-0.5, Math.random()-0.5).divide(5);
        setAccelaration(jitter);
        super.act();
    }

    /**
     * Assigns a new pose target and resets arrival state.
     */
    public void setTarget(Vector p, int hold) {
        target = p.copy();
        targetTicks = hold;
        settled = false;
    }

    /**
     * Returns true if currently has a pose target.
     */
    public boolean hasTarget() {
        return target != null;
    }

    /**
     * Returns true if at or past arrival (including jitter completed).
     */
    public boolean atTarget() {
        return target != null &&
               (settled || getLocation().subtract(target).getLength() <= ARRIVAL_RADIUS);
    }

    /**
     * Clears pose target and restores default speed.
     */
    public void clearTarget() {
        target = null;
        targetTicks = 0;
        settled = false;
        setMinimumSpeed(50);
    }

    /**
     * Applies all steering rules and a beat kick to set acceleration.
     */
    private void applyFlocking() {
        setMinimumSpeed(50);
        List<Boid> flock = getWorld().getObjects(Boid.class);
        Vector total = RULES.stream()
                             .map(r -> r.apply(this, flock))
                             .reduce(new Vector(), (a,v) -> a.add(v));
        total.multiply(speedMul);
        if (mode == AudioMode.FILE && beatActive) {
            total.add(getVelocity().copy().setLength(70));
        }
        setAccelaration(total);
    }

    //─────────────────────────────────────────────────────────────────
    /**
     * Wall avoidance force pushes boid away from world edges.
     */
    public Vector getWallForce() {
        Vector loc = getLocation();
        Vector wallForce = new Vector();
        if (loc.getX() <= WALL_DIST) {
            double factor = (WALL_DIST - loc.getX()) / WALL_DIST;
            wallForce.add(new Vector(WALL_FORCE * factor, 0));
        }
        if (getWorld().getWidth() - loc.getX() <= WALL_DIST) {
            double factor = (WALL_DIST - (getWorld().getWidth() - loc.getX())) / WALL_DIST;
            wallForce.subtract(new Vector(WALL_FORCE * factor, 0));
        }
        if (loc.getY() <= WALL_DIST) {
            double factor = (WALL_DIST - loc.getY()) / WALL_DIST;
            wallForce.add(new Vector(0, WALL_FORCE * factor));
        }
        if (getWorld().getHeight() - loc.getY() <= WALL_DIST) {
            double factor = (WALL_DIST - (getWorld().getHeight() - loc.getY())) / WALL_DIST;
            wallForce.subtract(new Vector(0, WALL_FORCE * factor));
        }
        return wallForce;
    }

    /**
     * Retrieves neighbors within a distance (raw List to allow adding 'this').
     */
    private List getNeighbours(int distance, Class cls) {
        return getObjectsInRange(distance, cls);
    }

    /**
     * Computes center of mass of neighbors + self within distance.
     */
    public Vector getCentreOfMass(int distance) {
        List neighbours = getNeighbours(distance, Boid.class);
        neighbours.add(this);
        Vector centre = new Vector();
        for (Object o : neighbours) centre.add(((Boid)o).getLocation());
        return centre.divide(neighbours.size());
    }

    /**
     * Attraction toward center of mass for cohesion behavior.
     */
    public Vector getFlockAttraction(int distance) {
        return getCentreOfMass(distance).subtract(getLocation());
    }

    /**
     * Repulsion from too-close neighbors and obstacles.
     */
    public Vector getFlockRepulsion(int distance) {
        Vector repulse = new Vector();
        List neighbours = getNeighbours(distance, SmoothActor.class);
        for (Object o : neighbours) {
            SmoothActor other = (SmoothActor)o;
            if (other == this) continue;
            Vector dist = getLocation().subtract(other.getLocation());
            double gap = dist.getLength();
            double avoidDist = distance + other.getAvoidRadius();
            if (gap <= avoidDist) repulse.add(dist.setLength(avoidDist - gap));
        }
        return repulse;
    }

    /**
     * Computes average velocity of neighbors + self for alignment.
     */
   private Vector getAverageVelocity(int distance) {
        List neighbours = getNeighbours(distance, Boid.class);
        neighbours.add(this);
        Vector avg = new Vector();
        for (Object o : neighbours) avg.add(((Boid)o).getVelocity());
        return avg.divide(neighbours.size());
    }

    /**
     * Steering vector toward matching neighbor headings.
     */
    private Vector getFlockAlignment(int distance) {
        return getAverageVelocity(distance).subtract(getVelocity());
    }
}
