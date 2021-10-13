package edu.upc.epsevg.prop.robocode;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;

/**
 * @author Oriol and Denise
 */
public class Robocop extends AdvancedRobot {

    // Variables globals
    int direction = 1; // -1: Back   1: Ahead
    boolean movingAwayFromWall = false;
    int wallDistance = 150;
    int enemyDistance = 300;
    double firePower = 1.5;
    int fireDistance = 400;

    private double enemyDirection;
    private double movimiento;
    private int dire = 1;

    // Info enemic
    ScannedRobotEvent enemy = null;
    private static double enemylife;
    double enemyLastKnownEnergy = 0;
    int enemyX = 0;
    int enemyY = 0;

    /**
     * Funcio principal
     */
    public void run() {
        setColors(Color.BLACK, Color.BLACK, Color.RED); // Prioritats

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

    public void analizaSituacion(ScannedRobotEvent e) {
        // Grados entre robocop y enemigo y orientación de robocop
        enemyDirection = e.getBearingRadians()+getHeadingRadians();

        // Velocidad de robocop y seno(Orientación del enemigo y direction)
        movimiento = e.getVelocity()+Math.sin(e.getHeadingRadians()+direction);

        // El cañón gira a la derecha en la siguiente ejecución
        //el ángulo relativo de movimiento - orientación de robocop
        setTurnGunRightRadians(Utils.normalRelativeAngle(movimiento-getHeadingRadians()));

        // El radar gira a la izquierda en la siguiente ejecución
        //el ángulo restante en el giro del radar
        setTurnRadarLeftRadians(getRadarTurnRemainingRadians());

        // Máxima velocidad de robocop (píxeles/giros)
        setMaxVelocity(Rules.MAX_VELOCITY/getTurnRemaining());

        // Robocop se mueve
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
     */
    private boolean tooCloseToWall() {
        return (
            getX() <= wallDistance ||
            getY() <= wallDistance ||
            getX() >= getBattleFieldWidth() - wallDistance ||
            getY() >= getBattleFieldHeight() - wallDistance
        );
    }

    /**
     * Fija el radar al robot que ha escaneado y mas cositas
     */
    public void onScannedRobot(ScannedRobotEvent e) {

        // Parte 1: Radar
        enemy = e;

        // Calcula cuanto ha de moverse el radar para fijar al enemigo, para saberlo usamos el bearing, que nos dice donde se encuentra el enemigo en relacion a donde apunta nuestro tanque, entonces tenemos que restar la direccion del radar con la direccion de nuestro tanque y sumando el bearing del enemigo tenemos cuanto hay que moverse para centrarlo. Esto mejor meterlo en la docu pero no se hasta que punto documentar aqui en codigo.
        // El 0.01 es para que no llegue a 0 y piense que lo ha perdido
        double radarToEnemyAngle = normalizeBearing(getHeading() - getRadarHeading() + e.getBearing()) + 0.01;

        // Movemos en radar los angulos que hemos calculado,
        setTurnRadarRight(radarToEnemyAngle);

        // Parte 2: Cañon

        // Esto lo usaremos para calcular donde disparar
        double enemyDistance = e.getDistance();
        double enemyVelocity = e.getVelocity();
        double enemyHeading = e.getHeading();
        double absoluteAngleToEnemy = Math.toRadians(getHeading() + e.getBearing());

        // Igual que con el radar
        double gunToEnemyAngle = normalizeBearing(getHeading() - getGunHeading() + e.getBearing());
        setTurnGunRight(gunToEnemyAngle);

        // Calcula la posicio del enemic
        // Falta explicar como funciona
        enemyX = (int) (getX() + Math.sin(absoluteAngleToEnemy) * e.getDistance());
        enemyY = (int) (getY() + Math.cos(absoluteAngleToEnemy) * e.getDistance());

        // Dispara si cumple las condiciones
        if (
            getGunHeat() == 0 &&
            Math.abs(getGunTurnRemaining()) < 8 &&
            enemyDistance < fireDistance &&
            getEnergy() > 5
        ) {
            setFire(firePower);
        }

        // DEBUG //
//        setDebugProperty("Body Heading: ", "" + getHeading());
//        setDebugProperty("Radar Heading: ", "" + getRadarHeading());
//        setDebugProperty("Enemy Bearing : ", "" + e.getBearing());
//        setDebugProperty("turnAngle : ", "" + radarToEnemyAngle);
//        setDebugProperty("angle : ", "" + absoluteAngleToEnemy);
    }

    /**
     * Descripcio del metode
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // turnLeft(180);
    }

    public void onHitWall(HitWallEvent e) {
        direction *= -1;
        setAhead(300 * direction);
    }

    /**
     * Metodo para cuando le damos a un enemigo
     * @param e nos permite obtener info del impacto al enemigo
     */
    @Override
    public void onBulletHit(BulletHitEvent e) {
        enemylife-=Rules.MAX_BULLET_POWER*4;
    }

    double normalizeBearing(double bearing) {
        while (bearing > 180) bearing -= 360;
        while (bearing < -180) bearing += 360;
        return bearing;
    }

    @Override
    public void onPaint(Graphics2D g) {
        // Posicio enemic
        g.setColor(new Color(0x00, 0xff, 0x00, 0x80));
        g.drawLine(enemyX, enemyY, (int) getX(), (int) getY());
        g.fillRect(enemyX - 20, enemyY - 20, 40, 40);

        // Radi
        int r = enemyDistance;
        g.drawOval((int)getX()-r, (int)getY()-r, 2*r, 2*r);
    }
}