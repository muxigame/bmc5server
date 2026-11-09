package com.muxigame.cameralock;

/** Pure math, also exercised without Minecraft or CustomNPCs on the classpath. */
public final class MotionMath {
    private MotionMath() {}
    public static double wrap(double degrees) { return degrees - Math.floor((degrees + 180) / 360) * 360; }
    public static double facing(double camera, double forward, double left) {
        return camera - Math.toDegrees(Math.atan2(left, forward));
    }
    public static final double FAST_TURN_MS = 192.5;
    public static final double SOFT_TURN_MS = 110.0;
    public static final double SWITCH_ACCEL_MS = 30.0;
    public static final double TURN_MS = FAST_TURN_MS + SOFT_TURN_MS;
    public static double ease(double t) { return ease(t,false); }
    // Integrate velocity, so the short switch ramp joins the fast phase continuously.
    public static double ease(double t, boolean switching) {
        double elapsed = Math.clamp(t, 0, 1) * TURN_MS;
        double ramp = switching ? SWITCH_ACCEL_MS : 0;
        double lost = ramp * .45;
        double distance = FAST_TURN_MS - lost + SOFT_TURN_MS * .3;
        if (ramp > 0 && elapsed < ramp) {
            double u = elapsed / ramp;
            return ramp * (.1*u + .9*(u*u*u - .5*u*u*u*u)) / distance;
        }
        if (elapsed <= FAST_TURN_MS) return (elapsed-lost) / distance;
        double u = (elapsed - FAST_TURN_MS) / SOFT_TURN_MS;
        return (FAST_TURN_MS-lost + SOFT_TURN_MS*(.5*u-.2*u*u)) / distance;
    }
    public record Shot(double x, double y, double z, double ticks) {}
    /** Discrete Minecraft integration: position += velocity; velocity *= drag; vy -= gravity. */
    public static Shot intercept(double x, double y, double z, double vx, double vy, double vz,
                                 double inheritX, double inheritY, double inheritZ,
                                 double speed, double gravity, double drag) {
        double previous = Double.NaN, previousT = .05;
        for (double t = .05; t <= 100; t += .1) {
            double v = error(t,x,y,z,vx,vy,vz,inheritX,inheritY,inheritZ,speed,gravity,drag);
            if (v <= 0 && previous > 0) {
                double lo = previousT, hi = t;
                for (int i=0;i<40;i++) {
                    double mid=(lo+hi)/2;
                    if(error(mid,x,y,z,vx,vy,vz,inheritX,inheritY,inheritZ,speed,gravity,drag)>0) lo=mid; else hi=mid;
                }
                t=(lo+hi)/2;
                double a=(1-Math.pow(drag,t))/(1-drag);
                return new Shot((x+vx*t)/a-inheritX,
                    (y+vy*t+gravity/(1-drag)*(t-a))/a-inheritY,
                    (z+vz*t)/a-inheritZ,t);
            }
            previous=v; previousT=t;
        }
        return null;
    }
    private static double error(double t,double x,double y,double z,double vx,double vy,double vz,
                                double ix,double iy,double iz,double speed,double g,double drag) {
        double a=(1-Math.pow(drag,t))/(1-drag);
        double dx=(x+vx*t)/a-ix, dy=(y+vy*t+g/(1-drag)*(t-a))/a-iy, dz=(z+vz*t)/a-iz;
        return dx*dx+dy*dy+dz*dz-speed*speed;
    }
    public static final class Gesture {
        private double displacement;
        private int side, count;
        private long lastEntry, strokeStarted;
        private boolean consumed;
        private int consumedSide;
        private double returnTravel;
        private long lastMotion=Long.MIN_VALUE;
        public int move(double dx, double threshold, long now) {
            if(now-lastEntry>650) { count=0; side=0; }
            // Zero-delta render callbacks are not another gesture and must not keep a stroke alive.
            if(Math.abs(dx)<.05)return 0;
            boolean stopped=lastMotion!=Long.MIN_VALUE && now-lastMotion>=90;
            lastMotion=now;
            if(stopped){consumed=false;displacement=0;strokeStarted=0;returnTravel=0;}
            if(consumed) {
                // Recentring the virtual cursor does NOT end the physical stroke. Its remaining
                // samples stay latched, regardless of distance, duration or frame rate.
                if(Math.signum(dx)==-consumedSide)returnTravel+=Math.abs(dx);
                else returnTravel=0;
                if(returnTravel<6)return 0; // require a deliberate return, not mouse jitter
                consumed=false;displacement=-consumedSide*returnTravel;strokeStarted=now;
                returnTravel=0;
            } else {
            if(strokeStarted==0 || now-strokeStarted>180 || displacement*dx<0) {
                displacement=0; strokeStarted=now;
            }
            displacement+=dx;
            }
            if(Math.abs(displacement)<threshold) return 0;
            int next=displacement>0?1:-1;
            displacement=0; strokeStarted=0; lastEntry=now;
            consumed=true;consumedSide=next;returnTravel=0;
            count=next==side?count+1:1; side=next;
            if(count==2){count=0;return next;}
            return 0;
        }
        public void reset(){displacement=0;side=0;count=0;lastEntry=0;strokeStarted=0;
            consumed=false;consumedSide=0;returnTravel=0;lastMotion=Long.MIN_VALUE;}
    }
}

