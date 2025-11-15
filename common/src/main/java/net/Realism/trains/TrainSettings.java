package net.Realism.trains;

import net.minecraft.nbt.CompoundTag;


public class TrainSettings {
    public enum tiltSetting{
        NONE,PASSIVE,ACTIVE,CUSTOM;
    }
    public enum accelerationSetting{
        NONE,REALISTIC, CUSTOM;
    }
    public TrainSettings trainSettings(tiltSetting ts, accelerationSetting as){
        this.ts = ts;
        this.as = as;
        return this;
    }

    public tiltSetting ts = tiltSetting.NONE;
    public accelerationSetting as = accelerationSetting.REALISTIC;

    public double customAcceleration = 1.0;

    public float customMaxTilt = 5.0f;

    public Double customMinSpeed = 40.0;

    public float customTiltIntensity = 1.0f;

    public boolean Inside = true;

    public boolean isInside() {
        return Inside;
    }

    public boolean isTiltActive() {
        return ts == tiltSetting.ACTIVE;
    }

    public boolean isTiltPassive() {
        return ts == tiltSetting.PASSIVE;
    }

    public boolean isTiltCustom() {
        return ts == tiltSetting.CUSTOM;
    }

    public boolean isTiltNone() {
        return ts == tiltSetting.NONE;
    }

    public boolean isAccelerationRealistic() {
        return as == accelerationSetting.REALISTIC;
    }

    public boolean isAccelerationCustom() {
        return as == accelerationSetting.CUSTOM;
    }

    public boolean isAccelerationNone() {
        return as == accelerationSetting.NONE;
    }

    public static tiltSetting tiltFromString(String s) {
        if (s == null) return tiltSetting.NONE;
        try {
            return tiltSetting.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return tiltSetting.NONE;
        }
    }

    public CompoundTag savetoNBT(){
        CompoundTag tag = new CompoundTag();
        tag.putString("TiltSetting", this.ts.toString());
        tag.putString("AccelerationSetting", this.as.toString());
        tag.putDouble("CustomAcceleration", this.customAcceleration);
        tag.putFloat("CustomMaxTilt", this.customMaxTilt);
        tag.putDouble("CustomMinSpeed", this.customMinSpeed);
        tag.putFloat("CustomTiltIntensity", this.customTiltIntensity);
        tag.putBoolean("Inside", this.Inside);
        return tag;
    }

    public static TrainSettings fromNBT(CompoundTag tag){
        TrainSettings settings = new TrainSettings();
        settings.ts = tiltFromString(tag.getString("TiltSetting"));
        settings.as = accelerationSetting.valueOf(tag.getString("AccelerationSetting"));
        settings.customAcceleration = tag.getDouble("CustomAcceleration");
        settings.customMaxTilt = tag.getFloat("CustomMaxTilt");
        settings.customMinSpeed = tag.getDouble("CustomMinSpeed");
        settings.customTiltIntensity = tag.getFloat("CustomTiltIntensity");
        settings.Inside = tag.getBoolean("Inside");
        return settings;
    }
}