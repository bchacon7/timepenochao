package penochao;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;

/**
 * Robo2 - "Caçador" (agressivo, curta distância)
 *
 * Papel: recebe o alvo do Robo1 por mensagem (onMessageReceived),
 * chega perto e orbita o inimigo atirando com potência alta.
 * Se o inimigo estiver fraco (energia baixa), parte pra cima pra finalizar na batida.
 */
public class Robo2 extends TeamRobot {

    private String alvo = null;      // nome do inimigo perseguido
    private long ultimoScan = 0;     // último tick em que vimos o alvo
    private int direcao = 1;         // 1 = frente, -1 = trás (órbita)

    @Override
    public void run() {
        setColors(Color.DARK_GRAY, Color.YELLOW, Color.WHITE);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            if (getTime() - ultimoScan > 3) {
                setTurnRadarRight(360);   // procura o alvo
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // ignora parceiro
        if (isTeammate(e.getName())) {
            return;
        }

        // se não tem alvo (ou não acha o do líder há tempo), pega o que aparecer
        if (alvo == null || (!e.getName().equals(alvo) && getTime() - ultimoScan > 20)) {
            alvo = e.getName();
        }
        if (!e.getName().equals(alvo)) {
            return;
        }
        ultimoScan = getTime();

        double dist = e.getDistance();
        double absBearing = getHeadingRadians() + e.getBearingRadians();

        // ---------- RADAR: trava no alvo ----------
        setTurnRadarRightRadians(
                Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        // ---------- MOVIMENTO ----------
        if (e.getEnergy() < 20) {
            // inimigo fraco: vai pra cima dele (abalroar)
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians()));
            setAhead(dist + 30);
        } else {
            // órbita fechada: chega perto (~150) e circula
            double a = dist > 200 ? 0.5 : (dist < 100 ? -0.3 : 0);
            setTurnRightRadians(Utils.normalRelativeAngle(
                    e.getBearingRadians() + Math.PI / 2 - a * direcao));
            setAhead(100 * direcao);
            if (Math.random() < 0.02) {
                direcao = -direcao;
            }
        }

        // ---------- TIRO: mira simples com antecipação ----------
        double potencia;
        if (dist < 120) {
            potencia = 3.0;
        } else if (dist < 250) {
            potencia = 2.5;
        } else {
            potencia = 1.8;
        }
        if (getEnergy() < 10) {
            potencia = Math.min(potencia, 1.0);
        }

        double velBala = 20 - 3 * potencia;
        double desvio = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing) / velBala;
        setTurnGunRightRadians(
                Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + desvio));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(potencia);
        }
    }

    // ---------- COMUNICAÇÃO: recebe ordens do Robo1 ----------
    @Override
    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();
        if (msg instanceof String) {
            String texto = (String) msg;
            if (texto.startsWith("ALVO;")) {
                alvo = texto.substring(5);
                ultimoScan = getTime();   // dá tempo pro radar achar o novo alvo
            }
        }
    }

    // ---------- OUTROS EVENTOS ----------
    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(alvo)) {
            alvo = null;
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        direcao = -direcao;
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        direcao = -direcao;
        setBack(50 * direcao * -1);
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        if (isTeammate(e.getName())) {
            setBack(60);   // bateu no parceiro: se afasta
            return;
        }
        // bateu no inimigo: aponta o canhão e dá tiro máximo
        double ang = Utils.normalRelativeAngle(
                getHeadingRadians() + e.getBearingRadians() - getGunHeadingRadians());
        setTurnGunRightRadians(ang);
        setFire(3);
    }
}
