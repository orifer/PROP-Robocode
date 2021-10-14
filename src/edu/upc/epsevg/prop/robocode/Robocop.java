package edu.upc.epsevg.prop.robocode;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * @author Oriol and Denise
 */
public class Robocop extends AdvancedRobot {

    // Variables globals
    int direction = 1; // -1: Back   1: Ahead
    boolean movingAwayFromWall = false;
    int wallDistance = 150;
    int enemyDistance = 300;
    double firePower = 1.5; // Por defecto
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
    int enemyPredictedX = 0;
    int enemyPredictedY = 0;

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
        enemy = e;

        // Radar
        // Calcula cuanto ha de moverse el radar para fijar al enemigo, para saberlo usamos el bearing, que nos dice donde se encuentra el enemigo en relacion a donde apunta nuestro tanque, entonces tenemos que restar la direccion del radar con la direccion de nuestro tanque y sumando el bearing del enemigo tenemos cuanto hay que moverse para centrarlo. Esto mejor meterlo en la docu pero no se hasta que punto documentar aqui en codigo.
        // El 0.01 es para que no llegue a 0 y piense que lo ha perdido
        double radarToEnemyAngle = normalizeBearing(getHeading() - getRadarHeading() + e.getBearing()) + 0.01;
        setTurnRadarRight(radarToEnemyAngle);

        // Gun
        // Ajusta la potencia de disparo segun la distancia del enemigo, hasta maximo 3 segun las reglas
        firePower = Math.min(450 / enemy.getDistance(), Rules.MAX_BULLET_POWER); // Probar varios valores hasta encontrar uno bueno

        // Mou el cano per apuntar al enemic
        moveGun();

        // Dispara si es compleixen les condicion
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
        double bulletSpeed = 20 - firePower * 3;

        // Calcla el temps que triga el tir en impactar -> Distancia = Velocitat x Temps ->  per tant ->  Temps = Distancia / Rate
        long time = (long) (enemyDistance / bulletSpeed);

        // Calcula la posicio del enemic
        enemyX = (int) (getX() + Math.sin(absoluteAngleToEnemy) * enemy.getDistance());
        enemyY = (int) (getY() + Math.cos(absoluteAngleToEnemy) * enemy.getDistance());
        enemyPredictedX = (int) (enemyX + Math.sin(enemyHeading) * enemyVelocity * time);
        enemyPredictedY = (int) (enemyY + Math.cos(enemyHeading) * enemyVelocity * time);

        // Calcula l'angle absolut de la posicio enemiga
        double absDeg = absoluteBearing(getX(), getY(), enemyPredictedX, enemyPredictedY);

        // Gira el cano a la posicio calculada
        setTurnGunRight(normalizeBearing(absDeg - getGunHeading()));

        // DEBUG //
//        setDebugProperty("Body Heading: ", "" + getHeading());
//        setDebugProperty("Radar Heading: ", "" + getRadarHeading());
//        setDebugProperty("Enemy Bearing : ", "" + e.getBearing());
//        setDebugProperty("turnAngle : ", "" + radarToEnemyAngle);
//        setDebugProperty("angle : ", "" + absoluteAngleToEnemy);
    }

    /**
     * Dispara si es compleixen les condicions i ho fa amb la potencia adecuada segons la distancia
     */
    private void fireIfPossible() {
        // Dispara si cumple las condiciones
        if (
                getGunHeat() == 0 &&
                        Math.abs(getGunTurnRemaining()) < 8 &&
                        enemyDistance < fireDistance &&
                        getEnergy() > 5
        )
            setFire(firePower);
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

    /**
     * Normaliza un angulo absoluto a uno relativo de -180 a +180
     * @param bearing es el angulo absoluto
     */
    double normalizeBearing(double bearing) {
        while (bearing > 180) bearing -= 360;
        while (bearing < -180) bearing += 360;
        return bearing;
    }

    /**
     * Calcula l'angule absolut entre dos punts
     * @param x1,y1 es el primer punt
     * @param x2,y2 es el segon punt
     * Retorna un valor double del 0 al 360
     */
    double absoluteBearing(double x1, double y1, double x2, double y2) {
        double xo = x2-x1;
        double yo = y2-y1;
        double hyp = Point2D.distance(x1, y1, x2, y2);
        double arcSin = Math.toDegrees(Math.asin(xo / hyp));
        double bearing = 0;

        if      (xo > 0 && yo > 0) bearing = arcSin; // Dos positius: inferior-esquerra
        else if (xo < 0 && yo > 0) bearing = 360 + arcSin; // x neg, y pos: inferior-dreta. arcsin es negatiu, seria 360 - ang
        else if (xo > 0 && yo < 0) bearing = 180 - arcSin;// x pos, y neg: superior-esquerra
        else if (xo < 0 && yo < 0) bearing = 180 - arcSin; // dos negatius: superior-dreta. arcsin es negatiu, seria 180 + ang

        return bearing;
    }

    @Override
    public void onPaint(Graphics2D g) {
        // Posicio enemic
        g.setColor(new Color(0x00, 0xff, 0x00, 0x80));
        g.drawLine(enemyPredictedX, enemyPredictedY, (int) getX(), (int) getY());
        g.fillRect(enemyX - 20, enemyY - 20, 40, 40);

        // Bolita impacto predicted
        g.drawOval((int)enemyPredictedX-20, (int)enemyPredictedY-20, 2*20, 2*20);

        // Radi
        int r = enemyDistance;
        g.drawOval((int)getX()-r, (int)getY()-r, 2*r, 2*r);
    }
}