import java.util.List;

/**
 * steering rule produces an acceleration vector for one animation tick
 * treat rules like Lego bricks, sort of?  each rule looks at the current boid
 * and the entire flock, then returns a vector whose components will be added to that boid’s overall
 * acceleration
 * world-step streams over a List of rules and sums their contributions
 */

@FunctionalInterface
public interface SteeringRule {

    /**
     * compute the rule’s contribution
     *
     * @param self   the boid that the rule is being evaluated for
     * @param flock  every boid in the world including self
     * @return a new vector to add to the boid’s acceleration
     */
    Vector apply(Boid self, List<Boid> flock);
}
