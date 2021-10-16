package edu.upc.epsevg.prop.robocode;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * @author Oriol and Denise
 * Robot fet per a PROP 2021-2022
 */
public class Robocop extends AdvancedRobot {

    // Variables globals
    int direction = 1; // -1: Back   1: Ahead
    boolean movingAwayFromWall = false;
    int wallDistance = 150; // Distancia a la que volem mantenir el mur
    int enemyDistance = 300; // Distancia a la que volem mantenir al enemic
    double firePower = 1.5; // Per defecte
    int fireDistance = 400; // Si l'enemic esta per sobre, no disparem

    private double enemyDirection;
    private double movimiento;
    private int dire;

    // Info enemic
    ScannedRobotEvent enemy = null;
    private static double enemylife;
    double enemyLastKnownEnergy = 0;
    int enemyX = 0;
    int enemyY = 0;
    int enemyPredictedX = 0;
    int enemyPredictedY = 0;

    /**
     * Funcio principal
     */
    public void run() {
        setColors(Color.BLACK, Color.BLACK, Color.RED, Color.BLACK, Color.RED); // Prioritats

        // Activa el modo diablo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Primera busqueda radar 360
        setTurnRadarLeft(Double.POSITIVE_INFINITY);

        while (true) {
            movement();
            radar();
            execute();
        }
    }

    /**
     * Mètode que fa un anàlisi de la situació de l'enemic
     * a més adjustem factors com l'orientació, velocitat, i el moviment del radar
     * y el canó
     * @param e Permet obtenir info de l'enemic
     */
    public void analizaSituacion(ScannedRobotEvent e) {
        // Graus entre Robocop i l'enemic i orientació de Robocop
        enemyDirection = e.getBearingRadians()+getHeadingRadians();

        // Velocitat de Robocop i sinus(Orientació de l'enemic y direcció)
        movimiento = e.getVelocity()+Math.sin(e.getHeadingRadians()+direction);

        // El canó gira cap a la dreta en la següent execució
        //l'angle relatiu de moviment - orientació de Robocop
        setTurnGunRightRadians(Utils.normalRelativeAngle(movimiento-getHeadingRadians()));

        // El radar gira a l'esquerra en la següent execució
        //l'angle restant en el gir del radar
        setTurnRadarLeftRadians(getRadarTurnRemainingRadians());

        // Màxima velocitat de robocop (píxels/girs)Máxima velocidad de robocop (píxeles/giros)
        setMaxVelocity(Rules.MAX_VELOCITY/getTurnRemaining());

        // Robocop es mou
        setAhead(100*dire);
    }

    /**
     * Gestiona una part de l'estrategia de moviment del radar
     * Nomes moura el radar si aquest no te treball pendent, indicant que ha perdut l'objectiu
     */
    private void radar() {
        // Si el radar no tiene ordenes pendientes, le hacemos buscar de nuevo
        if (getRadarTurnRemaining() == 0)
            setTurnRadarLeft(Double.POSITIVE_INFINITY);
    }

    /**
     * Gestiona l'estrategia de moviment del tanc
     */
    private void movement() {
        boolean changedDirection = false;

        changedDirection = manageWallCollision();

        if (enemy != null) {
            adjustMovementAngle();

            // Cambia de direccio si ens han disparat
            if (enemyFired() && !changedDirection)
                direction *= -1;
        }

        // Cambia de direccio cada x tics
//        if (getTime() % 60 == 0 && !changedDirection) {
//            direction *= -1;

        // Move!
        setAhead(1000 * direction);
    }

    /**
     * Gestiona una part de l'estrategia de moviment del tanc, concretament la que s'encarrega d'evitar apropar-se als murs del joc
     * Retorna el valor del boolea changedDirection, que indica si cal canviar la direccio de moviment
     */
    private boolean manageWallCollision() {
        boolean changedDirection = false;

        if (tooCloseToWall() && !movingAwayFromWall) {
            direction *= -1;
            changedDirection = true;
            movingAwayFromWall = true;
        } else if (!tooCloseToWall())
            movingAwayFromWall = false;

        return changedDirection;
    }

    /**
     * Gestiona una part de l'estrategia de moviment del tanc, concretament la que s'encarrega d'ajustar el gir segons la distancia a la que es troba l'enemic
     * Es mou de forma perpendicular al tanc enemic i es va apropant o allunyant per mantenir la distancia
     */
    private void adjustMovementAngle() {
        int factorDeAproximacio = 20;

        // Ens allunyem
        if (enemy.getDistance() < enemyDistance)
            setTurnRight(normalizeBearing(enemy.getBearing() + 90 + (factorDeAproximacio * direction)));

            // Ens apropem
        else
            setTurnRight(normalizeBearing(enemy.getBearing() + 90 - (factorDeAproximacio * direction)));
    }

    /**
     * Detecta si l'enemic ens ha disparat mirant si ha baixat l'energia
     * Retorna true si creiem que ens ha disparat, false si no
     */
    private boolean enemyFired() {
        double enemyCurrentEnergy = enemy.getEnergy();
        double dif = enemyLastKnownEnergy - enemyCurrentEnergy;
        enemyLastKnownEnergy = enemy.getEnergy();

        return dif > 0 && dif < 3;
    }


    /**
     * Retorna cert si som molt a prop del mur del mapa
    **/
    private boolean tooCloseToWall() {
        return (
            getX() <= wallDistance ||
            getY() <= wallDistance ||
            getX() >= getBattleFieldWidth() - wallDistance ||
            getY() >= getBattleFieldHeight() - wallDistance
        );
    }

    /**
     * Fixa el radar al robot que ha escanejat
     * Aquest mètode es truca automaticament quan al rang del radar detecta un robot
     * @param e l'esdeveniment scanned-robot establert
     **/
    public void onScannedRobot(ScannedRobotEvent e) {
        enemy = e;

        // Radar
        // Calcula quant ha de moure's el radar per fixar a l'enemic, per saber-ho
        //usem el bearing, que ens diu on es troba l'enemic en relació a on apunta
        //el nostre tanc, llavors hem de restar la direcció del radar amb
        //la direcció del nostre tanc i sumant el bearing de l'enemic obtenim quant ens hem de moure per centar-ho.
        // El 0.01 es per a que no arribi a 0 i pensi que ho a perdut
        double radarToEnemyAngle = normalizeBearing(getHeading() - getRadarHeading() + e.getBearing()) + 0.01;
        setTurnRadarRight(radarToEnemyAngle);

        // Gun
        // Ajusta la potencia de dispar segons la distancia de l'enemic, fins màxim 3 segons les regles
        firePower = Math.min(450 / enemy.getDistance(), Rules.MAX_BULLET_POWER); // Probar varios valores hasta encontrar uno bueno

        // Mou el cano per apuntar al enemic
        moveGun();

        // Dispara si es compleixen les condicions
        fireIfPossible();
    }

    /**
     *  Posiciona el cano en direccio a l'enemic per poder disparar
     */
    private void moveGun() {
        double enemyDistance = enemy.getDistance();
        double enemyVelocity = enemy.getVelocity();
        double enemyHeading = Math.toRadians(enemy.getHeading());
        double absoluteAngleToEnemy = Math.toRadians(getHeading() + enemy.getBearing());

        // Calcula la velocitat de la bala segons la wiki de robocode
        double bulletSpeed = 20 - 3 * firePower;

        // Calcla el temps que triga el tir en impactar -> Distancia = Velocitat x Temps ->  per tant ->  Temps = Distancia / Rate
        long time = (long) (enemyDistance / bulletSpeed);

        // Calcula la posicio del enemic
        enemyX = (int) (getX() + Math.sin(absoluteAngleToEnemy) * enemy.getDistance());
        enemyY = (int) (getY() + Math.cos(absoluteAngleToEnemy) * enemy.getDistance());

        // Calcula la posicio futura de l'enemic
        double distancia = enemyVelocity * time; // Distancia es Temps*Velocitat
        enemyPredictedX = (int) (enemyX + Math.sin(enemyHeading) * distancia);
        enemyPredictedY = (int) (enemyY + Math.cos(enemyHeading) * distancia);

        // Calcula l'angle absolut de la posicio enemiga
        double absDeg = absoluteBearing(getX(), getY(), enemyPredictedX, enemyPredictedY);

        // Gira el cano a la posicio calculada
        setTurnGunRight(normalizeBearing(absDeg - getGunHeading()));
    }

    /**
     * Dispara si es compleixen les condicions i ho fa amb la potencia adecuada segons la distancia
     */
    private void fireIfPossible() {
        // Dispara si cumpleix les condicions
        if (
                getGunHeat() == 0 &&
                        Math.abs(getGunTurnRemaining()) < 8 &&
                        enemyDistance < fireDistance &&
                        getEnergy() > 5
        )
            setFire(firePower);
    }

    /**
     * Aquest mètode es trucat quan el nostre robot és copejat per una bala.
     * @param e l’esdeveniment hit-by-bullet establert
     */

    public void onHitByBullet(HitByBulletEvent e) {
        // turnLeft(180);
    }

    /**
     * Aquest mètode es trucat quan el robot xoca amb una paret.
     * @param e l’esdeveniment hit-wall establert
     */

    public void onHitWall(HitWallEvent e) {
        direction *= -1;
        setAhead(300 * direction);
    }

    /**
     * Mètode per quan encertem i li donem a l'enemic
     * @param e ens permet rebre info de l'impacte a l'enemic
     */

    public void onBulletHit(BulletHitEvent e) {
        enemylife-=Rules.MAX_BULLET_POWER*4;
    }

    /**
     * Normalitza un angle absolut a un relatiu de -180 a +180
     * @param bearing es l'angle absolut
     */
    double normalizeBearing(double bearing) {
        while (bearing > 180) bearing -= 360;
        while (bearing < -180) bearing += 360;
        return bearing;
    }

    /**
     * Calcula el bearing absolut entre dos punts
     * @param x1,y1 es el primer punt
     * @param x2,y2 es el segon punt
     * Retorna un valor double del 0 al 360
     */
    double absoluteBearing(double x1, double y1, double x2, double y2) {
        double x = x2-x1;
        double y = y2-y1;
        double hyp = Point2D.distance(x1, y1, x2, y2);

        // L'angle que cal girar un cop nomalitzat amb la posicio del cano
        double arcSin = Math.toDegrees(Math.asin(x / hyp));
        double bearing = 0;

        // Convertir l'angle relatiu en absolut de forma cutre
        if      (x > 0 && y > 0) bearing = arcSin;          // Superior dreta: No cal adaptar res
        else if (x < 0 && y > 0) bearing = arcSin + 360;    // Superior esquerra: arcsin es negatiu, cal adaptar
        else if (x > 0 && y < 0) bearing = 180 - arcSin;    // Inferior dreta: arcsin positiu pero cal adaptar
        else if (x < 0 && y < 0) bearing = 180 - arcSin;    // Inferior esquerra: arcsin es negatiu

        return bearing;
    }


    /**
     * Aquest mètode es truca cada vegada que es pinta el robot.
     * @param g el que cal utilitzar per pintar elements gràfics per al robot
     */
    @Override
    public void onPaint(Graphics2D g) {
        // Posició enemic
        g.setColor(new Color(0x00, 0xff, 0x00, 0x80));
        g.drawLine(enemyPredictedX, enemyPredictedY, (int) getX(), (int) getY());
        g.fillRect(enemyX - 20, enemyY - 20, 40, 40);

        // Bola impacte predicted
        g.drawOval((int)enemyPredictedX-20, (int)enemyPredictedY-20, 2*20, 2*20);

        // Radi
        int r = enemyDistance;
        g.drawOval((int)getX()-r, (int)getY()-r, 2*r, 2*r);
    }
}