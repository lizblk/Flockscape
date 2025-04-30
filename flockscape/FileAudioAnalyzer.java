import javax.sound.sampled.*;
import java.io.File;

/**
 * Reads an audio file on a background thread, computes both boosted loudness (0–1)
 * and dominant-frequency tone (0–1).
 *
 * <ul>
 *   <li>Works out-of-the-box with 16-bit PCM WAV.</li>
 *   <li>Works with MP3/OGG/etc. if JavaSound SPI decoders are on class-path
 *       (e.g. mp3spi-1.9.5.jar + tritonus_share.jar).</li>
 * </ul>
 *
 * Usage:
 * <pre>
 *   AudioSource audio = new FileAudioAnalyzer("track.wav");
 * </pre>
 *
 * Implements {@link AudioSource} for use with both file and microphone analyzers,
 * and {@link Runnable} for background processing.
 *
 * @author Poul Henriksen
 * @version 1.0
 */
public class FileAudioAnalyzer implements AudioSource, Runnable {

    /** Boosted RMS level (0–1). */
    private volatile double level = 0;

    /** Dominant-frequency tone (bass=0 … treble=1). */
    private volatile double tone = 0;

    /** Unclipped RMS raw energy. */
    private volatile double raw = 0;

    /** Flag indicating whether the analysis thread should keep running. */
    private volatile boolean running = true;

    /** Path to the audio file to analyze. */
    private final String path;

    /**
     * Constructor: starts background thread to analyze the specified audio file.
     *
     * @param path file path to the audio track
     */
    public FileAudioAnalyzer(String path) {
        this.path = path;
        new Thread(this, "FileAudioAnalyser").start();
    }

    /**
     * Returns the boosted RMS level (0–1).
     *
     * @return current boosted level
     */
    @Override
    public double getLevel() {
        return level;
    }

    /**
     * Returns the dominant-frequency tone (0–1).
     *
     * @return current tone ratio
     */
    @Override
    public double getTone() {
        return tone;
    }

    /**
     * Core loop: read audio frames, perform FFT, compute RMS and tone,
     * and pace processing to real-time.
     */
    @Override
    public void run() {
        try {
            AudioInputStream src = AudioSystem.getAudioInputStream(new File(path));
            AudioFormat target = new AudioFormat(44100, 16, 1, true, false);
            AudioInputStream in = AudioSystem.getAudioInputStream(target, src);

            byte[] buf = new byte[2048];     // 1024 samples per frame
            final long FRAME_MS = 23;        // ~23 ms per 1024-sample frame

            while ((running = (in.read(buf) != -1))) {
                // Copy buffer to real and imaginary arrays
                double[] re = new double[1024];
                double[] im = new double[1024];
                for (int i = 0; i < 1024; i++) {
                    int lo = buf[2*i] & 0xFF;
                    int hi = buf[2*i + 1] & 0xFF;
                    short s = (short)((hi << 8) | lo);
                    re[i] = s / 32768.0;
                }

                // Perform in-place FFT
                fft1024(re, im);

                // Compute RMS and dominant bin
                double sum = 0, peak = 0;
                int peakBin = 1;
                for (int k = 1; k < 512; k++) {
                    double mag = re[k]*re[k] + im[k]*im[k];
                    sum += mag;
                    if (mag > peak) {
                        peak = mag;
                        peakBin = k;
                    }
                }
                double rms = Math.sqrt(sum / 512);
                raw = rms;
                level = Math.min(1.0, rms * 20);

                double freq = peakBin * 44100.0 / 1024;
                tone = clamp01((Math.log10(freq) - 1.7) / 2.0);

                // Sleep to match playback rate
                Thread.sleep(FRAME_MS);
            }
        } catch (Exception e) {
            System.err.println("File analyser: " + e);
        }
    }

    /**
     * Returns unclipped raw RMS energy.
     *
     * @return raw RMS value
     */
    @Override
    public double getRaw() {
        return raw;
    }

    /**
     * Indicates whether the file analysis is still running (EOF not reached).
     *
     * @return true if still processing, false once done
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * In-place radix-2 FFT for 1024-point real signal.
     *
     * @param re real part array (length 1024)
     * @param im imaginary part array (length 1024)
     */
    private void fft1024(double[] re, double[] im) {
        for (int len = 1; len < 1024; len <<= 1) {
            double ang = -Math.PI / len;
            double wMulRe = Math.cos(ang);
            double wMulIm = Math.sin(ang);
            for (int start = 0; start < 1024; start += len << 1) {
                double wRe = 1, wIm = 0;
                for (int j = 0; j < len; j++) {
                    int even = start + j;
                    int odd = even + len;
                    double tre = re[odd]*wRe - im[odd]*wIm;
                    double tim = re[odd]*wIm + im[odd]*wRe;
                    re[odd] = re[even] - tre;
                    im[odd] = im[even] - tim;
                    re[even] += tre;
                    im[even] += tim;
                    double tmp = wRe;
                    wRe = tmp*wMulRe - wIm*wMulIm;
                    wIm = tmp*wMulIm + wIm*wMulRe;
                }
            }
        }
        // Bit-reverse permutation
        for (int i = 1, j = 0; i < 1024; i++) {
            int bit = 512;
            for (; j >= bit; bit >>= 1) j -= bit;
            j += bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
    }

    /**
     * Clamp a value to the [0,1] range.
     *
     * @param v input value
     * @return clamped value
     */
    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}