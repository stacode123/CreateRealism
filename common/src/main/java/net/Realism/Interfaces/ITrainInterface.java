package net.Realism.Interfaces;

import net.Realism.trains.TrainSettings;
import net.Realism.trains.etcs.ETCS;

public interface ITrainInterface{
    ETCS realism$getETCS();
    void realism$setETCS(ETCS etcs);
    TrainSettings realism$getSettings();
    void realism$setSettings(TrainSettings tiltSetting);

}
