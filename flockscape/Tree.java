import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/** Decorative palm that never moves. */
public class Tree extends SmoothActor {

    public Tree() {
        GreenfootImage img = new GreenfootImage("palm.png");

        /* shrink art so it fits the beach */
        int maxH = 200;                           // px height limit
        if (img.getHeight() > maxH) {
            int newW = img.getWidth()  * maxH / img.getHeight();
            img.scale(newW, maxH);
        }
        setImage(img);
    }

    @Override public void act() { /* nothing – decorative */ }
}