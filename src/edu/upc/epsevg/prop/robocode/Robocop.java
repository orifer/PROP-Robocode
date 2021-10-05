
package edu.upc.epsevg.prop.robocode;

import robocode.HitByBulletEvent;
import robocode.Robot;
import robocode.ScannedRobotEvent;

/**
 * @author Oriol and Denise
 */
public class Robocop extends Robot {

    public void run() {
        turnLeft(getHeading());
        while (true) {
            ahead(1000);
            turnRight(90);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        fire(1);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        turnLeft(180);
    }
}
