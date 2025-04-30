import greenfoot.GreenfootImage;
import greenfoot.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 LetterTargets.java
 *
 * Utility class to convert a given word into a set of target
 * pixel coordinates, suitable for assigning to boids in the sky.
 *
 * Steps:
 *  1. Choose the largest font size that fits the world dimensions.
 *  2. Sample opaque pixels at a grid step so that the total number
 *     of targets does not exceed the desired maxPoints.
 *  3. Center and shuffle the resulting list of {@link Vector}
 *     positions.
 *
 * The returned list is a constant-time subList if it exceeds maxPoints.
 *
 * This class contains only static methods and cannot be instantiated.
 *
 * @author
 * @version 1.0
 */
public final class LetterTargets {

    /**
     * Private constructor to prevent instantiation.
     */
    private LetterTargets() { }

    /**
     * Rasterise a word into a list of pixel coordinates (≤ maxPoints),
     * centered within the world and shuffled for organic assignment.
     *
     * @param word      The text to render (all caps recommended).
     * @param worldW    Width of the world in pixels.
     * @param worldH    Height of the world in pixels.
     * @param maxPoints Maximum number of target coordinates to return.
     * @return A shuffled list of {@link Vector} positions for boid targets.
     */
    public static List<Vector> forWord(String word,
                                       int worldW,
                                       int worldH,
                                       int maxPoints) {
        // 1. Choose font size: start large and decrease until it fits horizontally
        int font = worldH - 40;
        GreenfootImage img = new GreenfootImage(
                word, font, Color.WHITE, new Color(0, 0, 0, 0));
        while (font > 10) {
            GreenfootImage test = new GreenfootImage(
                    word, font, Color.WHITE, new Color(0, 0, 0, 0));
            if (test.getWidth() <= worldW - 40) {
                img = test;
                break;
            }
            font -= 4; // try a slightly smaller size
        }

        // 2. Compute offset to center the image in the world
        int offX = (worldW - img.getWidth()) / 2;
        int offY = (worldH - img.getHeight()) / 2;

        // 3. Adaptive sampling: grid step ensures ≤ maxPoints opaque pixels
        int opaqueCount = countOpaque(img);
        int step = (int) Math.ceil(Math.sqrt((double) opaqueCount / maxPoints));
        step = Math.max(step, 2); // maintain legibility

        // 4. Collect sample points
        List<Vector> targets = new ArrayList<>();
        for (int x = 0; x < img.getWidth(); x += step) {
            for (int y = 0; y < img.getHeight(); y += step) {
                if (img.getColorAt(x, y).getAlpha() > 128) {
                    targets.add(new Vector(offX + x, offY + y));
                }
            }
        }

        // 5. Shuffle and trim to maxPoints
        Collections.shuffle(targets);
        if (targets.size() > maxPoints) {
            targets = targets.subList(0, maxPoints);
        }
        return targets;
    }

    /**
     * Count the number of opaque pixels (alpha > 128) in the image.
     *
     * @param img The image to scan.
     * @return The count of opaque pixels.
     */
    private static int countOpaque(GreenfootImage img) {
        int count = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (img.getColorAt(x, y).getAlpha() > 128) {
                    count++;
                }
            }
        }
        return count;
    }
}