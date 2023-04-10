package xyz.kyngs.mfrc522.tool;

import com.diozero.devices.MFRC522;

@FunctionalInterface
public interface ToolTask {
    void run(MFRC522 device, MFRC522.UID card);

    default boolean requiresCard() {
        return true;
    }
}
