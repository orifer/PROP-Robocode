package edu.upc.epsevg.prop.robocode;

import robocode.*;
import robocode.Robot;
import robocode.util.Utils;

import java.awt.*;

/**
 * @author Oriol and Denise
 */
public class Robocop extends AdvancedRobot {

    // Variables globals

    // Posicio de enemic
    int enemyX = 0;
    int enemyY = 0;
    private static double enemylife;
    private double direction;
    private double movimiento;
    private int dire = 1;

    public void run() {
        setColors(Color.BLACK, Color.BLACK, Color.RED);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        setTurnRadarLeft(Double.POSITIVE_INFINITY);
        while (true) {
            setAhead(10);

            // Si el radar no tiene ordenes pendientes, le hacemos buscar de nuevo
            if (getRadarTurnRemaining() == 0)
                setTurnRadarLeft(Double.POSITIVE_INFINITY);

            execute();
        }
    }

    /**
     * Fija el radar al robot que ha escaneado y mas cositas
     */
    public void onScannedRobot(ScannedRobotEvent e) {

        // Parte 1: Radar
        // Algo temporal para que haga algo el tanque hasta que tengamos estrategia xD
        setTurnRight(e.getBearing());

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

        // Dispara si ha acabado de posicionar el canon y puede disparar, sino perderia el turno
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 8) {
            setFire(1);
        }

        // DEBUG //
        setDebugProperty("Body Heading: ", "" + getHeading());
        setDebugProperty("Radar Heading: ", "" + getRadarHeading());
        setDebugProperty("Enemy Bearing : ", "" + e.getBearing());
        setDebugProperty("turnAngle : ", "" + radarToEnemyAngle);
        setDebugProperty("angle : ", "" + absoluteAngleToEnemy);
    }

    /**
     * Descripcio del metode
     */
    public void onHitByBullet(HitByBulletEvent e) {
        turnLeft(180);
    }

    public void onHitWall(HitWallEvent e) {
        double bearing = e.getBearing();
        turnRight(-bearing);
        ahead(100);
    }

    double normalizeBearing(double bearing) {
        while (bearing > 180) bearing -= 360;
        while (bearing < -180) bearing += 360;
        return bearing;
    }
    
    /**
     * Metodo para cuando le damos a un enemigo
     * @param e nos permite obtener info del impacto al enemigo
     */
    @Override
    public void onBulletHit(BulletHitEvent e){
        enemylife-=Rules.MAX_BULLET_POWER*4;
    }
    
    public void analizaSituacion(ScannedRobotEvent e) { 
        // Grados entre robocop y enemigo y orientación de robocop
        direction = e.getBearingRadians()+getHeadingRadians();  
        
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

    @Override
    public void onPaint(Graphics2D g) {
        g.setColor(new Color(0x00, 0xff, 0x00, 0x80));
        g.drawLine(enemyX, enemyY, (int) getX(), (int) getY());
        g.fillRect(enemyX - 20, enemyY - 20, 40, 40);
    }
}
