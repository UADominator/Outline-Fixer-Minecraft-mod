package dev.dominator.outline_fixer;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Mod(Shared.MODID)
public class Shared {
	public static final String MODID = "outline_fixer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);


	public Shared() {
		LOGGER.info(MODID + " - loaded");
	}
}
