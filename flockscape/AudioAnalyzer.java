import javax.sound.sampled.*;

/**
 * Captures audio from the default microphone, computes realtime loudness (RMS)
 * and dominant-frequency tone via a 1024-point FFT, and exposes these metrics.
 * 
 * <ul>
 *   <li>loudness  getLevel(): 0.0 (silence) to 1.0 (clipping, boosted)</li>
 *   <li>tone      getTone(): 0.0 (bass) to 1.0 (treble)</li>
 *   <li>raw energy getRaw(): unclipped RMS 0.0–~0.25</li>
 * </ul>
 * 
 * Runs in a background thread to avoid blocking the Greenfoot "act" loop.
 */
public class AudioAnalyzer implements AudioSource {

    /** Boosted RMS loudness [0.0–1.0]. */
    private volatile double level = 0.0;
    /** Dominant-frequency tone ratio [0.0–1.0]. */
    private volatile double tone = 0.0;
    /** Unclipped raw RMS energy. */
    private volatile double raw = 0.0;

    /**
     * Starts the microphone capture thread immediately.
     */
    public AudioAnalyzer() {
        Thread captureThread = new Thread(this::capture, "AudioAnalyzer");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /**
     * Returns boosted loudness (0.0–1.0).
     */
    @Override
    public double getLevel() {
        return level;
    }

    /**
     * Returns dominant-frequency tone (0.0 bass – 1.0 treble).
     */
    @Override
    public double getTone() {
        return tone;
    }

    /**
     * Returns raw RMS energy (unclipped).
     */
    @Override
    public double getRaw() {
        return raw;
    }

    /**
     * Continuously captures audio, computes FFT, and updates level/tone/raw.
     */
    private void capture() {
        try {
            // Configure audio format: 44.1kHz, 16-bit, mono, little-endian
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            TargetDataLine line = AudioSystem.getTargetDataLine(format);
            line.open(format);
            line.start();

            byte[] buffer = new byte[2048]; // 1024 samples ~46ms
            while (true) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead < 2) {
                    continue; // skip if not enough data
                }

                // Copy samples into real array for FFT
                double[] re = new double[1024];
                for (int i = 0; i < re.length && i < bytesRead / 2; i++) {
                    int lo = buffer[2*i] & 0xFF;
                    int hi = buffer[2*i + 1] & 0xFF;
                    short sample = (short)((hi << 8) | lo);
                    re[i] = sample / 32768.0;
                }
                double[] im = new double[1024]; // all zeros

                // In-place FFT
                fft1024(re, im);

                // Compute RMS and peak frequency bin
                double sumSq = 0.0;
                double peakMag = 0.0;
                int peakBin = 1;
                for (int k = 1; k < 512; k++) {
                    double mag = re[k]*re[k] + im[k]*im[k];
                    sumSq += mag;
                    if (mag > peakMag) {
                        peakMag = mag;
                        peakBin = k;
                    }
                }
                double rms = Math.sqrt(sumSq / 512);
                raw = rms;
                level = Math.min(1.0, rms * 20.0);

                double frequency = peakBin * 44100.0 / 1024;
                tone = clamp01((Math.log10(frequency) - 1.7) / 2.0);
            }
        } catch (Exception e) {
            System.err.println("Mic initialization failed: " + e);
        }
    }

    /**
     * In-place radix-2 FFT for 1024-sample real signal.
     * @param re real components
     * @param im imaginary components (initialized to zero)
     */
    private void fft1024(double[] re, double[] im) {
        // Cooley–Tukey
        for (int len = 1; len < 1024; len <<= 1) {
            double angle = -Math.PI / len;
            double wMulRe = Math.cos(angle);
            double wMulIm = Math.sin(angle);

            for (int start = 0; start < 1024; start += len << 1) {
                double wRe = 1.0, wIm = 0.0;
                for (int j = 0; j < len; j++) {
                    int even = start + j;
                    int odd = even + len;

                    double tre = re[odd]*wRe - im[odd]*wIm;
                    double tim = re[odd]*wIm + im[odd]*wRe;

                    re[odd] = re[even] - tre;
                    im[odd] = im[even] - tim;
                    re[even] += tre;
                    im[even] += tim;

                    double tmpRe = wRe;
                    wRe = tmpRe * wMulRe - wIm * wMulIm;
                    wIm = tmpRe * wMulIm + wIm * wMulRe;
                }
            }
        }
        // Bit-reverse permutation
        for (int i = 1, j = 0; i < 1024; i++) {
            int bit = 512;
            for (; j >= bit; bit >>= 1) j -= bit;
            j += bit;
            if (i < j) {
                double tempRe = re[i]; re[i] = re[j]; re[j] = tempRe;
                double tempIm = im[i]; im[i] = im[j]; im[j] = tempIm;
            }
        }
    }

    /**
     * Clamps a value to [0.0, 1.0].
     * @param v input value
     * @return clamped result
     */
    private double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
