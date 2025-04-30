public interface AudioSource {
    /** Loudness 0 – 1 (after boost curve) */
    double getLevel();
    /** Dominant-frequency ratio 0 – 1  (bass → 0, treble → 1) */
    double getTone();
    /* unclipped RMS 0-~0.25 (never saturates) */
    default double getRaw() { return getLevel(); }   // fallback for any old impls
}


