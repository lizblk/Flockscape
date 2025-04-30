import greenfoot.*;  
import java.util.List;

/**
 * The Greenfoot world for our “Flockscape” scenario.
 * <p>
 * Boids flock around, react to audio (file or mic), and can form letter‐shapes
 * when the user types a word.
 */
public class Sky extends World {

    //CONFIG

    /** Number of frames over which we keep a rolling RMS history (~1 s at 1024‐sample buffers). */
    private static final int WIN = 43;

    /** Beat detection sensitivity: beat if level > avg * BEAT_SENS. */
    private static final double BEAT_SENS = 1.35;

    /** Frames to hold letter‐pose after all boids have arrived (default ~0.75 s). */
    private static final int SETTLE_FRAMES = 45;


    //AUDIO + BEAT STATE

    /** Current audio source (mic / file / silent). */
    private AudioSource audio = new SilenceSource();

    /** GreenfootSound used when playing a file once. */
    private GreenfootSound track = null;

    /** Circular buffer of raw RMS levels for beat detection. */
    private final double[] energy = new double[WIN];

    /** Write‐position in the energy buffer. */
    private int pos = 0;

    /** Frames remaining in the current beat “burst.” */
    private int beatFrames = 0;


    // LETTER‐POSE STATE 

    /** Currently holding a letter‐pose? */
    private boolean inPose = false;

    /** Countdown frames until boids are released from pose. */
    private int settle = 0;


    /**
     * Set up a world of size 800×600px, draw the background,
     * and populate with trees + boids.
     */
    public Sky() {
        super(800, 600, 1);
        // Background
        GreenfootImage bg = new GreenfootImage("landscape.png");
        bg.scale(getWidth(), getHeight());
        setBackground(bg);

        // Render order (boids in front of trees)
        setPaintOrder(Boid.class, Tree.class);

        populateTrees(20);
        populateBoids(90);
    }


    // ──────────────────────────────────────────────────────────

    /**
     * Per‐frame world logic:
     *  1. Check for end of file‐mode playback  
     *  2. Handle hot‐key toggles (F/M/N/Enter)  
     *  3. Compute audio metrics & beat detection  
     *  4. Manage letter‐pose release timing  
     */
    @Override
    public void act() {
        // 1) auto‐stop when file finishes playing
        if (Boid.mode == Boid.AudioMode.FILE
            && audio instanceof FileAudioAnalyzer
            && !((FileAudioAnalyzer)audio).isRunning())
        {
            stopCurrentAudio();
            audio     = new SilenceSource();
            Boid.mode = Boid.AudioMode.NONE;
        }

        // 2) hot‐keys
        String key = Greenfoot.getKey();
        if ("f".equals(key)) {                    // FILE MODE
            stopCurrentAudio();
            track       = new GreenfootSound("track1.1.wav");
            track.play();                          // play once
            audio       = new FileAudioAnalyzer("track1.1.wav");
            Boid.mode   = Boid.AudioMode.FILE;
        }
        if ("m".equals(key)) {                    // MIC MODE
            stopCurrentAudio();
            audio       = new AudioAnalyzer();
            Boid.mode   = Boid.AudioMode.MIC;
        }
        if ("n".equals(key)) {                    // NORMAL flocking (silent)
            stopCurrentAudio();
            audio       = new SilenceSource();
            Boid.mode   = Boid.AudioMode.NONE;
        }

        // Enter a word to form letter‐pose
        if ("enter".equals(key)) {
            String word = Greenfoot.ask("Enter word:");
            if (word != null && !word.trim().isEmpty()) {
                assignLetterTargets(word.trim().toUpperCase());
            }
        }

        // 3) per‐frame audio metrics
        double loud = audio.getLevel();           // boosted 0–1
        double tone = audio.getTone();            // 0=bass … 1=treble
        double rms  = audio.getRaw();             // unclamped RMS

        // map pitch → speed multiplier
        Boid.speedMul   = 0.4 + 0.6 * tone;       // roughly 0.4–1.0×

        // size‐pulse from mic only
        Boid.audioLevel = (Boid.mode == Boid.AudioMode.MIC) ? loud : 0.0;
        Boid.audioTone  = tone;

        // simple rolling‐average beat detector
        energy[pos]     = rms;
        pos             = (pos + 1) % WIN;
        double avg      = 0;
        for (double e : energy) avg += e;
        avg /= WIN;
        if (rms > avg * BEAT_SENS) beatFrames = 6;  // ~0.25 s pulse
        Boid.beatActive = (beatFrames-- > 0);

        // 4) letter‐pose release timing
        if (inPose) {
            boolean allArrived = true;
            for (Boid b : getObjects(Boid.class)) {
                if (b.hasTarget() && !b.atTarget()) {
                    allArrived = false;
                    break;
                }
            }
            if (allArrived) {
                if (--settle == 0) {
                    // release all boids back to flocking
                    for (Boid b : getObjects(Boid.class)) {
                        b.clearTarget();
                    }
                    inPose = false;
                }
            } else {
                settle = SETTLE_FRAMES;
            }
        }
    }


    /** Called when the simulation is stopped via the “Stop” button. */
    @Override
    public void stopped() {
        stopCurrentAudio();
    }

    /** Called when the simulation is restarted.  We remain silent. */
    @Override
    public void started() {
        // nothing to do
    }


    // ───────── HELPERS ─────────

    /**
     * Stops and clears the current GreenfootSound (if any).
     */
    private void stopCurrentAudio() {
        if (track != null) {
            track.stop();
            track = null;
        }
    }

    /**
     * Assigns each boid a pixel in the rasterised word and
     * enters “pose” mode for SETTLE_FRAMES frames.
     *
     * @param word  uppercase word to form
     */
    private void assignLetterTargets(String word) {
        List<Boid> flock = getObjects(Boid.class);
        List<Vector> pixels = LetterTargets.forWord(
            word, getWidth(), getHeight(), flock.size()
        );

        for (int i = 0; i < flock.size(); i++) {
            Vector target = pixels.get(i % pixels.size());
            flock.get(i).setTarget(target, Integer.MAX_VALUE);
        }
        inPose = true;
        settle = SETTLE_FRAMES;
    }

    /**
     * Add N boids in random positions.
     */
    private void populateBoids(int n) {
        for (int i = 0; i < n; i++) {
            addObject(
                new Boid(),
                Greenfoot.getRandomNumber(getWidth()),
                Greenfoot.getRandomNumber(getHeight())
            );
        }
    }

    /**
     * Add N decorative trees along the beach area.
     */
    private void populateTrees(int n) {
        int beachY = getHeight() * 2 / 3;
        for (int i = 0; i < n; i++) {
            addObject(
                new Tree(),
                Greenfoot.getRandomNumber(getWidth()),
                beachY + Greenfoot.getRandomNumber(getHeight() - beachY)
            );
        }
    }
}
