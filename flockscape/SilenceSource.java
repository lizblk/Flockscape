/** Dummy input: always silence, no tone, no raw energy. */
public class SilenceSource implements AudioSource {
    @Override public double getLevel() { return 0; }
    @Override public double getTone()  { return 0; }
    @Override public double getRaw()   { return 0; }
}
