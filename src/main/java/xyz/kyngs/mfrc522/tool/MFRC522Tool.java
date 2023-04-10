package xyz.kyngs.mfrc522.tool;

import com.diozero.devices.MFRC522;
import com.diozero.util.Hex;
import org.tinylog.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class MFRC522Tool {

    public static final LinkedHashMap<String, ToolTask> TASKS = new LinkedHashMap<>();
    public static final Scanner SCANNER = new Scanner(System.in);

    private static boolean running = true;

    static {
        TASKS.put("Exit", new ToolTask() {
            @Override
            public void run(MFRC522 device, MFRC522.UID card) {
                running = false;
            }

            @Override
            public boolean requiresCard() {
                return false;
            }
        });
        TASKS.put("Read card header", ((device, card) -> {}));
        TASKS.put("Set UID", ((device, card) -> {
            Logger.info("Please enter the new UID (4 bytes) in the HEX format (for example 00000000):");
            var newUid = readHex();
            if (newUid.length != 4) {
                Logger.error("Invalid UID length");
                return;
            }

            if (device.mifareSetUid(newUid, card, MFRC522.DEFAULT_KEY)) {
                Logger.info("Successfully changed UID from 0x{} to 0x{}", Hex.encodeHexString(card.getUidBytes()), Hex.encodeHexString(newUid));
            } else {
                Logger.error("Failed to change UID");
            }

        }));
        TASKS.put("Unbrick card", new ToolTask() {
            @Override
            public void run(MFRC522 device, MFRC522.UID card) {
                Logger.info("Please enter the new manufacturer id (1 byte) in the HEX format (for example 08 for MIFARE_1K):");
                var manufacturerId = readHex();
                if (manufacturerId.length != 1) {
                    Logger.error("Invalid manufacturer id length");
                    return;
                }

                if (device.mifareUnbrickUidSector(manufacturerId[0])) {
                    Logger.info("Successfully unbricked card");
                } else {
                    Logger.error("Failed to unbrick card");
                }
            }

            @Override
            public boolean requiresCard() {
                return false; //In some cases a bricked card cannot be even read.
            }
        });
        TASKS.put("Dump", (device, card) -> {
            switch (card.getType()) {
                case MIFARE_1K:
                case MIFARE_4K:
                case MIFARE_MINI:
                    device.dumpMifareClassicToConsole(card, MFRC522.DEFAULT_KEY);
                    break;
                case MIFARE_UL:
                    device.dumpMifareUltralightToConsole();
                    break;
                default:
                    Logger.error("Unsupported card type");
                    break;
            }

        });
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            Logger.error("Usage: <spi-controller> <CE> <rst-gpio>");
            Logger.error("Example: 0 0 25 for SPI0 CE0 and RESET on GPIO25");
            System.exit(1);
        }

        var controller = Integer.parseInt(args[0]);
        var ce = Integer.parseInt(args[1]);
        var resetPin = Integer.parseInt(args[2]);

        Logger.info("Initializing MFRC522 on SPI{} CE{} and RESET on GPIO{}", controller, ce, resetPin);

        try (var device = new MFRC522(controller, ce, resetPin)) {
            Logger.info("MFRC522 initialized");
            while (running) {
                Logger.info("Please select a task:");
                var i = 0;
                for (Map.Entry<String, ToolTask> entry : TASKS.entrySet()) {
                    Logger.info("{}. {}", i++, entry.getKey());
                }

                var choice = SCANNER.nextInt();
                SCANNER.nextLine();
                if (choice < 0 || choice >= i) {
                    Logger.error("Invalid choice");
                } else {
                    var task = TASKS.values().toArray(new ToolTask[0])[choice];
                    if (task.requiresCard()) {
                        Logger.info("Please put a card on the reader");
                        var card = device.awaitCardPresent(200);
                        Logger.info("Found card with UID 0x{}, type: {}", Hex.encodeHexString(card.getUidBytes()), card.getType());
                        task.run(device, card);
                    } else {
                        task.run(device, null);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("An unexpected error occurred initializing the board:");
            Logger.error(e);
            System.exit(1);
        } finally {
            Logger.info("Exiting...");
        }
    }

    private static byte[] readHex() {
        var line = SCANNER.nextLine();
        return Hex.decodeHex(line);
    }

}
