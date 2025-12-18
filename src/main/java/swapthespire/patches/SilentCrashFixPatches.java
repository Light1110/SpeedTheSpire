package swapthespire.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.TheSilent;
import com.megacrit.cardcrawl.core.EnergyManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.CharSelectInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class SilentCrashFixPatches {
    private static final Logger logger = LogManager.getLogger(SilentCrashFixPatches.class.getName());

    /**
     * Attempts to re-initialize TheSilent if it's detected to be broken during Dungeon initialization.
     * This runs after AbstractDungeon constructor but before Exordium/other acts perform their logic (like saving).
     */
    @SpirePatch(clz = AbstractDungeon.class, method = SpirePatch.CONSTRUCTOR, paramtypez = {String.class, String.class, AbstractPlayer.class, ArrayList.class})
    public static class DungeonInitFix {
        @SpirePostfixPatch
        public static void Postfix(AbstractDungeon __instance, String name, String levelId, AbstractPlayer p, ArrayList<String> newSpecialOneTimeEventList) {
            if (p instanceof TheSilent) {
                boolean broken = p.hb == null || 
                                 p.healthHb == null || 
                                 p.energy == null || 
                                 p.maxHealth <= 0;
                                 
                if (broken) {
                    logger.info("Detected broken Silent instance during Dungeon init. Attempting re-initialization...");
                    try {
                        CharSelectInfo loadout = p.getLoadout();
                        ReflectionHacks.privateMethod(AbstractPlayer.class, "initializeClass", 
                            String.class, String.class, String.class, String.class, 
                            CharSelectInfo.class, float.class, float.class, float.class, float.class, 
                            EnergyManager.class)
                        .invoke(p, 
                            null, 
                            "images/characters/theSilent/shoulder2.png", 
                            "images/characters/theSilent/shoulder.png", 
                            "images/characters/theSilent/corpse.png", 
                            loadout, 
                            -20.0f, -24.0f, 240.0f, 240.0f, 
                            new EnergyManager(3));
                            
                        logger.info("Re-initialization complete.");
                    } catch (Exception e) {
                        logger.error("Failed to re-initialize Silent via reflection", e);
                    }
                }
            }
        }
    }
}
