package penochao;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;

/**
 * Robo1 - "Atirador" (líder do time)
 *
 * Papel: fica a média/longa distância, trava o radar no alvo e usa
 * tiro com predição (calcula onde o inimigo vai estar quando a bala chegar).
 * Também escolhe o alvo e AVISA o parceiro (Robo2) por mensagem.
 */
public class Robo1 extends TeamRobot {

    private String alvo = null;          // nome do inimigo travado
    private long ultimoScan = 0;         // último tick em que vimos o alvo
    private int direcao = 1;             // 1 = frente, -1 = trás (movimento lateral)
    private double energiaAnteriorAlvo = 100;
    private long ultimaTrocaParede = 0;

    @Override
    public void run() {
        // corpo, canhão, radar
        setColors(Color.BLACK, Color.RED, Color.ORANGE);

        // corpo, canhão e radar giram de forma independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            // se perdemos o alvo de vista, gira o radar procurando
            if (getTime() - ultimoScan > 3) {
                setTurnRadarRight(360);
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // nunca atira nem trava em parceiro de time
        if (isTeammate(e.getName())) {
            return;
        }

        // escolhe alvo novo se não tem ou se perdeu o antigo há muito tempo
        if (alvo == null || (!e.getName().equals(alvo) && getTime() - ultimoScan > 20)) {
            definirAlvo(e.getName());
            energiaAnteriorAlvo = e.getEnergy();
        }
        if (!e.getName().equals(alvo)) {
            return;
        }
        ultimoScan = getTime();

        // reenvia o alvo de vez em quando pro parceiro não se perder
        if (getTime() % 40 == 0) {
            enviarAlvo();
        }

        double dist = e.getDistance();
        double absBearing = getHeadingRadians() + e.getBearingRadians();

        // ---------- RADAR: trava no alvo ----------
        setTurnRadarRightRadians(
                Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        // ---------- MOVIMENTO: esquiva lateral ----------
        // se a energia do inimigo caiu entre 0.1 e 3, ele provavelmente atirou -> troca de lado
        double queda = energiaAnteriorAlvo - e.getEnergy();
        if (queda > 0.09 && queda < 3.01) {
            direcao = -direcao;
        }
        energiaAnteriorAlvo = e.getEnergy();

        // um pouco de aleatoriedade pra ficar imprevisível
        if (Math.random() < 0.03) {
            direcao = -direcao;
        }

        // evita ficar colado na parede
        double margem = 60;
        boolean pertoParede = getX() < margem || getX() > getBattleFieldWidth() - margem
                || getY() < margem || getY() > getBattleFieldHeight() - margem;
        if (pertoParede && getTime() - ultimaTrocaParede > 20) {
            direcao = -direcao;
            ultimaTrocaParede = getTime();
        }

        // anda de lado (perpendicular ao inimigo), mantendo ~300 de distância
        double a = dist > 350 ? 0.4 : (dist < 200 ? -0.4 : 0);
        setTurnRightRadians(Utils.normalRelativeAngle(
                e.getBearingRadians() + Math.PI / 2 - a * direcao));
        setAhead(120 * direcao);

        // ---------- TIRO: predição linear ----------
        double potencia;
        if (dist < 150) {
            potencia = 3.0;
        } else if (dist < 400) {
            potencia = 2.0;
        } else {
            potencia = 1.2;
        }
        if (getEnergy() < 15) {
            potencia = Math.min(potencia, 1.0);   // economiza energia
        }
        potencia = Math.min(potencia, e.getEnergy() / 4 + 0.1); // não gasta além do necessário
        potencia = Math.max(0.1, potencia);

        double velBala = 20 - 3 * potencia;
        double inimigoX = getX() + dist * Math.sin(absBearing);
        double inimigoY = getY() + dist * Math.cos(absBearing);
        double predX = inimigoX;
        double predY = inimigoY;
        double larg = getBattleFieldWidth();
        double alt = getBattleFieldHeight();
        double tempo = 0;

        // simula o inimigo andando até a bala conseguir alcançar
        while ((++tempo) * velBala < Point2D.distance(getX(), getY(), predX, predY)) {
            predX += Math.sin(e.getHeadingRadians()) * e.getVelocity();
            predY += Math.cos(e.getHeadingRadians()) * e.getVelocity();
            if (predX < 18 || predY < 18 || predX > larg - 18 || predY > alt - 18) {
                predX = Math.min(Math.max(18, predX), larg - 18);
                predY = Math.min(Math.max(18, predY), alt - 18);
                break;
            }
        }

        double anguloMira = Utils.normalAbsoluteAngle(
                Math.atan2(predX - getX(), predY - getY()));
        setTurnGunRightRadians(
                Utils.normalRelativeAngle(anguloMira - getGunHeadingRadians()));

        // só atira quando o canhão esfriou e já está quase apontado
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(potencia);
        }
    }

    // ---------- COMUNICAÇÃO ----------
    private void definirAlvo(String nome) {
        alvo = nome;
        enviarAlvo();
    }

    private void enviarAlvo() {
        if (alvo == null) {
            return;
        }
        try {
            broadcastMessage("ALVO;" + alvo);
        } catch (IOException ex) {
            // se falhar, segue o jogo normalmente
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
        direcao = -direcao;   // levou tiro -> muda de lado
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        direcao = -direcao;
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // bateu em alguém (parceiro ou inimigo): dá ré pra se afastar
        direcao = -direcao;
        setAhead(100 * direcao);
    }
}
