package net.Realism.Interfaces;

import java.util.UUID;

/**
 * Interface for adding banking (roll) functionality to OrientedContraptionEntity
 */
public interface IOrientedContraptionEntity {
    /**
     * Get the current roll angle
     */
    float realism$getRoll();

    /**
     * Set the current roll angle
     */
    void realism$setRoll(float roll);

    /**
     * Get the previous tick's roll angle (for interpolation)
     */
    float realism$getPrevRoll();

    /**
     * Set the previous tick's roll angle (for interpolation)
     */
    void realism$setPrevRoll(float prevRoll);

    /**
     * Get the interpolated roll angle for rendering
     * @param partialTicks The partial tick time
     * @return Interpolated roll angle
     */
    float realism$getViewRoll(float partialTicks);

    UUID getuid();
}

