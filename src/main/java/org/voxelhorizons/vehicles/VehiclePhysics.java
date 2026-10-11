package org.voxelhorizons.vehicles;

/** Allocation-light deterministic per-tick acceleration and steering. */
public final class VehiclePhysics {
    private VehiclePhysics() {}
    public static final class State {
        public double speed;
        public float yaw;
        /** Signed vertical velocity, negative while falling (blocks/tick). */
        public double verticalSpeed;
        public boolean grounded=true;
        public State(float yaw) { this.yaw=yaw; }
    }
    public static final class Input {
        public static final Input NEUTRAL=new Input(0,0,false,false);
        public final double throttle,turn;
        public final boolean jump,sneak;
        public Input(double throttle,double turn,boolean jump,boolean sneak) {
            this.throttle=Math.max(-1,Math.min(1,throttle));
            this.turn=Math.max(-1,Math.min(1,turn));this.jump=jump;this.sneak=sneak;
        }
    }
    public static void advance(State s,VehicleDefinition d,Input input) {
        double target=input.throttle>0?d.speed*input.throttle:d.reverse*input.throttle;
        double rate=s.speed!=0&&Math.signum(target)!=Math.signum(s.speed)?d.braking:d.acceleration;
        double delta=target-s.speed;
        s.speed+=Math.abs(delta)<=rate?delta:Math.signum(delta)*rate;
        if(input.throttle==0){s.speed*=d.drag;if(Math.abs(s.speed)<0.001)s.speed=0;}
        double direction=s.speed<0?-1:1;
        double scale=Math.min(1,Math.abs(s.speed)/Math.max(0.01,d.speed*0.5));
        double yaw=(s.yaw+input.turn*d.turnRate*direction*scale)%360;
        s.yaw=(float)(yaw>180?yaw-360:yaw< -180?yaw+360:yaw);
    }
    /** No engine acceleration or sudden steering when all wheels leave the terrain. */
    public static void advanceAirborne(State state,VehicleDefinition definition) {
        state.speed*=definition.airDrag;
        if(Math.abs(state.speed)<0.0001)state.speed=0;
    }

    /** Minecraft-like gravity with a configurable terminal velocity. */
    public static double fallSpeed(double previous,double gravity,double terminalVelocity){
        return Math.max(-terminalVelocity,Math.min(0,previous-gravity));
    }

    /**
     * Nose dips if the vehicle is moving forwards and descending. Reversing off
     * a ledge tips the rear down. No pitch is introduced for a straight drop.
     */
    public static double airbornePitch(double speed,double fallSpeed,double maxPitch,
            double factor){
        double direction=Math.signum(speed);
        double amount=Math.min(1,Math.abs(speed)/0.12);
        if(amount<0.001||fallSpeed>=0)return 0;
        double radians=Math.atan2(-fallSpeed,Math.max(0.08,Math.abs(speed)));
        double pitch=-direction*Math.toDegrees(radians)*amount*factor;
        return Math.max(-maxPitch,Math.min(maxPitch,pitch));
    }

    public static double dx(float yaw){return -Math.sin(Math.toRadians(yaw));}
    public static double dz(float yaw){return Math.cos(Math.toRadians(yaw));}
}
